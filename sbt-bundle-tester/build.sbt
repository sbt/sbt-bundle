lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

name := "simple-test"

version := "0.1.0-SNAPSHOT"

BundleKeys.nrOfCpus := 1.0
BundleKeys.memory := "64m"
BundleKeys.diskSpace := "10m"
BundleKeys.roles := Set("web-server")