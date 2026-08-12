package io.github.martinwitt.notesapp;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.github.martinwitt.notesapp.domain.Note;
import io.github.martinwitt.notesapp.domain.Tag;
import java.time.temporal.Temporal;
import org.hibernate.validator.internal.constraintvalidators.bv.NotBlankValidator;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.thymeleaf.expression.Temporals;

class NotesAppRuntimeHintsTest {

    private final RuntimeHints hints = new RuntimeHints();

    @Test
    void domainTypesAreRegistered() {
        new NotesAppRuntimeHints()
                .registerHints(hints, Thread.currentThread().getContextClassLoader());
        assertThat(RuntimeHintsPredicates.reflection().onType(Note.class).test(hints)).isTrue();
        assertThat(RuntimeHintsPredicates.reflection().onType(Tag.class).test(hints)).isTrue();
    }

    @Test
    void notBlankValidatorIsRegisteredForReflection() {
        new NotesAppRuntimeHints()
                .registerHints(hints, Thread.currentThread().getContextClassLoader());
        assertThat(RuntimeHintsPredicates.reflection().onType(NotBlankValidator.class).test(hints))
                .isTrue();
    }

    @Test
    void temporalsFormatIsRegisteredForReflection() throws NoSuchMethodException {
        new NotesAppRuntimeHints()
                .registerHints(hints, Thread.currentThread().getContextClassLoader());
        assertThat(
                        RuntimeHintsPredicates.reflection()
                                .onMethod(
                                        Temporals.class.getMethod(
                                                "format", Temporal.class, String.class))
                                .test(hints))
                .isTrue();
    }
}
