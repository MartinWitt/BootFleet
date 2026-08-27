package io.github.martinwitt.codesweeper.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WriteFileToolsTest {

    @TempDir Path checkout;

    @Test
    void editsUniqueOccurrence() throws IOException {
        Path file = Files.writeString(checkout.resolve("A.java"), "int a = 1;\nint b = 2;\n");
        WriteFileTools tools = new WriteFileTools(checkout);

        String result = tools.editFile("A.java", "int a = 1;", "int a = 42;");

        assertThat(result).isEqualTo("Edited A.java");
        assertThat(Files.readString(file)).isEqualTo("int a = 42;\nint b = 2;\n");
    }

    @Test
    void reportsLineNumbersWhenOldTextIsAmbiguous() throws IOException {
        Files.writeString(
                checkout.resolve("A.java"),
                "one\nreturn x.status(503).build();\ntwo\nreturn x.status(503).build();\n");
        WriteFileTools tools = new WriteFileTools(checkout);

        String result = tools.editFile("A.java", "return x.status(503).build();", "return x.ok();");

        assertThat(result).contains("matches 2 times").contains("lines 2, 4");
    }

    @Test
    void reportsWhenOldTextIsMissing() throws IOException {
        Files.writeString(checkout.resolve("A.java"), "int a = 1;\n");
        WriteFileTools tools = new WriteFileTools(checkout);

        String result = tools.editFile("A.java", "does not exist", "replacement");

        assertThat(result).contains("oldText not found");
    }
}
