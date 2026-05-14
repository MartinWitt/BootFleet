package io.github.martinwitt.imagedetector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableConfigurationProperties(ImageDetectorProperties.class)
public class ImageUpdateDetectorApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(ImageUpdateDetectorApplication.class);
        application.setAdditionalProfiles("local");
        application.run(args);
    }
}
