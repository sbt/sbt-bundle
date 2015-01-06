# Typesafe ConductR Bundle Plugin

[![Build Status](https://api.travis-ci.org/sbt/sbt-bundle.png?branch=master)](https://travis-ci.org/sbt/sbt-bundle)

## Introduction

A plugin that uses the [sbt-native-packager](https://github.com/sbt/sbt-native-packager) to produce Typesafe ConductR bundles.

The plugin will take any package that you have presently configured and wrap it in a bundle.

## Usage

Declare the plugin (typically in a `plugins.sbt`):

```scala
addSbtPlugin("com.typesafe.sbt" % "sbt-bundle" % "0.6.0")
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
        protocol  = "http"
        bind-port = 9000
      }
    }
  }
}
```

## Settings

The following settings are provided:

Name         | Description
-------------|-------------
bundleConf   | The bundle configuration file contents.
bundleType   | The type of configuration that this bundling relates to. By default Universal is used.
endpoints    | Declares endpoints. The default is `Map("web" -> Endpoint("http", 9000))`.
startCommand | Command line args required to start the component. Paths are expressed relative to the component's bin folder. The default is to use the bash script in the bin folder.
system       | A logical name that can be used to associate multiple bundles with each other. This could be an application or service association and should include a version e.g. myapp-1.0.0.

&copy; Typesafe Inc., 2014
