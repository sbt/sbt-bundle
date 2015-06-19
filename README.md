# ConductR Bundle Plugin

[![Build Status](https://api.travis-ci.org/sbt/sbt-bundle.png?branch=master)](https://travis-ci.org/sbt/sbt-bundle)

## Introduction

A plugin that uses the [sbt-native-packager](https://github.com/sbt/sbt-native-packager) to produce Typesafe ConductR bundles.

The plugin will take any package that you have presently configured and wrap it in a bundle.

## Usage

Declare the plugin (typically in a `plugins.sbt`):

```scala
addSbtPlugin("com.typesafe.sbt" % "sbt-bundle" % "1.0.0")
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

It is possible to produce additional configuration bundles that contain an optional bundle.conf the value of which override the main bundle, as
well as arbitrary shell scripts.

These additional configuration files must be placed in <src>/bundle-configuration/<configurationFolderName>.

The bundle-configuration folder may contain many configurations, the desired configuration can be specified with the setting:

```
configurationName := <configurationFolderName>
```

in build.sbt

Then to produce this additional bundle:

```
configuration:dist
```

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
checks         | Declares uris to check to signal to ConductR that the bundle components have started for situations where a component doesn't do that. For example Seq("$WEB_HOST") will check that a endpoint named "web" will be checked given its host environment var. Once that URL becomes available then ConductR will be signalled that the bundle is ready.

&copy; Typesafe Inc., 2014-2015
