package io.github.martinwitt.codesweeper.process;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves which Maven module a repo-relative file path belongs to, if any - works for any target
 * repo, whether it's a multi-module reactor or a single plain pom.xml.
 */
public final class ModulePath {

    private ModulePath() {}

    /**
     * @return the root-level directory name, if it has its own pom.xml (multi-module repo); empty
     *     if the file isn't under such a directory, meaning the whole repo should be built instead.
     */
    public static Optional<String> moduleFor(Path checkout, String repoRelativeFilePath) {
        int slash = repoRelativeFilePath.indexOf('/');
        if (slash == -1) {
            return Optional.empty();
        }
        String candidate = repoRelativeFilePath.substring(0, slash);
        return Files.isRegularFile(checkout.resolve(candidate).resolve("pom.xml"))
                ? Optional.of(candidate)
                : Optional.empty();
    }
}
