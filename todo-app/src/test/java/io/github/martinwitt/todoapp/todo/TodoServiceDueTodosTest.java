package io.github.martinwitt.todoapp.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TodoServiceDueTodosTest {

    @Mock private TodoRepository todoRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
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
        assertThat(captured.toLocalDate()).isEqualTo(LocalDate.now(ZoneId.of("Europe/Berlin")));
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
