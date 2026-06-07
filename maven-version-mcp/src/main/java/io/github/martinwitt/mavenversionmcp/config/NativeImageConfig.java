package io.github.martinwitt.mavenversionmcp.config;

import io.github.martinwitt.mavenversionmcp.client.dto.MavenDependencyParts;
import io.github.martinwitt.mavenversionmcp.client.dto.MavenMetadata;
import io.github.martinwitt.mavenversionmcp.client.dto.MavenVersioning;
import org.springframework.ai.mcp.annotation.context.DefaultMetaProvider;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.context.annotation.Configuration;

// Workaround for https://github.com/spring-projects/spring-ai/pull/6293
@Configuration
@RegisterReflectionForBinding({
    DefaultMetaProvider.class,
    MavenDependencyParts.class,
    MavenMetadata.class,
    MavenVersioning.class
})
public class NativeImageConfig {}
