# ConductR Bundle Plugin

[![Build Status](https://api.travis-ci.org/sbt/sbt-bundle.png?branch=master)](https://travis-ci.org/sbt/sbt-bundle)

## Introduction

A plugin that uses the [sbt-native-packager](https://github.com/sbt/sbt-native-packager) to produce Typesafe ConductR bundles.

The plugin will take a Universal or Docker package that you have presently configured and wrap it in a bundle.

## Usage

Declare the plugin (typically in a `plugins.sbt`):

```scala
addSbtPlugin("com.typesafe.sbt" % "sbt-bundle" % "1.2.1")
```

Declaring the native packager or any of its other plugins should be sufficient. For example, in your `build.sbt` file:

```scala
lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)
```

Note that users of Play 2.4 onward can instead type:

```scala
enablePlugins(PlayScala)
```

...or `PlayJava` for Java.

For Play 2.3 that you must also additionally enable `JavaAppPackaging` for your build e.g.:

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

It is possible to produce additional configuration bundles that contain an optional `bundle.conf` the value of which override the main bundle, as
well as arbitrary shell scripts. These additional configuration files must be placed in your project's src/bundle-configuration/default folder.

The bundle-configuration folder may contain many configurations in order to support development style scenarios, the desired configuration can be specified with the setting ("default" is the default folder name):

```
BundleKeys.configurationName := "default"
```

...in build.sbt

Then, to produce this additional bundle:

```
configuration:dist
```

> Note that bundle configuration that is generally performed from within sbt is therefore part of the project to support developer use-cases. Operational use-cases where sensitive data is held in configuration is intended to be performed outside of sbt, and in conjunction with the [ConductR CLI](https://github.com/typesafehub/conductr-cli#command-line-interface-cli-for-typesafe-conductr) (specifically the `shazar` command).

### Advanced bundles and configuration

sbt-bundle is capable of producing many bundles and bundle configurations for a given sbt module.

#### Adding Java options

Suppose you need to add Java options to your start command. You'll need to do this, say, to get a Play application binding to the correct IP address and port (supposing that the endpoint is named "web"):

```scala
javaOptions in Bundle ++= Seq("-Dhttp.address=$WEB_BIND_IP", "-Dhttp.port=$WEB_BIND_PORT")
```

#### Renaming an executable

Sometimes you need to invoke something other than the script that the native packager assumes. For example, if you have a script in the bin folder named `start.sh`, and it isn't expecting any Java options:

```scala
BundleKeys.executableScriptPath in Bundle := (file((normalizedName in Bundle).value) / "bin" / "start.sh").getPath
javaOptions in Bundle := Seq.empty
```

#### Extending bundles

Suppose that you have an sbt module where there are multiple ways in which it can be produced. 
[ReactiveMaps](https://github.com/typesafehub/ReactiveMaps) is one such example where the one application can be 
deployed in three ways:

* frontend
* backend-region
* backend-summary

Its frontend configuration is expressed in the regular way i.e. within the global scope:

```scala

// Main bundle configuration

normalizedName := "reactive-maps-frontend"
BundleKeys.nrOfCpus := 2.0
...
```

Thus a regular `bundle:dist` will produce the frontend bundle. 

We can then extend the bundle configuration and overlay some new values for a different target. Here's a sample
of what the backend-region target looks like:

```scala

lazy val BackendRegion = config("backend-region").extend(Bundle)
SbtBundle.bundleSettings(BackendRegion)
inConfig(BackendRegion)(Seq(
  normalizedName := "reactive-maps-backend-region",
  BundleKeys.configurationName := (normalizedName in BackendRegion).value,
  ...
))
```

A new configuration is created that extends the regular `Bundle` one for the purposes of delegating sbt settings.
Therefore anything declared within the `inConfig` function will have precedence over that which is declared in the
`Bundle` sbt configuration. The `bundleSettings` function defines a few important settings that you need.

To produce the above bundle then becomes a matter of just `backend-region:dist`.

#### Extending bundle configurations

The optional `bundle.conf` file can either be provided directly, or be generated via sbt settings. The following shows
how to create an sbt configuration and then define `bundle.conf` settings. The settings are for a fictitious `backend`
configuration that overrides the bundle name and the roles:

```scala
lazy val Backend = config("backend").extend(BundleConfiguration)
SbtBundle.configurationSettings(Backend)
inConfig(Backend)(Seq(
  normalizedName := "reactive-maps-backend",
  roles := Set("big-backend-server")
))
```

Note the distinction between the `configurationSettings` and `bundleSettings` for bundle configurations and bundles 
respectively.

You must also associate the configuration with your project:

```
lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .configs(Backend)
```

A configuration for the above can then be generated:

```
backend:dist
```

## Settings

The following settings are provided under the `BundleKeys` object:

Name                 | Description
---------------------|-------------
bundleConf           | The bundle configuration file contents.
bundleType           | The type of configuration that this bundling relates to. By default Universal is used.
checkInitialDelay    | Initial delay before the check uris are triggered. The `FiniteDuration` value gets rounded up to full seconds. Default is 3 seconds.
checks               | Declares uris to check to signal to ConductR that the bundle components have started for situations where component doesn't do that. For example `Seq(uri("$WEB_HOST"))` will check that a endpoint named "web" will be checked given its host environment var. Once that URL becomes available then ConductR will be signalled that the bundle is ready. Note that a `docker+` prefix should be used when waiting on Docker components so that the Docker build event is waited on e.g. `Seq(uri("docker+$WEB_HOST"))`<br/>Optional params are: 'retry-count': Number of retries, 'retry-delay': Delay in seconds between retries, 'docker-timeout': Timeout in seconds for docker container start. For example: `Seq(uri("$WEB_HOST?retry-count=5&retry-delay=2"))`.
compatibilityVersion | A versioning scheme that will be included in a bundle's name that describes the level of compatibility with bundles that go before it. By default we take the major version component of a version as defined by [http://semver.org/]. However you can make this mean anything that you need it to mean in relation to bundles produced prior to it. We take the notion of a compatibility version from [http://ometer.com/parallel.html]."
configurationName    | The name of the directory of the additional configuration to use. Defaults to 'default'
diskSpace            | The amount of disk space required to host an expanded bundle and configuration. Append the letter k or K to indicate kilobytes, or m or M to indicate megabytes. Required.
endpoints            | Declares endpoints using an `Endpoint(protocol, bindPort, services)` structure. The default is `Map("web" -> Endpoint("http", services = Set(URI(s"http://:9000"))))` where the key is the `name` of this project. The "web" key is used to form a set of environment variables for your components. For example you will have a `WEB_BIND_PORT` in this example.
executableScriptPath | The relative path of the executableScript within the bundle.
memory               | The amount of memory required to run the bundle.
nrOfCpus             | The number of cpus required to run the bundle (can be fractions thereby expressing a portion of CPU). Required.
roles                | The types of node in the cluster that this bundle can be deployed to. Defaults to "web".
startCommand         | Command line args required to start the component. Paths are expressed relative to the component's bin folder. The default is to use the bash script in the bin folder. <br/> Example JVM component: </br> `BundleKeys.startCommand += "-Dhttp.address=$WEB_BIND_IP -Dhttp.port=$WEB_BIND_PORT"` </br> Example Docker component (should additional args be required): </br> `BundleKeys.startCommand += "dockerArgs -v /var/lib/postgresql/data:/var/lib/postgresql/data"` (this adds arguments to `docker run`). Note that memory heap is controlled by the memory BundleKey and heap flags should not be passed here.
system               | A logical name that can be used to associate multiple bundles with each other. This could be an application or service association and should include a version e.g. myapp-1.0.0. Defaults to the package name.
systemVersion        | A version to associate with a system. This setting defaults to the value of compatibilityVersion.

&copy; Typesafe Inc., 2014-2015
