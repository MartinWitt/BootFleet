package io.github.martinwitt.mavenversionmcp.config;

import io.github.martinwitt.mavenversionmcp.client.dto.MavenDependencyParts;
import io.github.martinwitt.mavenversionmcp.client.dto.MavenMetadata;
import io.github.martinwitt.mavenversionmcp.client.dto.MavenVersioning;
import io.github.martinwitt.mavenversionmcp.mcp.dto.ArtifactExistsResult;
import io.github.martinwitt.mavenversionmcp.mcp.dto.VersionComparisonResult;
import io.github.martinwitt.mavenversionmcp.mcp.dto.VersionInfoResult;
import io.github.martinwitt.mavenversionmcp.mcp.dto.VersionResult;
import io.github.martinwitt.mavenversionmcp.mcp.dto.VersionsListResult;
import org.springframework.ai.mcp.annotation.context.DefaultMetaProvider;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.context.annotation.Configuration;

// Workaround for https://github.com/spring-projects/spring-ai/pull/6293
@Configuration
@RegisterReflectionForBinding({
    ArtifactExistsResult.class,
    DefaultMetaProvider.class,
    MavenDependencyParts.class,
    MavenMetadata.class,
    MavenVersioning.class,
    VersionComparisonResult.class,
    VersionInfoResult.class,
    VersionResult.class,
    VersionsListResult.class
})
public class NativeImageConfig {}
