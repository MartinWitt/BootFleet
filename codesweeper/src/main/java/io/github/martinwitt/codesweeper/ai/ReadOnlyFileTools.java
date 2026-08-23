package io.github.martinwitt.codesweeper.ai;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Read-only file access for models that must inspect a checkout without being able to mutate it -
 * e.g. the judge, which reviews a fix rather than making one. Scoped to one repo checkout; every
 * path is resolved and checked against escaping it, since the paths come from model output.
 */
public class ReadOnlyFileTools {

    private static final Logger log = LoggerFactory.getLogger(ReadOnlyFileTools.class);
    private static final int MAX_SEARCH_MATCHES = 200;

    protected final Path checkout;

    public ReadOnlyFileTools(Path checkout) {
        this.checkout = checkout.normalize();
    }

    @Tool(
            description =
                    "Lists files under a directory in the repo checkout, relative to the repo root,"
                            + " recursively. Skips .git and target directories.")
    public String listFiles(
            @ToolParam(description = "Directory to list, relative to repo root; \"\" for the root")
                    String relativeDir) {
        log.info("tool call: listFiles(\"{}\")", relativeDir);
        Path dir = resolve(relativeDir.isBlank() ? "." : relativeDir);
        if (!Files.isDirectory(dir)) {
            return "Not a directory: " + relativeDir;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(this::notInIgnoredDir)
                    .map(p -> checkout.relativize(p).toString())
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Tool(
            description =
                    "Searches file contents under a directory in the repo checkout for a regex"
                            + " pattern, recursively. Skips .git and target directories and binary"
                            + " files. Returns at most "
                            + MAX_SEARCH_MATCHES
                            + " matches as \"path:lineNumber: line content\", useful for finding"
                            + " other callers or usages before fixing a finding.")
    public String searchFiles(
            @ToolParam(description = "Java regex pattern to search for") String pattern,
            @ToolParam(
                            description =
                                    "Directory to search under, relative to repo root; \"\" for the"
                                            + " root")
                    String relativeDir) {
        log.info("tool call: searchFiles(\"{}\", \"{}\")", pattern, relativeDir);
        Path dir = resolve(relativeDir.isBlank() ? "." : relativeDir);
        if (!Files.isDirectory(dir)) {
            return "Not a directory: " + relativeDir;
        }
        Pattern regex;
        try {
            regex = Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            return "Invalid pattern: " + e.getMessage();
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            List<String> matches =
                    walk.filter(Files::isRegularFile)
                            .filter(this::notInIgnoredDir)
                            .flatMap(p -> grep(p, regex))
                            .limit(MAX_SEARCH_MATCHES)
                            .toList();
            log.info("tool result: searchFiles found {} match(es)", matches.size());
            return matches.isEmpty() ? "No matches" : String.join("\n", matches);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Tool(
            description =
                    "Reads the full content of a file in the repo checkout, relative to the repo"
                        + " root. Each line is prefixed with its 1-based line number followed by a"
                        + " tab, e.g. \"12\\tSystem.out.println();\", so it can be matched directly"
                        + " against a finding's reported line number.")
    public String readFile(
            @ToolParam(description = "File path relative to the repo root") String relativePath) {
        log.info("tool call: readFile(\"{}\")", relativePath);
        try {
            List<String> lines = Files.readAllLines(resolve(relativePath), StandardCharsets.UTF_8);
            return IntStream.range(0, lines.size())
                    .mapToObj(i -> (i + 1) + "\t" + lines.get(i))
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Stream<String> grep(Path file, Pattern regex) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return Stream.empty(); // binary or unreadable file - skip
        }
        String relativePath = checkout.relativize(file).toString();
        return IntStream.range(0, lines.size())
                .filter(i -> regex.matcher(lines.get(i)).find())
                .mapToObj(i -> relativePath + ":" + (i + 1) + ": " + lines.get(i).strip());
    }

    private boolean notInIgnoredDir(Path p) {
        String path = p.toString();
        return !path.contains(File.separator + ".git" + File.separator)
                && !path.contains(File.separator + "target" + File.separator);
    }

    protected Path resolve(String relativePath) {
        Path target = checkout.resolve(relativePath).normalize();
        if (!target.startsWith(checkout)) {
            throw new IllegalArgumentException("Path escapes checkout: " + relativePath);
        }
        return target;
    }
}
