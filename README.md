# ConductR Bundle Plugin

[![Build Status](https://api.travis-ci.org/sbt/sbt-bundle.png?branch=master)](https://travis-ci.org/sbt/sbt-bundle)

## Introduction

A plugin that uses the [sbt-native-packager](https://github.com/sbt/sbt-native-packager) to produce Typesafe ConductR bundles.

The plugin will take any package that you have presently configured and wrap it in a bundle.

## Usage

Declare the plugin (typically in a `plugins.sbt`):

```scala
addSbtPlugin("com.typesafe.sbt" % "sbt-bundle" % "0.19.1")
```

Declaring the native packager or any of its other plugins should be sufficient. For example, in your `build.sbt` file:

```scala
lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)
```

_Note that if you have used Play 2.3 that you must also additionally enable `JavaAppPackaging` for your build e.g.:_

```scala
enablePlugins(JavaAppPackaging, PlayScala)
```

_Note also that if you have used a pre 1.0 version of sbt-native-packager then you must remove imports such as the following from your `.sbt` files:_


```
import com.typesafe.sbt.SbtNativePackager._
import NativePackagerKeys._
```

_...otherwise you will get duplicate imports reported. This is because the new 1.0+ version uses sbt's auto plugin feature._

Finally, produce a bundle:

```
bundle:dist
```

## ConductR Bundles

ConductR has a bundle format in order for components to be described. In general there is a one-to-one correlation between a bundle and a component.

Bundles provide ConductR with some basic knowledge about components in a *bundle descriptor*; in particular, what is required in order to load and run a component. The following is an example of a `bundle.conf` descriptor:
([Typesafe configuration](https://github.com/typesafehub/config) is used):

```
version    = "1.0.0"
name       = "simple-test"
system     = "simple-test-0.1.0-SNAPSHOT"
nrOfCpus   = 1.0
memory     = 67108864
diskSpace  = 10485760
roles      = ["web-server"]
components = {
  "angular-seed-play-1.0-SNAPSHOT" = {
    description      = "angular-seed-play"
    file-system-type = "universal"
    start-command    = ["angular-seed-play-1.0-SNAPSHOT/bin/angular-seed-play", "-Xms=67108864", "-Xmx=67108864"]
    endpoints        = {
      "angular-seed-play" = {
        protocol  = "http"
        bind-port = 0
        services  = ["http://:9000"]
      }
    }
  }
}
```

At a minimum, you will be required to declare the bundle's required number of cpus, its memory and disk space for your project's build file. For a typical microservice this may look like the following:

```scala
import ByteConversions._

BundleKeys.nrOfCpus := 1.0
BundleKeys.memory := 64.MiB
BundleKeys.diskSpace := 10.MB
BundleKeys.roles := Set("web-server")
```

(You'll note that the international standards for supporting [binary prefixes](http://en.wikipedia.org/wiki/Binary_prefix), as in `MiB`, is supported).

## Endpoints

Understanding endpoint declarations is important in order for your bundle to be able to become available within ConductR.

A bundle's component may be run either within a container or on the ConductR host. In either circumstance the bind interface and bind port provided by ConductR need to be used by a component in order to successfully bind and start listening for traffic. These are made available as `name_BIND_IP` and `name_BIND_PORT` environment variables respectively (see the "Standard Environment Variables" section toward the bottom of this document for a reference to all environment variables).

Because multiple bundles may run on the same host, and that their respective components may bind to the same port, we have a means of avoiding them clash.

The following port definitions are used:

Name         | Description
-------------|------------
service-port | The port number to be used as the public-facing port. It is proxied to the host-port.
service-name | A name to be used to address the service. In the case of http protocols, this is interpreted as a path to be used for proxying. Other protocols will have different interpretations.
host-port    | This is not declared but is dynamically allocated if bundle is running in a container. Otherwise it has the same value as bind-port.
bind-port    | The port the bundle component’s application or service actually binds to. When this is 0 it will be dynamically allocated (which is the default).

Endpoints are declared using an `endpoint` setting using a Map of endpoint-name/`Endpoint(bindProtocol, bindPort, services)` pairs.

The bind-port allocated to your bundle will be available as an environment variable to your component. For example, given the default settings where an endpoint named "web" is declared that has a dynamically allocated port, an environment variable named `WEB_BIND_PORT` will become available. `WEB_BIND_IP` is also available and should be used as the interface to bind to.  

As an example, and for Play applications, the following can be specified:

    BundleKeys.startCommand += "-Dhttp.address=$WEB_BIND_IP -Dhttp.port=$WEB_BIND_PORT"

### Docker Containers and ports

When your component will run within a container you may alternatively declare the bind port to be whatever it may be. Taking our Play example again, we can set the bind port with no problem of it clashing with another port given that it is run within a container:

    BundleKeys.endpoints := Map("web" -> Endpoint("http", 9000, ...))

### Service ports

The services define the protocol, port, and/or path under which your service will be addressed to the outside world on. For example, if http and port 80 are to be used to provide your services and then the following expression can be used to resolve `/myservice` on:

    BundleKeys.endpoints := Map("web" -> Endpoint("http", services = Set(URI("http:/myservice"))))

## Settings

The following settings are provided under the `BundleKeys` object:

Name           | Description
---------------|-------------
bundleConf     | The bundle configuration file contents.
bundleType     | The type of configuration that this bundling relates to. By default Universal is used.
diskSpace      | The amount of disk space required to host an expanded bundle and configuration. Append the letter k or K to indicate kilobytes, or m or M to indicate megabytes. Required.
endpoints      | Declares endpoints using an `Endpoint(protocol, bindPort, services)` structure. The default is `Map("web" -> Endpoint("http", services = Set(URI(s"http://:9000"))))` where the key is the `name` of this project. The "web" key is used to form a set of environment variables for your components. For example you will have a `WEB_BIND_PORT` in this example.
memory         | The amount of memory required to run the bundle.
nrOfCpus       | The number of cpus required to run the bundle (can be fractions thereby expressing a portion of CPU). Required.
roles          | The types of node in the cluster that this bundle can be deployed to. Defaults to having no specific roles.
system         | A logical name that can be used to associate multiple bundles with each other. This could be an application or service association and should include a version e.g. myapp-1.0.0. Defaults to the package name.
startCommand   | Command line args required to start the component. Paths are expressed relative to the component's bin folder. The default is to use the bash script in the bin folder.

## Standard Environment Variables
For reference, the following standard environment variables are available to a bundle component at runtime:

Name                      | Description
--------------------------|------------
BUNDLE_ID                 | The bundle identifier associated with the bundle and its optional configuration.
BUNDLE_SYSTEM             | A logical name that can be used to associate multiple bundles with each other. This could be an application or service association and should include a version e.g. myapp-1.0.0.
BUNDLE_HOST_IP            | The IP address of a bundle component’s host.
CONDUCTR_CONTROL          | A URL for the control protocol of ConductR, composed as $CONDUCTR_CONTROL_PROTOCOL://$CONDUCTR_CONTROL_IP:$CONDUCTR_CONTROL_PORT
CONDUCTR_CONTROL_PROTOCOL | The protocol of the above.
CONDUCTR_CONTROL_IP       | The assigned ConductR’s bind IP address.
CONDUCTR_CONTROL_PORT     | The port for the above. Inaccessible to containerized bundles such as those hosted by Docker.
CONDUCTR_STATUS           | A URL for components to report their start status, composed as $CONDUCTR_STATUS_PROTOCOL://$CONDUCTR_STATUS_IP:$CONDUCTR_STATUS_PORT
CONDUCTR_STATUS_PROTOCOL  | The protocol of the above.
CONDUCTR_STATUS_IP        | The assigned ConductR’s bind IP address.
CONDUCTR_STATUS_PORT      | The port for the above.
SERVICE_LOCATOR           | A URL composed as $SERVICE_LOCATOR_PROTOCOL://$SERVICE_LOCATOR_IP:$SERVICE_LOCATOR_PORT
SERVICE_LOCATOR_PROTOCOL  | The protocol of the above.
SERVICE_LOCATOR_IP        | The interface of an http service for resolving addresses.
SERVICE_LOCATOR_PORT      | The port of the above.
SERVICE_PROXY_IP          | The interface of this bundle's proxy.
CONTAINER_ENV             | A colon separated list of environment variables that will be passed through to a container. When overriding this be sure to include its original value e.g. CONTAINER_ENV=$CONTAINER_ENV:SOME_OTHER_ENV..

In addition the following environment variables are declared for each component endpoint:

Name              | Description
------------------|------------
name_PROTOCOL     | The protocol of a bundle component’s endpoint.
name_HOST         | A bundle component’s host URL composed as $name_PROTOCOL://$name_HOST_IP:$name_HOST_PORT
name_HOST_PORT    | The port exposed on a bundle’s host.
name_BIND_IP      | The interface the component should bind to.
name_BIND_PORT    | The port the component should bind to.

&copy; Typesafe Inc., 2014-2015
