package io.github.martinwitt.servicefinder.aot;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.jspecify.annotations.NonNull;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * Registers GraalVM native-image reflection and resource hints for all Fabric8 Kubernetes client
 * model classes. Must be imported via {@code @ImportRuntimeHints} on the Kubernetes config bean.
 */
public class KubernetesClientRuntimeHintsRegistrar implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(@NonNull RuntimeHints hints, ClassLoader classLoader) {
        try (ScanResult scanResult =
                new ClassGraph()
                        .enableClassInfo()
                        .ignoreClassVisibility()
                        .acceptPackages("io.fabric8.kubernetes")
                        .overrideClassLoaders(classLoader)
                        .scan()) {

            for (ClassInfo classInfo : scanResult.getAllClasses()) {
                hints.reflection()
                        .registerType(
                                TypeReference.of(classInfo.getName()), MemberCategory.values());
                hints.serialization().registerType(TypeReference.of(classInfo.getName()));
            }
        }

        hints.reflection()
                .registerType(
                        TypeReference.of("io.fabric8.kubernetes.client.impl.KubernetesClientImpl"),
                        MemberCategory.values());

        hints.reflection()
                .registerType(
                        TypeReference.of(
                                "io.fabric8.kubernetes.client.vertx.VertxHttpClientFactory"),
                        MemberCategory.values());

        hints.resources()
                .registerPattern(
                        "META-INF/services/io.fabric8.kubernetes.client.http.HttpClient.Factory");

        hints.resources().registerPattern("**/vertx-version.txt");
    }
}
