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
class TodoServiceFindSomedayTest {

    @Mock private TodoRepository todoRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private TodoService todoService;

    @Test
    void shouldReturnOpenTodosWithNoDeadlineAndNoCron() {
        Todo someday = somedayTodo(1L, "Buy a book");
        when(todoRepository.findByStatusAndDeadlineIsNullAndCronExpressionIsNullOrderByPositionAsc(
                        TodoStatus.OPEN))
                .thenReturn(List.of(someday));

        assertThat(todoService.findSomeday()).containsExactly(someday);
    }

    @Test
    void shouldReturnEmptyListWhenNoSomedayTodos() {
        when(todoRepository.findByStatusAndDeadlineIsNullAndCronExpressionIsNullOrderByPositionAsc(
                        TodoStatus.OPEN))
                .thenReturn(List.of());

        assertThat(todoService.findSomeday()).isEmpty();
    }

    @Test
    void shouldReturnMultipleSomedayTodosInPositionOrder() {
        Todo first = somedayTodo(1L, "First");
        first.setPosition(1);
        Todo second = somedayTodo(2L, "Second");
        second.setPosition(2);
        when(todoRepository.findByStatusAndDeadlineIsNullAndCronExpressionIsNullOrderByPositionAsc(
                        TodoStatus.OPEN))
                .thenReturn(List.of(first, second));

        List<Todo> result = todoService.findSomeday();

        assertThat(result).containsExactly(first, second);
    }

    private Todo somedayTodo(Long id, String title) {
        Todo todo = new Todo();
        todo.setId(id);
        todo.setTitle(title);
        todo.setStatus(TodoStatus.OPEN);
        return todo;
    }
}
