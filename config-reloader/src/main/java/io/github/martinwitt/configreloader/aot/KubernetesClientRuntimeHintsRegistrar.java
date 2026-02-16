package io.github.martinwitt.configreloader.aot;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.jspecify.annotations.NonNull;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * Register runtime hints for the Kubernetes client model classes. This is needed for GraalVM native
 * image support.
 */
public class KubernetesClientRuntimeHintsRegistrar implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(@NonNull RuntimeHints hints, ClassLoader classLoader) {
        try (ScanResult scanResult =
                new ClassGraph()
                        .enableClassInfo()
                        .ignoreClassVisibility()
                        .acceptPackages("io.fabric8.kubernetes") // FIXED
                        .overrideClassLoaders(classLoader)
                        .scan()) {

            for (ClassInfo classInfo : scanResult.getAllClasses()) {
                hints.reflection()
                        .registerType(
                                TypeReference.of(classInfo.getName()), MemberCategory.values());
                hints.serialization().registerType(TypeReference.of(classInfo.getName()));
            }
        }

        // Keep the core client impl (Fabric8 loads this via reflection)
        hints.reflection()
                .registerType(
                        TypeReference.of("io.fabric8.kubernetes.client.impl.KubernetesClientImpl"),
                        MemberCategory.values());

        // Vert.x HTTP client factory
        hints.reflection()
                .registerType(
                        TypeReference.of(
                                "io.fabric8.kubernetes.client.vertx.VertxHttpClientFactory"),
                        MemberCategory.values());

        // SPI file
        hints.resources()
                .registerPattern(
                        "META-INF/services/io.fabric8.kubernetes.client.http.HttpClient.Factory");

        // Vert.x version file
        hints.resources().registerPattern("**/vertx-version.txt");
    }
}
