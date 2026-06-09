package io.github.martinwitt.todoapp.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.martinwitt.todoapp.todo.Todo;
import io.github.martinwitt.todoapp.todo.TodoDeletedEvent;
import io.github.martinwitt.todoapp.todo.TodoSavedEvent;
import io.github.martinwitt.todoapp.todo.TodoService;
import io.github.martinwitt.todoapp.todo.TodoStatus;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;

@SuppressWarnings({"unchecked", "rawtypes"})
@ExtendWith(MockitoExtension.class)
class RecurringTodoSchedulerTest {

    @Mock private TaskScheduler taskScheduler;
    @Mock private TodoService todoService;
    @Mock private TodoNotificationService notificationService;
    @Mock private ScheduledFuture scheduledFuture;
    @Mock private ApplicationArguments applicationArguments;

    private RecurringTodoScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new RecurringTodoScheduler(taskScheduler, todoService, notificationService);
    }

    @Test
    void shouldScheduleEachRecurringTodoOnStartup() throws Exception {
        Todo first = recurringTodo(1L, "0 18 * * 1");
        Todo second = recurringTodo(2L, "0 18 * * 2");
        when(todoService.findRecurring()).thenReturn(List.of(first, second));
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class)))
                .thenReturn(scheduledFuture);

        scheduler.run(applicationArguments);

        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void shouldConvertFiveFieldCronToSixFieldSpringFormat() throws Exception {
        Todo todo = recurringTodo(1L, "0 18 * * 1");
        when(todoService.findRecurring()).thenReturn(List.of(todo));
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class)))
                .thenReturn(scheduledFuture);

        scheduler.run(applicationArguments);

        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(taskScheduler).schedule(any(Runnable.class), triggerCaptor.capture());
        assertThat(((CronTrigger) triggerCaptor.getValue()).getExpression())
                .isEqualTo("0 0 18 * * 1");
    }

    @Test
    void shouldScheduleTaskWhenRecurringTodoSaved() {
        Todo todo = recurringTodo(1L, "0 18 * * 1");
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class)))
                .thenReturn(scheduledFuture);

        scheduler.onTodoSaved(new TodoSavedEvent(todo));

        verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void shouldNotScheduleWhenSavedTodoHasNoCron() {
        Todo todo = new Todo();
        todo.setId(1L);
        todo.setTitle("One-time");

        scheduler.onTodoSaved(new TodoSavedEvent(todo));

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void shouldNotScheduleWhenRecurringTodoIsDone() {
        Todo done = recurringTodo(1L, "0 18 * * 1");
        done.setStatus(TodoStatus.DONE);

        scheduler.onTodoSaved(new TodoSavedEvent(done));

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void shouldCancelOldScheduleAndRescheduleWhenCronUpdated() {
        Todo original = recurringTodo(1L, "0 18 * * 1");
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class)))
                .thenReturn(scheduledFuture);
        scheduler.onTodoSaved(new TodoSavedEvent(original));

        Todo updated = recurringTodo(1L, "0 9 * * 2");
        scheduler.onTodoSaved(new TodoSavedEvent(updated));

        verify(scheduledFuture).cancel(false);
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void shouldCancelScheduleWhenTodoDeleted() {
        Todo todo = recurringTodo(1L, "0 18 * * 1");
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class)))
                .thenReturn(scheduledFuture);
        scheduler.onTodoSaved(new TodoSavedEvent(todo));

        scheduler.onTodoDeleted(new TodoDeletedEvent(1L));

        verify(scheduledFuture).cancel(false);
    }

    @Test
    void shouldDoNothingWhenDeletingUnscheduledTodo() {
        scheduler.onTodoDeleted(new TodoDeletedEvent(99L));

        verify(scheduledFuture, never()).cancel(false);
    }

    private Todo recurringTodo(Long id, String cron) {
        Todo todo = new Todo();
        todo.setId(id);
        todo.setTitle("Recurring");
        todo.setCronExpression(cron);
        return todo;
    }
}
