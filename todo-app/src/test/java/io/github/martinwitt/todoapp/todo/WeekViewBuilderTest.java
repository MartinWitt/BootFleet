package io.github.martinwitt.todoapp.todo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WeekViewBuilderTest {

    // Monday, June 8, 2026
    private static final LocalDate MONDAY = LocalDate.of(2026, 6, 8);

    @Test
    void shouldBuildSevenDaysStartingMonday() {
        List<Map<String, Object>> week = WeekViewBuilder.build(List.of(), MONDAY);

        assertThat(week).hasSize(7);
        assertThat(week.get(0).get("name")).isEqualTo("Mon");
        assertThat(week.get(6).get("name")).isEqualTo("Sun");
        assertThat(week.get(0).get("isToday")).isEqualTo(true);
    }

    @Test
    void shouldPlaceRecurringTodoOnItsCronWeekday() {
        Todo monday = new Todo();
        monday.setTitle("Küche");
        monday.setCronExpression("0 18 * * 1");

        List<Map<String, Object>> week = WeekViewBuilder.build(List.of(monday), MONDAY);

        assertThat(todosOf(week, 0)).containsExactly(monday);
        assertThat(todosOf(week, 1)).isEmpty();
    }

    @Test
    void shouldPlaceOneTimeTodoOnItsDeadlineDay() {
        Todo tuesday = new Todo();
        tuesday.setTitle("Bad");
        tuesday.setStatus(TodoStatus.OPEN);
        tuesday.setDeadline(LocalDateTime.of(2026, 6, 9, 12, 0));

        List<Map<String, Object>> week = WeekViewBuilder.build(List.of(tuesday), MONDAY);

        assertThat(todosOf(week, 1)).containsExactly(tuesday);
        assertThat(todosOf(week, 0)).isEmpty();
    }

    @Test
    void shouldExcludeDoneOneTimeTodos() {
        Todo done = new Todo();
        done.setTitle("Done task");
        done.setStatus(TodoStatus.DONE);
        done.setDeadline(LocalDateTime.of(2026, 6, 9, 12, 0));

        List<Map<String, Object>> week = WeekViewBuilder.build(List.of(done), MONDAY);

        assertThat(todosOf(week, 1)).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private List<Todo> todosOf(List<Map<String, Object>> week, int dayIndex) {
        return (List<Todo>) week.get(dayIndex).get("todos");
    }
}
