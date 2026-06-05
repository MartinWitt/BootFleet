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
    void shouldUpdateUpdatedAtOnUpdate() throws InterruptedException {
        Note note = new Note();
        note.prePersist();
        LocalDateTime before = note.getUpdatedAt();

        Thread.sleep(2);
        note.preUpdate();

        assertThat(note.getUpdatedAt()).isAfter(before);
    }
}
