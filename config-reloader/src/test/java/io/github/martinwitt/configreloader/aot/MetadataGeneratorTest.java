package io.github.martinwitt.configreloader.aot;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.fabric8.kubernetes.client.impl.KubernetesClientImpl;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

class MetadataGeneratorTest {

    @Test
    void generateMetadata() throws IOException {
        RuntimeHints hints = new RuntimeHints();
        new KubernetesClientRuntimeHintsRegistrar()
                .registerHints(hints, Thread.currentThread().getContextClassLoader());
        // Registrar run successfully.
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
