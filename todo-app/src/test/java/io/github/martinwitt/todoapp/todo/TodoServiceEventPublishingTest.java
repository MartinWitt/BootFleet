package io.github.martinwitt.todoapp.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TodoServiceEventPublishingTest {

    @Mock private TodoRepository todoRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private TodoService todoService;

    @Test
    void shouldPublishTodoSavedEventAfterSave() {
        Todo todo = new Todo();
        todo.setTitle("Test");
        when(todoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        todoService.save(todo);

        ArgumentCaptor<TodoSavedEvent> captor = ArgumentCaptor.forClass(TodoSavedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().todo()).isSameAs(todo);
    }

    @Test
    void shouldPublishTodoDeletedEventAfterDelete() {
        todoService.deleteById(42L);

        ArgumentCaptor<TodoDeletedEvent> captor = ArgumentCaptor.forClass(TodoDeletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().todoId()).isEqualTo(42L);
    }
}
