package io.kubernetes.client.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

class ImagePropertiesTests {

    private ImageProperties config = new ImageProperties();

    private ConfigurableEnvironment environment = new StandardEnvironment();

    @BeforeEach
    private void setUp() {
        config.setEnvironment(environment);
    }

    @Test
    void testExternal() {
        assertThat(config.computeManifestUrl("gcr.io/cf-sandbox-dsyer/demo")).isEqualTo("https://gcr.io/v2/cf-sandbox-dsyer/demo/manifests/latest");
    }    

    @Test
    void testDockerhub() {
        assertThat(config.computeManifestUrl("index.docker.io/dsyer/demo")).isEqualTo("https://index.docker.io/v2/dsyer/demo/manifests/latest");
    }

    @Test
    void testDockerhubDefault() {
        assertThat(config.computeManifestUrl("dsyer/demo")).isEqualTo("https://index.docker.io/v2/dsyer/demo/manifests/latest");
    }

    @Test
    void testLibrary() {
        assertThat(config.computeManifestUrl("ubuntu")).isEqualTo("https://index.docker.io/v2/library/ubuntu/manifests/latest");
    }

    @Test
    void testLocalhost() {
        assertThat(config.computeManifestUrl("localhost:5000/apps/demo")).isEqualTo("http://localhost:5000/v2/apps/demo/manifests/latest");
    }

    @Test
    void testLocalhostInCluster() {
        TestPropertyValues.of("spring.main.cloud-platform=kubernetes").applyTo(environment);
        assertThat(config.computeManifestUrl("localhost:5000/apps/demo")).isEqualTo("http://registry:5000/v2/apps/demo/manifests/latest");
    }

    @Test
    void testDefaultDuration() {
        assertThat(config.computeInterval(null)).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void testOverrideDefaultDuration() {
        config.setInterval(Duration.ofSeconds(20));
        assertThat(config.computeInterval(null)).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void testSpecificDurationSeconds() {
        assertThat(config.computeInterval("10s")).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void testSpecificDurationMinutesAndSeconds() {
        assertThat(config.computeInterval("1m30s")).isEqualTo(Duration.ofSeconds(90));
    }
}