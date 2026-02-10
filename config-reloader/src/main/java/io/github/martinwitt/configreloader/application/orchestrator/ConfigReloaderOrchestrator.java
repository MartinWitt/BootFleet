package io.github.martinwitt.configreloader.application.orchestrator;

import io.github.martinwitt.configreloader.application.service.ConfigResourceUpdateService;
import io.github.martinwitt.configreloader.application.service.WorkloadManagementService;
import io.github.martinwitt.configreloader.infrastructure.kubernetes.KubernetesWorkloadReader;
import io.github.martinwitt.configreloader.infrastructure.kubernetes.informer.InformerFactory;
import io.github.martinwitt.configreloader.infrastructure.kubernetes.informer.handler.ConfigMapEventHandler;
import io.github.martinwitt.configreloader.infrastructure.kubernetes.informer.handler.DeploymentEventHandler;
import io.github.martinwitt.configreloader.infrastructure.kubernetes.informer.handler.SecretEventHandler;
import io.github.martinwitt.configreloader.infrastructure.kubernetes.informer.handler.StatefulSetEventHandler;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Main orchestrator that sets up and coordinates all informers and their event handlers. This
 * replaces the old ResourceWatcherService.
 */
@Component
@ConditionalOnProperty(name = "configreloader.enabled", havingValue = "true")
public class ConfigReloaderOrchestrator {
    private static final Logger logger = LoggerFactory.getLogger(ConfigReloaderOrchestrator.class);

    private final InformerFactory informerFactory;
    private final WorkloadManagementService workloadManagementService;
    private final ConfigResourceUpdateService configResourceUpdateService;
    private final KubernetesWorkloadReader workloadReader;

    public ConfigReloaderOrchestrator(
            InformerFactory informerFactory,
            WorkloadManagementService workloadManagementService,
            ConfigResourceUpdateService configResourceUpdateService,
            KubernetesWorkloadReader workloadReader) {
        this.informerFactory = informerFactory;
        this.workloadManagementService = workloadManagementService;
        this.configResourceUpdateService = configResourceUpdateService;
        this.workloadReader = workloadReader;
    }

    @PostConstruct
    public void initialize() {
        logger.info("Initializing Config Reloader with informer-based architecture");

        setupInformers();
        startInformers();

        logger.info("Config Reloader initialization complete");
    }

    private void setupInformers() {
        logger.info("Setting up informers and event handlers");

        // Setup workload informers
        var deploymentInformer = informerFactory.getDeploymentInformer();
        deploymentInformer.addEventHandler(
                new DeploymentEventHandler(workloadManagementService, workloadReader));
        logger.info("Deployment informer configured");

        var statefulSetInformer = informerFactory.getStatefulSetInformer();
        statefulSetInformer.addEventHandler(
                new StatefulSetEventHandler(workloadManagementService, workloadReader));
        logger.info("StatefulSet informer configured");

        // Setup config resource informers
        var configMapInformer = informerFactory.getConfigMapInformer();
        configMapInformer.addEventHandler(new ConfigMapEventHandler(configResourceUpdateService));
        logger.info("ConfigMap informer configured");

        var secretInformer = informerFactory.getSecretInformer();
        secretInformer.addEventHandler(new SecretEventHandler(configResourceUpdateService));
        logger.info("Secret informer configured");
    }

    private void startInformers() {
        logger.info("Starting all informers...");
        informerFactory.startAllInformers();
        logger.info("All informers started and watching for changes");
    }
}
