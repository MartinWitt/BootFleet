package io.github.martinwitt.mavenversionmcp.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** DTO for maven-metadata.xml versioning element. */
public record MavenVersioning(
        @JsonProperty("latest") String latest,
        @JsonProperty("release") String release,
        @JsonProperty("versions") List<String> versions,
        @JsonProperty("lastUpdated") String lastUpdated) {}
