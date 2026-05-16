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
        logger.info("Initializing Config Reloader");
        setupInformers();
        startInformers();
        logger.info("Config Reloader initialized");
    }

    private void setupInformers() {
        var deploymentInformer = informerFactory.getDeploymentInformer();
        deploymentInformer.addEventHandler(
                new DeploymentEventHandler(workloadManagementService, workloadReader));

        var statefulSetInformer = informerFactory.getStatefulSetInformer();
        statefulSetInformer.addEventHandler(
                new StatefulSetEventHandler(workloadManagementService, workloadReader));

        var configMapInformer = informerFactory.getConfigMapInformer();
        configMapInformer.addEventHandler(new ConfigMapEventHandler(configResourceUpdateService));

        var secretInformer = informerFactory.getSecretInformer();
        secretInformer.addEventHandler(new SecretEventHandler(configResourceUpdateService));
    }

    private void startInformers() {
        informerFactory.startAllInformers();
    }
}
