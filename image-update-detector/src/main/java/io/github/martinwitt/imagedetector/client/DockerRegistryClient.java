package io.github.martinwitt.imagedetector.client;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class DockerRegistryClient {
  private static final Logger logger = LoggerFactory.getLogger(DockerRegistryClient.class);
  private final RestTemplate restTemplate;

  public DockerRegistryClient(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  @Cacheable(value = "docker-tags", key = "#registry + '/' + #repository")
  public String getLatestTag(String registry, String repository) {
    try {
      if ("docker.io".equals(registry)) {
        return getDockerHubLatest(repository);
      } else if ("ghcr.io".equals(registry) || "quay.io".equals(registry)) {
        return getOciRegistryLatest(registry, repository);
      }
      return null;
    } catch (Exception e) {
      logger.warn("Failed to fetch latest tag for {}/{}: {}", registry, repository, e.getMessage());
      return null;
    }
  }

  private String getDockerHubLatest(String repository) throws Exception {
    String url = "https://registry.hub.docker.com/v2/repositories/" + repository + "/tags/?page_size=1&ordering=-last_updated";
    var response = restTemplate.getForObject(url, Map.class);
    if (response == null || !response.containsKey("results")) {
      return null;
    }
    
    List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
    if (!results.isEmpty() && results.get(0).containsKey("name")) {
      return (String) results.get(0).get("name");
    }
    return null;
  }

  private String getOciRegistryLatest(String registry, String repository) throws Exception {
    String url = "https://" + registry + "/v2/" + repository + "/tags/list";
    try {
      var response = restTemplate.getForObject(url, Map.class);
      if (response != null && response.containsKey("tags")) {
        List<String> tags = (List<String>) response.get("tags");
        if (!tags.isEmpty()) {
          return tags.get(0);
        }
      }
    } catch (Exception e) {
      logger.debug("Failed to fetch OCI tags from {}/{}", registry, repository);
    }
    return null;
  }
}
