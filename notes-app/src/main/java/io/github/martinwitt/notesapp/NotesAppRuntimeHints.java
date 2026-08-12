package io.github.martinwitt.notesapp;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import io.github.martinwitt.notesapp.domain.Note;
import io.github.martinwitt.notesapp.domain.Tag;
import java.time.temporal.Temporal;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.stereotype.Component;
import org.thymeleaf.expression.Temporals;

@Component
public class NotesAppRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(Note.class);
        hints.reflection().registerType(Tag.class);
        try {
            hints.reflection()
                    .registerMethod(
                            Temporals.class.getMethod("format", Temporal.class, String.class),
                            ExecutableMode.INVOKE);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }

        try (ScanResult scanResult =
                new ClassGraph()
                        .enableClassInfo()
                        .ignoreClassVisibility()
                        .acceptPackages("org.hibernate.validator.internal.constraintvalidators")
                        .overrideClassLoaders(classLoader)
                        .scan()) {
            for (ClassInfo classInfo : scanResult.getAllClasses()) {
                hints.reflection()
                        .registerType(
                                TypeReference.of(classInfo.getName()), MemberCategory.values());
            }
        }
    }
}
