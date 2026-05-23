package io.github.martinwitt.todoapp.web;

import io.github.martinwitt.todoapp.domain.Tag;
import io.github.martinwitt.todoapp.domain.Todo;
import io.github.martinwitt.todoapp.domain.TodoStatus;
import io.github.martinwitt.todoapp.service.TagService;
import io.github.martinwitt.todoapp.service.TodoService;
import jakarta.validation.Valid;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class TodoController {
    private final TodoService todoService;
    private final TagService tagService;

    public TodoController(TodoService todoService, TagService tagService) {
        this.todoService = todoService;
        this.tagService = tagService;
    }

    @GetMapping({"/", "/todos"})
    public String list(@RequestParam(value = "tag", required = false) String tag, Model model) {
        List<Tag> tags = tagService.findAll();
        List<Todo> todos =
                (tag == null || tag.isBlank())
                        ? todoService.findAllSorted()
                        : todoService.findByTagName(tag);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");
        Map<Long, String> formattedDeadlines = new HashMap<>();
        Map<Long, String> cronDescriptions = new HashMap<>();
        for (Todo todo : todos) {
            if (todo.getDeadline() != null) {
                formattedDeadlines.put(todo.getId(), todo.getDeadline().format(formatter));
            }
            if (todo.getCronExpression() != null && !todo.getCronExpression().isBlank()) {
                cronDescriptions.put(todo.getId(), humanReadableCron(todo.getCronExpression()));
            }
        }

        model.addAttribute("todos", todos);
        model.addAttribute("formattedDeadlines", formattedDeadlines);
        model.addAttribute("cronDescriptions", cronDescriptions);
        model.addAttribute("weekDays", buildWeekDays(todos));
        model.addAttribute("tags", tags);
        model.addAttribute("selectedTag", tag);
        model.addAttribute("statuses", TodoStatus.values());
        return "todos/list";
    }

    @GetMapping("/todos/new")
    public String createForm(Model model) {
        model.addAttribute("todo", new Todo());
        model.addAttribute("tags", tagService.findAll());
        model.addAttribute("statuses", TodoStatus.values());
        model.addAttribute("tagNames", java.util.Set.of());
        model.addAttribute("deadlineDate", "");
        model.addAttribute("deadlineTime", "");
        model.addAttribute("cronExpression", "");
        return "todos/form";
    }

    @PostMapping("/todos")
    public String create(
            @Valid Todo todo,
            BindingResult bindingResult,
            @RequestParam(value = "tagNames", required = false) List<String> tagNames,
            @RequestParam(value = "deadlineDate", required = false) String deadlineDate,
            @RequestParam(value = "deadlineTime", required = false) String deadlineTime,
            @RequestParam(value = "cronExpression", required = false) String cronExpression,
            Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("tags", tagService.findAll());
            model.addAttribute("statuses", TodoStatus.values());
            model.addAttribute("tagNames", tagNames != null ? Set.copyOf(tagNames) : Set.of());
            model.addAttribute("deadlineDate", deadlineDate != null ? deadlineDate : "");
            model.addAttribute("deadlineTime", deadlineTime != null ? deadlineTime : "");
            model.addAttribute("cronExpression", cronExpression != null ? cronExpression : "");
            return "todos/form";
        }

        if (cronExpression != null && !cronExpression.isBlank()) {
            todo.setCronExpression(cronExpression);
        }

        if (deadlineDate != null && !deadlineDate.isBlank()) {
            LocalDate date = LocalDate.parse(deadlineDate);
            LocalTime time =
                    (deadlineTime != null && !deadlineTime.isBlank())
                            ? LocalTime.parse(deadlineTime)
                            : LocalTime.of(0, 0);
            todo.setDeadline(LocalDateTime.of(date, time));
        }

        if (tagNames != null) {
            todo.setTags(tagNames.stream().map(todoService::ensureTag).collect(Collectors.toSet()));
        }
        todoService.save(todo);
        return "redirect:/todos";
    }

    @GetMapping("/todos/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        var t = todoService.findById(id);
        if (t.isEmpty()) return "redirect:/todos";
        model.addAttribute("todo", t.get());
        model.addAttribute(
                "tagNames",
                t.get().getTags().stream().map(Tag::getName).collect(Collectors.toSet()));
        model.addAttribute(
                "cronExpression",
                t.get().getCronExpression() != null ? t.get().getCronExpression() : "");

        if (t.get().getDeadline() != null) {
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
            model.addAttribute("deadlineDate", t.get().getDeadline().format(dateFormatter));
            model.addAttribute("deadlineTime", t.get().getDeadline().format(timeFormatter));
        } else {
            model.addAttribute("deadlineDate", "");
            model.addAttribute("deadlineTime", "");
        }

        model.addAttribute("tags", tagService.findAll());
        model.addAttribute("statuses", TodoStatus.values());
        return "todos/form";
    }

    @PostMapping("/todos/{id}")
    public String update(
            @PathVariable Long id,
            @Valid Todo todo,
            BindingResult bindingResult,
            @RequestParam(value = "tagNames", required = false) List<String> tagNames,
            @RequestParam(value = "deadlineDate", required = false) String deadlineDate,
            @RequestParam(value = "deadlineTime", required = false) String deadlineTime,
            @RequestParam(value = "cronExpression", required = false) String cronExpression,
            Model model) {
        if (bindingResult.hasErrors()) {
            todo.setId(id);
            model.addAttribute("tags", tagService.findAll());
            model.addAttribute("statuses", TodoStatus.values());
            model.addAttribute("tagNames", tagNames != null ? Set.copyOf(tagNames) : Set.of());
            model.addAttribute("deadlineDate", deadlineDate != null ? deadlineDate : "");
            model.addAttribute("deadlineTime", deadlineTime != null ? deadlineTime : "");
            model.addAttribute("cronExpression", cronExpression != null ? cronExpression : "");
            return "todos/form";
        }
        todo.setId(id);

        if (cronExpression != null && !cronExpression.isBlank()) {
            todo.setCronExpression(cronExpression);
        } else {
            todo.setCronExpression(null);
        }

        if (deadlineDate != null && !deadlineDate.isBlank()) {
            LocalDate date = LocalDate.parse(deadlineDate);
            LocalTime time =
                    (deadlineTime != null && !deadlineTime.isBlank())
                            ? LocalTime.parse(deadlineTime)
                            : LocalTime.of(0, 0);
            todo.setDeadline(LocalDateTime.of(date, time));
        }

        if (tagNames != null)
            todo.setTags(tagNames.stream().map(todoService::ensureTag).collect(Collectors.toSet()));
        todoService.save(todo);
        return "redirect:/todos";
    }

    @PostMapping("/todos/{id}/delete")
    public String delete(@PathVariable Long id) {
        todoService.deleteById(id);
        return "redirect:/todos";
    }

    @PostMapping("/todos/{id}/status")
    @ResponseBody
    public ResponseEntity<String> changeStatus(@PathVariable Long id, @RequestParam String status) {
        TodoStatus parsedStatus;
        try {
            parsedStatus = TodoStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid status value.");
        }
        boolean found = todoService.changeStatus(id, parsedStatus);
        return found ? ResponseEntity.ok("ok") : ResponseEntity.notFound().build();
    }

    @PostMapping("/todos/reorder")
    @ResponseBody
    public String reorder(@RequestBody List<Long> orderedIds) {
        todoService.reorder(orderedIds);
        return "ok";
    }

    private List<Map<String, Object>> buildWeekDays(List<Todo> todos) {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        String[] labels = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d");

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            // DayOfWeek.getValue(): 1=Mon..7=Sun → cron: 0=Sun,1=Mon..6=Sat
            int cronDay = date.getDayOfWeek().getValue() % 7;

            List<Todo> dayTodos = new ArrayList<>();
            for (Todo todo : todos) {
                String cron = todo.getCronExpression();
                if (cron != null && !cron.isBlank()) {
                    String[] parts = cron.trim().split("\\s+");
                    if (parts.length >= 5 && parseCronWeekdays(parts[4]).contains(cronDay)) {
                        dayTodos.add(todo);
                    }
                } else if (todo.getDeadline() != null
                        && todo.getDeadline().toLocalDate().equals(date)
                        && todo.getStatus() != TodoStatus.DONE) {
                    dayTodos.add(todo);
                }
            }

            Map<String, Object> day = new LinkedHashMap<>();
            day.put("name", labels[i]);
            day.put("date", date.format(fmt));
            day.put("isToday", date.equals(today));
            day.put("todos", dayTodos);
            result.add(day);
        }
        return result;
    }

    private Set<Integer> parseCronWeekdays(String field) {
        Set<Integer> days = new LinkedHashSet<>();
        if ("*".equals(field)) {
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
                } catch (NumberFormatException ignored) {
                }
            } else {
                try {
                    days.add(Integer.parseInt(part));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return days;
    }

    private String humanReadableCron(String cron) {
        if (cron == null || cron.isBlank()) return "";
        String[] p = cron.trim().split("\\s+");
        if (p.length < 5) return cron;

        String min = p[0], hour = p[1], dom = p[2], dow = p[4];
        String time = "";
        if (!"*".equals(hour) && !"*".equals(min)) {
            try {
                time = String.format(" at %s:%02d", hour, Integer.parseInt(min));
            } catch (NumberFormatException ignored) {
            }
        }

        if ("*".equals(dow) && "*".equals(dom)) return "Daily" + time;
        if ("1".equals(dom) && "*".equals(dow)) return "Monthly (1st)" + time;
        if ("1-5".equals(dow)) return "Weekdays (Mon–Fri)" + time;
        if ("6,0".equals(dow) || "0,6".equals(dow)) return "Weekends" + time;

        Map<String, String> dayNames = new LinkedHashMap<>();
        dayNames.put("0", "Sunday");
        dayNames.put("1", "Monday");
        dayNames.put("2", "Tuesday");
        dayNames.put("3", "Wednesday");
        dayNames.put("4", "Thursday");
        dayNames.put("5", "Friday");
        dayNames.put("6", "Saturday");

        if (dayNames.containsKey(dow)) return "Every " + dayNames.get(dow) + time;

        String joined =
                Arrays.stream(dow.split(","))
                        .map(d -> dayNames.getOrDefault(d.trim(), d))
                        .collect(Collectors.joining(", "));
        return joined.isEmpty() ? cron : joined + time;
    }
}
