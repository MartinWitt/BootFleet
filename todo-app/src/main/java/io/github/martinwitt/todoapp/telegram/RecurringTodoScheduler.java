package io.github.martinwitt.todoapp.telegram;

import io.github.martinwitt.todoapp.todo.Todo;
import io.github.martinwitt.todoapp.todo.TodoDeletedEvent;
import io.github.martinwitt.todoapp.todo.TodoSavedEvent;
import io.github.martinwitt.todoapp.todo.TodoService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

@Component
class RecurringTodoScheduler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RecurringTodoScheduler.class);

    private final TaskScheduler taskScheduler;
    private final TodoService todoService;
    private final TodoNotificationService notificationService;
    private final Map<Long, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();

    RecurringTodoScheduler(
            TaskScheduler taskScheduler,
            TodoService todoService,
            TodoNotificationService notificationService) {
        this.taskScheduler = taskScheduler;
        this.todoService = todoService;
        this.notificationService = notificationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        todoService.findRecurring().forEach(this::scheduleTask);
        log.info("Scheduled {} recurring todo(s)", scheduled.size());
    }

    @EventListener
    void onTodoSaved(TodoSavedEvent event) {
        Todo todo = event.todo();
        cancelExisting(todo.getId());
        if (todo.isRecurring()) {
            scheduleTask(todo);
        }
    }

    @EventListener
    void onTodoDeleted(TodoDeletedEvent event) {
        cancelExisting(event.todoId());
    }

    private void scheduleTask(Todo todo) {
        Long todoId = todo.getId();
        String springCron = toSpringCron(todo.getCronExpression());
        try {
            ScheduledFuture<?> future =
                    taskScheduler.schedule(
                            () -> notificationService.sendRecurringTodo(todoId),
                            new CronTrigger(springCron));
            scheduled.put(todoId, future);
            log.debug("Scheduled recurring todo {} with cron '{}'", todoId, springCron);
        } catch (Exception e) {
            log.error("Failed to schedule recurring todo {} with cron '{}'", todoId, springCron, e);
        }
    }

    private void cancelExisting(Long todoId) {
        ScheduledFuture<?> future = scheduled.remove(todoId);
        if (future != null) {
            future.cancel(false);
        }
    }

    /** Converts a 5-field unix cron to Spring's 6-field format by prepending the seconds field. */
    private String toSpringCron(String fiveFieldCron) {
        return "0 " + fiveFieldCron.trim();
    }
}
