/*
* Copyright 2019-2019 the original author or authors.
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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import io.kubernetes.client.examples.models.V1Image;
import io.kubernetes.client.examples.models.V1ImageList;
import io.kubernetes.client.examples.models.V1ImageSpec;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.KubernetesApiResponse;

/**
 * @author Dave Syer
 */
@SpringBootTest
public class ClusterIT {

	@Value("${NAMESPACE:default}")
	private final String namespace = "default";

	@Autowired
	private GenericKubernetesApi<V1Image, V1ImageList> configs;

	private String name;

	@AfterEach
	public void after() {
		if (name != null) {
			configs.delete(namespace, name);
		}
	}

	@Test
	void createImageAndCheckStatus() throws Exception {
		var client = new V1Image();
		client.setKind("Image");
		client.setApiVersion("example.com/v1");

		var metadata = new V1ObjectMeta();
		metadata.setGenerateName("image-");
		metadata.setNamespace(namespace);
		client.setMetadata(metadata);

		var spec = new V1ImageSpec();
		spec.setImage("localhost:5000/apps/demo");
		client.setSpec(spec);

		var response = configs.create(client);
		Assertions.assertTrue(response.isSuccess());
		V1Image result = response.getObject();
		assertThat(result).isNotNull();
		name = result.getMetadata().getName();
		if (name != null) {
			Awaitility.await().atMost(Duration.ofMinutes(1)).until(() -> {
				KubernetesApiResponse<V1Image> config = configs.get(namespace, name);
				return config != null && //
						config.getObject() != null && //
						config.getObject().getStatus() != null && //
						config.getObject().getStatus().getComplete() != null && //
						config.getObject().getStatus().getComplete().equals(Boolean.TRUE);
			});
		}
	}

}
