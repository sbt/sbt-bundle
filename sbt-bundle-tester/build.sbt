import ByteConversions._

name := "simple-test"

version := "0.1.0-SNAPSHOT"

normalizedName in Bundle := "simple-test-frontend"

BundleKeys.nrOfCpus := 1.0
BundleKeys.memory := 64.MiB
BundleKeys.diskSpace := 10.MB
BundleKeys.roles := Set("web-server")

BundleKeys.configurationName := "frontend"

lazy val BackendRegion = config("backend-region").extend(Bundle)
SbtBundle.bundleSettings(BackendRegion)
inConfig(BackendRegion)(Seq(
  normalizedName := "reactive-maps-backend-region"
))

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .configs(BackendRegion)
