package io.github.martinwitt.configreloader.aot;

import io.fabric8.kubernetes.client.impl.KubernetesClientImpl;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import java.util.ArrayList;
import java.util.List;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeHint;
import org.springframework.aot.hint.TypeReference;

/**
 * Runtime hints registrar for Fabric8 Kubernetes client. Dynamically discovers all classes in the
 * Kubernetes model packages at AOT compile time and registers them for reflection and serialization
 * in GraalVM native images. Uses BindingReflectionHintsRegistrar to properly handle Jackson
 * annotations.
 *
 * <p>This replaces static JSON configuration by doing all discovery and registration
 * programmatically at native image build time, ensuring complete and accurate coverage.
 */
public class KubernetesClientRuntimeHintsRegistrar implements RuntimeHintsRegistrar {

    private final BindingReflectionHintsRegistrar hintsRegistrar =
            new BindingReflectionHintsRegistrar();

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        registerResourceHints(hints);
        registerKubernetesClientHints(hints);
        registerKubernetesModelHints(hints, classLoader);
        registerJacksonHints(hints);
        registerProxyHints(hints, classLoader);
    }

    private void registerKubernetesClientHints(RuntimeHints hints) {
        hints.reflection()
                .registerTypes(
                        TypeReference.listOf(KubernetesClientImpl.class),
                        TypeHint.builtWith(
                                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                                MemberCategory.INVOKE_PUBLIC_METHODS,
                                MemberCategory.INVOKE_DECLARED_METHODS,
                                MemberCategory.ACCESS_DECLARED_FIELDS,
                                MemberCategory.ACCESS_PUBLIC_FIELDS));
    }

    private void registerKubernetesModelHints(RuntimeHints hints, ClassLoader classLoader) {
        // Single comprehensive scan of all Kubernetes model packages
        var graph =
                new ClassGraph()
                        .acceptPackages(
                                "io.fabric8.kubernetes.api.model",
                                "io.fabric8.kubernetes.api.model.apps",
                                "io.fabric8.kubernetes.api.model.batch",
                                "io.fabric8.kubernetes.api.model.rbac",
                                "io.fabric8.kubernetes.api.model.storage",
                                "io.fabric8.kubernetes.api.model.networking",
                                "io.fabric8.kubernetes.api.model.extensions",
                                "io.fabric8.kubernetes.api.model.policy",
                                "io.fabric8.kubernetes.api.model.admissionregistration",
                                "io.fabric8.kubernetes.api.model.coordination",
                                "io.fabric8.kubernetes.api.model.discovery",
                                "io.fabric8.kubernetes.api.model.events",
                                "io.fabric8.kubernetes.api.model.flowcontrol",
                                "io.fabric8.kubernetes.api.model.metrics",
                                "io.fabric8.kubernetes.api.model.scheduling",
                                "io.fabric8.kubernetes.api.model.certificates",
                                "io.fabric8.kubernetes.api.model.node");

        try (var scan = graph.scan()) {
            // Collect all eligible classes
            List<Class<?>> allClassesList = new ArrayList<>();
            scan.getAllClasses().stream()
                    .map(ClassInfo::loadClass)
                    .filter(this::isEligibleForRegistration)
                    .forEach(allClassesList::add);

            Class<?>[] allClasses = allClassesList.toArray(new Class<?>[0]);

            if (allClasses.length > 0) {
                // Use BindingReflectionHintsRegistrar for proper Jackson support
                // This will introspect Jackson annotations and register all necessary members
                hintsRegistrar.registerReflectionHints(hints.reflection(), allClasses);

                // ALSO explicitly register with full member categories to ensure constructors
                // are accessible for Jackson deserialization - critical for native images
                hints.reflection()
                        .registerTypes(
                                TypeReference.listOf(allClasses),
                                TypeHint.builtWith(
                                        MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                                        MemberCategory.INVOKE_PUBLIC_METHODS,
                                        MemberCategory.INVOKE_DECLARED_METHODS,
                                        MemberCategory.ACCESS_DECLARED_FIELDS,
                                        MemberCategory.ACCESS_PUBLIC_FIELDS));

                // Also register all types for serialization
                for (Class<?> type : allClasses) {
                    hints.serialization().registerType(TypeReference.of(type));
                }
            }
        }

        // Register Quantity custom serializers/deserializers explicitly
        registerQuantityHints(hints);
    }

    private boolean isEligibleForRegistration(Class<?> clazz) {
        int modifiers = clazz.getModifiers();
        // Skip only interfaces and synthetic classes
        // Include abstract classes and inner classes as they may have important annotations
        return !java.lang.reflect.Modifier.isInterface(modifiers) && !clazz.isSynthetic();
    }

    private void registerQuantityHints(RuntimeHints hints) {
        // Comprehensive list of all problematic model classes and their builders
        String[] explicitClasses = {
            // Quantity and related
            "io.fabric8.kubernetes.api.model.Quantity",
            "io.fabric8.kubernetes.api.model.Quantity$Deserializer",
            "io.fabric8.kubernetes.api.model.Quantity$Serializer",

            // ManagedFieldsEntry - the main culprit
            "io.fabric8.kubernetes.api.model.ManagedFieldsEntry",
            "io.fabric8.kubernetes.api.model.ManagedFieldsEntryBuilder",

            // FieldsV1
            "io.fabric8.kubernetes.api.model.FieldsV1",
            "io.fabric8.kubernetes.api.model.FieldsV1Builder",

            // ObjectMeta
            "io.fabric8.kubernetes.api.model.ObjectMeta",
            "io.fabric8.kubernetes.api.model.ObjectMetaBuilder",

            // ListMeta
            "io.fabric8.kubernetes.api.model.ListMeta",
            "io.fabric8.kubernetes.api.model.ListMetaBuilder",

            // Common base classes
            "io.fabric8.kubernetes.api.model.KubernetesResource",
            "io.fabric8.kubernetes.api.model.KubernetesResourceList",

            // Deployment related
            "io.fabric8.kubernetes.api.model.apps.Deployment",
            "io.fabric8.kubernetes.api.model.apps.DeploymentBuilder",
            "io.fabric8.kubernetes.api.model.apps.DeploymentList",
            "io.fabric8.kubernetes.api.model.apps.DeploymentListBuilder",
            "io.fabric8.kubernetes.api.model.apps.DeploymentSpec",
            "io.fabric8.kubernetes.api.model.apps.DeploymentSpecBuilder",
            "io.fabric8.kubernetes.api.model.apps.DeploymentStatus",
            "io.fabric8.kubernetes.api.model.apps.DeploymentStatusBuilder",

            // Pod related
            "io.fabric8.kubernetes.api.model.Pod",
            "io.fabric8.kubernetes.api.model.PodBuilder",
            "io.fabric8.kubernetes.api.model.PodList",
            "io.fabric8.kubernetes.api.model.PodListBuilder",
            "io.fabric8.kubernetes.api.model.PodSpec",
            "io.fabric8.kubernetes.api.model.PodSpecBuilder",
            "io.fabric8.kubernetes.api.model.PodStatus",
            "io.fabric8.kubernetes.api.model.PodStatusBuilder",
            "io.fabric8.kubernetes.api.model.PodTemplateSpec",
            "io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder",

            // Container related
            "io.fabric8.kubernetes.api.model.Container",
            "io.fabric8.kubernetes.api.model.ContainerBuilder",
            "io.fabric8.kubernetes.api.model.ContainerPort",
            "io.fabric8.kubernetes.api.model.ContainerPortBuilder",
            "io.fabric8.kubernetes.api.model.ContainerStatus",
            "io.fabric8.kubernetes.api.model.ContainerStatusBuilder",

            // Service related
            "io.fabric8.kubernetes.api.model.Service",
            "io.fabric8.kubernetes.api.model.ServiceBuilder",
            "io.fabric8.kubernetes.api.model.ServiceList",
            "io.fabric8.kubernetes.api.model.ServiceListBuilder",
            "io.fabric8.kubernetes.api.model.ServiceSpec",
            "io.fabric8.kubernetes.api.model.ServiceSpecBuilder",
            "io.fabric8.kubernetes.api.model.ServicePort",
            "io.fabric8.kubernetes.api.model.ServicePortBuilder",

            // ConfigMap and Secrets
            "io.fabric8.kubernetes.api.model.ConfigMap",
            "io.fabric8.kubernetes.api.model.ConfigMapBuilder",
            "io.fabric8.kubernetes.api.model.Secret",
            "io.fabric8.kubernetes.api.model.SecretBuilder",

            // Resource quantities
            "io.fabric8.kubernetes.api.model.ResourceRequirements",
            "io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder",

            // Selectors and labels
            "io.fabric8.kubernetes.api.model.LabelSelector",
            "io.fabric8.kubernetes.api.model.LabelSelectorBuilder",

            // StatefulSet
            "io.fabric8.kubernetes.api.model.apps.StatefulSet",
            "io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder",
            "io.fabric8.kubernetes.api.model.apps.StatefulSetList",
            "io.fabric8.kubernetes.api.model.apps.StatefulSetListBuilder",

            // DaemonSet
            "io.fabric8.kubernetes.api.model.apps.DaemonSet",
            "io.fabric8.kubernetes.api.model.apps.DaemonSetBuilder",

            // ReplicaSet
            "io.fabric8.kubernetes.api.model.apps.ReplicaSet",
            "io.fabric8.kubernetes.api.model.apps.ReplicaSetBuilder",

            // Job
            "io.fabric8.kubernetes.api.model.batch.v1.Job",
            "io.fabric8.kubernetes.api.model.batch.v1.JobBuilder",

            // Namespace
            "io.fabric8.kubernetes.api.model.Namespace",
            "io.fabric8.kubernetes.api.model.NamespaceBuilder",

            // RBAC
            "io.fabric8.kubernetes.api.model.rbac.Role",
            "io.fabric8.kubernetes.api.model.rbac.RoleBuilder",
            "io.fabric8.kubernetes.api.model.rbac.RoleBinding",
            "io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder",

            // Events
            "io.fabric8.kubernetes.api.model.Event",
            "io.fabric8.kubernetes.api.model.EventBuilder",

            // Volume related
            "io.fabric8.kubernetes.api.model.Volume",
            "io.fabric8.kubernetes.api.model.VolumeBuilder",
            "io.fabric8.kubernetes.api.model.VolumeMount",
            "io.fabric8.kubernetes.api.model.VolumeMountBuilder",

            // ResourceVersion and metadata
            "io.fabric8.kubernetes.api.model.ObjectFieldSelector",
            "io.fabric8.kubernetes.api.model.ObjectFieldSelectorBuilder",
            "io.fabric8.kubernetes.api.model.OwnerReference",
            "io.fabric8.kubernetes.api.model.OwnerReferenceBuilder"
        };

        List<Class<?>> classList = new ArrayList<>();

        for (String className : explicitClasses) {
            try {
                Class<?> c =
                        Class.forName(
                                className, false, Thread.currentThread().getContextClassLoader());
                classList.add(c);
            } catch (ClassNotFoundException ex) {
                // Silently ignore classes that don't exist in this version
            }
        }

        if (!classList.isEmpty()) {
            Class<?>[] classes = classList.toArray(new Class<?>[0]);
            // Register with BindingReflectionHintsRegistrar for proper Jackson support
            hintsRegistrar.registerReflectionHints(hints.reflection(), classes);

            // Also register with explicit constructor and field access for deserialization
            for (Class<?> c : classes) {
                hints.reflection()
                        .registerType(
                                c,
                                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                                MemberCategory.INVOKE_PUBLIC_METHODS,
                                MemberCategory.INVOKE_DECLARED_METHODS,
                                MemberCategory.ACCESS_DECLARED_FIELDS,
                                MemberCategory.ACCESS_PUBLIC_FIELDS);
                hints.serialization().registerType(TypeReference.of(c));
            }
        }
    }

    private void registerResourceHints(RuntimeHints hints) {
        hints.resources().registerPattern(".*\\.properties$");
        hints.resources().registerPattern(".*\\.yaml$");
        hints.resources().registerPattern(".*\\.yml$");
        hints.resources().registerPattern(".*\\.txt$");
    }

    private void registerProxyHints(RuntimeHints hints, ClassLoader cl) {
        try {
            Class<?> watchable =
                    Class.forName("io.fabric8.kubernetes.client.dsl.Watchable", false, cl);
            Class<?> autoClose = Class.forName("java.lang.AutoCloseable", false, cl);
            hints.proxies().registerJdkProxy(watchable, autoClose);
        } catch (Throwable ex) {
            // ignore
        }

        try {
            Class<?> nameable =
                    Class.forName("io.fabric8.kubernetes.client.dsl.Nameable", false, cl);
            Class<?> serializable = Class.forName("java.io.Serializable", false, cl);
            hints.proxies().registerJdkProxy(nameable, serializable);
        } catch (Throwable ex) {
            // ignore
        }
    }

    private void registerJacksonHints(RuntimeHints hints) {
        // Register Jackson infrastructure for proper serialization/deserialization
        String[] jacksonClasses = {
            "com.fasterxml.jackson.databind.ser.BeanSerializerFactory",
            "com.fasterxml.jackson.databind.deser.BeanDeserializerFactory",
            "com.fasterxml.jackson.databind.deser.FieldProperty",
            "com.fasterxml.jackson.databind.deser.SetterlessProperty",
            "com.fasterxml.jackson.databind.DeserializationContext",
            "com.fasterxml.jackson.databind.deser.BeanDeserializer",
            "com.fasterxml.jackson.databind.deser.impl.MethodProperty",
            "io.fabric8.kubernetes.model.jackson.SettableBeanPropertyDelegating"
        };

        List<Class<?>> classList = new ArrayList<>();
        for (String className : jacksonClasses) {
            try {
                Class<?> c =
                        Class.forName(
                                className, false, Thread.currentThread().getContextClassLoader());
                classList.add(c);
            } catch (ClassNotFoundException ex) {
                // ignore if class not present
            }
        }

        if (!classList.isEmpty()) {
            Class<?>[] classes = classList.toArray(new Class<?>[0]);
            hints.reflection()
                    .registerTypes(
                            TypeReference.listOf(classes),
                            TypeHint.builtWith(
                                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                                    MemberCategory.INVOKE_PUBLIC_METHODS,
                                    MemberCategory.INVOKE_DECLARED_METHODS,
                                    MemberCategory.ACCESS_DECLARED_FIELDS,
                                    MemberCategory.ACCESS_PUBLIC_FIELDS));
            for (Class<?> c : classes) {
                hints.serialization().registerType(TypeReference.of(c));
            }
        }
    }
}
