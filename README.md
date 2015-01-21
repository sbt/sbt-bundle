# Typesafe ConductR Bundle Plugin

[![Build Status](https://api.travis-ci.org/sbt/sbt-bundle.png?branch=master)](https://travis-ci.org/sbt/sbt-bundle)

## Introduction

A plugin that uses the [sbt-native-packager](https://github.com/sbt/sbt-native-packager) to produce Typesafe ConductR bundles.

The plugin will take any package that you have presently configured and wrap it in a bundle.

## Usage

Declare the plugin (typically in a `plugins.sbt`):

```scala
addSbtPlugin("com.typesafe.sbt" % "sbt-bundle" % "0.10.0")
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
system  = "angular-seed-play-1.0-SNAPSHOT"
components = {
  "angular-seed-play-1.0-SNAPSHOT" = {
    description      = "angular-seed-play"
    file-system-type = "universal"
    start-command    = ["bin/angular-seed-play"]
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

## Endpoints

Understanding endpoint declarations is important in order for your bundle to be able to become available within ConductR.

A bundle's component may be run either within a container or on the ConductR host. In either circumstance a port and a name needs to be specified so that the component’s application or service may bind to that port.

Because multiple bundles may run on the same host, and that their respective components may bind to the same port, we have a means of avoiding them clash.

The following port definitions are used:

Name         | Description
-------------|------------
service-port | The port number to be used as the public-facing port. It is proxied to the host-port.
host-port    | This is not declared but is dynamically allocated if bundle is running in a container. Otherwise it has the same value as bind-port.
bind-port    | The port the bundle component’s application or service actually binds to. When this is 0 it will be dynamically allocated (which is the default).

Endpoints are declared using an `endpoint` setting using an Map of endpoint-name/`Endpoint(protocol, bindPort, servicePort)` pairs.

The bind-port allocated to your bundle will be available as an environment variable to your component. For example, given the default settings where an endpoint named "web" is declared that has a dynamically allocated port, an environment variable named `WEB_BIND_PORT` will become available. The value of this environment variable should be used to bind to. 

As an example, and for Play applications, the following can be specified:

    BundleKeys.startCommand += "-Dhttp.port=$WEB_BIND_PORT"

### Docker Containers and ports

When your component will runs within a container you may alternatively declare the bind port to be whatever it may be. Taking our Play example again, we can set the bind port with no problem of it clashing with another port given that it is run within a container:

    BundleKeys.endpoints := Map("web" -> Endpoint("http", 9000, 9000))

### Service ports

The service port is the port on which your service will be addressed to the outside world on. Extending last example, if port 80 is to be used to provide your services and then the following expression can be used:

    BundleKeys.endpoints := Map("web" -> Endpoint("http", 9000, 80))

## Settings

The following settings are provided under the `BundleKeys` object:

Name         | Description
-------------|-------------
bundleConf   | The bundle configuration file contents.
bundleType   | The type of configuration that this bundling relates to. By default Universal is used.
endpoints    | Declares endpoints using an `Endpoint(protocol, bindPort, servicePort)` structure. The default is `Map("web" -> Endpoint("http", 0, 9000))`.
startCommand | Command line args required to start the component. Paths are expressed relative to the component's bin folder. The default is to use the bash script in the bin folder.
system       | A logical name that can be used to associate multiple bundles with each other. This could be an application or service association and should include a version e.g. myapp-1.0.0.

&copy; Typesafe Inc., 2014
