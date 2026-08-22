package io.github.martinwitt.codesweeper.process;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Runs the same spotless/test commands CLAUDE.md requires for any Java edit in this repo. */
@Component
public class MavenClient {

    private static final Logger log = LoggerFactory.getLogger(MavenClient.class);

    private final ProcessRunner processRunner;

    public MavenClient(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    public void spotlessApply(Path checkout, String module) {
        ProcessResult result =
                processRunner.run(
                        checkout, mavenCommand(module, "spotless:apply", "--no-transfer-progress"));
        if (!result.success()) {
            log.warn("spotless:apply failed for module {}: {}", module, result.output());
        }
    }

    public boolean test(Path checkout, String module) {
        ProcessResult result =
                processRunner.run(
                        checkout, mavenCommand(module, "test", "--no-transfer-progress", "-q"));
        if (!result.success()) {
            log.warn("mvn test failed for module {}: {}", module, result.output());
        }
        return result.success();
    }

    /**
     * Always builds from the checkout root with {@code -pl <module> -am} instead of cd-ing into the
     * module directory, so Maven resolves the module through the reactor and builds any
     * reactor-local (snapshot) dependencies it needs first - required for any multi-module Maven
     * project, not just single-module checkouts.
     */
    private String[] mavenCommand(String module, String... goalAndArgs) {
        List<String> command = new ArrayList<>();
        command.add("mvn");
        if (module != null) {
            command.add("-pl");
            command.add(module);
            command.add("-am");
        }
        command.addAll(List.of(goalAndArgs));
        return command.toArray(new String[0]);
    }
}
