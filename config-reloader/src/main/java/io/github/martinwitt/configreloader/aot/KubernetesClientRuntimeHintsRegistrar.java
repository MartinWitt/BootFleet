package io.github.martinwitt.configreloader.aot;

import io.fabric8.kubernetes.client.impl.KubernetesClientImpl;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;
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
    public void registerHints(@NonNull RuntimeHints hints, ClassLoader classLoader) {
        registerResourceHints(hints);
        registerKubernetesClientHints(hints);
        registerKubernetesModelHints(hints);
        registerJacksonHints(hints);
    }

    private void registerKubernetesClientHints(RuntimeHints hints) {
        hints.reflection()
                .registerTypes(
                        TypeReference.listOf(KubernetesClientImpl.class),
                        TypeHint.builtWith(MemberCategory.values()));
    }

    private void registerKubernetesModelHints(RuntimeHints hints) {
        // Single comprehensive scan of all Kubernetes model packages
        var graph = new ClassGraph().acceptPackages("io.fabric8.kubernetes.api.*");

        try (var scan = graph.scan()) {
            // Collect all eligible classes
            Class<?>[] allClasses =
                    scan.getAllClasses().stream()
                            .map(ClassInfo::loadClass)
                            .collect(
                                    Collectors.collectingAndThen(
                                            Collectors.toList(),
                                            it -> it.toArray(new Class<?>[0])));

            if (allClasses.length > 0) {
                // Use BindingReflectionHintsRegistrar for proper Jackson support
                // This will introspect Jackson annotations and register all necessary members
                hintsRegistrar.registerReflectionHints(hints.reflection(), allClasses);

                // ALSO explicitly register with full member categories to ensure constructors
                // are accessible for Jackson deserialization - critical for native images
                hints.reflection()
                        .registerTypes(
                                TypeReference.listOf(allClasses),
                                TypeHint.builtWith(MemberCategory.values()));

                // Also register all types for serialization
                for (Class<?> type : allClasses) {
                    hints.serialization().registerType(TypeReference.of(type));
                }
            }
        }
    }

    private void registerResourceHints(RuntimeHints hints) {
        // Catch all approach for Vert.x
        hints.resources()
                .registerPattern("vertx-version.txt")
                .registerPattern("META-INF/vertx/*")
                .registerPattern("META-INF/services/*")
                .registerPattern("**/*.properties")
                .registerPattern("**/*.yaml")
                .registerPattern("**/*.txt")
                .registerPattern("**/*.yml");
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
                            TypeHint.builtWith(MemberCategory.values()));
            for (Class<?> c : classes) {
                hints.serialization().registerType(TypeReference.of(c));
            }
        }
    }
}
