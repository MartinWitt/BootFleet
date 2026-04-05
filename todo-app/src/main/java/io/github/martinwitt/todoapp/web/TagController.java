package io.github.martinwitt.todoapp.web;

import io.github.martinwitt.todoapp.domain.Tag;
import io.github.martinwitt.todoapp.service.TagService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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
        return "tags/list";
    }

    @PostMapping
    public String create(@ModelAttribute Tag tag) {
        tagService.save(tag);
        return "redirect:/tags";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        tagService.deleteById(id);
        return "redirect:/tags";
    }
}
