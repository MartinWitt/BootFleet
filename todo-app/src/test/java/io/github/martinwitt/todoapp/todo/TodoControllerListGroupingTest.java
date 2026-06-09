package io.github.martinwitt.todoapp.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.martinwitt.todoapp.tag.TagService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

@ExtendWith(MockitoExtension.class)
class TodoControllerListGroupingTest {

    @Mock private TodoService todoService;

    @Mock private TagService tagService;

    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private TodoController todoController;

    @Test
    void shouldPutCronTodoInRecurringGroup() {
        Todo todo = recurringTodo("daily standup", "0 9 * * 1-5", TodoStatus.OPEN);
        when(todoService.findAllSorted()).thenReturn(List.of(todo));
        when(tagService.findAll()).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        todoController.list(null, model);

        assertThat(listFrom(model, "recurringTodos")).containsExactly(todo);
        assertThat(listFrom(model, "oneTimeTodos")).isEmpty();
        assertThat(listFrom(model, "doneTodos")).isEmpty();
    }

    @Test
    void shouldPutOneTimeTodoInOneTimeGroup() {
        Todo todo = oneTimeTodo("buy milk", TodoStatus.OPEN);
        when(todoService.findAllSorted()).thenReturn(List.of(todo));
        when(tagService.findAll()).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        todoController.list(null, model);

        assertThat(listFrom(model, "oneTimeTodos")).containsExactly(todo);
        assertThat(listFrom(model, "recurringTodos")).isEmpty();
        assertThat(listFrom(model, "doneTodos")).isEmpty();
    }

    @Test
    void shouldPutDoneTodoInDoneGroup() {
        Todo todo = oneTimeTodo("finished task", TodoStatus.DONE);
        when(todoService.findAllSorted()).thenReturn(List.of(todo));
        when(tagService.findAll()).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        todoController.list(null, model);

        assertThat(listFrom(model, "doneTodos")).containsExactly(todo);
        assertThat(listFrom(model, "recurringTodos")).isEmpty();
        assertThat(listFrom(model, "oneTimeTodos")).isEmpty();
    }

    @Test
    void shouldPutDoneCronTodoInDoneGroupNotRecurring() {
        Todo todo = recurringTodo("finished recurring", "0 9 * * *", TodoStatus.DONE);
        when(todoService.findAllSorted()).thenReturn(List.of(todo));
        when(tagService.findAll()).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        todoController.list(null, model);

        assertThat(listFrom(model, "doneTodos")).containsExactly(todo);
        assertThat(listFrom(model, "recurringTodos")).isEmpty();
        assertThat(listFrom(model, "oneTimeTodos")).isEmpty();
    }

    @Test
    void shouldPutInProgressCronTodoInRecurringGroupNotOneTime() {
        Todo todo = recurringTodo("wip recurring", "0 9 * * *", TodoStatus.IN_PROGRESS);
        when(todoService.findAllSorted()).thenReturn(List.of(todo));
        when(tagService.findAll()).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        todoController.list(null, model);

        assertThat(listFrom(model, "recurringTodos")).containsExactly(todo);
        assertThat(listFrom(model, "oneTimeTodos")).isEmpty();
        assertThat(listFrom(model, "doneTodos")).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private List<Todo> listFrom(Model model, String attr) {
        return (List<Todo>) model.getAttribute(attr);
    }

    private Todo recurringTodo(String title, String cron, TodoStatus status) {
        Todo t = new Todo();
        t.setTitle(title);
        t.setCronExpression(cron);
        t.setStatus(status);
        return t;
    }

    private Todo oneTimeTodo(String title, TodoStatus status) {
        Todo t = new Todo();
        t.setTitle(title);
        t.setStatus(status);
        return t;
    }
}
