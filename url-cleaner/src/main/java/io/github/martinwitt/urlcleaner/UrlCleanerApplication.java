package io.github.martinwitt.urlcleaner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UrlCleanerApplication {

    // fix this in spring 4.1
    static void main(String[] args) {
        SpringApplication.run(UrlCleanerApplication.class);
    }
}
