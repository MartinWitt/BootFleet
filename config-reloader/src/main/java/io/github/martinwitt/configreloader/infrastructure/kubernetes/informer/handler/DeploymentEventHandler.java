package io.github.martinwitt.configreloader.infrastructure.kubernetes.informer.handler;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.github.martinwitt.configreloader.application.service.WorkloadManagementService;
import io.github.martinwitt.configreloader.domain.model.WorkloadConfiguration;
import io.github.martinwitt.configreloader.domain.model.WorkloadId;
import io.github.martinwitt.configreloader.domain.model.WorkloadType;
import io.github.martinwitt.configreloader.infrastructure.kubernetes.KubernetesWorkloadReader;
import io.micrometer.core.annotation.Counted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Event handler for Deployment informer events. */
public class DeploymentEventHandler implements ResourceEventHandler<Deployment> {
    private static final Logger logger = LoggerFactory.getLogger(DeploymentEventHandler.class);

    private final WorkloadManagementService workloadManagementService;
    private final KubernetesWorkloadReader workloadReader;

    public DeploymentEventHandler(
            WorkloadManagementService workloadManagementService,
            KubernetesWorkloadReader workloadReader) {
        this.workloadManagementService = workloadManagementService;
        this.workloadReader = workloadReader;
    }

    @Override
    @Counted(value = "informer.deployment.add", description = "Count of deployment add events")
    public void onAdd(Deployment deployment) {
        logger.debug(
                "Deployment added: {}/{}",
                deployment.getMetadata().getNamespace(),
                deployment.getMetadata().getName());
        handleDeployment(deployment);
    }

    @Override
    @Counted(
            value = "informer.deployment.update",
            description = "Count of deployment update events")
    public void onUpdate(Deployment oldDeployment, Deployment newDeployment) {
        logger.debug(
                "Deployment updated: {}/{}",
                newDeployment.getMetadata().getNamespace(),
                newDeployment.getMetadata().getName());
        handleDeployment(newDeployment);
    }

    @Override
    @Counted(
            value = "informer.deployment.delete",
            description = "Count of deployment delete events")
    public void onDelete(Deployment deployment, boolean deletedFinalStateUnknown) {
        logger.info(
                "Deployment deleted: {}/{}",
                deployment.getMetadata().getNamespace(),
                deployment.getMetadata().getName());

        WorkloadId workloadId =
                new WorkloadId(
                        deployment.getMetadata().getNamespace(),
                        deployment.getMetadata().getName(),
                        WorkloadType.DEPLOYMENT);

        workloadManagementService.unregisterWorkload(workloadId);
    }

    private void handleDeployment(Deployment deployment) {
        try {
            String namespace = deployment.getMetadata().getNamespace();
            String name = deployment.getMetadata().getName();
            boolean watchEnabled =
                    workloadReader.shouldWatch(deployment.getMetadata().getAnnotations());

            WorkloadId workloadId = new WorkloadId(namespace, name, WorkloadType.DEPLOYMENT);

            var configDependencies =
                    workloadReader.extractConfigReferences(
                            namespace, deployment.getSpec().getTemplate().getSpec());

            WorkloadConfiguration config =
                    new WorkloadConfiguration(workloadId, configDependencies, watchEnabled);

            workloadManagementService.registerWorkload(config);
        } catch (Exception e) {
            logger.error(
                    "Error handling deployment {}/{}",
                    deployment.getMetadata().getNamespace(),
                    deployment.getMetadata().getName(),
                    e);
        }
    }
}
