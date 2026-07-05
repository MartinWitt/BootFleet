package io.github.martinwitt.todoapp.todo;

import io.github.martinwitt.todoapp.tag.Tag;
import io.github.martinwitt.todoapp.tag.TagService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TodoController {

    private final TodoService todoService;
    private final TagService tagService;
    private final ApplicationEventPublisher eventPublisher;

    public TodoController(
            TodoService todoService,
            TagService tagService,
            ApplicationEventPublisher eventPublisher) {
        this.todoService = todoService;
        this.tagService = tagService;
        this.eventPublisher = eventPublisher;
    }

    @GetMapping({"/", "/todos"})
    public String list(@RequestParam(value = "tag", required = false) String tag, Model model) {
        List<Tag> tags = tagService.findAll();
        List<Todo> todos =
                (tag == null || tag.isBlank())
                        ? todoService.findAllSorted()
                        : todoService.findByTagName(tag);

        List<Todo> recurringTodos = new ArrayList<>();
        List<Todo> oneTimeTodos = new ArrayList<>();
        List<Todo> doneTodos = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");
        Map<Long, String> formattedDeadlines = new HashMap<>();
        Map<Long, String> cronDescriptions = new HashMap<>();
        Map<Long, String> lastCompletedDates = new HashMap<>();
        for (Todo todo : todos) {
            if (todo.getStatus() == TodoStatus.DONE) {
                doneTodos.add(todo);
            } else if (todo.isRecurring()) {
                recurringTodos.add(todo);
            } else {
                oneTimeTodos.add(todo);
            }
            if (todo.getDeadline() != null) {
                formattedDeadlines.put(todo.getId(), todo.getDeadline().format(formatter));
            }
            if (todo.isRecurring()) {
                cronDescriptions.put(
                        todo.getId(), CronExpressionFormatter.describe(todo.getCronExpression()));
            }
            if (todo.getLastCompletedAt() != null) {
                lastCompletedDates.put(todo.getId(), todo.getLastCompletedAt().format(formatter));
            }
        }

        model.addAttribute("recurringTodos", recurringTodos);
        model.addAttribute("oneTimeTodos", oneTimeTodos);
        model.addAttribute("doneTodos", doneTodos);
        model.addAttribute("formattedDeadlines", formattedDeadlines);
        model.addAttribute("cronDescriptions", cronDescriptions);
        model.addAttribute("lastCompletedDates", lastCompletedDates);
        model.addAttribute("weekDays", WeekViewBuilder.build(todos, LocalDate.now()));
        model.addAttribute("tags", tags);
        model.addAttribute("selectedTag", tag);
        model.addAttribute("statuses", TodoStatus.values());
        return "todos/list";
    }

    @GetMapping("/todos/new")
    public String createForm(Model model) {
        model.addAttribute("todo", new Todo());
        return populateFormModel(model, null, "", "", "");
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
            return populateFormModel(model, tagNames, deadlineDate, deadlineTime, cronExpression);
        }
        applyFormInputs(todo, tagNames, deadlineDate, deadlineTime, cronExpression);
        todoService.save(todo);
        return "redirect:/todos";
    }

    @GetMapping("/todos/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        var todo = todoService.findById(id);
        if (todo.isEmpty()) return "redirect:/todos";
        var t = todo.get();
        model.addAttribute("todo", t);

        String deadlineDate = "";
        String deadlineTime = "";
        if (t.getDeadline() != null) {
            deadlineDate = t.getDeadline().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            deadlineTime = t.getDeadline().format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        List<String> tagNames = t.getTags().stream().map(Tag::getName).toList();
        String cron = t.getCronExpression() != null ? t.getCronExpression() : "";
        return populateFormModel(model, tagNames, deadlineDate, deadlineTime, cron);
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
        todo.setId(id);
        if (bindingResult.hasErrors()) {
            return populateFormModel(model, tagNames, deadlineDate, deadlineTime, cronExpression);
        }
        applyFormInputs(todo, tagNames, deadlineDate, deadlineTime, cronExpression);
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

    @PostMapping("/todos/{id}/snooze")
    @ResponseBody
    public ResponseEntity<?> snooze(@PathVariable Long id, @RequestParam int days) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM dd, yyyy");
        return todoService
                .snooze(id, days)
                .map(dt -> ResponseEntity.ok(Map.of("deadline", dt.format(fmt))))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/todos/push-now")
    public String pushNow(RedirectAttributes redirectAttributes) {
        eventPublisher.publishEvent(new SendTodosNowEvent());
        redirectAttributes.addFlashAttribute("pushMessage", "Telegram Push ausgelöst! 📬");
        return "redirect:/todos";
    }

    @PostMapping("/todos/reorder")
    @ResponseBody
    public String reorder(@RequestBody List<Long> orderedIds) {
        todoService.reorder(orderedIds);
        return "ok";
    }

    private String populateFormModel(
            Model model,
            List<String> tagNames,
            String deadlineDate,
            String deadlineTime,
            String cronExpression) {
        model.addAttribute("tags", tagService.findAll());
        model.addAttribute("statuses", TodoStatus.values());
        model.addAttribute(
                "tagNames", tagNames != null ? new HashSet<>(tagNames) : new HashSet<>());
        model.addAttribute("deadlineDate", deadlineDate != null ? deadlineDate : "");
        model.addAttribute("deadlineTime", deadlineTime != null ? deadlineTime : "");
        model.addAttribute("cronExpression", cronExpression != null ? cronExpression : "");
        return "todos/form";
    }

    private void applyFormInputs(
            Todo todo,
            List<String> tagNames,
            String deadlineDate,
            String deadlineTime,
            String cronExpression) {
        todo.setCronExpression(
                (cronExpression != null && !cronExpression.isBlank()) ? cronExpression : null);

        if (deadlineDate != null && !deadlineDate.isBlank()) {
            LocalDate date = LocalDate.parse(deadlineDate);
            LocalTime time =
                    (deadlineTime != null && !deadlineTime.isBlank())
                            ? LocalTime.parse(deadlineTime)
                            : LocalTime.of(0, 0);
            todo.setDeadline(LocalDateTime.of(date, time));
        }

        if (tagNames != null) {
            todo.setTags(tagNames.stream().map(tagService::ensureTag).collect(Collectors.toSet()));
        }
    }
}
