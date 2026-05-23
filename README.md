# BootFleet

Spring Boot monorepo — a collection of independent services and utilities.

## Modules

| Module | Description |
|---|---|
| [`api-gateway`](api-gateway/) | Spring Cloud Gateway with K8s service discovery and per-route JWT/API-key auth |
| [`config-reloader`](config-reloader/) | Watches ConfigMaps and reloads Spring config at runtime |
| [`image-update-detector`](image-update-detector/) | Detects new container image versions |
| [`mail-summary`](mail-summary/) | Email summarisation service |
| [`maven-version-mcp`](maven-version-mcp/) | MCP server for Maven artifact version lookups |
| [`mcp-utils`](mcp-utils/) | Shared utilities for MCP servers |
| [`sequential-thinking-mcp`](sequential-thinking-mcp/) | MCP server for sequential reasoning chains |
| [`spring-boot-utils`](spring-boot-utils/) | Shared Spring Boot helpers |
| [`todo-app`](todo-app/) | Example todo application |
| [`url-cleaner`](url-cleaner/) | Strips tracking parameters from URLs |

## Build

Requires Java 25 and Maven.

```bash
./mvnw verify
```
