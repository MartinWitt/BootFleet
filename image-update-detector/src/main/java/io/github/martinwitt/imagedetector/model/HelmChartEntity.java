package io.github.martinwitt.imagedetector.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "helm_charts", uniqueConstraints = @UniqueConstraint(columnNames = "app_name"))
public class HelmChartEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false, unique = true, name = "app_name")
    private String appName;

    @Column(name = "chart_name")
    private String chartName;

    @Column(nullable = false)
    private String imageRef;

    @Column(nullable = false)
    private String version;

    @Column(name = "last_updated")
    private Instant lastUpdated;

    public HelmChartEntity() {}

    public HelmChartEntity(String appName, String chartName, String imageRef, String version) {
        this.appName = appName;
        this.chartName = chartName;
        this.imageRef = imageRef;
        this.version = version;
        this.lastUpdated = Instant.now();
    }

    // Getters & Setters
    public Long getId() {
        return id;
    }

    public String getAppName() {
        return appName;
    }

    public String getChartName() {
        return chartName;
    }

    public void setChartName(String chartName) {
        this.chartName = chartName;
    }

    public String getImageRef() {
        return imageRef;
    }

    public void setImageRef(String imageRef) {
        this.imageRef = imageRef;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
