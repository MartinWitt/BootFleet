package io.github.martinwitt.notesapp.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class NoteTest {

    @Test
    void shouldSetUpdatedAtOnPersist() {
        Note note = new Note();

        note.prePersist();

        assertThat(note.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldUpdateUpdatedAtOnUpdate() {
        Note note = new Note();
        note.setUpdatedAt(LocalDateTime.now().minusSeconds(1));
        LocalDateTime before = note.getUpdatedAt();

        note.preUpdate();

        assertThat(note.getUpdatedAt()).isAfter(before);
    }
}
