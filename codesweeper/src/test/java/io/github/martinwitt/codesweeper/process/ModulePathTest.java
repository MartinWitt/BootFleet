package io.github.martinwitt.codesweeper.process;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModulePathTest {

    @Test
    void extractsRootDirectoryAsModule() {
        assertThat(ModulePath.moduleFor("maven-version-mcp/src/main/java/Foo.java"))
                .isEqualTo("maven-version-mcp");
    }

    @Test
    void returnsWholePathWhenNoSlash() {
        assertThat(ModulePath.moduleFor("pom.xml")).isEqualTo("pom.xml");
    }
}
