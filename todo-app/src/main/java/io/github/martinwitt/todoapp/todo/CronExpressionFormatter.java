package io.github.martinwitt.todoapp.todo;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Formats 5-field unix cron expressions ({@code min hour dom month dow}) for display and parses
 * their day-of-week field.
 */
final class CronExpressionFormatter {
    static final int CRON_FIELD_COUNT = 5;

    private static final String WILDCARD = "*";
    private static final String MONTHLY_DOM = "1";
    private static final String WEEKDAYS_DOW = "1-5";
    private static final String WEEKENDS_DOW_ALT1 = "6,0";
    private static final String WEEKENDS_DOW_ALT2 = "0,6";

    private static final Map<String, String> DAY_NAMES = new LinkedHashMap<>();

    static {
        DAY_NAMES.put("0", "Sunday");
        DAY_NAMES.put("1", "Monday");
        DAY_NAMES.put("2", "Tuesday");
        DAY_NAMES.put("3", "Wednesday");
        DAY_NAMES.put("4", "Thursday");
        DAY_NAMES.put("5", "Friday");
        DAY_NAMES.put("6", "Saturday");
    }

    private CronExpressionFormatter() {}

    static String describe(String cron) {
        if (cron == null || cron.isBlank()) return "";
        String[] p = cron.trim().split(" ");
        if (p.length < CRON_FIELD_COUNT) return cron;

        String min = p[0], hour = p[1], dom = p[2], dow = p[4];
        String time = "";
        if (!WILDCARD.equals(hour) && !WILDCARD.equals(min)) {
            try {
                time = String.format(" at %s:%02d", hour, Integer.parseInt(min));
            } catch (NumberFormatException _) {
            }
        }

        if (WILDCARD.equals(dow) && WILDCARD.equals(dom)) return "Daily" + time;
        if (MONTHLY_DOM.equals(dom) && WILDCARD.equals(dow)) return "Monthly (1st)" + time;
        if (WEEKDAYS_DOW.equals(dow)) return "Weekdays (Mon–Fri)" + time;
        if (WEEKENDS_DOW_ALT1.equals(dow) || WEEKENDS_DOW_ALT2.equals(dow))
            return "Weekends" + time;

        if (DAY_NAMES.containsKey(dow)) return "Every " + DAY_NAMES.get(dow) + time;

        String joined =
                Arrays.stream(dow.split(","))
                        .map(d -> DAY_NAMES.getOrDefault(d.trim(), d))
                        .collect(Collectors.joining(", "));
        return joined.isEmpty() ? cron : joined + time;
    }

    /** Expands a cron day-of-week field (e.g. {@code "1-5"}, {@code "6,0"}) to 0=Sun..6=Sat. */
    static Set<Integer> parseWeekdays(String field) {
        Set<Integer> days = new LinkedHashSet<>();
        if (WILDCARD.equals(field)) {
            for (int d = 0; d <= 6; d++) days.add(d);
            return days;
        }
        for (String part : field.split(",")) {
            part = part.trim();
            if (part.contains("-")) {
                String[] range = part.split("-", 2);
                try {
                    int start = Integer.parseInt(range[0].trim());
                    int end = Integer.parseInt(range[1].trim());
                    for (int d = start; d <= end; d++) days.add(d);
                } catch (NumberFormatException _) {
                }
            } else {
                try {
                    days.add(Integer.parseInt(part));
                } catch (NumberFormatException _) {
                }
            }
        }
        return days;
    }
}
