package io.github.martinwitt.notesapp.web;

import io.github.martinwitt.notesapp.domain.Note;
import io.github.martinwitt.notesapp.domain.Tag;
import io.github.martinwitt.notesapp.service.ContentExpansionService;
import io.github.martinwitt.notesapp.service.NoteService;
import io.github.martinwitt.notesapp.service.OllamaUnavailableException;
import io.github.martinwitt.notesapp.service.TagSuggestionService;
import jakarta.validation.Valid;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class NoteController {

    private final NoteService noteService;
    private final TagSuggestionService tagSuggestionService;
    private final ContentExpansionService contentExpansionService;

    public NoteController(
            NoteService noteService,
            TagSuggestionService tagSuggestionService,
            ContentExpansionService contentExpansionService) {
        this.noteService = noteService;
        this.tagSuggestionService = tagSuggestionService;
        this.contentExpansionService = contentExpansionService;
    }

    @GetMapping({"/", "/notes"})
    public String list(@RequestParam(value = "tag", required = false) String tag, Model model) {
        List<Note> notes =
                (tag == null || tag.isBlank())
                        ? noteService.findAll()
                        : noteService.findByTagName(tag);
        model.addAttribute("notes", notes);
        model.addAttribute(
                "allTags", noteService.findAllTags().stream().map(Tag::getName).sorted().toList());
        model.addAttribute("selectedTag", tag);
        return "notes/list";
    }

    @GetMapping("/notes/new")
    public String createForm(Model model) {
        model.addAttribute("note", new Note());
        model.addAttribute("allTags", noteService.findAllTags());
        model.addAttribute("tagNames", Set.of());
        return "notes/form";
    }

    @PostMapping("/notes")
    public String create(
            @Valid Note note,
            BindingResult bindingResult,
            @RequestParam(value = "tagNames", required = false) List<String> tagNames,
            Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("allTags", noteService.findAllTags());
            model.addAttribute("tagNames", tagNames != null ? Set.copyOf(tagNames) : Set.of());
            return "notes/form";
        }
        if (tagNames != null) {
            note.setTags(
                    tagNames.stream()
                            .map(noteService::ensureTag)
                            .collect(Collectors.toCollection(HashSet::new)));
        }
        noteService.save(note);
        return "redirect:/notes";
    }

    @GetMapping("/notes/{id}")
    public String detail(@PathVariable Long id, Model model) {
        return noteService
                .findById(id)
                .map(
                        note -> {
                            model.addAttribute("note", note);
                            return "notes/detail";
                        })
                .orElse("redirect:/notes");
    }

    @GetMapping("/notes/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        return noteService
                .findById(id)
                .map(
                        note -> {
                            model.addAttribute("note", note);
                            model.addAttribute("allTags", noteService.findAllTags());
                            model.addAttribute(
                                    "tagNames",
                                    note.getTags().stream()
                                            .map(Tag::getName)
                                            .collect(Collectors.toSet()));
                            return "notes/form";
                        })
                .orElse("redirect:/notes");
    }

    @PostMapping("/notes/{id}")
    public String update(
            @PathVariable Long id,
            @Valid Note note,
            BindingResult bindingResult,
            @RequestParam(value = "tagNames", required = false) List<String> tagNames,
            Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("allTags", noteService.findAllTags());
            model.addAttribute("tagNames", tagNames != null ? Set.copyOf(tagNames) : Set.of());
            return "notes/form";
        }
        return noteService
                .findById(id)
                .map(
                        existing -> {
                            existing.setTitle(note.getTitle());
                            existing.setContent(note.getContent());
                            existing.setTags(
                                    tagNames != null
                                            ? tagNames.stream()
                                                    .map(noteService::ensureTag)
                                                    .collect(Collectors.toCollection(HashSet::new))
                                            : new HashSet<>());
                            noteService.save(existing);
                            return "redirect:/notes/" + id;
                        })
                .orElse("redirect:/notes");
    }

    @PostMapping("/notes/{id}/delete")
    public String delete(@PathVariable Long id) {
        noteService.deleteById(id);
        return "redirect:/notes";
    }

    @PostMapping("/notes/expand-content")
    @ResponseBody
    public ResponseEntity<String> expandContent(
            @RequestParam String title,
            @RequestParam(required = false, defaultValue = "") String content) {
        if (title == null || title.isBlank()) return ResponseEntity.badRequest().build();
        try {
            return ResponseEntity.ok(contentExpansionService.expandContent(title, content));
        } catch (OllamaUnavailableException e) {
            return ResponseEntity.status(503).build();
        }
    }

    @PostMapping("/notes/suggest-tags")
    @ResponseBody
    public ResponseEntity<List<String>> suggestTagsForNew(
            @RequestParam String title,
            @RequestParam(required = false, defaultValue = "") String content) {
        if (title == null || title.isBlank()) return ResponseEntity.ok(List.of());
        try {
            return ResponseEntity.ok(tagSuggestionService.suggestTags(title, content));
        } catch (OllamaUnavailableException e) {
            return ResponseEntity.<List<String>>status(503).build();
        }
    }

    @PostMapping("/notes/{id}/suggest-tags")
    @ResponseBody
    public ResponseEntity<List<String>> suggestTags(@PathVariable Long id) {
        var noteOpt = noteService.findById(id);
        if (noteOpt.isEmpty()) return ResponseEntity.notFound().build();
        try {
            return ResponseEntity.ok(tagSuggestionService.suggestTags(noteOpt.get()));
        } catch (OllamaUnavailableException e) {
            return ResponseEntity.<List<String>>status(503).build();
        }
    }

    @PostMapping("/notes/{id}/tags/{tagName}")
    @ResponseBody
    public ResponseEntity<String> addTag(@PathVariable Long id, @PathVariable String tagName) {
        String normalized = tagName == null ? "" : tagName.trim().toLowerCase();
        if (normalized.isBlank()) return ResponseEntity.badRequest().build();
        boolean added = noteService.addTag(id, normalized);
        return added ? ResponseEntity.ok("added") : ResponseEntity.notFound().build();
    }
}
