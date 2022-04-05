package io.kubernetes.client.examples;

import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

@ConfigurationProperties("image")
public class ImageProperties implements EnvironmentAware {

    private Environment environment;

    public String computeManifestUrl(String image) {
        String label = "latest"; // TODO: extract from image path
        String protocol = "https://";
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

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

}
