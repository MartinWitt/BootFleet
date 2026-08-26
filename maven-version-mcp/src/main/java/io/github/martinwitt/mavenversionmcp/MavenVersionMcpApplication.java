package io.github.martinwitt.mavenversionmcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class MavenVersionMcpApplication {

    static void main(String[] args) {
        SpringApplication.run(MavenVersionMcpApplication.class, args);
    }
}
