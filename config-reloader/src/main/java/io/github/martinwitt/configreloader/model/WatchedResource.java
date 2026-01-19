package io.github.martinwitt.configreloader.model;

import java.util.ArrayList;
import java.util.List;

public record WatchedResource(
        String namespace, String name, ResourceType type, List<String> deploymentNames) {
    public WatchedResource addPod(String podName) {
        List<String> newPods = new ArrayList<>(deploymentNames);
        newPods.add(podName);
        return new WatchedResource(namespace, name, type, newPods);
    }

    public WatchedResource addDeployment(String deploymentName) {
        List<String> newDeployments = new ArrayList<>(deploymentNames);
        newDeployments.add(deploymentName);
        return new WatchedResource(namespace, name, type, newDeployments);
    }
}
