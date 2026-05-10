package io.github.martinwitt.imagedetector.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "helm_chart_dependencies",
        uniqueConstraints = @UniqueConstraint(columnNames = {"app_name", "dependency_name"}))
public class HelmChartDependencyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false, name = "app_name")
    private String appName;

    @Column(nullable = false, name = "dependency_name")
    private String dependencyName;

    @Column(nullable = false)
    private String version;

    @Column(nullable = false)
    private String repository;

    @Column(name = "last_updated")
    private Instant lastUpdated;

    @Column(name = "latest_version")
    private String latestVersion;

    public HelmChartDependencyEntity() {}

    public HelmChartDependencyEntity(
            String appName, String dependencyName, String version, String repository) {
        this.appName = appName;
        this.dependencyName = dependencyName;
        this.version = version;
        this.repository = repository;
        this.lastUpdated = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getAppName() {
        return appName;
    }

    public String getDependencyName() {
        return dependencyName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public void setLatestVersion(String latestVersion) {
        this.latestVersion = latestVersion;
    }

    public boolean hasUpdate() {
        return latestVersion != null && !latestVersion.equals(version);
    }
}
