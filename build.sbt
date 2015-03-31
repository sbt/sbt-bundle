import scalariform.formatter.preferences._

sbtPlugin := true

organization := "com.typesafe.sbt"
name := "sbt-bundle"

libraryDependencies += "com.typesafe.akka" %% "akka-http-core-experimental" % "1.0-M5"

scalaVersion := "2.10.4"
scalacOptions ++= List(
  "-unchecked",
  "-deprecation",
  "-language:_",
  "-target:jvm-1.6",
  "-encoding", "UTF-8"
)

scalariformSettings
ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(PreserveDanglingCloseParenthesis, true)

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.0-RC1")

releaseSettings
ReleaseKeys.versionBump := sbtrelease.Version.Bump.Minor

publishMavenStyle := false
publishTo := {
  if (isSnapshot.value) Some(Classpaths.sbtPluginSnapshots)
  else Some(Classpaths.sbtPluginReleases)
}

scriptedSettings
scriptedLaunchOpts <+= version apply { v => s"-Dproject.version=$v" }
