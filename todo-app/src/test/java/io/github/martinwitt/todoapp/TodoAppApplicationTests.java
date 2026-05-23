package io.github.martinwitt.todoapp;

import io.github.martinwitt.todoapp.service.TodoService;
import io.github.martinwitt.todoapp.web.TodoController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

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
