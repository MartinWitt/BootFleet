package io.github.martinwitt.todoapp.telegram;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@ExtendWith(MockitoExtension.class)
class TodoTelegramBotTest {

    private static final String TOKEN = "test-token";

    @Mock private TodoNotificationService notificationService;

    private TodoTelegramBot bot;

    @BeforeEach
    void setUp() {
        bot =
                new TodoTelegramBot(
                        new TelegramProperties(
                                new TelegramProperties.Bot(TOKEN, "bot"),
                                new TelegramProperties.User(0L)),
                        notificationService);
    }

    @Test
    void shouldReturnConfiguredToken() {
        assertThat(bot.getBotToken()).isEqualTo(TOKEN);
    }

    @Test
    void shouldCallSendTodaysTodosOnTodosCommand() throws Exception {
        Update update = updateWithText("/todos");

        bot.consume(update);

        verify(notificationService).sendTodaysTodos();
    }

    @Test
    void shouldIgnoreUnknownCommands() throws Exception {
        Update update = updateWithText("/unknown");

        bot.consume(update);

        verify(notificationService, never()).sendTodaysTodos();
    }

    @Test
    void shouldCallMarkDoneOnCallbackQuery() throws Exception {
        Update update = callbackUpdate("42", 100);

        bot.consume(update);

        verify(notificationService).markDone(42L, 100);
    }

    private static org.assertj.core.api.AbstractStringAssert<?> assertThat(String actual) {
        return org.assertj.core.api.Assertions.assertThat(actual);
    }

    private static Update updateWithText(String text) {
        Update update = org.mockito.Mockito.mock(Update.class);
        Message message = org.mockito.Mockito.mock(Message.class);
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn(text);
        return update;
    }

    private static Update callbackUpdate(String data, int messageId) {
        Update update = org.mockito.Mockito.mock(Update.class);
        CallbackQuery callbackQuery = org.mockito.Mockito.mock(CallbackQuery.class);
        MaybeInaccessibleMessage message = org.mockito.Mockito.mock(Message.class);
        when(update.hasMessage()).thenReturn(false);
        when(update.hasCallbackQuery()).thenReturn(true);
        when(update.getCallbackQuery()).thenReturn(callbackQuery);
        when(callbackQuery.getData()).thenReturn(data);
        when(callbackQuery.getMessage()).thenReturn(message);
        when(message.getMessageId()).thenReturn(messageId);
        return update;
    }
}
