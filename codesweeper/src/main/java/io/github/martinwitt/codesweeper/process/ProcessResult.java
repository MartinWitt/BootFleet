package io.github.martinwitt.codesweeper.process;

public record ProcessResult(int exitCode, String output) {
    public boolean success() {
        return exitCode == 0;
    }
}
