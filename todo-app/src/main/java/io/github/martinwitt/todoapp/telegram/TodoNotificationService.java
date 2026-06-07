package io.github.martinwitt.todoapp.telegram;

import io.github.martinwitt.todoapp.domain.Todo;
import io.github.martinwitt.todoapp.domain.TodoStatus;
import io.github.martinwitt.todoapp.service.TodoService;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Service
class TodoNotificationService {

    private static final Logger log = LoggerFactory.getLogger(TodoNotificationService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final TodoService todoService;
    private final TelegramClient telegramClient;
    private final long chatId;

    TodoNotificationService(
            TodoService todoService, TelegramClient telegramClient, TelegramProperties props) {
        this.todoService = todoService;
        this.telegramClient = telegramClient;
        this.chatId = props.user().chatId();
    }

    @Scheduled(cron = "0 0 9 * * *")
    void sendDailyNotification() {
        sendTodaysTodos();
    }

    @EventListener
    void onSendNow(SendTodosNowEvent event) {
        sendTodaysTodos();
    }

    void sendTodaysTodos() {
        List<Todo> todos = todoService.findDueTodos();
        if (todos.isEmpty()) {
            send(
                    SendMessage.builder()
                            .chatId(chatId)
                            .text("Keine offenen Todos für heute 🎉")
                            .build());
            return;
        }
        for (Todo todo : todos) {
            sendTodoMessage(todo);
        }
    }

    void markDone(long todoId, int messageId) {
        todoService.changeStatus(todoId, TodoStatus.DONE);
        EditMessageReplyMarkup edit =
                EditMessageReplyMarkup.builder()
                        .chatId(chatId)
                        .messageId(messageId)
                        .replyMarkup(
                                InlineKeyboardMarkup.builder()
                                        .keyboard(Collections.emptyList())
                                        .build())
                        .build();
        try {
            telegramClient.execute(edit);
        } catch (TelegramApiException e) {
            log.error("Failed to edit message after marking todo {} done", todoId, e);
        }
    }

    private void sendTodoMessage(Todo todo) {
        InlineKeyboardButton doneButton =
                InlineKeyboardButton.builder()
                        .text("✓ Erledigt")
                        .callbackData(String.valueOf(todo.getId()))
                        .build();
        InlineKeyboardMarkup keyboard =
                InlineKeyboardMarkup.builder()
                        .keyboardRow(new InlineKeyboardRow(doneButton))
                        .build();
        send(
                SendMessage.builder()
                        .chatId(chatId)
                        .text(buildMessageText(todo))
                        .replyMarkup(keyboard)
                        .build());
    }

    String buildMessageText(Todo todo) {
        StringBuilder sb = new StringBuilder("📌 ").append(todo.getTitle());
        if (todo.getDeadline() != null) {
            sb.append("\n⏰ ").append(todo.getDeadline().format(DATE_FMT));
        }
        return sb.toString();
    }

    private void send(SendMessage message) {
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram message", e);
        }
    }
}
