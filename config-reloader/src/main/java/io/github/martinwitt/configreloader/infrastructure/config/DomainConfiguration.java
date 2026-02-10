package io.github.martinwitt.configreloader.infrastructure.config;

import io.github.martinwitt.configreloader.domain.port.WorkloadReader;
import io.github.martinwitt.configreloader.domain.port.WorkloadRestarter;
import io.github.martinwitt.configreloader.domain.service.ConfigResourceRepository;
import io.github.martinwitt.configreloader.domain.service.WorkloadConfigurationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configuration for domain services and dependency injection. */
@Configuration
public class DomainConfiguration {

    @Bean
    public WorkloadConfigurationService workloadConfigurationService(
            ConfigResourceRepository repository,
            WorkloadReader workloadReader,
            WorkloadRestarter workloadRestarter) {
        return new WorkloadConfigurationService(repository, workloadReader, workloadRestarter);
    }
}
