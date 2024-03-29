# Simple Supply Chain with Cartographer

The aim is to create a supply chain that makes k8s deployments from pre-built images. The simplest way that could possibly work would be with a hard-coded image in the workload. To make it slightly more interesting we want to monitor the image repository and update the deployment if the image changes. There is an closed source Tanzu [source-controller](https://github.com/vmware-tanzu/source-controller) that meets that need but it doesn't work with an insecure local registry. Instead we use a custom controller written as a Spring Boot application with source code in this repository.

## Setting up a Cluster

The example here works on [Kind](https://github.com/kubernetes-sigs/kind) with a local registry on `localhost:5000`. The Kind docs show you how to do that, or you can use the `kind-setup.sh` script in this project. To make sure it is working:

```
$ kubctl get all
NAME                   TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)   AGE
service/kubernetes     ClusterIP   10.96.0.1       <none>        443/TCP   21h
```

and

```
$ curl localhost:5000/v2/
{}
```

We also put an application in the repo with docker push. It doesn't matter what it does because it's only there so we can see something wiggle. E.g.

```
$ docker pull nginx:stable-alpine
$ docker tag nginx:stable-alpine localhost:5000/apps/demo
$ docker push localhost:5000/apps/demo
```

## Installing Cartographer

Follow the instructions to [install Cartographer](https://github.com/vmware-tanzu/cartographer/blob/main/README.md#installation):

```
$ kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.7.2/cert-manager.yaml
$ kubectl create namespace cartographer-system
$ kubectl apply -f https://github.com/vmware-tanzu/cartographer/releases/latest/download/cartographer.yaml
```

Keep running the last one until it is successful.

## Installing the Image Controller

You can install the CRDs from `src/main/k8s/crds`:

```
$ kubectl apply -f src/main/k8s/crds
```

It will be successful if you can list the images:

```
$ kubectl get images
No resources found in default namespace.
```

Once you have the CRDs in place you can run the controller locally (it also works in cluster):

```
$ ./mvnw spring-boot:run
...

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v2.6.6)

2022-04-05 08:44:31.381  INFO 1846520 --- [           main] i.k.c.examples.SpringControllerExample   : Starting SpringControllerExample using Java 11.0.11 on carbon with PID 1846520 (/home/dsyer/dev/scratch/carto/target/classes started by dsyer in /home/dsyer/dev/scratch/carto)
...
starting informers...
running controller..
2022-04-05 08:44:34.940  INFO 1846520 --- [troller-V1Image] i.k.client.informer.cache.Controller     : informer#Controller: ready to run resync & reflector runnable
2022-04-05 08:44:34.941  INFO 1846520 --- [troller-V1Image] i.k.client.informer.cache.Controller     : informer#Controller: resync skipped due to 0 full resync period
2022-04-05 08:44:34.944  INFO 1846520 --- [odels.V1Image-1] i.k.c.informer.cache.ReflectorRunnable   : class io.kubernetes.client.examples.models.V1Image#Start listing and watching...
```

## Example Workload

Here is a minimal workload (in `src/test/k8s/demo`):

```yaml
apiVersion: carto.run/v1alpha1
kind: Workload
metadata:
  name: demo
  labels:
    app.tanzu.vmware.com/workload-type: web
spec:
  serviceAccountName: admin
  params:
    - name: image_prefix
      value: localhost:5000/apps/
```

The two things any workload really needs are a label, which matches a supply-chain, and an image location, so that it can kick things off. The image name is constructed by the supply chain from the prefix and the workload name. This workload is quite common in that it also needs a service account because it is going to manage two kinds of resource (images and deployments).

## The Supply Chain

You can install a sample supply chain and all its dependencies like this:

```
$ kubectl apply -f src/test/k8s/demo/admin/
```

If successful there should be a bunch of new resource types. E.g.

```
$ kubectl get clusterimagetemplates.carto.run
NAME             AGE
image   39m

$ kubectl get clustertemplates.carto.run
NAME         AGE
deployment   16h

$ kubectl get clustersupplychains.carto.run
NAME           READY   REASON   AGE
supply-chain   True    Ready    16h
```

### Service Account

There is a `src/test/k8s/demo/admin/service-account.yaml` that sets the service account up for workloads to use. It has to have these permissions (at least):

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: admin-permissions
rules:
  - apiGroups: ["apps", ""]
    resources: ["deployments", "pods"]
    verbs: ["*"]
  - apiGroups:
      - example.com
    resources: ["images"]
    verbs: ["*"]
```

N.B. `example.com` is the API group for the custom controller. Those permssisions are bound to the `admin` account using a `RoleBinding`:

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: admin

---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: admin-permissions
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: admin-permissions
subjects:
  - kind: ServiceAccount
    name: admin
```

### The Supply Chain

Here's the extremely minimal supply chain from `src/test/k8s/demo/admin/supply-chain.yaml`:

```yaml
apiVersion: carto.run/v1alpha1
kind: ClusterSupplyChain
metadata:
  name: supply-chain
spec:
  selector:
    app.tanzu.vmware.com/workload-type: web
  resources:
    - name: image-builder
      templateRef:
        kind: ClusterImageTemplate
        name: image

    - name: deployer
      templateRef:
        kind: ClusterTemplate
        name: deployment
      images:
        - resource: image-builder
          name: image
```

It has a selector matching the label in our workload, and 2 resources, one each of

- A resource named `image-builder` which is a `ClusterImageTemplate` named `image`. This creates an `image`, which is the source of an image path for the deployment.
- A resource named `deployer` which is a `ClusterTemplate` named `deployment`. This creates a deployment for the image. It has a reference back to the image via the resource identifier and the name of a reference `imagePath` in the template, which in turn directs to a field in the actual `image`.

The templates are defined in `src/test/k8s/demo/admin/*-template.yaml` and will typically be created by an admin or architect - they are shared between applications.

### The Image Template

The template that produces image resources is as simple as it could be

```yaml
apiVersion: carto.run/v1alpha1
kind: ClusterImageTemplate
metadata:
  name: image
spec:
  imagePath: .status.latestImage

  template:
    apiVersion: example.com/v1
    kind: Image
    metadata:
      name: $(workload.metadata.name)$
    spec:
      image: $(workload.spec.source.image)$
```

It instructs cartographer to create an image with a parameterized name and image location, and then look for a field called `.status.latestImage` in the result, exposing that as the `image` property of the resource.

### The Deployment Template

This is also as simple as it gets. There are no ports exposed. No env vars. Nothing except a name and an image path to configure:

```yaml
apiVersion: carto.run/v1alpha1
kind: ClusterTemplate
metadata:
  name: deployment
spec:
  template:
    apiVersion: apps/v1
    kind: Deployment
    metadata:
      name: $(workload.metadata.name)$
      labels:
        app: $(workload.metadata.name)$
    spec:
      replicas: 1
      selector:
        matchLabels:
          app: $(workload.metadata.name)$
      template:
        metadata:
          labels:
            app: $(workload.metadata.name)$
        spec:
          containers:
            - image: $(images.image.image)$
              name: $(workload.metadata.name)$
```

You can see a `template` which is a standard `apps/deployment`. In the template the `workload.metadata.name` is used to provide labels and names of things. All supply chains have an output property `images` that we are using to pull out the image path for the deployment. Because of the way the supply chain was defined `images` has only one element, and its name is `image`. That element is an [`ImageTemplate`](https://cartographer.sh/docs/v0.3.0/architecture/#templates) and its `spec.imagePath` output translates into an `image` input for other resources.

### See it Working

Apply the workload resource and see what happens:

```
$ kubectl apply -f src/test/k8s/demo/workload.yaml

$ kubectl describe images
Name:         demo
Namespace:    default
...
Spec:
  Image:  localhost:5000/apps/demo
Status:
  Complete:      true
  Latest Image:  localhost:5000/apps/demo@sha256:72defb0353f4fb7a3869a2b89d92fbc3b6a99b48d1b960bba092fa3c8d093eed
Events:          <none>
```

and a deployment has been created with the new image reference:

```
$ kubectl get all
NAME                       READY   STATUS    RESTARTS   AGE
pod/demo-7684fb795-mv6tf   1/1     Running   0          81s

NAME                 TYPE        CLUSTER-IP   EXTERNAL-IP   PORT(S)   AGE
service/kubernetes   ClusterIP   10.96.0.1    <none>        443/TCP   23h

NAME                   READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/demo   1/1     1            1           81s

NAME                             DESIRED   CURRENT   READY   AGE
replicaset.apps/demo-7684fb795   1         1         1       81s

NAME                      SOURCE   SUPPLYCHAIN    READY   REASON   AGE
workload.carto.run/demo            supply-chain   True    Ready    83s
```

Awesome.

## Monitoring Image Changes

Image changes are detected by simply requeuing the reconciliation request (every 10 seconds). So you should see that happening in the controller logs:

```
2022-04-05 09:35:15.669  INFO 1882354 --- [ageController-1] i.k.client.examples.ImageReconciler      : Checking digest for: http://localhost:5000/v2/apps/demo/manifests/latest
2022-04-05 09:35:25.689  INFO 1882354 --- [ageController-2] i.k.client.examples.ImageReconciler      : Checking digest for: http://localhost:5000/v2/apps/demo/manifests/latest
2022-04-05 09:35:35.717  INFO 1882354 --- [ageController-1] i.k.client.examples.ImageReconciler      : Checking digest for: http://localhost:5000/v2/apps/demo/manifests/latest
...
```

If you actually change the image contents, e.g.

```
$ docker tag nginx localhost:5000/apps/demo
$ docker push localhost:5000/apps/demo
```

then it will show up in the image resource as a change in the digest:

```
$ kubectl get images.example.com
NAME   IMAGE                      LATEST
demo   localhost:5000/apps/demo   localhost:5000/apps/demo@sha256:83d487b625d8c7818044c04f1b48aabccd3f51c3341fc300926846bca0c439e6
```

and a new replicaset is created as the deployment is updated.

## Building and Deploying in the Cluster

For "real" use cases you would want to run the controller in the cluster. So build an image:

```
$ ./mvnw spring-boot:build-image
...
[INFO] Successfully built image 'localhost:5000/apps/controller:latest'
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  05:56 min
[INFO] Finished at: 2022-04-05T16:01:59+01:00
[INFO] ------------------------------------------------------------------------
```

Create the RBAC rules:

```
$ kubectl apply -f src/main/k8s/rbac/roles.yaml
```

and deploy the controller:

```
$ kubectl apply -f src/main/k8s/controller/
$ kubectl describe pod --namespace spring-system
...
Events:
  Type    Reason     Age    From               Message
  ----    ------     ----   ----               -------
  Normal  Scheduled  7m26s  default-scheduler  Successfully assigned spring-system/spring-controller-manager-6c48bb6658-7vgdz to kind-control-plane
  Normal  Pulling    7m25s  kubelet            Pulling image "localhost:5000/apps/controller"
  Normal  Pulled     7m25s  kubelet            Successfully pulled image "localhost:5000/apps/controller" in 70.492849ms
  Normal  Created    7m25s  kubelet            Created container manager
  Normal  Started    7m25s  kubelet            Started container manager
  Normal  Pulled     7m25s  kubelet            Container image "gcr.io/kubebuilder/kube-rbac-proxy:v0.4.0" already present on machine
  Normal  Created    7m25s  kubelet            Created container kube-rbac-proxy
  Normal  Started    7m25s  kubelet            Started container kube-rbac-proxy
```

## Generating OpenAPI Source Code

From a cluster with all the CRDs:

```
$ kubectl get --raw /openapi/v2 | jq 'with_entries(select([.key] | inside(["definitions", "components", "info", "swagger", "openapi"]))) + {paths:{}}' > target/k8s.json
```

### Preprocessing the OpenAPI Spec

The [Kubernetes Client](https://github.com/kubernetes-client/java) has a documented process for generating Java bindings to the CRDs. It involves pre-processing the JSON OpenAPI spec with python. We can do the same for our relatively simple use case using `jq`. First extract the info headers:

```
$ jq 'with_entries(select([.key] | inside(["info", "swagger", "openapi"])))' target/k8s.json > target/info.json
```

Then grab the definitions from our `com.example` group identifier:

```
$ jq '.definitions | with_entries( select(.key|startswith("com.example"))) | with_entries(.key|= sub("com.example.";"")) |with_entries(.value += {"x-implements":[if(.key|test(".*List.*")) then "io.kubernetes.client.common.KubernetesListObject" else "io.kubernetes.client.common.KubernetesObject" end]})' target/k8s.json > target/definitions.json
```

Then merge them and add empty `paths`:

```
$ jq -s '.[0] + {definitions:.[1]} + {paths:{}}' target/info.json target/definitions.json  > target/k8s.json
```

Plus a bit of search and replace for the long qualifiers in field type identifiers:

```
$ sed -i -e 's,#/definitions/io.k8s.apimachinery.pkg.apis.meta.,#/definitions/,' -e 's,#/definitions/com.example.,#/definitions/,' target/k8s.json
```

### Generating Code from Just the CRDs

We can use the OpenAPI Maven plugin for generating Java code (or Rust or C etc.) from the CRDs. With `target/k8s.json` prepared as above:

```
$ mvn generate-sources -P generator-java
```

Configuration for the plugin is copied from the Kubernetes Client source code. Here is the configuration behind the `generator-java` profile:

```xml
<properties>
	<generator.package.name>io.kubernetes.client.examples</generator.package.name>
	<generator.client.version>${project.version}</generator.client.version>
</properties>
<build>
	<plugins>
		<plugin>
			<groupId>org.openapitools</groupId>
			<artifactId>openapi-generator-maven-plugin</artifactId>
			<configuration>
				<output>target/openapi</output>
				<generatorName>java</generatorName>
				<generateModelDocumentation>false</generateModelDocumentation>
				<importMappings>
					io.k8s.apimachinery.pkg.apis.meta.v1.ListMeta=io.kubernetes.client.openapi.models.V1ListMeta,
					V1ObjectMeta=io.kubernetes.client.openapi.models.V1ObjectMeta,
					IntOrString=io.kubernetes.client.custom.IntOrString,
					Quantity=io.kubernetes.client.custom.Quantity,
					V1Patch=io.kubernetes.client.custom.V1Patch,
					V1DeleteOptions=io.kubernetes.client.openapi.models.V1DeleteOptions,
					V1Status=io.kubernetes.client.openapi.models.V1Status,
					V1Scale=io.kubernetes.client.openapi.models.V1Scale,
				</importMappings>
				<skipValidateSpec>true</skipValidateSpec>
				<generateApiTests>false</generateApiTests>
				<generateModelTests>false</generateModelTests>
				<configOptions>
					<invokerPackage>io.kubernetes.client.openapi</invokerPackage>
					<modelPackage>${generator.package.name}.models</modelPackage>
					<apiPackage>${generator.package.name}.apis</apiPackage>
					<invokerPackage>${generator.package.name}</invokerPackage>
					<ensureUniqueParams>true</ensureUniqueParams>
					<serializableModel>false</serializableModel>
					<bigDecimalAsString>false</bigDecimalAsString>
					<fullJavaUtil>false</fullJavaUtil>
					<hideGenerationTimestamp>true</hideGenerationTimestamp>
					<dateLibrary>java8</dateLibrary>
					<useRxJava>false</useRxJava>
					<library>rest-assured</library>
					<useReflectionEqualsHashCode>false</useReflectionEqualsHashCode>
				</configOptions>
				<typeMappings>int-or-string=IntOrString,quantity=Quantity,patch=V1Patch</typeMappings>
				<importMappings>IntOrString=io.kubernetes.client.custom.IntOrString,Quantity=io.kubernetes.client.custom.Quantity,V1Patch=io.kubernetes.client.custom.V1Patch</importMappings>
			</configuration>
		</plugin>
		<plugin>
			<artifactId>maven-resources-plugin</artifactId>
			<executions>
				<execution>
					<id>copy-resources</id>
					<phase>generate-sources</phase>
					<goals>
						<goal>copy-resources</goal>
					</goals>
					<configuration>
						<outputDirectory>${basedir}/src/main/java/io/kubernetes/client/examples/models</outputDirectory>
						<resources>
							<resource>
								<directory>${basedir}/target/openapi/src/main/java/io/kubernetes/client/examples/models</directory>
								<includes>
									<include>*.java</include>
								</includes>
							</resource>
						</resources>
					</configuration>
				</execution>
			</executions>
		</plugin>
	</plugins>
</build>
```

> N.B. the `library=rest-assured` has no direct impact on the generated code (no client is needed), but there is a [bug in the generator](https://github.com/OpenAPITools/openapi-generator/issues/12391) that makes it fail with the more obvious choice of `native`. Choosing `rest-assured` has a side effect of forcing the JSON serializer to GSON.

### Manual Code Generation with Kubernetes Client

We need the Java version of the `pom.xml` from the generator:

```
$ mkdir -p target/openapi
$ curl https://raw.githubusercontent.com/kubernetes-client/gen/master/openapi/java.xml > target/openapi/pom.xml
```

and the JSON spec:

```
$ cp target/k8s.json target/openapi/
```

Finally we can create the code and copy the bits we care about back into the main project:

```
$ (cd target/openapi; LIBRARY=rest-assured OPENAPI_SKIP_BASE_INTERFACE=true KUBERNETES_CRD_MODE=true mvn -Dgenerator.spec.path=k8s.json -D=generator.client.version=0.0.1 -D=generator.package.name=io.kubernetes.client.examples -D=openapi-generator-version=6.0.0-beta generate-sources)
$ cp -rf target/openapi/src/main/java/io/kubernetes/client/examples/models src/main/java/io/kubernetes/client/examples
```

### Docker in Docker and Kind

The official way (involving docker and kind) is much heavier and probably more fragile. You have to stop your kind cluster or it will complain, and then if it fails it doesn't clean up. The result is a bunch of generated files owned by `root` (which sucks). Anyway, here it is:

```
$ docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "$(pwd)":"$(pwd)" -ti --network host docker.pkg.github.com/kubernetes-client/java/crd-model-gen:v1.0.6 /generate.sh -u $(pwd)/src/main/k8s/crds/image.yaml -n com.example -p com.dsyer -o "$(pwd)"
$ sudo chown -R dsyer:dsyer src/main/java/com/dsyer/
```

## Build a WASM from Source code in C

Generate the client code (a profile is activated by the JSON file above):

```
$ mvn install -P generation-c
$ ls target/generated-sources/
annotations  openapi
```

Then you can make a linkable library with `make` (or `emmake make` if you want a WASM):

```
$ make clean
$ emmake make
$ tar -tzvf k8s-wasm.tgz | grep lib
drwxr-xr-x dsyer/dsyer       0 2022-04-28 10:15 lib/
-rw-r--r-- dsyer/dsyer    5190 2022-04-28 10:15 lib/libk8s.a
$ tar -tzvf k8s-wasm.tgz | grep com_example
-rw-r--r-- dsyer/dsyer     982 2022-04-28 10:15 include/k8s/com_example_v1_image_spec.h
-rw-r--r-- dsyer/dsyer    1270 2022-04-28 10:15 include/k8s/com_example_v1_image_list.h
-rw-r--r-- dsyer/dsyer    1103 2022-04-28 10:15 include/k8s/com_example_v1_image_status.h
-rw-r--r-- dsyer/dsyer    1367 2022-04-28 10:15 include/k8s/com_example_v1_image.h
```

## Generate Rust Bindings

To generate some code from the CRDs (assuming `target/k8s.json` is pre-processed as above):

```
$ mvn generate-sources -P generator-rust
```

The generated code will be in `target/rust`. You will need to copy the `src/lib/models` code and include the [`k8s-openapi`](https://docs.rs/k8s-openapi/latest/k8s_openapi/) dependency in `Cargo.toml`, e.g:

```toml
[dependencies]
k8s-openapi = { version = "0.14.0", features = ["v1_19"], default-features = false }
```