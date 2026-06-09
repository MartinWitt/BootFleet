package io.github.martinwitt.todoapp.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TodoServiceSnoozeTest {

    @Mock private TodoRepository todoRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private TodoService todoService;

    @Test
    void shouldSnoozeExistingDeadlineByGivenDays() {
        Todo todo = new Todo();
        todo.setId(1L);
        todo.setDeadline(LocalDateTime.of(2026, 6, 1, 9, 0));
        when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));
        when(todoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<LocalDateTime> result = todoService.snooze(1L, 7);

        assertThat(result).contains(LocalDateTime.of(2026, 6, 8, 9, 0));
    }

    @Test
    void shouldSnoozeFromNowWhenNoDeadlineIsSet() {
        Todo todo = new Todo();
        todo.setId(2L);
        when(todoRepository.findById(2L)).thenReturn(Optional.of(todo));
        when(todoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime before = LocalDateTime.now();
        Optional<LocalDateTime> result = todoService.snooze(2L, 3);
        LocalDateTime after = LocalDateTime.now();

        assertThat(result).isPresent();
        assertThat(result.get()).isBetween(before.plusDays(3), after.plusDays(3));
    }

    @Test
    void shouldReturnEmptyOptionalWhenTodoNotFound() {
        when(todoRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<LocalDateTime> result = todoService.snooze(99L, 7);

        assertThat(result).isEmpty();
    }
}
