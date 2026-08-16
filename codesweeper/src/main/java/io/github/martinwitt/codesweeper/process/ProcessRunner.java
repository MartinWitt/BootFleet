package io.github.martinwitt.codesweeper.process;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Runs the external CLIs (git, gh, mvn) codesweeper leans on instead of reimplementing their logic
 * in Java.
 */
@Component
public class ProcessRunner {

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");

    public ProcessResult run(Path workDir, String... command) {
        List<String> fullCommand = new ArrayList<>();
        if (WINDOWS) {
            // ponytail: mvn ships as mvn.cmd on Windows, which ProcessBuilder won't resolve without
            // a shell
            fullCommand.add("cmd");
            fullCommand.add("/c");
        }
        fullCommand.addAll(List.of(command));

        ProcessBuilder builder =
                new ProcessBuilder(fullCommand)
                        .directory(workDir.toFile())
                        .redirectErrorStream(true);
        try {
            Process process = builder.start();
            String output =
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            return new ProcessResult(exitCode, output);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted running: " + String.join(" ", command), e);
        }
    }
}
