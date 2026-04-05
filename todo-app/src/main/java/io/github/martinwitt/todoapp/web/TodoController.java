package io.github.martinwitt.todoapp.web;

import io.github.martinwitt.todoapp.domain.Tag;
import io.github.martinwitt.todoapp.domain.Todo;
import io.github.martinwitt.todoapp.domain.TodoStatus;
import io.github.martinwitt.todoapp.service.TagService;
import io.github.martinwitt.todoapp.service.TodoService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
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

        // Format deadlines in controller to avoid Thymeleaf compatibility issues
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");
        Map<Long, String> formattedDeadlines = new HashMap<>();
        for (Todo todo : todos) {
            if (todo.getDeadline() != null) {
                formattedDeadlines.put(todo.getId(), todo.getDeadline().format(formatter));
            }
        }

        model.addAttribute("todos", todos);
        model.addAttribute("formattedDeadlines", formattedDeadlines);
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

        // Set cron expression if provided
        if (cronExpression != null && !cronExpression.isBlank()) {
            todo.setCronExpression(cronExpression);
        }

        // Combine date and time into LocalDateTime
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

        // Load cron expression
        model.addAttribute(
                "cronExpression",
                t.get().getCronExpression() != null ? t.get().getCronExpression() : "");

        // Split deadline into date and time for form display
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

        // Set cron expression if provided
        if (cronExpression != null && !cronExpression.isBlank()) {
            todo.setCronExpression(cronExpression);
        } else {
            todo.setCronExpression(null);
        }

        // Combine date and time into LocalDateTime
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

    @PostMapping("/todos/{id}/status")
    @ResponseBody
    public ResponseEntity<String> changeStatus(@PathVariable Long id, @RequestParam String status) {
        try {
            boolean found = todoService.changeStatus(id, status);
            return found ? ResponseEntity.ok("ok") : ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid status value.");
        }
    }

    @PostMapping("/todos/reorder")
    @ResponseBody
    public String reorder(@RequestBody List<Long> orderedIds) {
        todoService.reorder(orderedIds);
        return "ok";
    }
}
