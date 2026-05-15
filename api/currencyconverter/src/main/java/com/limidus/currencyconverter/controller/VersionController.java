package com.limidus.currencyconverter.controller;

import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VersionController {

    private final Optional<BuildProperties> buildProperties;

    public VersionController(Optional<BuildProperties> buildProperties) {
        this.buildProperties = buildProperties;
    }

    @GetMapping("/version")
    public VersionResponse version() {
        String version = buildProperties.map(BuildProperties::getVersion).orElse("unknown");
        Instant buildTime = buildProperties.map(BuildProperties::getTime).orElse(null);
        // git fields are baked into build-info.properties as additional properties at Gradle build time
        String gitCommit = buildProperties.map(b -> b.get("git.commit")).orElse("unknown");
        String gitCommitShort = buildProperties.map(b -> b.get("git.commit.short")).orElse("unknown");
        String gitBranch = buildProperties.map(b -> b.get("git.branch")).orElse("unknown");
        return new VersionResponse(version, gitCommit, gitCommitShort, gitBranch, buildTime);
    }

    public record VersionResponse(
            String version,
            String gitCommit,
            String gitCommitShort,
            String gitBranch,
            Instant buildTime) {}
}
