package io.github.martinwitt.servicefinder;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.github.martinwitt.servicefinder.aot.KubernetesClientRuntimeHintsRegistrar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(KubernetesClientRuntimeHintsRegistrar.class)
public class KubernetesConfig {

    @Bean
    public KubernetesClient kubernetesClient() {
        return new KubernetesClientBuilder().build();
    }
}
