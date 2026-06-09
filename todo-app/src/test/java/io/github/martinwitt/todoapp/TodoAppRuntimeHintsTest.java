package io.github.martinwitt.todoapp;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.github.martinwitt.todoapp.tag.Tag;
import io.github.martinwitt.todoapp.todo.Todo;
import io.github.martinwitt.todoapp.todo.TodoStatus;
import org.hibernate.validator.internal.constraintvalidators.bv.NotBlankValidator;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

class TodoAppRuntimeHintsTest {

    private final RuntimeHints hints = new RuntimeHints();

    @Test
    void domainTypesAreRegistered() {
        new TodoAppRuntimeHints()
                .registerHints(hints, Thread.currentThread().getContextClassLoader());
        assertThat(RuntimeHintsPredicates.reflection().onType(Todo.class).test(hints)).isTrue();
        assertThat(RuntimeHintsPredicates.reflection().onType(Tag.class).test(hints)).isTrue();
        assertThat(RuntimeHintsPredicates.reflection().onType(TodoStatus.class).test(hints))
                .isTrue();
    }

    @Test
    void notBlankValidatorIsRegisteredForReflection() {
        new TodoAppRuntimeHints()
                .registerHints(hints, Thread.currentThread().getContextClassLoader());
        assertThat(RuntimeHintsPredicates.reflection().onType(NotBlankValidator.class).test(hints))
                .isTrue();
    }
}
