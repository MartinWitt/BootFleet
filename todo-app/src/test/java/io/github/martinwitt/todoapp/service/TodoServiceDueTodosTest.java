package io.github.martinwitt.todoapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.github.martinwitt.todoapp.domain.Todo;
import io.github.martinwitt.todoapp.domain.TodoStatus;
import io.github.martinwitt.todoapp.repository.TagRepository;
import io.github.martinwitt.todoapp.repository.TodoRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TodoServiceDueTodosTest {

    @Mock private TodoRepository todoRepository;
    @Mock private TagRepository tagRepository;
    @InjectMocks private TodoService todoService;

    @Test
    void shouldReturnOpenTodosWithDeadlineTodayOrBefore() {
        Todo overdue = new Todo();
        overdue.setTitle("Overdue task");
        overdue.setStatus(TodoStatus.OPEN);
        overdue.setDeadline(LocalDateTime.now().minusDays(1));

        when(todoRepository.findByStatusAndDeadlineLessThanEqual(
                        eq(TodoStatus.OPEN), any(LocalDateTime.class)))
                .thenReturn(List.of(overdue));

        List<Todo> result = todoService.findDueTodos();

        assertThat(result).containsExactly(overdue);
    }

    @Test
    void shouldPassEndOfTodayAsDeadlineCutoff() {
        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        when(todoRepository.findByStatusAndDeadlineLessThanEqual(
                        eq(TodoStatus.OPEN), cutoffCaptor.capture()))
                .thenReturn(List.of());

        todoService.findDueTodos();

        LocalDateTime captured = cutoffCaptor.getValue();
        assertThat(captured.toLocalDate()).isEqualTo(LocalDate.now());
        assertThat(captured.getHour()).isEqualTo(23);
    }

    @Test
    void shouldReturnEmptyListWhenNoDueTodos() {
        when(todoRepository.findByStatusAndDeadlineLessThanEqual(
                        eq(TodoStatus.OPEN), any(LocalDateTime.class)))
                .thenReturn(List.of());

        assertThat(todoService.findDueTodos()).isEmpty();
    }
}
