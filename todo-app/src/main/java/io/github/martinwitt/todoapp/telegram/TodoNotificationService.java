package io.github.martinwitt.todoapp.telegram;

import io.github.martinwitt.todoapp.todo.SendTodosNowEvent;
import io.github.martinwitt.todoapp.todo.Todo;
import io.github.martinwitt.todoapp.todo.TodoService;
import io.github.martinwitt.todoapp.todo.TodoStatus;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.message.Message;
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

    // In-memory only: lost on restart, and entries are removed once every todo in the
    // batch is done. Acceptable for a single-user personal bot; move to a DB table if
    // that ever becomes a problem.
    private final Map<Integer, TrackedMessage> trackedMessages = new ConcurrentHashMap<>();

    private record TrackedMessage(String header, List<Todo> todos) {}

    TodoNotificationService(
            TodoService todoService, TelegramClient telegramClient, TelegramProperties props) {
        this.todoService = todoService;
        this.telegramClient = telegramClient;
        this.chatId = props.user().chatId();
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Berlin")
    void sendDailyNotification() {
        sendTodaysTodos();
    }

    @EventListener
    void onSendNow(SendTodosNowEvent event) {
        sendTodaysTodos();
    }

    void sendRecurringTodo(Long todoId) {
        todoService
                .findById(todoId)
                .ifPresent(
                        t -> {
                            if (t.getStatus() == TodoStatus.DONE) {
                                todoService.changeStatus(todoId, TodoStatus.OPEN);
                            }
                            sendTodoMessage(t);
                        });
    }

    void sendTodaysTodos() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Berlin"));
        List<Todo> dueTodos = todoService.findDueTodos();
        List<Todo> recurringToday = findRecurringDueOn(today);
        List<Todo> someday = todoService.findSomeday();

        Set<Long> seen = new HashSet<>();
        List<Todo> all = new ArrayList<>(dueTodos);
        dueTodos.forEach(t -> seen.add(t.getId()));
        recurringToday.stream().filter(t -> !seen.contains(t.getId())).forEach(all::add);

        if (all.isEmpty() && someday.isEmpty()) {
            send(
                    SendMessage.builder()
                            .chatId(chatId)
                            .text("Keine offenen Todos für heute 🎉")
                            .build());
            return;
        }
        sendBatch(null, all);
        sendBatch("📋 Someday (noch offen):", someday);
    }

    List<Todo> findRecurringDueOn(LocalDate date) {
        return todoService.findRecurring().stream()
                .filter(t -> t.getStatus() != TodoStatus.DONE)
                .filter(t -> cronFiresOn(t.getCronExpression(), date))
                .toList();
    }

    static boolean cronFiresOn(String fiveFieldCron, LocalDate date) {
        try {
            CronExpression expr = CronExpression.parse("0 " + fiveFieldCron.trim());
            ZoneId berlin = ZoneId.of("Europe/Berlin");
            ZonedDateTime justBefore = date.atStartOfDay(berlin).minusNanos(1);
            ZonedDateTime next = expr.next(justBefore);
            return next != null && next.toLocalDate().equals(date);
        } catch (Exception e) {
            log.warn("Cannot evaluate cron '{}'", fiveFieldCron, e);
            return false;
        }
    }

    void markDone(long todoId, int messageId) {
        todoService.changeStatus(todoId, TodoStatus.DONE);
        TrackedMessage tracked = trackedMessages.get(messageId);
        if (tracked == null) {
            clearKeyboard(messageId);
            return;
        }
        BatchContent content;
        synchronized (tracked) {
            tracked.todos().stream()
                    .filter(t -> t.getId().equals(todoId))
                    .findFirst()
                    .ifPresent(t -> t.setStatus(TodoStatus.DONE));
            content = buildBatchContent(tracked.header(), tracked.todos());
        }
        EditMessageText edit =
                EditMessageText.builder()
                        .chatId(chatId)
                        .messageId(messageId)
                        .text(content.text())
                        .replyMarkup(content.keyboard())
                        .build();
        try {
            telegramClient.execute(edit);
            if (content.keyboard().getKeyboard().isEmpty()) {
                trackedMessages.remove(messageId);
            }
        } catch (TelegramApiException e) {
            log.error("Failed to edit message after marking todo {} done", todoId, e);
        }
    }

    private void clearKeyboard(int messageId) {
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
            log.error("Failed to clear keyboard for message {}", messageId, e);
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

    private void sendBatch(String header, List<Todo> todos) {
        if (todos.isEmpty()) {
            return;
        }
        BatchContent content = buildBatchContent(header, todos);
        SendMessage message =
                SendMessage.builder()
                        .chatId(chatId)
                        .text(content.text())
                        .replyMarkup(content.keyboard())
                        .build();
        try {
            Message sent = telegramClient.execute(message);
            if (sent != null) {
                trackedMessages.put(
                        sent.getMessageId(), new TrackedMessage(header, new ArrayList<>(todos)));
            }
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram message", e);
        }
    }

    private record BatchContent(String text, InlineKeyboardMarkup keyboard) {}

    private BatchContent buildBatchContent(String header, List<Todo> todos) {
        List<String> lines = new ArrayList<>();
        if (header != null) {
            lines.add(header);
        }
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (int i = 0; i < todos.size(); i++) {
            Todo todo = todos.get(i);
            int number = i + 1;
            String label = number + ". " + todo.getTitle();
            if (todo.getDeadline() != null) {
                label += " ⏰ " + todo.getDeadline().format(DATE_FMT);
            }
            if (todo.getStatus() == TodoStatus.DONE) {
                lines.add("✅ " + label);
            } else {
                lines.add(label);
                rows.add(
                        new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text("✓ " + number + ". " + todo.getTitle())
                                        .callbackData(String.valueOf(todo.getId()))
                                        .build()));
            }
        }
        return new BatchContent(
                String.join("\n", lines), InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    private void send(SendMessage message) {
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram message", e);
        }
    }
}
