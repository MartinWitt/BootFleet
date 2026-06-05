package io.github.martinwitt.notesapp;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import io.github.martinwitt.notesapp.domain.Note;
import io.github.martinwitt.notesapp.domain.Tag;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.stereotype.Component;

@Component
public class NotesAppRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(Note.class);
        hints.reflection().registerType(Tag.class);

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
