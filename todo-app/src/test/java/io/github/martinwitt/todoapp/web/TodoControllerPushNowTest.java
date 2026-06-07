package io.github.martinwitt.todoapp.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import io.github.martinwitt.todoapp.service.TagService;
import io.github.martinwitt.todoapp.service.TodoService;
import io.github.martinwitt.todoapp.telegram.SendTodosNowEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@ExtendWith(MockitoExtension.class)
class TodoControllerPushNowTest {

    @Mock private TodoService todoService;
    @Mock private TagService tagService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private TodoController todoController;

    @Test
    void shouldPublishSendTodosNowEvent() {
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

        todoController.pushNow(redirectAttributes);

        verify(eventPublisher).publishEvent(any(SendTodosNowEvent.class));
    }

    @Test
    void shouldRedirectToTodoList() {
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

        String result = todoController.pushNow(redirectAttributes);

        assertThat(result).isEqualTo("redirect:/todos");
    }

    @Test
    void shouldAddFlashMessage() {
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

        todoController.pushNow(redirectAttributes);

        assertThat(redirectAttributes.getFlashAttributes()).containsKey("pushMessage");
    }
}
