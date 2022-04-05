package io.kubernetes.client.examples;

import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import io.kubernetes.client.apimachinery.GroupVersion;
import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.examples.models.V1Image;
import io.kubernetes.client.examples.models.V1ImageStatus;
import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.KubernetesApiResponse;

class ImageReconciler implements Reconciler {

    private static Log log = LogFactory.getLog(ImageReconciler.class);

    private SharedIndexInformer<V1Image> parentInformer;

    private ApiClient api;

    private String pluralName = "images";

    private ImageProperties config;

    public ImageReconciler(SharedIndexInformer<V1Image> parentInformer, ApiClient imageApi, ImageProperties config) {
        this.parentInformer = parentInformer;
        this.api = imageApi;
        this.config = config;
    }

    @Override
    public Result reconcile(Request request) {
        Lister<V1Image> parentLister = new Lister<>(parentInformer.getIndexer(), request.getNamespace());
        V1Image parent = parentLister.get(request.getName());

        Result result = new Result(false);
        if (parent != null) {

            if (parent.getMetadata().getDeletionTimestamp() != null) {
                return result;
            }

            result = reconcile(parent);
            if (result.isRequeue()) {
                result.setRequeueAfter(config.computeInterval(parent.getSpec().getInterval()));
            }

            GroupVersion gv = GroupVersion.parse(parent);
            @SuppressWarnings("unchecked")
            Class<V1Image> apiType = (Class<V1Image>) parent.getClass();
            GenericKubernetesApi<V1Image, ?> status = new GenericKubernetesApi<>(apiType, KubernetesListObject.class,
                    gv.getGroup(), gv.getVersion(), pluralName, this.api);

            // TODO: make this conditional on the status having changed
            KubernetesApiResponse<V1Image> update = status.updateStatus(parent, obj -> obj.getStatus());
            if (!update.isSuccess()) {
                log.warn("Cannot update parent");
            }

        }

        return result;
    }

    private Duration getDuration(V1Image parent) {
        return config.computeInterval(parent.getSpec().getInterval());
    }

    private Result reconcile(V1Image parent) {
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
        return new Result(next!=null);
    }

    private String fetchImage(V1Image node) {
        RestTemplate rest = new RestTemplate();
        if (node==null || node.getSpec()==null) {
            return null;
        }
        try {
            String image = node.getSpec().getImage();
            String url = this.config.computeManifestUrl(image);
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

class Manifest extends HashMap<String, Object> {
}
