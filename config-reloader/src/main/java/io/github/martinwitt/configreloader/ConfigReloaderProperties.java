package io.github.martinwitt.configreloader;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "configreloader")
public class ConfigReloaderProperties {

    private String enabledAnnotation;
    private boolean dryRun = false;
    private String watchMode = "annotation"; // "annotation" or "all"

    public String getEnabledAnnotation() {
        return enabledAnnotation;
    }

    public void setEnabledAnnotation(String enabledAnnotation) {
        this.enabledAnnotation = enabledAnnotation;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public String getWatchMode() {
        return watchMode;
    }

    public void setWatchMode(String watchMode) {
        this.watchMode = watchMode;
    }
}
