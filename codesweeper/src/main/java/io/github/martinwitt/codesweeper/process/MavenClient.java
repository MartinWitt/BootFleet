package io.github.martinwitt.codesweeper.process;

import java.nio.file.Path;
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
                        moduleDir(checkout, module),
                        "mvn",
                        "spotless:apply",
                        "--no-transfer-progress");
        if (!result.success()) {
            log.warn("spotless:apply failed for module {}: {}", module, result.output());
        }
    }

    public boolean test(Path checkout, String module) {
        ProcessResult result =
                processRunner.run(
                        moduleDir(checkout, module), "mvn", "test", "--no-transfer-progress", "-q");
        if (!result.success()) {
            log.warn("mvn test failed for module {}: {}", module, result.output());
        }
        return result.success();
    }

    /**
     * module isn't a reactor module of the checkout's root pom - most of BootFleet's directories
     * are independent Maven projects with their own pom.xml, not declared under the root's
     * &lt;modules&gt;, so build from inside the module's own directory instead of using -pl from
     * the checkout root.
     */
    private Path moduleDir(Path checkout, String module) {
        return module == null ? checkout : checkout.resolve(module);
    }
}
