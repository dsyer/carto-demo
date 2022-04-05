# Simple Supply Chain with Cartographer

The aim is to create a supply chain that makes k8s deployments from pre-built images. The simplest way that could possibly work would be with a hard-coded image in the workload. To make it slightly more interesting we want to monitor the image repository and update the deployment if the image changes. There is an closed source Tanzu [source-controller](https://github.com/vmware-tanzu/source-controller) that meets that need but it doesn't work with a local registry set up in the simplest way. Instead we use a custom controller written as a Spring Boot application with source code in this repository.

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
$ docker pull nginx
$ docker tag nginx localhost:5000/apps/demo
$ docker push localhost:5000/apps/demo
```

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

### Service Account

There is a `src/test/k8s/demo/admin/service-account.yaml` that sets the service account up for workloads to use. It has to have these permissions (at least):

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: admin-permissions
rules:
- apiGroups: ['apps', '']
  resources: ['deployments', 'pods']
  verbs: ['*']
- apiGroups:
  - example.com
  resources: ['images']
  verbs: ['*']
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
      params:
        - name: image_prefix
          default: localhost:5000/apps/

    - name: deployer
      templateRef:
        kind: ClusterTemplate
        name: deployment
      images:
        - resource: image-builder
          name: image
```

It has a selector matching the label in our workload, and 2 resources, one each of 

* A `ClusterImageTemplate` named `image`. This creates an `image`, which is the source of an image path for the deployment.
* A `ClusterTemplate` named `deployment`. This creates a deployment for the image. It has a reference back to the image via the resource identifier and the name of a reference `imagePath` in the template, which in turn directs to a field in the actual `image`.

The templates are defined in `src/test/k8s/demo/admin/*-template.yaml`. You can apply thos along with the other admin resources:

```
$ kubectl apply -f admin

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
  Latest Image:  localhost:5000/apps/demo@sha256:2ecba98b9f60ac24d12a90b42e836104b97563337872991280c983ada26199d3
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

and a new replicaset as the deployment is updated.