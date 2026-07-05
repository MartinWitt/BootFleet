package io.github.martinwitt.todoapp.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.martinwitt.todoapp.todo.Todo;
import io.github.martinwitt.todoapp.todo.TodoService;
import io.github.martinwitt.todoapp.todo.TodoStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@ExtendWith(MockitoExtension.class)
class TodoNotificationServiceSomedayTest {

    private static final long CHAT_ID = 123L;

    @Mock private TodoService todoService;
    @Mock private TelegramClient telegramClient;

    private TodoNotificationService service;

    @BeforeEach
    void setUp() {
        service =
                new TodoNotificationService(
                        todoService,
                        telegramClient,
                        new TelegramProperties(
                                new TelegramProperties.Bot("token", "bot"),
                                new TelegramProperties.User(CHAT_ID)));
    }

    @Test
    void sendTodaysTodos_shouldIncludeSomedayTodosWhenPresent() throws Exception {
        Todo someday = somedayTodo(1L, "Read a book");
        when(todoService.findDueTodos()).thenReturn(List.of());
        when(todoService.findRecurring()).thenReturn(List.of());
        when(todoService.findSomeday()).thenReturn(List.of(someday));

        service.sendTodaysTodos();

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, times(1)).execute(captor.capture());
        assertThat(captor.getValue().getText())
                .contains("📋 Someday (noch offen):")
                .contains("Read a book");
    }

    @Test
    void sendTodaysTodos_shouldOmitSomedayBlockWhenNoSomedayTodos() throws Exception {
        Todo dueTodo = new Todo();
        dueTodo.setId(1L);
        dueTodo.setTitle("Fix bug");
        dueTodo.setStatus(TodoStatus.OPEN);
        when(todoService.findDueTodos()).thenReturn(List.of(dueTodo));
        when(todoService.findRecurring()).thenReturn(List.of());
        when(todoService.findSomeday()).thenReturn(List.of());

        service.sendTodaysTodos();

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, times(1)).execute(captor.capture());
        assertThat(captor.getValue().getText()).doesNotContain("Someday");
    }

    @Test
    void sendTodaysTodos_shouldNotSendEmptyMessageWhenOnlySomedayTodosExist() throws Exception {
        Todo someday = somedayTodo(1L, "Learn Spanish");
        when(todoService.findDueTodos()).thenReturn(List.of());
        when(todoService.findRecurring()).thenReturn(List.of());
        when(todoService.findSomeday()).thenReturn(List.of(someday));

        service.sendTodaysTodos();

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, times(1)).execute(captor.capture());
        assertThat(captor.getValue().getText()).doesNotContain("Keine offenen Todos");
    }

    @Test
    void sendTodaysTodos_shouldSendEmptyMessageWhenAllThreeCategoriesAreEmpty() throws Exception {
        when(todoService.findDueTodos()).thenReturn(List.of());
        when(todoService.findRecurring()).thenReturn(List.of());
        when(todoService.findSomeday()).thenReturn(List.of());

        service.sendTodaysTodos();

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getText()).isEqualTo("Keine offenen Todos für heute 🎉");
    }

    @Test
    void sendTodaysTodos_shouldSendInlineButtonForSomedayTodo() throws Exception {
        Todo someday = somedayTodo(42L, "Travel to Japan");
        when(todoService.findDueTodos()).thenReturn(List.of());
        when(todoService.findRecurring()).thenReturn(List.of());
        when(todoService.findSomeday()).thenReturn(List.of(someday));

        service.sendTodaysTodos();

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, times(1)).execute(captor.capture());
        assertThat(captor.getValue().getReplyMarkup()).isNotNull();
    }

    @Test
    void sendTodaysTodos_shouldSendMultipleSomedayTodosInOrder() throws Exception {
        Todo first = somedayTodo(1L, "Alpha");
        Todo second = somedayTodo(2L, "Beta");
        when(todoService.findDueTodos()).thenReturn(List.of());
        when(todoService.findRecurring()).thenReturn(List.of());
        when(todoService.findSomeday()).thenReturn(List.of(first, second));

        service.sendTodaysTodos();

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, times(1)).execute(captor.capture());
        assertThat(captor.getValue().getText())
                .isEqualTo("📋 Someday (noch offen):\n1. Alpha\n2. Beta");
    }

    private Todo somedayTodo(Long id, String title) {
        Todo todo = new Todo();
        todo.setId(id);
        todo.setTitle(title);
        todo.setStatus(TodoStatus.OPEN);
        return todo;
    }
}
