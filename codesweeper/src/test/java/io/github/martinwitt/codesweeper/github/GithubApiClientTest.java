package io.github.martinwitt.codesweeper.github;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.martinwitt.codesweeper.config.CodesweeperProperties;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GithubApiClientTest {

    @TempDir Path destDir;

    private final GithubApiClient client =
            new GithubApiClient(
                    new CodesweeperProperties(null, null, "test-token", null, null, List.of()));

    /**
     * Real Qodana artifacts wrap their output in a nested zip (inside the GitHub artifact zip) and
     * include both the full SARIF and a "-short" summary with zero results; extraction must descend
     * into the nested zip and pick the real one, not whichever sorts first.
     */
    @Test
    void picksTheFullSarifOverAnEmptyShortSummaryInsideANestedZip() throws IOException {
        byte[] shortSarif = "{\"runs\":[{\"results\":[]}]}".getBytes();
        byte[] fullSarif =
                ("{\"runs\":[{\"results\":[{\"ruleId\":\"X\"}]}]}" + "x".repeat(1000)).getBytes();
        byte[] innerZip =
                zip("qodana-short.sarif.json", shortSarif, "qodana.sarif.json", fullSarif);
        byte[] outerZip = zip("qodana-report.zip", innerZip);

        Path extracted = client.extractSarifFile(new ByteArrayInputStream(outerZip), destDir);

        assertThat(extracted.getFileName().toString()).isEqualTo("qodana.sarif.json");
        assertThat(Files.readAllBytes(extracted)).isEqualTo(fullSarif);
    }

    @Test
    void returnsNullWhenNoSarifEntryExists() throws IOException {
        byte[] outerZip = zip("readme.txt", "hello".getBytes());

        assertThat(client.extractSarifFile(new ByteArrayInputStream(outerZip), destDir)).isNull();
    }

    private static byte[] zip(Object... nameContentPairs) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i < nameContentPairs.length; i += 2) {
                zos.putNextEntry(new ZipEntry((String) nameContentPairs[i]));
                zos.write((byte[]) nameContentPairs[i + 1]);
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
