package io.github.martinwitt.todoapp.telegram;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.martinwitt.todoapp.todo.SendTodosNowEvent;
import io.github.martinwitt.todoapp.todo.TodoService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@ExtendWith(MockitoExtension.class)
class TodoNotificationServiceEventTest {

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
                                new TelegramProperties.User(123L)));
    }

    @Test
    void shouldCallSendTodaysTodosWhenEventReceived() throws Exception {
        when(todoService.findDueTodos()).thenReturn(List.of());

        service.onSendNow(new SendTodosNowEvent());

        verify(todoService).findDueTodos();
    }
}
