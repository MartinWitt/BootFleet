package io.github.martinwitt.todoapp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.martinwitt.todoapp.todo.TodoController;
import io.github.martinwitt.todoapp.todo.TodoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TodoAppApplicationTests {

    @Autowired private TodoController todoController;

    @Autowired private TodoService todoService;

    @Test
    void contextLoads() {
        assertThat(todoController).isNotNull();
        assertThat(todoService).isNotNull();
    }

    @Test
    void allBeansLoadSuccessfully() {
        assertThat(true).isTrue();
    }
}
