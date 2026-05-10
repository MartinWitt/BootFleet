package io.github.martinwitt.imagedetector.controller;

import io.github.martinwitt.imagedetector.model.HelmChartDependencyEntity;
import io.github.martinwitt.imagedetector.model.HelmChartDependencyRepository;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/")
public class ImageDashboardController {
    private final HelmChartDependencyRepository dependencyRepo;

    public ImageDashboardController(HelmChartDependencyRepository dependencyRepo) {
        this.dependencyRepo = dependencyRepo;
    }

    @GetMapping
    public String index(Model model) {
        List<HelmChartDependencyEntity> dependencies = dependencyRepo.findAll();
        model.addAttribute("dependencies", dependencies);
        model.addAttribute("dependencyCount", dependencies.size());
        return "index";
    }
}
