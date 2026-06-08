package io.github.martinwitt.todoapp.telegram;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

class TelegramRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        MemberCategory[] all = MemberCategory.values();

        // Register all Telegram Bot API types for Jackson deserialization in a native image.
        // The library uses Lombok @Builder which generates *Builder/*BuilderImpl inner classes —
        // their build() method must be accessible via reflection.
        try (ScanResult scan =
                new ClassGraph()
                        .enableClassInfo()
                        .acceptPackages("org.telegram.telegrambots.meta.api")
                        .scan()) {
            scan.getAllClasses()
                    .forEach(
                            classInfo ->
                                    hints.reflection()
                                            .registerTypeIfPresent(
                                                    classLoader, classInfo.getName(), all));
        }

        hints.reflection()
                .registerType(TelegramProperties.class, all)
                .registerType(TelegramProperties.Bot.class, all)
                .registerType(TelegramProperties.User.class, all);

        hints.reflection().registerType(SendTodosNowEvent.class, all);
    }
}
