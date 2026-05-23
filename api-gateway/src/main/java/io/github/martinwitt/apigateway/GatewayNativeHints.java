package io.github.martinwitt.apigateway;

import io.github.martinwitt.apigateway.auth.ApiKeyGatewayFilterFactory;
import io.github.martinwitt.apigateway.auth.DynamicRouteAuthorizationManager;
import io.github.martinwitt.apigateway.auth.JwtClaimsHeaderFilter;
import io.github.martinwitt.apigateway.discovery.AnnotationBasedRouteDefinitionLocator;
import io.github.martinwitt.apigateway.discovery.RouteRefreshScheduler;
import io.kubernetes.client.openapi.models.V1ListMeta;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import java.util.stream.Stream;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Registers GraalVM native-image hints for classes that are conditional at AOT time (K8s disabled
 * locally) but required at runtime in-cluster.
 */
@Configuration
@ImportRuntimeHints(GatewayNativeHints.Registrar.class)
public class GatewayNativeHints {

    static class Registrar implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            // Our own conditional/global beans
            Stream.of(
                            AnnotationBasedRouteDefinitionLocator.class,
                            RouteRefreshScheduler.class,
                            DynamicRouteAuthorizationManager.class,
                            JwtClaimsHeaderFilter.class)
                    .forEach(
                            type ->
                                    hints.reflection()
                                            .registerType(
                                                    type,
                                                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                                                    MemberCategory.INVOKE_PUBLIC_METHODS));

            // Filter factory configs — property binding needs setter reflection
            hints.reflection()
                    .registerType(
                            ApiKeyGatewayFilterFactory.Config.class,
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS);

            // K8s model classes used by CoreV1Api via Gson reflection.
            Stream.of(
                            V1Service.class,
                            V1ServiceList.class,
                            V1ObjectMeta.class,
                            V1ServiceSpec.class,
                            V1ServicePort.class,
                            V1ListMeta.class)
                    .forEach(
                            type ->
                                    hints.reflection()
                                            .registerType(
                                                    type,
                                                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                                                    MemberCategory.INVOKE_PUBLIC_METHODS,
                                                    MemberCategory.PUBLIC_FIELDS));
        }
    }
}
