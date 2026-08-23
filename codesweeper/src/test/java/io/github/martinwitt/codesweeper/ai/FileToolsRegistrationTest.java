package io.github.martinwitt.codesweeper.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

class FileToolsRegistrationTest {

    @TempDir Path checkout;

    @Test
    void fixerToolsExposeAllFourToolNames() {
        MethodToolCallbackProvider provider =
                MethodToolCallbackProvider.builder()
                        .toolObjects(new ReadOnlyFileTools(checkout), new WriteFileTools(checkout))
                        .build();

        assertThat(Arrays.stream(provider.getToolCallbacks()).map(ToolCallback::getToolDefinition))
                .extracting(def -> def.name())
                .containsExactlyInAnyOrder("listFiles", "searchFiles", "readFile", "editFile");
    }
}
