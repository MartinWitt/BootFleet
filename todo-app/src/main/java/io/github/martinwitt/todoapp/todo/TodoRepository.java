package io.github.martinwitt.todoapp.todo;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface TodoRepository extends JpaRepository<Todo, Long> {
    List<Todo> findAllByOrderByPositionAscDeadlineAsc();

    List<Todo> findByTags_NameOrderByPositionAscDeadlineAsc(String name);

    List<Todo> findByStatusAndDeadlineLessThanEqual(TodoStatus status, LocalDateTime deadline);

    List<Todo> findByCronExpressionIsNotNull();

    List<Todo> findByStatusAndDeadlineIsNullAndCronExpressionIsNullOrderByPositionAsc(
            TodoStatus status);
}
