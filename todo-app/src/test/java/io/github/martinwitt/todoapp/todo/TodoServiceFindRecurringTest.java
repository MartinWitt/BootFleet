package io.github.martinwitt.todoapp.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TodoServiceFindRecurringTest {

    @Mock private TodoRepository todoRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private TodoService todoService;

    @Test
    void shouldReturnTodosWithCronExpression() {
        Todo recurring = todoWithCron("0 18 * * 1");
        when(todoRepository.findByCronExpressionIsNotNull()).thenReturn(List.of(recurring));

        assertThat(todoService.findRecurring()).containsExactly(recurring);
    }

    @Test
    void shouldFilterOutBlankCronExpressions() {
        Todo blank = todoWithCron("   ");
        Todo recurring = todoWithCron("0 18 * * 1");
        when(todoRepository.findByCronExpressionIsNotNull()).thenReturn(List.of(blank, recurring));

        assertThat(todoService.findRecurring()).containsExactly(recurring);
    }

    @Test
    void shouldReturnEmptyListWhenNoRecurringTodos() {
        when(todoRepository.findByCronExpressionIsNotNull()).thenReturn(List.of());

        assertThat(todoService.findRecurring()).isEmpty();
    }

    private Todo todoWithCron(String cron) {
        Todo todo = new Todo();
        todo.setId(1L);
        todo.setTitle("Recurring");
        todo.setCronExpression(cron);
        return todo;
    }
}
