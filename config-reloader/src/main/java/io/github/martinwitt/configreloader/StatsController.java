package io.github.martinwitt.configreloader;

import io.github.martinwitt.configreloader.domain.service.ConfigResourceRepository;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StatsController {

    private final ConfigResourceRepository repository;

    public StatsController(ConfigResourceRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/stats")
    public String getStats(Model model) {
        int totalSecrets = repository.countSecrets();
        int totalConfigMaps = repository.countConfigMaps();
        var resources = repository.findAll();

        model.addAttribute("totalSecrets", totalSecrets);
        model.addAttribute("totalConfigMaps", totalConfigMaps);
        model.addAttribute("totalResources", resources.size());
        model.addAttribute(
                "watchedResources",
                resources.values().stream()
                        .map(
                                r -> {
                                    String deploymentNames =
                                            r.dependentWorkloads().stream()
                                                    .map(w -> w.toQualifiedName())
                                                    .collect(Collectors.joining(", "));
                                    return Map.of(
                                            "namespace",
                                            r.resourceId().namespace(),
                                            "name",
                                            r.resourceId().name(),
                                            "type",
                                            r.resourceId().type().name(),
                                            "joinedDeployments",
                                            deploymentNames);
                                })
                        .collect(Collectors.toList()));

        return "stats :: stats";
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }
}
