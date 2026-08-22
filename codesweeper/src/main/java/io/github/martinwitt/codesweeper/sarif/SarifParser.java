package io.github.martinwitt.codesweeper.sarif;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Pulls the handful of fields codesweeper needs out of a SARIF 2.1.0 log, one entry per result. */
@Component
public class SarifParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<SarifFinding> parse(InputStream sarifJson) throws IOException {
        JsonNode root = objectMapper.readTree(sarifJson);
        List<SarifFinding> findings = new ArrayList<>();
        for (JsonNode run : root.path("runs")) {
            for (JsonNode result : run.path("results")) {
                SarifFinding finding = toFinding(result);
                if (finding != null) {
                    findings.add(finding);
                }
            }
        }
        return findings;
    }

    private SarifFinding toFinding(JsonNode result) {
        JsonNode location = result.path("locations").path(0).path("physicalLocation");
        String filePath = location.path("artifactLocation").path("uri").asText(null);
        if (filePath == null) {
            return null;
        }
        String ruleId = result.path("ruleId").asText("");
        String message = result.path("message").path("text").asText("");
        int startLine = location.path("region").path("startLine").asInt(0);
        return new SarifFinding(ruleId, message, filePath, startLine);
    }
}
