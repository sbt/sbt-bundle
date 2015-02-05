# Typesafe ConductR Bundle Plugin

[![Build Status](https://api.travis-ci.org/sbt/sbt-bundle.png?branch=master)](https://travis-ci.org/sbt/sbt-bundle)

## Introduction

A plugin that uses the [sbt-native-packager](https://github.com/sbt/sbt-native-packager) to produce Typesafe ConductR bundles.

The plugin will take any package that you have presently configured and wrap it in a bundle.

## Usage

Declare the plugin (typically in a `plugins.sbt`):

```scala
addSbtPlugin("com.typesafe.sbt" % "sbt-bundle" % "0.13.0")
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

## Typesafe ConductR Bundles

Typesafe ConductR has its own bundle format in order for components to be described. In general there is a one-to-one correlation between a bundle and a component, but it is also possible to have multiple components per bundle. You may want to do this when there is a strong dependency between one component and another. For example, perhaps a Play 2.2 applications requires a specific version of Nginx to proxy it (not that this is a real situation, just an example).

Bundles provide Typesafe ConductR with some basic knowledge about components in a *bundle descriptor*; in particular, what is required in order to start a component. The following is an example of a `bundle.conf` descriptor:
([Typesafe configuration](https://github.com/typesafehub/config) is used):

```
version = "1.0.0"
components = {
  "angular-seed-play-1.0-SNAPSHOT" = {
    description      = "angular-seed-play"
    file-system-type = "universal"
    start-command    = ["angular-seed-play-1.0-SNAPSHOT/bin/angular-seed-play"]
    endpoints        = {
      web = {
        protocol     = "http"
        bind-port    = 0
        service-port = 9000
        service-name = "/angular-seed-play"
      }
    }
  }
}
```

## Standard Environment Variables
The following standard environment variables are available to a bundle component at runtime:

Name                   | Description
-----------------------|------------
BUNDLE_ID              | The bundle identifier associated with the bundle and its optional configuration.
BUNDLE_SYSTEM          | A logical name that can be used to associate multiple bundles with each other. This could be an application or service association and should include a version e.g. myapp-1.0.0.
CONDUCTR_CONTROL       | A URL for the control protocol of ConductR, composed as $CONDUCTR_CONTROL_PROTO://$CONDUCTR_CONTROL_IP:$CONDUCTR_CONTROL_PORT
CONDUCTR_CONTROL_PROTO | The protocol of the above.
CONDUCTR_CONTROL_IP    | The assigned ConductR’s bind IP address.
CONDUCTR_CONTROL_PORT  | The port for the above. Inaccessible to containerized bundles such as those hosted by Docker.
CONDUCTR_STATUS        | A URL for components to report their start status, composed as $CONDUCTR_STATUS_PROTO://$CONDUCTR_STATUS_IP:$CONDUCTR_STATUS_PORT
CONDUCTR_STATUS_PROTO  | The protocol of the above.
CONDUCTR_STATUS_IP     | The assigned ConductR’s bind IP address.
CONDUCTR_STATUS_PORT   | The port for the above.
SERVICE_LOCATOR        | A URL composed as $SERVICE_LOCATOR_PROTO://$SERVICE_LOCATOR_IP:$SERVICE_LOCATOR_PORT
SERVICE_LOCATOR_PROTO  | The protocol of the above.
SERVICE_LOCATOR_IP     | The interface of an http service for resolving addresses e.g. haproxy. This will be equivalent to the CONDUCTR used for the ConductR i.e. its bind address and assumes that the service locator will always bind to the same interface as the ConductR (which is reasonable given that the service locator will depend on ConductR state).
SERVICE_LOCATOR_PORT   | The port of the above.
HOST_IP                | The IP address of a bundle component’s host.

In addition the following environment variables are declared for each component endpoint:

Name              | Description
------------------|------------
name_PROTO        | The protocol of a bundle component’s endpoint.
name_SERVICE      | A bundle component’s addressable service URL which will be used for proxying purposes. It is composed as $name_PROTO://$name_HOST_IP:$name_SERVICE_PORT$name_SERVICE_NAME
name_SERVICE_NAME | A bundle component’s addressable service name for proxying purposes.
name_SERVICE_PORT | The port to be used for proxying the host port to.
name_HOST         | A bundle component’s host URL composed as $name_PROTO://$name_HOST_IP:$name_HOST_PORT
name_HOST_IP      | The address of a bundle’s host.
name_HOST_PORT    | The port exposed on a bundle’s host.
name_BIND_PORT    | The port the component should bind to.

## Endpoints

Understanding endpoint declarations is important in order for your bundle to be able to become available within ConductR.

A bundle's component may be run either within a container or on the ConductR host. In either circumstance a port and a name needs to be specified so that the component’s application or service may bind to that port.

Because multiple bundles may run on the same host, and that their respective components may bind to the same port, we have a means of avoiding them clash.

The following port definitions are used:

Name         | Description
-------------|------------
service-port | The port number to be used as the public-facing port. It is proxied to the host-port.
service-name | A name to be used to address the service. In the case of http protocols, this is interpreted as a path to be used for proxying. Other protocols will have different interpretations.
host-port    | This is not declared but is dynamically allocated if bundle is running in a container. Otherwise it has the same value as bind-port.
bind-port    | The port the bundle component’s application or service actually binds to. When this is 0 it will be dynamically allocated (which is the default).

Endpoints are declared using an `endpoint` setting using an Map of endpoint-name/`Endpoint(protocol, bindPort, servicePort, serviceName)` pairs.

The bind-port allocated to your bundle will be available as an environment variable to your component. For example, given the default settings where an endpoint named "web" is declared that has a dynamically allocated port, an environment variable named `WEB_BIND_PORT` will become available. The value of this environment variable should be used to bind to. 

As an example, and for Play applications, the following can be specified:

    BundleKeys.startCommand += "-Dhttp.port=$WEB_BIND_PORT"

### Docker Containers and ports

When your component will runs within a container you may alternatively declare the bind port to be whatever it may be. Taking our Play example again, we can set the bind port with no problem of it clashing with another port given that it is run within a container:

    BundleKeys.endpoints := Map("web" -> Endpoint("http", 9000, 9000))

### Service ports

The service port is the port on which your service will be addressed to the outside world on. Extending last example, if port 80 is to be used to provide your services and then the following expression can be used to resolve `/myservices/someservice` on:

    BundleKeys.endpoints := Map("web" -> Endpoint("http", 9000, 80, "/myservices/someservice"))

## Settings

The following settings are provided under the `BundleKeys` object:

Name         | Description
-------------|-------------
bundleConf   | The bundle configuration file contents.
bundleType   | The type of configuration that this bundling relates to. By default Universal is used.
endpoints    | Declares endpoints using an `Endpoint(protocol, bindPort, servicePort, serviceName)` structure. The default is `Map("web" -> Endpoint("http", 0, 9000, "$name"))` where the service name is the `name` of this project. The "web" key is used to form a set of environment variables for your components. For example you will have a `WEB_BIND_PORT` in this example.
startCommand | Command line args required to start the component. Paths are expressed relative to the component's bin folder. The default is to use the bash script in the bin folder.

&copy; Typesafe Inc., 2014
