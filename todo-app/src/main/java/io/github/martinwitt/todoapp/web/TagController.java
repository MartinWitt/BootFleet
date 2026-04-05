package io.github.martinwitt.todoapp.web;

import io.github.martinwitt.todoapp.domain.Tag;
import io.github.martinwitt.todoapp.service.TagService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/tags")
public class TagController {
    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("tags", tagService.findAll());
        if (!model.containsAttribute("tag")) {
            model.addAttribute("tag", new Tag());
        }
        return "tags/list";
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute Tag tag,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Tag name cannot be blank.");
            return "redirect:/tags";
        }
        try {
            tagService.save(tag);
        } catch (DataIntegrityViolationException e) {
            redirectAttributes.addFlashAttribute(
                    "error", "A tag with the name \"" + tag.getName() + "\" already exists.");
        }
        return "redirect:/tags";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        tagService.deleteById(id);
        return "redirect:/tags";
    }
}
