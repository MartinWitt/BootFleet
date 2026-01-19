package io.github.martinwitt.configreloader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ConfigReloaderProperties.class)
public class ConfigReloaderApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigReloaderApplication.class, args);
    }
}
