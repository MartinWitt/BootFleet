package io.github.martinwitt.mavenversionmcp.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * DTO for maven-metadata.xml root element.
 *
 * <p>Represents the structure of the maven-metadata.xml file from Maven repositories.
 */
@JacksonXmlRootElement(localName = "metadata")
public record MavenMetadata(
        @JsonProperty("groupId") String groupId,
        @JsonProperty("artifactId") String artifactId,
        @JsonProperty("versioning") MavenVersioning versioning) {}
