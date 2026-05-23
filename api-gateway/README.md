# api-gateway

Spring Cloud Gateway module providing Kubernetes-native service discovery, per-route authentication (JWT + API key), and a dashboard UI.

## Features

- **K8s service discovery** — exposes any service labelled `gateway.bootfleet.io/expose=true` as a route automatically
- **Per-route auth** — JWT (via Keycloak/OIDC) or API key, configured via service annotations
- **JWT claim forwarding** — upstream services receive `X-Auth-Sub`, `X-Auth-Email`, `X-Auth-Username` headers
- **Dashboard** — `/gateway/services` endpoint + static UI at `/`

## Service annotations

| Annotation | Default | Description |
|---|---|---|
| `gateway.bootfleet.io/expose` | — | Set to `true` to expose the service |
| `gateway.bootfleet.io/path` | `/<service-name>/**` | Path predicate |
| `gateway.bootfleet.io/strip-prefix` | path depth | Number of prefix segments to strip |
| `gateway.bootfleet.io/auth` | `none` | `none`, `jwt`, `api-key`, or `jwt+api-key` |
| `gateway.bootfleet.io/api-key-env` | `<NAME>_API_KEY` | Env var holding the required API key |
| `gateway.bootfleet.io/api-key-header` | `X-API-Key` | Header name for the API key |
| `gateway.bootfleet.io/jwt-required-scope` | — | Scope stored in route metadata (future use) |

## Configuration

Key `application.yml` properties (all overridable via env vars):

| Property | Env override | Default |
|---|---|---|
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | `KEYCLOAK_ISSUER_URI` | `http://keycloak:8080/realms/master` |
| `spring.cloud.kubernetes.client.namespace` | `SPRING_CLOUD_KUBERNETES_CLIENT_NAMESPACE` | `gateway` |
| `spring.cloud.kubernetes.discovery.all-namespaces` | `SPRING_CLOUD_KUBERNETES_DISCOVERY_ALL_NAMESPACES` | `true` |
| `gateway.route-refresh-interval-ms` | — | `30000` |
| `gateway.auth.jwt.protected-paths` | — | `[]` |




### Expose a service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: my-service
  labels:
    gateway.bootfleet.io/expose: "true"
  annotations:
    gateway.bootfleet.io/path: "/api/my-service/**"
    gateway.bootfleet.io/auth: "jwt"
```

## Running locally

```bash
# Without K8s (annotation-based discovery disabled automatically via ConditionalOnBean)
./mvnw spring-boot:run -pl api-gateway
```
