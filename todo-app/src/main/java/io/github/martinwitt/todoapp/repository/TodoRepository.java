package io.github.martinwitt.todoapp.repository;

import io.github.martinwitt.todoapp.domain.Todo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TodoRepository extends JpaRepository<Todo, Long> {
    List<Todo> findAllByOrderByPositionAscDeadlineAsc();

    List<Todo> findByTags_NameOrderByPositionAscDeadlineAsc(String name);
}
