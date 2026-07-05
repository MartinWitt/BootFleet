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
class TodoServiceCompletionTest {

    @Mock private TodoRepository todoRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private TodoService todoService;

    @Test
    void shouldStampLastCompletedAtWhenMarkingRecurringTodoDone() {
        Todo todo = new Todo();
        todo.setId(1L);
        todo.setCronExpression("0 9 * * 1");
        when(todoRepository.findById(1L)).thenReturn(Optional.of(todo));
        when(todoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime before = LocalDateTime.now();
        todoService.changeStatus(1L, TodoStatus.DONE);
        LocalDateTime after = LocalDateTime.now();

        assertThat(todo.getLastCompletedAt()).isBetween(before, after);
    }

    @Test
    void shouldOverwriteLastCompletedAtOnRepeatedDoneMarks() {
        Todo todo = new Todo();
        todo.setId(2L);
        todo.setCronExpression("0 9 * * 1");
        todo.setLastCompletedAt(LocalDateTime.of(2020, 1, 1, 0, 0));
        when(todoRepository.findById(2L)).thenReturn(Optional.of(todo));
        when(todoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        todoService.changeStatus(2L, TodoStatus.DONE);

        assertThat(todo.getLastCompletedAt()).isAfter(LocalDateTime.of(2020, 1, 1, 0, 0));
    }

    @Test
    void shouldNotStampLastCompletedAtForNonRecurringTodo() {
        Todo todo = new Todo();
        todo.setId(3L);
        when(todoRepository.findById(3L)).thenReturn(Optional.of(todo));
        when(todoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        todoService.changeStatus(3L, TodoStatus.DONE);

        assertThat(todo.getLastCompletedAt()).isNull();
    }
}
