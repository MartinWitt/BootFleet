package io.github.martinwitt.todoapp.tag;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {
    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    public List<Tag> findAll() {
        return tagRepository.findAll();
    }

    public Tag save(Tag tag) {
        return tagRepository.save(tag);
    }

    public void deleteById(Long id) {
        tagRepository.deleteById(id);
    }

    /** Returns the tag with the given name, creating it first if it does not exist. */
    @Transactional
    public Tag ensureTag(String name) {
        return tagRepository.findByName(name).orElseGet(() -> tagRepository.save(new Tag(name)));
    }
}
