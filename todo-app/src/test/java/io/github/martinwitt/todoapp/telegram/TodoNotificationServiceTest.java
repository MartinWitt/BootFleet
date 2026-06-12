package io.github.martinwitt.todoapp.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.martinwitt.todoapp.todo.Todo;
import io.github.martinwitt.todoapp.todo.TodoService;
import io.github.martinwitt.todoapp.todo.TodoStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@ExtendWith(MockitoExtension.class)
class TodoNotificationServiceTest {

    private static final long CHAT_ID = 123456789L;

    @Mock private TodoService todoService;
    @Mock private TelegramClient telegramClient;

    private TodoNotificationService service;

    @BeforeEach
    void setUp() {
        lenient().when(todoService.findRecurring()).thenReturn(java.util.List.of());
        service =
                new TodoNotificationService(
                        todoService,
                        telegramClient,
                        new TelegramProperties(
                                new TelegramProperties.Bot("token", "bot"),
                                new TelegramProperties.User(CHAT_ID)));
    }

    @Test
    void shouldSendEmptyMessageWhenNoDueTodos() throws Exception {
        when(todoService.findDueTodos()).thenReturn(List.of());

        service.sendTodaysTodos();

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getText()).isEqualTo("Keine offenen Todos für heute 🎉");
    }

    @Test
    void shouldSendOneMessagePerDueTodo() throws Exception {
        Todo first = todoWithTitle("Buy milk");
        Todo second = todoWithTitle("Call dentist");
        when(todoService.findDueTodos()).thenReturn(List.of(first, second));

        service.sendTodaysTodos();

        verify(telegramClient, org.mockito.Mockito.times(2)).execute(any(SendMessage.class));
    }

    @Test
    void shouldIncludeTitleInTodoMessage() throws Exception {
        Todo todo = todoWithTitle("Walk the dog");
        when(todoService.findDueTodos()).thenReturn(List.of(todo));

        service.sendTodaysTodos();

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getText()).contains("Walk the dog");
    }

    @Test
    void shouldIncludeDeadlineInMessageWhenPresent() throws Exception {
        Todo todo = todoWithTitle("Pay rent");
        todo.setDeadline(LocalDateTime.of(2026, 6, 7, 0, 0));
        when(todoService.findDueTodos()).thenReturn(List.of(todo));

        service.sendTodaysTodos();

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getText()).contains("07.06.2026");
    }

    @Test
    void shouldCallChangeStatusDoneWhenMarkingDone() throws Exception {
        service.markDone(42L, 100);

        verify(todoService).changeStatus(42L, TodoStatus.DONE);
    }

    @Test
    void shouldEditMessageToRemoveKeyboardAfterMarkingDone() throws Exception {
        service.markDone(42L, 100);

        ArgumentCaptor<EditMessageReplyMarkup> captor =
                ArgumentCaptor.forClass(EditMessageReplyMarkup.class);
        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getMessageId()).isEqualTo(100);
    }

    private Todo todoWithTitle(String title) {
        Todo todo = new Todo();
        todo.setId(1L);
        todo.setTitle(title);
        todo.setStatus(TodoStatus.OPEN);
        return todo;
    }
}
