package io.github.martinwitt.notesapp.repository;

import io.github.martinwitt.notesapp.domain.Note;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteRepository extends JpaRepository<Note, Long> {
    List<Note> findAllByOrderByUpdatedAtDesc();

    List<Note> findByTags_NameOrderByUpdatedAtDesc(String tagName);
}
