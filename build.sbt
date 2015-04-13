import scalariform.formatter.preferences._
import bintray.Keys._

sbtPlugin := true

organization := "com.typesafe.sbt"
name := "sbt-bundle"

scalaVersion := "2.10.4"
scalacOptions ++= List(
  "-unchecked",
  "-deprecation",
  "-language:_",
  "-target:jvm-1.7",
  "-encoding", "UTF-8"
)

scalariformSettings
ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(PreserveDanglingCloseParenthesis, true)

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.0")

releaseSettings
ReleaseKeys.versionBump := sbtrelease.Version.Bump.Minor

licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))
publishMavenStyle := false
bintrayPublishSettings
repository in bintray := "sbt-plugins"
bintrayOrganization in bintray := Some("sbt-bundle")

scriptedSettings
scriptedLaunchOpts <+= version apply { v => s"-Dproject.version=$v" }
