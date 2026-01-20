package io.github.martinwitt.configreloader.aot;

import io.fabric8.kubernetes.client.impl.KubernetesClientImpl;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeHint;
import org.springframework.aot.hint.TypeReference;

/**
 * Runtime hints registrar for Fabric8 Kubernetes client. Uses ClassGraph to dynamically discover
 * all classes in the Kubernetes model packages and registers them for reflection and serialization
 * in GraalVM native images. Uses BindingReflectionHintsRegistrar to properly handle Jackson
 * annotations.
 */
public class KubernetesClientRuntimeHintsRegistrar implements RuntimeHintsRegistrar {

    private final BindingReflectionHintsRegistrar hintsRegistrar =
            new BindingReflectionHintsRegistrar();

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        registerResourceHints(hints);
        registerKubernetesClientHints(hints);
        registerKubernetesModelHints(hints);
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

    private void registerKubernetesModelHints(RuntimeHints hints) {
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
            // Get ALL classes from all model packages
            var allClasses =
                    scan.getAllClasses().stream()
                            .map(ClassInfo::loadClass)
                            .filter(this::isEligibleForRegistration)
                            .toArray(Class[]::new);

            if (allClasses.length > 0) {
                // Use BindingReflectionHintsRegistrar for proper Jackson support
                // This will introspect Jackson annotations and register all necessary members
                hintsRegistrar.registerReflectionHints(hints.reflection(), allClasses);

                // ALSO explicitly register with full member categories to ensure constructors
                // are accessible for Jackson deserialization
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
        String[] quantityClasses = {
            "io.fabric8.kubernetes.api.model.Quantity",
            "io.fabric8.kubernetes.api.model.Quantity$Deserializer",
            "io.fabric8.kubernetes.api.model.Quantity$Serializer"
        };

        for (String className : quantityClasses) {
            try {
                Class<?> c =
                        Class.forName(
                                className, false, Thread.currentThread().getContextClassLoader());
                hintsRegistrar.registerReflectionHints(hints.reflection(), new Class[] {c});
                // Also register with explicit constructor access
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
            } catch (ClassNotFoundException ex) {
                // ignore if class not present
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
}
