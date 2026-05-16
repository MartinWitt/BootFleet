package io.github.martinwitt.imagedetector.client;

import io.github.martinwitt.imagedetector.ImageDetectorProperties;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
public class GitOpsClient {
    private static final Logger logger = LoggerFactory.getLogger(GitOpsClient.class);
    private final ImageDetectorProperties properties;
    private final GitHub gitHub;

    public GitOpsClient(ImageDetectorProperties properties, GitHub gitHub) {
        this.properties = properties;
        this.gitHub = gitHub;
    }

    @Cacheable(value = "helm-charts", key = "#path")
    public String getFileContent(String path) {
        try {
            String repo = properties.getRepo();
            String branch = properties.getBranch();
            GHRepository repository = gitHub.getRepository(repo);
            return repository.getFileContent(path, branch).getContent();
        } catch (GHFileNotFoundException e) {
            logger.debug("File not found {}: {}", path, e.getMessage());
            return null;
        } catch (Exception e) {
            logger.debug("Failed to fetch file {}: {}", path, e.getMessage());
            return null;
        }
    }

    public List<String> listApps() {
        List<String> apps = new ArrayList<>();
        try {
            String repo = properties.getRepo();
            String branch = properties.getBranch();
            GHRepository repository = gitHub.getRepository(repo);
            repository.getDirectoryContent("apps", branch).stream()
                    .filter(GHContent::isDirectory)
                    .map(GHContent::getName)
                    .forEach(apps::add);
        } catch (Exception e) {
            logger.warn("Failed to list apps: {}", e.getMessage());
        }
        return apps;
    }
}
