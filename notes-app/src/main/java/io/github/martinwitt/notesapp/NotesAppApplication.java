package io.github.martinwitt.notesapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints({NotesAppRuntimeHints.class, LiquibaseNativeHints.class})
public class NotesAppApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotesAppApplication.class, args);
    }
}
