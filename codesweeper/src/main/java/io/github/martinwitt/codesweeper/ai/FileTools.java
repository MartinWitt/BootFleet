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
 * File access the fixer model can call directly instead of having file content spliced into its
 * prompt. Scoped to one repo checkout; every path is resolved and checked against escaping it,
 * since the paths come from model output.
 */
public class FileTools {

    private static final Logger log = LoggerFactory.getLogger(FileTools.class);
    private static final int MAX_SEARCH_MATCHES = 200;

    private final Path checkout;

    public FileTools(Path checkout) {
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

    @Tool(
            description =
                    "Makes a targeted fix to a file by replacing one exact snippet of text with new"
                        + " text - use this instead of rewriting the whole file. oldText must match"
                        + " the file's current content exactly (whitespace included, no line-number"
                        + " prefixes) and must occur exactly once in the file; include a line or"
                        + " two of surrounding context if the snippet alone isn't unique. Keep"
                        + " oldText and newText as small as possible - just the lines that actually"
                        + " change.")
    public String editFile(
            @ToolParam(description = "File path relative to the repo root") String relativePath,
            @ToolParam(
                            description =
                                    "The exact existing text to replace, without line-number"
                                            + " prefixes")
                    String oldText,
            @ToolParam(description = "The replacement text, without line-number prefixes")
                    String newText) {
        log.info(
                "tool call: editFile(\"{}\", {} chars -> {} chars)",
                relativePath,
                oldText.length(),
                newText.length());
        Path target = resolve(relativePath);
        String content;
        try {
            content = Files.readString(target, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.atError().setCause(e).log("Failed to read {} for editFile", relativePath);
            throw new UncheckedIOException(e);
        }
        int firstIndex = content.indexOf(oldText);
        if (firstIndex == -1) {
            return "oldText not found - it must match the file's current content exactly"
                    + " (whitespace included), with no line-number prefixes. Re-read the file if"
                    + " unsure.";
        }
        if (content.indexOf(oldText, firstIndex + 1) != -1) {
            return "oldText matches more than once - include more surrounding context to make it"
                    + " unique.";
        }
        String updated =
                content.substring(0, firstIndex)
                        + newText
                        + content.substring(firstIndex + oldText.length());
        try {
            Files.writeString(target, updated, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.atError().setCause(e).log("Failed to write {} for editFile", relativePath);
            throw new UncheckedIOException(e);
        }
        return "Edited " + relativePath;
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

    private Path resolve(String relativePath) {
        Path target = checkout.resolve(relativePath).normalize();
        if (!target.startsWith(checkout)) {
            throw new IllegalArgumentException("Path escapes checkout: " + relativePath);
        }
        return target;
    }
}
