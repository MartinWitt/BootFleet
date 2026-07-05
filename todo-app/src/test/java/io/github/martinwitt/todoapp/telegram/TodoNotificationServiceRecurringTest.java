package io.github.martinwitt.todoapp.telegram;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.martinwitt.todoapp.todo.Todo;
import io.github.martinwitt.todoapp.todo.TodoService;
import io.github.martinwitt.todoapp.todo.TodoStatus;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@ExtendWith(MockitoExtension.class)
class TodoNotificationServiceRecurringTest {

    private static final long CHAT_ID = 123L;

    @Mock private TodoService todoService;
    @Mock private TelegramClient telegramClient;

    private TodoNotificationService service;

    @BeforeEach
    void setUp() {
        service =
                new TodoNotificationService(
                        todoService,
                        telegramClient,
                        new TelegramProperties(
                                new TelegramProperties.Bot("token", "bot"),
                                new TelegramProperties.User(CHAT_ID)));
    }

    @Test
    void shouldSendNotificationWhenRecurringTodoExists() throws Exception {
        Todo todo = new Todo();
        todo.setId(7L);
        todo.setTitle("Küche");
        todo.setStatus(TodoStatus.OPEN);
        when(todoService.findById(7L)).thenReturn(Optional.of(todo));

        service.sendRecurringTodo(7L);

        verify(telegramClient).execute(any(SendMessage.class));
    }

    @Test
    void shouldDoNothingWhenRecurringTodoNotFound() throws Exception {
        when(todoService.findById(99L)).thenReturn(Optional.empty());

        service.sendRecurringTodo(99L);

        verify(telegramClient, never()).execute(any(SendMessage.class));
    }

    @Test
    void shouldResetDoneTodoToOpenAndSendNotificationWhenCronFires() throws Exception {
        Todo done = new Todo();
        done.setId(5L);
        done.setTitle("Küche");
        done.setStatus(TodoStatus.DONE);
        when(todoService.findById(5L)).thenReturn(Optional.of(done));

        service.sendRecurringTodo(5L);

        verify(todoService).changeStatus(5L, TodoStatus.OPEN);
        verify(telegramClient).execute(any(SendMessage.class));
    }
}
