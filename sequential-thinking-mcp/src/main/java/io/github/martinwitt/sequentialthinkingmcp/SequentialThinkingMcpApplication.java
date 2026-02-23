package io.github.martinwitt.sequentialthinkingmcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot application for Sequential Thinking MCP server. Provides dynamic and reflective
 * problem-solving capabilities through model context protocol.
 */
@SpringBootApplication
@EnableScheduling
public class SequentialThinkingMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(SequentialThinkingMcpApplication.class, args);
    }
}
