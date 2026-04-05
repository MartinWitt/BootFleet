package io.github.martinwitt.todoapp.service;

import io.github.martinwitt.todoapp.domain.Tag;
import io.github.martinwitt.todoapp.domain.Todo;
import io.github.martinwitt.todoapp.domain.TodoStatus;
import io.github.martinwitt.todoapp.repository.TagRepository;
import io.github.martinwitt.todoapp.repository.TodoRepository;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
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
        AtomicInteger pos = new AtomicInteger();
        for (Long id : orderedIds) {
            todoRepository
                    .findById(id)
                    .ifPresent(
                            t -> {
                                t.setPosition(pos.getAndIncrement());
                                todoRepository.save(t);
                            });
        }
    }

    @Transactional
    public void changeStatus(Long id, String status) {
        todoRepository
                .findById(id)
                .ifPresent(
                        t -> {
                            try {
                                t.setStatus(TodoStatus.valueOf(status));
                                todoRepository.save(t);
                            } catch (Exception ignored) {
                            }
                        });
    }

    @Transactional
    public Tag ensureTag(String name) {
        return tagRepository.findByName(name).orElseGet(() -> tagRepository.save(new Tag(name)));
    }
}
