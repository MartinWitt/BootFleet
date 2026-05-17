package io.github.martinwitt.mavenversionmcp.config;

import io.github.martinwitt.mavenversionmcp.client.dto.MavenDependencyParts;
import io.github.martinwitt.mavenversionmcp.client.dto.MavenMetadata;
import io.github.martinwitt.mavenversionmcp.client.dto.MavenVersioning;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.context.annotation.Configuration;

@Configuration
@RegisterReflectionForBinding({
    MavenMetadata.class,
    MavenVersioning.class,
    MavenDependencyParts.class
})
public class NativeImageConfig {}
