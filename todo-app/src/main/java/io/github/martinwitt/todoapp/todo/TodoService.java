package io.github.martinwitt.todoapp.todo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TodoService {
    private final TodoRepository todoRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TodoService(TodoRepository todoRepository, ApplicationEventPublisher eventPublisher) {
        this.todoRepository = todoRepository;
        this.eventPublisher = eventPublisher;
    }

    public List<Todo> findDueTodos() {
        LocalDateTime endOfToday = LocalDate.now(ZoneId.of("Europe/Berlin")).atTime(23, 59, 59);
        return todoRepository.findByStatusAndDeadlineLessThanEqual(TodoStatus.OPEN, endOfToday);
    }

    public List<Todo> findRecurring() {
        return todoRepository.findByCronExpressionIsNotNull().stream()
                .filter(Todo::isRecurring)
                .toList();
    }

    public List<Todo> findSomeday() {
        return todoRepository
                .findByStatusAndDeadlineIsNullAndCronExpressionIsNullOrderByPositionAsc(
                        TodoStatus.OPEN);
    }

    public List<Todo> findAllSorted() {
        return todoRepository.findAllByOrderByPositionAscDeadlineAsc();
    }

    public Optional<Todo> findById(Long id) {
        return todoRepository.findById(id);
    }

    public Todo save(Todo todo) {
        Todo saved = todoRepository.save(todo);
        eventPublisher.publishEvent(new TodoSavedEvent(saved));
        return saved;
    }

    public void deleteById(Long id) {
        todoRepository.deleteById(id);
        eventPublisher.publishEvent(new TodoDeletedEvent(id));
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
    public boolean changeStatus(Long id, TodoStatus status) {
        return todoRepository
                .findById(id)
                .map(
                        t -> {
                            t.setStatus(status);
                            todoRepository.save(t);
                            return true;
                        })
                .orElse(false);
    }

    @Transactional
    public Optional<LocalDateTime> snooze(Long id, int days) {
        return todoRepository
                .findById(id)
                .map(
                        t -> {
                            LocalDateTime base =
                                    t.getDeadline() != null ? t.getDeadline() : LocalDateTime.now();
                            LocalDateTime newDeadline = base.plusDays(days);
                            t.setDeadline(newDeadline);
                            todoRepository.save(t);
                            return newDeadline;
                        });
    }
}
