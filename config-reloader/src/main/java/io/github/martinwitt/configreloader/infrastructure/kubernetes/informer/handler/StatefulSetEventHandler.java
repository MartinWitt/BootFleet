package io.github.martinwitt.configreloader.infrastructure.kubernetes.informer.handler;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.github.martinwitt.configreloader.application.service.WorkloadManagementService;
import io.github.martinwitt.configreloader.domain.model.WorkloadConfiguration;
import io.github.martinwitt.configreloader.domain.model.WorkloadId;
import io.github.martinwitt.configreloader.domain.model.WorkloadType;
import io.github.martinwitt.configreloader.infrastructure.kubernetes.KubernetesWorkloadReader;
import io.micrometer.core.annotation.Counted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Event handler for StatefulSet informer events. */
public class StatefulSetEventHandler implements ResourceEventHandler<StatefulSet> {
    private static final Logger logger = LoggerFactory.getLogger(StatefulSetEventHandler.class);

    private final WorkloadManagementService workloadManagementService;
    private final KubernetesWorkloadReader workloadReader;

    public StatefulSetEventHandler(
            WorkloadManagementService workloadManagementService,
            KubernetesWorkloadReader workloadReader) {
        this.workloadManagementService = workloadManagementService;
        this.workloadReader = workloadReader;
    }

    @Override
    @Counted(value = "informer.statefulset.add", description = "Count of statefulset add events")
    public void onAdd(StatefulSet statefulSet) {
        logger.debug(
                "StatefulSet added: {}/{}",
                statefulSet.getMetadata().getNamespace(),
                statefulSet.getMetadata().getName());
        handleStatefulSet(statefulSet);
    }

    @Override
    @Counted(
            value = "informer.statefulset.update",
            description = "Count of statefulset update events")
    public void onUpdate(StatefulSet oldStatefulSet, StatefulSet newStatefulSet) {
        logger.debug(
                "StatefulSet updated: {}/{}",
                newStatefulSet.getMetadata().getNamespace(),
                newStatefulSet.getMetadata().getName());
        handleStatefulSet(newStatefulSet);
    }

    @Override
    @Counted(
            value = "informer.statefulset.delete",
            description = "Count of statefulset delete events")
    public void onDelete(StatefulSet statefulSet, boolean deletedFinalStateUnknown) {
        logger.info(
                "StatefulSet deleted: {}/{}",
                statefulSet.getMetadata().getNamespace(),
                statefulSet.getMetadata().getName());

        WorkloadId workloadId =
                new WorkloadId(
                        statefulSet.getMetadata().getNamespace(),
                        statefulSet.getMetadata().getName(),
                        WorkloadType.STATEFULSET);

        workloadManagementService.unregisterWorkload(workloadId);
    }

    private void handleStatefulSet(StatefulSet statefulSet) {
        try {
            String namespace = statefulSet.getMetadata().getNamespace();
            String name = statefulSet.getMetadata().getName();
            boolean watchEnabled =
                    workloadReader.shouldWatch(statefulSet.getMetadata().getAnnotations());

            WorkloadId workloadId = new WorkloadId(namespace, name, WorkloadType.STATEFULSET);

            var configDependencies =
                    workloadReader.extractConfigReferences(
                            namespace, statefulSet.getSpec().getTemplate().getSpec());

            WorkloadConfiguration config =
                    new WorkloadConfiguration(workloadId, configDependencies, watchEnabled);

            workloadManagementService.registerWorkload(config);
        } catch (Exception e) {
            logger.error(
                    "Error handling statefulset {}/{}",
                    statefulSet.getMetadata().getNamespace(),
                    statefulSet.getMetadata().getName(),
                    e);
        }
    }
}
