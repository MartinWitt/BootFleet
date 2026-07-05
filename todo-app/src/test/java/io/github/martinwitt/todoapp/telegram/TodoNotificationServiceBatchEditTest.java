package io.github.martinwitt.todoapp.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.github.martinwitt.todoapp.todo.Todo;
import io.github.martinwitt.todoapp.todo.TodoService;
import io.github.martinwitt.todoapp.todo.TodoStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@ExtendWith(MockitoExtension.class)
class TodoNotificationServiceBatchEditTest {

    private static final int MESSAGE_ID = 500;

    @Mock private TodoService todoService;
    @Mock private TelegramClient telegramClient;

    private TodoNotificationService service;

    @BeforeEach
    void setUp() throws Exception {
        service =
                new TodoNotificationService(
                        todoService,
                        telegramClient,
                        new TelegramProperties(
                                new TelegramProperties.Bot("token", "bot"),
                                new TelegramProperties.User(123L)));
        when(todoService.findRecurring()).thenReturn(List.of());
        Message sentMessage = org.mockito.Mockito.mock(Message.class);
        when(sentMessage.getMessageId()).thenReturn(MESSAGE_ID);
        when(telegramClient.execute(any(SendMessage.class))).thenReturn(sentMessage);
    }

    @Test
    void markDone_shouldMarkOnlyThatLineDoneAndKeepOtherLinesAndButtons() throws Exception {
        Todo first = todo(1L, "Buy milk");
        Todo second = todo(2L, "Call dentist");
        when(todoService.findDueTodos()).thenReturn(List.of(first, second));
        service.sendTodaysTodos();

        service.markDone(1L, MESSAGE_ID);

        ArgumentCaptor<EditMessageText> captor = ArgumentCaptor.forClass(EditMessageText.class);
        org.mockito.Mockito.verify(telegramClient).execute(captor.capture());
        EditMessageText edit = captor.getValue();
        assertThat(edit.getMessageId()).isEqualTo(MESSAGE_ID);
        assertThat(edit.getText()).contains("✅ 1. Buy milk").contains("2. Call dentist");
        assertThat(edit.getText()).doesNotContain("✅ 2. Call dentist");
        InlineKeyboardMarkup keyboard = (InlineKeyboardMarkup) edit.getReplyMarkup();
        assertThat(keyboard.getKeyboard()).hasSize(1);
    }

    @Test
    void markDone_shouldEndUpWithEmptyKeyboardWhenAllTodosInBatchAreDone() throws Exception {
        Todo only = todo(1L, "Buy milk");
        when(todoService.findDueTodos()).thenReturn(List.of(only));
        service.sendTodaysTodos();

        service.markDone(1L, MESSAGE_ID);

        ArgumentCaptor<EditMessageText> captor = ArgumentCaptor.forClass(EditMessageText.class);
        org.mockito.Mockito.verify(telegramClient).execute(captor.capture());
        EditMessageText edit = captor.getValue();
        assertThat(edit.getText()).isEqualTo("✅ 1. Buy milk");
        InlineKeyboardMarkup keyboard = (InlineKeyboardMarkup) edit.getReplyMarkup();
        assertThat(keyboard.getKeyboard()).isEmpty();
    }

    @Test
    void markDone_shouldForgetMessageOnceAllTodosInBatchAreDone() throws Exception {
        Todo only = todo(1L, "Buy milk");
        when(todoService.findDueTodos()).thenReturn(List.of(only));
        service.sendTodaysTodos();
        service.markDone(1L, MESSAGE_ID);

        service.markDone(999L, MESSAGE_ID);

        org.mockito.Mockito.verify(telegramClient)
                .execute(
                        any(
                                org.telegram.telegrambots.meta.api.methods.updatingmessages
                                        .EditMessageReplyMarkup.class));
    }

    private Todo todo(Long id, String title) {
        Todo todo = new Todo();
        todo.setId(id);
        todo.setTitle(title);
        todo.setStatus(TodoStatus.OPEN);
        return todo;
    }
}
