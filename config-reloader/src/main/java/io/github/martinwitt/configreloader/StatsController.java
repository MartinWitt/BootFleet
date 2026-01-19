package io.github.martinwitt.configreloader;

import io.github.martinwitt.configreloader.manager.ResourceManager;
import io.github.martinwitt.configreloader.model.WatchedResource;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StatsController {

    private final ResourceManager resourceManager;

    public StatsController(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    @GetMapping("/stats")
    public String getStats(Model model) {
        int totalSecrets = resourceManager.getTotalSecrets();
        int totalConfigMaps = resourceManager.getTotalConfigMaps();
        Map<String, WatchedResource> resources = resourceManager.getWatchedResources();

        model.addAttribute("totalSecrets", totalSecrets);
        model.addAttribute("totalConfigMaps", totalConfigMaps);
        model.addAttribute("totalResources", resources.size());
        model.addAttribute(
                "watchedResources",
                resources.values().stream()
                        .map(
                                r ->
                                        Map.of(
                                                "namespace", r.namespace(),
                                                "name", r.name(),
                                                "type", r.type().name(),
                                                "joinedDeployments",
                                                        String.join(", ", r.deploymentNames())))
                        .collect(Collectors.toList()));

        return "stats :: stats";
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }
}
