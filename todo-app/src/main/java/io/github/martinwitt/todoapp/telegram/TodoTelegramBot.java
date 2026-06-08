package io.github.martinwitt.todoapp.telegram;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
class TodoTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final String botToken;
    private final TodoNotificationService notificationService;

    TodoTelegramBot(TelegramProperties props, TodoNotificationService notificationService) {
        this.botToken = props.bot().token();
        this.notificationService = notificationService;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            if ("/todos".equals(update.getMessage().getText())) {
                notificationService.sendTodaysTodos();
            }
        } else if (update.hasCallbackQuery()) {
            String data = update.getCallbackQuery().getData();
            if (data == null) return;
            try {
                long todoId = Long.parseLong(data);
                int messageId = update.getCallbackQuery().getMessage().getMessageId();
                notificationService.markDone(todoId, messageId);
            } catch (NumberFormatException ignored) {
            }
        }
    }
}
