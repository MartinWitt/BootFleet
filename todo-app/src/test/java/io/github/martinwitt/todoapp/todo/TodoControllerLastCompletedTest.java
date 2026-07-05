package io.github.martinwitt.todoapp.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.martinwitt.todoapp.tag.TagService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

@ExtendWith(MockitoExtension.class)
class TodoControllerLastCompletedTest {

    @Mock private TodoService todoService;

    @Mock private TagService tagService;

    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private TodoController todoController;

    @Test
    void shouldExposeLastCompletedDateForRecurringTodo() {
        Todo todo = recurringTodo("daily standup", "0 9 * * 1-5");
        todo.setId(1L);
        LocalDateTime completedAt = LocalDateTime.of(2026, 6, 30, 8, 0);
        todo.setLastCompletedAt(completedAt);
        when(todoService.findAllSorted()).thenReturn(List.of(todo));
        when(tagService.findAll()).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        todoController.list(null, model);

        assertThat(lastCompletedDates(model).get(1L))
                .isEqualTo(
                        DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)
                                .format(completedAt));
    }

    @Test
    void shouldNotExposeLastCompletedDateWhenNeverCompleted() {
        Todo todo = recurringTodo("daily standup", "0 9 * * 1-5");
        todo.setId(2L);
        when(todoService.findAllSorted()).thenReturn(List.of(todo));
        when(tagService.findAll()).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        todoController.list(null, model);

        assertThat(lastCompletedDates(model)).doesNotContainKey(2L);
    }

    @SuppressWarnings("unchecked")
    private Map<Long, String> lastCompletedDates(Model model) {
        return (Map<Long, String>) model.getAttribute("lastCompletedDates");
    }

    private Todo recurringTodo(String title, String cron) {
        Todo t = new Todo();
        t.setTitle(title);
        t.setCronExpression(cron);
        t.setStatus(TodoStatus.OPEN);
        return t;
    }
}
