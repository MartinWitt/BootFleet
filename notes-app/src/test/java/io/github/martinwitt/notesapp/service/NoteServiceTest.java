package io.github.martinwitt.notesapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.martinwitt.notesapp.domain.Note;
import io.github.martinwitt.notesapp.domain.Tag;
import io.github.martinwitt.notesapp.repository.NoteRepository;
import io.github.martinwitt.notesapp.repository.TagRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock private NoteRepository noteRepository;
    @Mock private TagRepository tagRepository;
    @InjectMocks private NoteService noteService;

    @Test
    void shouldSaveAndReturnNote() {
        Note note = new Note();
        note.setTitle("Test Note");
        note.setContent("Some content");
        when(noteRepository.save(any(Note.class))).thenReturn(note);

        Note result = noteService.save(note);

        assertThat(result.getTitle()).isEqualTo("Test Note");
        verify(noteRepository).save(note);
    }

    @Test
    void shouldFindNoteById() {
        Note note = new Note();
        note.setTitle("Found");
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        Optional<Note> result = noteService.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Found");
    }

    @Test
    void shouldReturnEmptyWhenNoteNotFound() {
        when(noteRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Note> result = noteService.findById(99L);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldDeleteNoteById() {
        noteService.deleteById(5L);

        verify(noteRepository).deleteById(5L);
    }

    @Test
    void shouldFindAllNotesSortedByUpdatedAtDesc() {
        Note a = new Note();
        a.setTitle("A");
        Note b = new Note();
        b.setTitle("B");
        when(noteRepository.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of(a, b));

        List<Note> result = noteService.findAll();

        assertThat(result).hasSize(2);
    }

    @Test
    void shouldFilterNotesByTagNameSortedByUpdatedAtDesc() {
        Note note = new Note();
        note.setTitle("Tagged");
        when(noteRepository.findByTags_NameOrderByUpdatedAtDesc("java")).thenReturn(List.of(note));

        List<Note> result = noteService.findByTagName("java");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Tagged");
    }

    @Test
    void shouldEnsureTagCreatesNewTagIfAbsent() {
        when(tagRepository.findByName("spring")).thenReturn(Optional.empty());
        Tag saved = new Tag("spring");
        when(tagRepository.save(any(Tag.class))).thenReturn(saved);

        Tag result = noteService.ensureTag("spring");

        assertThat(result.getName()).isEqualTo("spring");
        verify(tagRepository).save(any(Tag.class));
    }

    @Test
    void shouldEnsureTagReturnsExistingTag() {
        Tag existing = new Tag("spring");
        when(tagRepository.findByName("spring")).thenReturn(Optional.of(existing));

        Tag result = noteService.ensureTag("spring");

        assertThat(result).isEqualTo(existing);
        verify(tagRepository).findByName("spring");
    }

    @Test
    void shouldAddTagToNote() {
        Note note = new Note();
        note.setTitle("My Note");
        Tag tag = new Tag("kotlin");
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));
        when(tagRepository.findByName("kotlin")).thenReturn(Optional.of(tag));
        when(noteRepository.save(any(Note.class))).thenReturn(note);

        boolean result = noteService.addTag(1L, "kotlin");

        assertThat(result).isTrue();
        assertThat(note.getTags()).contains(tag);
    }

    @Test
    void shouldReturnFalseWhenAddTagToMissingNote() {
        when(noteRepository.findById(99L)).thenReturn(Optional.empty());

        boolean result = noteService.addTag(99L, "kotlin");

        assertThat(result).isFalse();
    }
}
