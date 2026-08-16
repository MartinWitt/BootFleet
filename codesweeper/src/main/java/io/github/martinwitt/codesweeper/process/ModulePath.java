package io.github.martinwitt.codesweeper.process;

/** BootFleet is a multi-module Maven repo where every module lives in a root-level directory. */
public final class ModulePath {

    private ModulePath() {}

    public static String moduleFor(String repoRelativeFilePath) {
        int slash = repoRelativeFilePath.indexOf('/');
        return slash == -1 ? repoRelativeFilePath : repoRelativeFilePath.substring(0, slash);
    }
}
