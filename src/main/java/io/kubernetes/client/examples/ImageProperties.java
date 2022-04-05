package io.kubernetes.client.examples;

import java.time.Duration;

import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@ConfigurationProperties("image")
public class ImageProperties implements EnvironmentAware {

    private Environment environment;

    private Duration interval = Duration.ofSeconds(30);

    public Duration getInterval() {
        return interval;
    }

    public void setInterval(Duration interval) {
        this.interval = interval;
    }

    public String computeManifestUrl(String image) {
        String label = "latest"; // TODO: extract from image path
        String protocol = "https://";
        if (!image.contains("/")) {
            image = "library/" + image;
        }
        if (!image.contains(".") && !image.contains(":")) {
            // No host
            image = "index.docker.io/" + image;
        }
        String path = image.replaceFirst("/", "/v2/");
        if (path.startsWith("localhost")) {
            protocol = "http://";
            if (environment != null && CloudPlatform.getActive(environment) == CloudPlatform.KUBERNETES) {
                path = path.replaceFirst("localhost", "registry");
            }
        }
        String url = protocol + path + "/manifests/" + label;
        return url;
    }

    public Duration computeInterval(String interval) {
        if (!StringUtils.hasText(interval)) {
            return this.interval;
        }
        return Duration.parse("PT" + interval.toUpperCase());
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

}
