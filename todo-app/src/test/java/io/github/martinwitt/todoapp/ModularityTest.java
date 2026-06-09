package io.github.martinwitt.todoapp;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    @Test
    void verifiesModularStructure() {
        ApplicationModules.of(TodoAppApplication.class).verify();
    }
}
