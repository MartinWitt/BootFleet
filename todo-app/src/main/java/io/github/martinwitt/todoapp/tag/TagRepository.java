package io.github.martinwitt.todoapp.tag;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByName(String name);
}
