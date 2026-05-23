package io.github.martinwitt.apigateway.auth;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Per-route API key authentication filter.
 *
 * <p>Spring Cloud Gateway derives the filter name from the class name by stripping the {@code
 * GatewayFilterFactory} suffix, so this filter is registered as {@value #NAME}.
 *
 * <p>Usage in {@code application.yml} (shortcut form):
 *
 * <pre>
 * filters:
 *   - ApiKey=X-API-Key,${MY_SECRET_KEY}
 * </pre>
 *
 * <p>Or named form:
 *
 * <pre>
 * filters:
 *   - name: ApiKey
 *     args:
 *       headerName: X-API-Key
 *       requiredKey: "${MY_SECRET_KEY}"
 * </pre>
 */
@Component
public class ApiKeyGatewayFilterFactory
        extends AbstractGatewayFilterFactory<ApiKeyGatewayFilterFactory.Config> {

    /** The name Spring Cloud Gateway registers this factory under. */
    public static final String NAME = "ApiKey";

    private static final Logger log = LoggerFactory.getLogger(ApiKeyGatewayFilterFactory.class);

    public ApiKeyGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("headerName", "requiredKey");
    }

    @Override
    public GatewayFilter apply(Config config) {
        if (config.getRequiredKey() == null || config.getRequiredKey().isBlank()) {
            log.error(
                    "ApiKey filter applied with a blank/missing requiredKey — all requests on this"
                            + " route will be rejected");
        }
        return (exchange, chain) -> {
            String provided = exchange.getRequest().getHeaders().getFirst(config.getHeaderName());
            if (config.getRequiredKey() == null
                    || config.getRequiredKey().isBlank()
                    || !config.getRequiredKey().equals(provided)) {
                log.warn(
                        "Rejected {} – bad/missing key in header '{}'",
                        exchange.getRequest().getPath(),
                        config.getHeaderName());
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid or missing API key");
            }
            return chain.filter(exchange);
        };
    }

    public static class Config {
        private String headerName = "X-API-Key";
        private String requiredKey;

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        public String getRequiredKey() {
            return requiredKey;
        }

        public void setRequiredKey(String requiredKey) {
            this.requiredKey = requiredKey;
        }
    }
}
