package io.github.martinwitt.codesweeper.process;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModulePathTest {

    @TempDir Path checkout;

    @Test
    void extractsRootDirectoryAsModuleWhenItHasAPom() throws IOException {
        Files.createDirectories(checkout.resolve("maven-version-mcp"));
        Files.createFile(checkout.resolve("maven-version-mcp/pom.xml"));

        assertThat(ModulePath.moduleFor(checkout, "maven-version-mcp/src/main/java/Foo.java"))
                .contains("maven-version-mcp");
    }

    @Test
    void isEmptyWhenRootDirectoryHasNoPom() {
        assertThat(ModulePath.moduleFor(checkout, "src/main/java/Foo.java")).isEmpty();
    }

    @Test
    void isEmptyWhenNoSlash() {
        assertThat(ModulePath.moduleFor(checkout, "pom.xml")).isEmpty();
    }
}
