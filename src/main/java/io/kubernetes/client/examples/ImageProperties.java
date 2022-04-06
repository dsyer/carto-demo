/*
 * Copyright 2022-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
