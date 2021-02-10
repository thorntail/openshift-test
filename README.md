# Thorntail OpenShift Test

A JUnit 5 extension that simplifies testing JKube-based projects against OpenShift.

Prerequisites: JDK, Maven, `oc` (the OpenShift cmdline client).
You also have to be logged into the OpenShift cluster (`oc login ...`) and have a project (`oc new-project ...`).

To use, add a test-scoped dependency:

```xml
<dependency>
    <groupId>io.thorntail.openshift-test</groupId>
    <artifactId>thorntail-openshift-test</artifactId>
    <version>...</version>
    <scope>test</scope>
</dependency>
```

This will transitively bring dependencies on:

- JUnit Jupiter (together with JUnit Vintage engine, so JUnit 4 testing is still possible);
- Fabric8 OpenShift Client;
- Rest Assured
- Awaitility
- AssertJ Core

So usually, no other dependencies are needed.

OpenShift tests must be integration tests executed by Failsafe (`*IT`) annotated with `@OpenShiftTest`:

```java
@OpenShiftTest
public class HelloOpenShiftIT {
    @Test
    public void hello() {
        ...
    }
}
```

This will make sure that OpenShift resources are deployed before the test class is executed, and also undeployed after this test class is executed.
It is expected that a YAML file with a complete list of OpenShift resources to deploy is present in `target/classes/META-INF/jkube/openshift.yml`.
That's what JKube does by default.

After the OpenShift resources are deployed, the test framework waits until they become ready.
After that, the test framework waits for all the routes to respond.
If there's a corresponding readiness probe, it is used, otherwise a liveness probe is used if it exists; if there's no health probe, the root path `/` is awaited.
Only after the routes responds successfully with status code 200 will the tests be allowed to run.

The `@Test` methods can use RestAssured.
In case the OpenShift resources only describe a single application, RestAssured will be preconfigured to point to it.
When you need to wait for something to happen, use Awaitility; don't write your own wait loops with `Thread.sleep` etc.

```java
@OpenShiftTest
public class HelloOpenShiftIT {
    @Test
    public void hello() {
        // this is an EXAMPLE, waiting here isn't necessary! the test framework
        // already waits for the application to start responding
        await().atMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
            when()
                    .get("/hello")
            .then()
                    .statusCode(200)
                    .body("content", is("Hello, World!"));
        });
    }
}
```

### Dependency injection

For more complex test scenarios, you can obtain some useful objects by annotating a test instance field or a test method parameter with `@TestResource`:

```java
@OpenShiftTest
public class HelloOpenShiftIT {
    @TestResource
    private OpenShiftClient oc;

    @Test
    public void hello() {
        oc.routes().list().getItems().forEach(System.out::println);

        ...
    }
}
```

The full set of objects that you can inject is:

- `OpenShiftClient`: the Fabric8 Kubernetes client, in default configuration
- `AppMetadata`: provides convenient access to some metadata about the tested application, if there's exactly one
- `AllAppsMetadata`: provides convenient access to some metadata about all the tested applications
- `AwaitUtil`: utility to wait for some OpenShift resources
- `OpenShiftUtil`: utility to perform higher-level actions on some OpenShift resources
- `Config`: simple configuration utility based on system properties
- `URL` or `URI`: URL of a route designated by the `@WithName` annotation; if there's exactly one application, then `@WithName` is not necessary and the URL of the application is injected

### Deploying additional resources

The test application(s) might require additional OpenShift resources to be deployed, such as ConfigMaps or other deployments.
To do that, you can use the `@AdditionalResources` annotation:

```java
@OpenShiftTest
@AdditionalResources("classpath:configmap.yaml")
public class HelloOpenShiftIT {
    ...
}
```

These resources are deployed _before_ the test application is deployed, and are also undeployed _after_ the test application is undeployed.
This annotation is `@Repeatable`, so you can include it more than once.

### Running tests in ephemeral namespaces

By default, the test framework expects that the user is logged into an OpenShift project, and that project is used for all tests.

If you start the tests with `-Dts.use-ephemeral-namespaces`, the test framework will create an ephemeral namespace for each test.
After the test is finished, the ephemeral namespace is automatically dropped.

The ephemeral namespaces are named `ts-<unique suffix>`, where the unique suffix is 10 random `a-z` characters.

### Retaining resources on failure

When the test finishes, all deployed resources are deleted.
Sometimes, that's not what you want: if the test fails, you might want all the OpenShift resources to stay intact, so that you can investigate the problem.
To do that, run the tests with `-Dts.retain-on-failure`.

This works with and without ephemeral namespaces, but note that if you're not using ephemeral namespaces, all the tests run in a single namespace.
In such case, when you enable retaining resources on test failure, it's best to only run a single test.

### Enabling/disabling tests

The `@OnlyIfConfigured` and `@OnlyIfNotConfigured` annotations can be used to selectively enable/disable execution of tests based on a configuration property.

### Custom application deployment

You can use `@CustomizeApplicationDeployment` and `@CustomizeApplicationUndeployment` to run custom code before application deployment and after application undeployment.

If the test class is annotated `@ManualApplicationDeployment`, the `target/classes/META-INF/jkube/openshift.yml` file is ignored and the test applications are _not_ deployed automatically.
Instead, you should use `@AdditionalResources`, `@CustomizeApplicationDeployment` and `@CustomizeApplicationUndeployment` to deploy the application manually.
You should also provide the necessary application metadata using  the`@CustomAppMetadata` annotation.

### Image overrides

It is sometimes useful to globally override certain images, for example when testing with a pre-release version of an image that is not yet available publicly.
In such case, you can set `-Dts.image-overrides` to a path of a file that looks like this:

```
registry.access.redhat.com/openjdk/openjdk-11-rhel7=registry.access.redhat.com/ubi8/openjdk-11
registry.access.redhat.com/rhscl/postgresql-10-rhel7=registry.redhat.io/rhscl/postgresql-12-rhel7
```

This looks like a `.properties` format, but it is not!
Specifically, the format is:

- empty lines and lines that begin with `#` are ignored;
- other lines must have a source image name (possibly with a tag), followed by `=`, followed by a target image name (possibly with a tag).

When a YAML file refers to the source image, it is changed to use the target image before it is deployed.
If there's no tag in the configuration of the source image, it will match all tags.

Note that this is _not_ dumb string search & replace.
We actually edit the Kubernetes resources on a few specific places (such as container definition or image stream definition), the rest is left unchanged.

This currently works automatically for the `target/classes/META-INF/jkube/openshift.yml` file and all the files deployed with `@AdditionalResources`.

Note that it is usually a good idea to set `-Dts.image-overrides` to a _full_ path, because when building multi-module projects, Maven changes the current working directory for each individual module.

### Skipping deployment

Sometimes, you want to run a test against applications that are already deployed (e.g. using S2I).
In other words, you want to skip the automatic application deployment process the test framework runs.

In such case, you can use `-Dts.skip-deployment`, and the test framework will _not_:

- run `oc apply -f target/classes/META-INF/jkube/openshift.yml` (and the corresponding `oc delete`);
- deploy (and undeploy) `@AdditionalResources`;
- run `@CustomizeApplicationDeployment` and `@CustomizeApplicationUndeployment` methods.

Note that everything else stays intact.
That especially means that the `target/classes/META-INF/jkube/openshift.yml` file must still exist and contain all the deployed applications.
(Unless `@CustomAppMetadata` is used.)

Combination with `@ManualApplicationDeployment` or `-Dts.use-ephemeral-namespaces` doesn't make much sense, but isn't detected or prevented.
