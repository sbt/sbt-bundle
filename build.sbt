sbtPlugin := true

organization := "com.typesafe.sbt"

name := "sbt-bundle"

version := "0.1.0"

scalaVersion := "2.10.4"

addSbtPlugin("com.typesafe.sbt" %% "sbt-native-packager" % "1.0.0-M1")

publishMavenStyle := false

publishTo := {
  if (isSnapshot.value) Some(Classpaths.sbtPluginSnapshots)
  else Some(Classpaths.sbtPluginReleases)
}

scriptedSettings

scriptedLaunchOpts <+= version apply { v => s"-Dproject.version=$v" }
