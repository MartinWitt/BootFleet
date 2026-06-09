package io.github.martinwitt.todoapp.todo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the Mon–Sun week view model: recurring todos by cron weekday, one-time by deadline. */
final class WeekViewBuilder {

    private static final String[] DAY_LABELS = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d");

    private WeekViewBuilder() {}

    static List<Map<String, Object>> build(List<Todo> todos, LocalDate today) {
        LocalDate monday = today.with(DayOfWeek.MONDAY);

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            // DayOfWeek.getValue(): 1=Mon..7=Sun → cron: 0=Sun,1=Mon..6=Sat
            int cronDay = date.getDayOfWeek().getValue() % 7;

            List<Todo> dayTodos = new ArrayList<>();
            for (Todo todo : todos) {
                if (todo.isRecurring()) {
                    String[] parts = todo.getCronExpression().trim().split("\\s+");
                    if (parts.length >= CronExpressionFormatter.CRON_FIELD_COUNT
                            && CronExpressionFormatter.parseWeekdays(parts[4]).contains(cronDay)) {
                        dayTodos.add(todo);
                    }
                } else if (todo.getDeadline() != null
                        && todo.getDeadline().toLocalDate().equals(date)
                        && todo.getStatus() != TodoStatus.DONE) {
                    dayTodos.add(todo);
                }
            }

            Map<String, Object> day = new LinkedHashMap<>();
            day.put("name", DAY_LABELS[i]);
            day.put("date", date.format(DATE_FMT));
            day.put("isToday", date.equals(today));
            day.put("todos", dayTodos);
            result.add(day);
        }
        return result;
    }
}
