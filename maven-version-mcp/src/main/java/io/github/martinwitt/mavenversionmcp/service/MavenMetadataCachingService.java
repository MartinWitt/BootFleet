package io.github.martinwitt.mavenversionmcp.service;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.github.martinwitt.mavenversionmcp.client.MavenRepositoryClient;
import io.github.martinwitt.mavenversionmcp.client.dto.MavenMetadata;
import io.github.martinwitt.mavenversionmcp.utils.MavenDependencyUtil;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * Service for fetching and caching Maven artifact metadata from remote repositories.
 *
 * <p>This service handles the remote HTTP calls and caching in a single, dedicated place. All
 * caching logic is centralized here to avoid Spring proxy/self-invocation issues.
 */
@Service
public class MavenMetadataCachingService {

    private static final Logger logger = LoggerFactory.getLogger(MavenMetadataCachingService.class);

    private final MavenRepositoryClient repositoryClient;
    private final XmlMapper xmlMapper;

    public MavenMetadataCachingService(MavenRepositoryClient repositoryClient) {
        this.repositoryClient = repositoryClient;
        this.xmlMapper = new XmlMapper();
    }

    /**
     * Fetch and cache all available versions for a Maven artifact from a custom repository.
     *
     * <p>This is the single point of caching for version fetching. The remote call is made here and
     * the result is cached by Spring.
     *
     * @param registryUrl Maven repository URL (e.g., "https://repo1.maven.org/maven2")
     * @param groupId Maven groupId
     * @param artifactId Maven artifactId
     * @return List of version strings, or empty list if metadata cannot be fetched
     */
    @Cacheable(
            value = "mavenVersions",
            key = "#registryUrl + ':' + #groupId + ':' + #artifactId",
            unless = "#result == null || #result.isEmpty()")
    public List<String> fetchAndCacheVersions(
            String registryUrl, String groupId, String artifactId) {
        try {
            String metadataUrl =
                    MavenDependencyUtil.getMavenMetadataUrl(registryUrl, groupId, artifactId);
            logger.info("Fetching Maven metadata from: {}", metadataUrl);

            String xmlContent = repositoryClient.fetchMetadataXml(metadataUrl);
            MavenMetadata metadata = xmlMapper.readValue(xmlContent, MavenMetadata.class);

            if (metadata.versioning() == null || metadata.versioning().versions() == null) {
                logger.warn("No versions found in metadata for {}:{}", groupId, artifactId);
                return new ArrayList<>();
            }

            List<String> versions = new ArrayList<>(metadata.versioning().versions());
            logger.info("Found {} versions for {}:{}", versions.size(), groupId, artifactId);
            return versions;

        } catch (RestClientException e) {
            logger.error(
                    "Failed to fetch metadata for {}:{} - HTTP error: {}",
                    groupId,
                    artifactId,
                    e.getMessage());
            return new ArrayList<>();
        } catch (Exception e) {
            logger.error(
                    "Failed to parse metadata for {}:{} - Error: {}",
                    groupId,
                    artifactId,
                    e.getMessage(),
                    e);
            return new ArrayList<>();
        }
    }
}
