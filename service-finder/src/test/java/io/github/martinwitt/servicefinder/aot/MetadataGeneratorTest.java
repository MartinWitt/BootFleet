package io.github.martinwitt.servicefinder.aot;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.fabric8.kubernetes.client.impl.KubernetesClientImpl;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

class MetadataGeneratorTest {

    @Test
    void generateMetadata() {
        RuntimeHints hints = new RuntimeHints();
        new KubernetesClientRuntimeHintsRegistrar()
                .registerHints(hints, Thread.currentThread().getContextClassLoader());
        assertThat(
                        RuntimeHintsPredicates.reflection()
                                .onType(KubernetesClientImpl.class)
                                .test(hints))
                .isTrue();
        assertThat(
                        RuntimeHintsPredicates.resource()
                                .forResource("META-INF/vertx/vertx-version.txt")
                                .test(hints))
                .isTrue();
    }
}
