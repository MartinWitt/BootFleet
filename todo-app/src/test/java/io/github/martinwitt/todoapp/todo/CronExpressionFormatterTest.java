package io.github.martinwitt.todoapp.todo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CronExpressionFormatterTest {

    @Test
    void shouldDescribeDailyCron() {
        assertThat(CronExpressionFormatter.describe("0 9 * * *")).isEqualTo("Daily at 9:00");
    }

    @Test
    void shouldDescribeSingleWeekdayCron() {
        assertThat(CronExpressionFormatter.describe("0 18 * * 1"))
                .isEqualTo("Every Monday at 18:00");
    }

    @Test
    void shouldDescribeWeekdaysCron() {
        assertThat(CronExpressionFormatter.describe("30 8 * * 1-5"))
                .isEqualTo("Weekdays (Mon–Fri) at 8:30");
    }

    @Test
    void shouldDescribeWeekendsCron() {
        assertThat(CronExpressionFormatter.describe("0 10 * * 6,0")).isEqualTo("Weekends at 10:00");
    }

    @Test
    void shouldDescribeMonthlyCron() {
        assertThat(CronExpressionFormatter.describe("0 8 1 * *"))
                .isEqualTo("Monthly (1st) at 8:00");
    }

    @Test
    void shouldReturnEmptyStringForBlankCron() {
        assertThat(CronExpressionFormatter.describe("  ")).isEmpty();
        assertThat(CronExpressionFormatter.describe(null)).isEmpty();
    }

    @Test
    void shouldReturnRawCronWhenTooFewFields() {
        assertThat(CronExpressionFormatter.describe("0 9 *")).isEqualTo("0 9 *");
    }

    @Test
    void shouldHandleMultipleSpacesBetweenFields() {
        assertThat(CronExpressionFormatter.describe("0  18  *  *  1"))
                .isEqualTo("Every Monday at 18:00");
    }

    @Test
    void shouldParseWildcardAsAllWeekdays() {
        assertThat(CronExpressionFormatter.parseWeekdays("*")).containsExactly(0, 1, 2, 3, 4, 5, 6);
    }

    @Test
    void shouldParseWeekdayRange() {
        assertThat(CronExpressionFormatter.parseWeekdays("1-5")).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void shouldParseWeekdayList() {
        assertThat(CronExpressionFormatter.parseWeekdays("6,0")).containsExactly(6, 0);
    }
}
