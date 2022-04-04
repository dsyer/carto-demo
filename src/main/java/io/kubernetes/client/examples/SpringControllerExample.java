/*
Copyright 2020 The Kubernetes Authors.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package io.kubernetes.client.examples;

import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.Executors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.nativex.hint.TypeAccess;
import org.springframework.nativex.hint.TypeHint;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import io.kubernetes.client.examples.models.V1Image;
import io.kubernetes.client.examples.models.V1ImageList;
import io.kubernetes.client.examples.models.V1ImageStatus;
import io.kubernetes.client.examples.reconciler.ParentReconciler;
import io.kubernetes.client.examples.reconciler.SubReconciler;
import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.generic.GenericKubernetesApi;

@TypeHint(types = { Manifest.class }, access = { TypeAccess.DECLARED_FIELDS,
		TypeAccess.DECLARED_METHODS, TypeAccess.DECLARED_CONSTRUCTORS,
		TypeAccess.DECLARED_CLASSES })
@SpringBootApplication
public class SpringControllerExample {

	public static void main(String[] args) {
		SpringApplication.run(SpringControllerExample.class, args);
	}

	@Configuration
	public static class AppConfig {

		private static Log log = LogFactory.getLog(AppConfig.class);

		@Bean
		public CommandLineRunner commandLineRunner(SharedInformerFactory sharedInformerFactory, Controller controller) {
			return args -> Executors.newSingleThreadExecutor().execute(() -> {
				System.out.println("starting informers...");
				sharedInformerFactory.startAllRegisteredInformers();

				System.out.println("running controller..");
				controller.run();
			});
		}

		@Bean
		public Controller nodePrintingController(SharedInformerFactory sharedInformerFactory,
				ParentReconciler<?, ?> reconciler) {
			var builder = ControllerBuilder //
					.defaultBuilder(sharedInformerFactory)//
					.watch((q) -> ControllerBuilder.controllerWatchBuilder(V1Image.class, q)
							.withResyncPeriod(Duration.ofHours(1)).build()) //
					.withWorkerCount(2);
			return builder.withReconciler(reconciler).withName("ImageController").build();
		}

		@Bean
		public GenericKubernetesApi<V1Image, V1ImageList> imageApi(ApiClient apiClient) {
			return new GenericKubernetesApi<>(V1Image.class, V1ImageList.class, "example.com", "v1",
					"images", apiClient);
		}

		@Bean
		public SharedIndexInformer<V1Image> nodeInformer(ApiClient apiClient,
				SharedInformerFactory sharedInformerFactory,
				GenericKubernetesApi<V1Image, V1ImageList> imageApi) {
			return sharedInformerFactory.sharedIndexInformerFor(imageApi, V1Image.class, 0);
		}

		@Bean
		public ParentReconciler<V1Image, V1ImageList> configClientReconciler(
				SharedIndexInformer<V1Image> parentInformer, ApiClient imageApi) {
			if (log.isDebugEnabled()) {
				imageApi.setDebugging(true);
			}
			return new ParentReconciler<>(parentInformer, imageApi, new ImageReconciler());
		}

	}

	private static class ImageReconciler implements SubReconciler<V1Image> {

		private static Log log = LogFactory.getLog(ImageReconciler.class);

		@Override
		public Result reconcile(V1Image parent) {
			String old = parent == null || parent.getStatus() == null ? null : parent.getStatus().getLatestImage();
			String next = fetchImage(parent);
			if (next != null && parent != null) {
				if (parent.getStatus()==null) {
					parent.setStatus(new V1ImageStatus());
				}
				parent.getStatus().setLatestImage(next);
				parent.getStatus().setComplete(true); // TODO: parent should do this?
			} else {
				next = old;
			}
			return new Result(old != null && !old.equals(next));
		}

		private String fetchImage(V1Image node) {
			RestTemplate rest = new RestTemplate();
			if (node==null || node.getSpec()==null) {
				return null;
			}
			try {
				String image = node.getSpec().getImage();
				if (!image.contains("/")) {
					log.error("image path has no /");
					return null;
				}
				String label = "latest"; // TODO: extract from image path
				String url = "http://" + image.replaceFirst("/", "/v2/") + "/manifests/" + label;
				log.info("Checking digest for: " + url);
				ResponseEntity<Manifest> response = rest.exchange(RequestEntity.get(url)
						.accept(MediaType.valueOf("application/vnd.docker.distribution.manifest.v2+json")).build(),
						Manifest.class);
				String value = response.getHeaders().getFirst("Docker-Content-Digest");
				if (value == null || !value.startsWith("sha256:")) {
					log.error("image has no digest: " + response);
					return null;
				}
				return image + "@" + value;
			} //
			catch (RestClientException e) {
				log.error("oops!", e);
				return null;
			}
		}

	}

}

class Manifest extends HashMap<String, Object> {

}
