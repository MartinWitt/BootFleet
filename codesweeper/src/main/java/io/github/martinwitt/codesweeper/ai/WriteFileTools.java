package io.github.martinwitt.codesweeper.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * File write access the fixer model can call directly instead of having file content spliced into
 * its prompt. Only bind this to models that are actually meant to change the checkout - the judge,
 * which reviews rather than fixes, gets {@link ReadOnlyFileTools} instead. Deliberately not a
 * subclass of {@link ReadOnlyFileTools}: Spring AI's tool discovery only sees @Tool methods
 * declared directly on the object passed to tools(), not inherited ones, so an "extends" here would
 * silently hide the write tool from callers that only register the read-only base.
 */
public class WriteFileTools {

    private static final Logger log = LoggerFactory.getLogger(WriteFileTools.class);

    private final Path checkout;

    public WriteFileTools(Path checkout) {
        this.checkout = checkout.normalize();
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
                "tool call: editFile(\"{}\", {} chars -> {} chars, old: {}, new: {})",
                relativePath,
                oldText.length(),
                newText.length(),
                oldText,
                newText);
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

    private Path resolve(String relativePath) {
        Path target = checkout.resolve(relativePath).normalize();
        if (!target.startsWith(checkout)) {
            throw new IllegalArgumentException("Path escapes checkout: " + relativePath);
        }
        return target;
    }
}
