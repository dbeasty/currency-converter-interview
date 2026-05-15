package com.limidus.currencyconverter.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

class VersionControllerTest {

    @Test
    void version_withoutBuildProperties_returnsUnknowns() {
        VersionController controller = new VersionController(Optional.empty());
        VersionController.VersionResponse response = controller.version();
        assertThat(response.version()).isEqualTo("unknown");
        assertThat(response.gitCommit()).isEqualTo("unknown");
        assertThat(response.buildTime()).isNull();
    }

    @Test
    void version_withBuildProperties_mapsFields() {
        Properties p = new Properties();
        p.setProperty("version", "1.2.3");
        p.setProperty("time", "2024-01-15T12:00:00Z");
        p.setProperty("git.commit", "abc");
        p.setProperty("git.commit.short", "abc123");
        p.setProperty("git.branch", "main");
        BuildProperties build = new BuildProperties(p);
        VersionController controller = new VersionController(Optional.of(build));
        VersionController.VersionResponse response = controller.version();
        assertThat(response.version()).isEqualTo("1.2.3");
        assertThat(response.gitCommit()).isEqualTo("abc");
        assertThat(response.gitCommitShort()).isEqualTo("abc123");
        assertThat(response.gitBranch()).isEqualTo("main");
        assertThat(response.buildTime()).isEqualTo(Instant.parse("2024-01-15T12:00:00Z"));
    }
}
