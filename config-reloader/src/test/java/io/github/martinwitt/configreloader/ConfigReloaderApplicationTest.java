package io.github.martinwitt.configreloader;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
        properties = {
            "spring.autoconfigure.exclude=io.github.martinwitt.configreloader.ResourceWatcherService"
        })
class ConfigReloaderApplicationTest {

    @Test
    void contextLoads() {
        // This test verifies that the Spring Boot context can load successfully
    }
}
