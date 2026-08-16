package io.github.martinwitt.codesweeper.sarif;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class SarifParserTest {

    private final SarifParser parser = new SarifParser();

    @Test
    void parsesFindingsOutOfSarifResults() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/sample.sarif.json")) {
            List<SarifFinding> findings = parser.parse(in);

            assertThat(findings).hasSize(1);
            SarifFinding finding = findings.get(0);
            assertThat(finding.ruleId()).isEqualTo("IgnoreResultOfCall");
            assertThat(finding.message()).isEqualTo("Result of 'Matcher.matches()' is ignored");
            assertThat(finding.filePath())
                    .isEqualTo(
                            "maven-version-mcp/src/main/java/io/github/martinwitt/mavenversionmcp/client/dto/MavenVersioning.java");
            assertThat(finding.startLine()).isEqualTo(42);
        }
    }

    @Test
    void returnsEmptyListWhenNoResults() throws Exception {
        String emptySarif = "{\"runs\":[{\"results\":[]}]}";
        List<SarifFinding> findings =
                parser.parse(new java.io.ByteArrayInputStream(emptySarif.getBytes()));

        assertThat(findings).isEmpty();
    }
}
