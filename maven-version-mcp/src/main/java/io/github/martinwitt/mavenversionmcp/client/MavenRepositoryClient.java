package io.github.martinwitt.mavenversionmcp.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client for fetching Maven metadata and POM files.
 *
 * <p>Provides direct access to Maven repository metadata and artifact files using RestTemplate.
 */
@Component
public class MavenRepositoryClient {

    private final RestTemplate restTemplate;

    public MavenRepositoryClient() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Fetch maven-metadata.xml as a string.
     *
     * @param url The full URL to the maven-metadata.xml file
     * @return XML content as string
     */
    public String fetchMetadataXml(String url) {
        return restTemplate.getForObject(url, String.class);
    }

    /**
     * Fetch a POM file as a string.
     *
     * @param url The full URL to the POM file
     * @return POM XML content as string
     */
    public String fetchPomXml(String url) {
        return restTemplate.getForObject(url, String.class);
    }

    /**
     * Fetch any file from the repository.
     *
     * @param url The full URL to the file
     * @return File content as byte array
     */
    public byte[] fetchFile(String url) {
        return restTemplate.getForObject(url, byte[].class);
    }
}
