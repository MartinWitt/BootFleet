package io.github.martinwitt.todoapp.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.martinwitt.todoapp.todo.Todo;
import io.github.martinwitt.todoapp.todo.TodoService;
import io.github.martinwitt.todoapp.todo.TodoStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@ExtendWith(MockitoExtension.class)
class TodoNotificationServiceMorningBriefTest {

    private static final long CHAT_ID = 123L;
    // June 12 at 09:00 — stable fixed date for deterministic tests
    private static final String CRON_JUNE12 = "0 9 12 6 *";
    private static final LocalDate JUNE12 = LocalDate.of(2026, 6, 12);
    private static final LocalDate JUNE13 = LocalDate.of(2026, 6, 13);

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

    // ── cronFiresOn unit tests ──────────────────────────────────────────────

    @Test
    void cronFiresOn_shouldReturnTrueWhenExpressionMatchesDate() {
        assertThat(TodoNotificationService.cronFiresOn(CRON_JUNE12, JUNE12)).isTrue();
    }

    @Test
    void cronFiresOn_shouldReturnFalseWhenExpressionDoesNotMatchDate() {
        assertThat(TodoNotificationService.cronFiresOn(CRON_JUNE12, JUNE13)).isFalse();
    }

    @Test
    void cronFiresOn_shouldReturnTrueForDailyCron() {
        assertThat(TodoNotificationService.cronFiresOn("0 9 * * *", JUNE12)).isTrue();
    }

    @Test
    void cronFiresOn_shouldReturnFalseForInvalidCron() {
        assertThat(TodoNotificationService.cronFiresOn("not-a-cron", JUNE12)).isFalse();
    }

    // ── findRecurringDueOn unit tests ───────────────────────────────────────

    @Test
    void findRecurringDueOn_shouldIncludeTodoWhenCronMatchesDate() {
        Todo todo = recurringTodo(1L, CRON_JUNE12);
        when(todoService.findRecurring()).thenReturn(List.of(todo));

        List<Todo> result = service.findRecurringDueOn(JUNE12);

        assertThat(result).containsExactly(todo);
    }

    @Test
    void findRecurringDueOn_shouldExcludeTodoWhenCronDoesNotMatchDate() {
        Todo todo = recurringTodo(1L, CRON_JUNE12);
        when(todoService.findRecurring()).thenReturn(List.of(todo));

        List<Todo> result = service.findRecurringDueOn(JUNE13);

        assertThat(result).isEmpty();
    }

    @Test
    void findRecurringDueOn_shouldExcludeDoneTodos() {
        Todo done = recurringTodo(1L, CRON_JUNE12);
        done.setStatus(TodoStatus.DONE);
        when(todoService.findRecurring()).thenReturn(List.of(done));

        List<Todo> result = service.findRecurringDueOn(JUNE12);

        assertThat(result).isEmpty();
    }

    // ── sendTodaysTodos integration (uses daily cron "0 9 * * *" so fires on any date) ──

    @Test
    void sendTodaysTodos_shouldSendRecurringTodoThatFiresToday() throws Exception {
        Todo recurring = recurringTodo(10L, "0 9 * * *");
        when(todoService.findDueTodos()).thenReturn(List.of());
        when(todoService.findRecurring()).thenReturn(List.of(recurring));

        service.sendTodaysTodos();

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getText()).contains("Daily task");
    }

    @Test
    void sendTodaysTodos_shouldNotDuplicateWhenTodoAppearsInBothDeadlineAndCronLists()
            throws Exception {
        Todo todo = recurringTodo(5L, "0 9 * * *");
        when(todoService.findDueTodos()).thenReturn(List.of(todo));
        when(todoService.findRecurring()).thenReturn(List.of(todo));

        service.sendTodaysTodos();

        verify(telegramClient, times(1)).execute(any(SendMessage.class));
    }

    @Test
    void sendTodaysTodos_shouldSendEmptyMessageWhenNeitherDeadlineNorRecurringTodosExist()
            throws Exception {
        when(todoService.findDueTodos()).thenReturn(List.of());
        when(todoService.findRecurring()).thenReturn(List.of());

        service.sendTodaysTodos();

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getText()).isEqualTo("Keine offenen Todos für heute 🎉");
    }

    @Test
    void sendTodaysTodos_shouldNotIncludeRecurringTodosWithStatusDone() throws Exception {
        Todo done = recurringTodo(10L, "0 9 * * *");
        done.setStatus(TodoStatus.DONE);
        when(todoService.findDueTodos()).thenReturn(List.of());
        when(todoService.findRecurring()).thenReturn(List.of(done));

        service.sendTodaysTodos();

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getText()).isEqualTo("Keine offenen Todos für heute 🎉");
    }

    @Test
    void sendTodaysTodos_shouldSendBothDeadlineAndRecurringTodosWhenDifferent() throws Exception {
        Todo deadline = new Todo();
        deadline.setId(1L);
        deadline.setTitle("Deadline task");
        deadline.setStatus(TodoStatus.OPEN);

        Todo recurring = recurringTodo(2L, "0 9 * * *");

        when(todoService.findDueTodos()).thenReturn(List.of(deadline));
        when(todoService.findRecurring()).thenReturn(List.of(recurring));

        service.sendTodaysTodos();

        verify(telegramClient, times(2)).execute(any(SendMessage.class));
    }

    private Todo recurringTodo(Long id, String cron) {
        Todo todo = new Todo();
        todo.setId(id);
        todo.setTitle("Daily task");
        todo.setCronExpression(cron);
        todo.setStatus(TodoStatus.OPEN);
        return todo;
    }
}
