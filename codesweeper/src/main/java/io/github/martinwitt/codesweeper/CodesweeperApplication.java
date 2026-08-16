package io.github.martinwitt.codesweeper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CodesweeperApplication {

    public static void main(String[] args) {
        System.exit(
                SpringApplication.exit(SpringApplication.run(CodesweeperApplication.class, args)));
    }
}
