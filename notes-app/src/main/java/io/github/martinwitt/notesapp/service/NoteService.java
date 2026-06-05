package io.github.martinwitt.notesapp.service;

import io.github.martinwitt.notesapp.domain.Note;
import io.github.martinwitt.notesapp.domain.Tag;
import io.github.martinwitt.notesapp.repository.NoteRepository;
import io.github.martinwitt.notesapp.repository.TagRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoteService {
    private final NoteRepository noteRepository;
    private final TagRepository tagRepository;

    public NoteService(NoteRepository noteRepository, TagRepository tagRepository) {
        this.noteRepository = noteRepository;
        this.tagRepository = tagRepository;
    }

    public List<Note> findAll() {
        return noteRepository.findAllByOrderByUpdatedAtDesc();
    }

    public Optional<Note> findById(Long id) {
        return noteRepository.findById(id);
    }

    public Note save(Note note) {
        return noteRepository.save(note);
    }

    public void deleteById(Long id) {
        noteRepository.deleteById(id);
    }

    public List<Note> findByTagName(String tagName) {
        return noteRepository.findByTags_NameOrderByUpdatedAtDesc(tagName);
    }

    public List<Tag> findAllTags() {
        return tagRepository.findAll();
    }

    @Transactional
    public Tag ensureTag(String name) {
        return tagRepository.findByName(name).orElseGet(() -> tagRepository.save(new Tag(name)));
    }

    @Transactional
    public boolean addTag(Long noteId, String tagName) {
        Optional<Note> noteOpt = noteRepository.findById(noteId);
        if (noteOpt.isEmpty()) return false;
        Note note = noteOpt.get();
        Tag tag = ensureTag(tagName);
        note.getTags().add(tag);
        noteRepository.save(note);
        return true;
    }
}
