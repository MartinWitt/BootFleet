package io.github.martinwitt.todoapp.service;

import io.github.martinwitt.todoapp.domain.Tag;
import io.github.martinwitt.todoapp.domain.Todo;
import io.github.martinwitt.todoapp.domain.TodoStatus;
import io.github.martinwitt.todoapp.repository.TagRepository;
import io.github.martinwitt.todoapp.repository.TodoRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TodoService {
    private final TodoRepository todoRepository;
    private final TagRepository tagRepository;

    public TodoService(TodoRepository todoRepository, TagRepository tagRepository) {
        this.todoRepository = todoRepository;
        this.tagRepository = tagRepository;
    }

    public List<Todo> findAllSorted() {
        return todoRepository.findAllByOrderByPositionAscDeadlineAsc();
    }

    public Optional<Todo> findById(Long id) {
        return todoRepository.findById(id);
    }

    public Todo save(Todo todo) {
        return todoRepository.save(todo);
    }

    public void deleteById(Long id) {
        todoRepository.deleteById(id);
    }

    public List<Todo> findByTagName(String tagName) {
        return todoRepository.findByTags_NameOrderByPositionAscDeadlineAsc(tagName);
    }

    @Transactional
    public void reorder(List<Long> orderedIds) {
        List<Todo> todos = todoRepository.findAllById(orderedIds);
        Map<Long, Integer> positionMap = new HashMap<>();
        for (int i = 0; i < orderedIds.size(); i++) {
            positionMap.put(orderedIds.get(i), i);
        }
        todos.forEach(t -> t.setPosition(positionMap.get(t.getId())));
        todoRepository.saveAll(todos);
    }

    @Transactional
    public boolean changeStatus(Long id, String status) {
        TodoStatus parsedStatus = TodoStatus.valueOf(status);
        return todoRepository
                .findById(id)
                .map(
                        t -> {
                            t.setStatus(parsedStatus);
                            todoRepository.save(t);
                            return true;
                        })
                .orElse(false);
    }

    @Transactional
    public Tag ensureTag(String name) {
        return tagRepository.findByName(name).orElseGet(() -> tagRepository.save(new Tag(name)));
    }
}
