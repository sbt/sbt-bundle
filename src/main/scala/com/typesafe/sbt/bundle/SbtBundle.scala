package com.typesafe.sbt.bundle

import java.io.{ FileInputStream, BufferedInputStream }
import java.nio.charset.Charset
import java.security.MessageDigest

import com.typesafe.sbt.SbtNativePackager
import com.typesafe.sbt.packager.universal.Archives
import sbt._
import sbt.Keys._
import SbtNativePackager.Universal

import scala.annotation.tailrec

object Import {

  object BundleKeys {

    val bundleConf = TaskKey[String](
      "bundle-conf",
      "The bundle configuration file contents"
    )

    val system = SettingKey[String](
      "bundle-system",
      "A logical name that can be used to associate multiple bundles with each other. This could be an application or service association and should include a version e.g. myapp-1.0.0."
    )

    val statusCommand = SettingKey[String](
      "bundle-status-command",
      "A command to be executed to check the start status; by default `exit 0` is used"
    )

    val bundleType = SettingKey[Configuration](
      "bundle-type",
      "The type of configuration that this bundling relates to. By default Universal is used."
    )

    val startCommand = SettingKey[Seq[String]](
      "bundle-start-command",
      "Command line args required to start the component. Paths are expressed relative to the component's bin folder. The default is to use the bash script in the bin folder."
    )

    val endpoints = SettingKey[Map[String, (String, String)]](
      "bundle-endpoints",
      """Provides a port mapping between an external facing endpoint and an internal one. The default is Map("web" -> ("http://0.0.0.0:9000" -> "http://0.0.0.0:9000"))"""
    )
  }

  val ReactiveRuntime = config("rr") extend Universal
}

object SbtBundle extends AutoPlugin {

  import Import._
  import BundleKeys._
  import SbtNativePackager.autoImport._

  val autoImport = Import

  private val sha256 = "SHA-256"

  private val utf8 = "UTF-8"

  private val utf8Charset = Charset.forName(utf8)

  override def `requires` = SbtNativePackager

  override def trigger = AllRequirements

  override def projectSettings = Seq(
    bundleConf := getConfig.value,
    system := (packageName in Universal).value,
    statusCommand := "exit 0",
    bundleType := Universal,
    startCommand := Seq((file("bin") / (executableScriptName in Universal).value).getPath),
    endpoints := Map("web" -> ("http://0.0.0.0:9000" -> "http://0.0.0.0:9000")),
    NativePackagerKeys.dist in ReactiveRuntime := Def.taskDyn {
      Def.task {
        createDist(bundleType.value)
      }.value
    }.value,
    NativePackagerKeys.stage in ReactiveRuntime := Def.taskDyn {
      Def.task {
        stageBundle(bundleType.value)
      }.value
    }.value,
    NativePackagerKeys.stagingDirectory in ReactiveRuntime := (target in ReactiveRuntime).value / "stage",
    target in ReactiveRuntime := target.value / "reactive-runtime"
  )

  private def createDist(bundleTypeConfig: Configuration): Def.Initialize[Task[File]] = Def.task {
    val bundleTarget = (target in ReactiveRuntime).value
    val configTarget = bundleTarget / "tmp"
    def relParent(p: (File, String)): (File, String) =
      (p._1, (packageName in Universal).value + java.io.File.separator + p._2)
    val configFile = writeConfig(configTarget, bundleConf.value)
    val bundleMappings =
      configFile.pair(relativeTo(configTarget)) ++ (mappings in bundleTypeConfig).value.map(relParent)
    val tgz = Archives.makeTgz(bundleTarget, (packageName in Universal).value, bundleMappings)
    val tgzName = tgz.getName
    val exti = tgzName.lastIndexOf('.')
    val hash = Hash.toHex(digestFile(tgz))
    val hashName = tgzName.take(exti) + "-" + hash + tgzName.drop(exti)
    val hashTgz = tgz.getParentFile / hashName
    IO.move(tgz, hashTgz)
    hashTgz
  }

  private def digestFile(f: File): Array[Byte] = {
    val digest = MessageDigest.getInstance(sha256)
    val in = new BufferedInputStream(new FileInputStream(f))
    val buf = Array.ofDim[Byte](8192)
    try {
      @tailrec
      def readAndUpdate(r: Int): Unit =
        if (r != -1) {
          digest.update(buf, 0, r)
          readAndUpdate(in.read(buf))
        }
      readAndUpdate(in.read(buf))
      digest.digest
    } finally {
      in.close()
    }
  }

  private def format(strings: Seq[String]): String =
    strings.map(quote).mkString("[ ", ", ", " ]")

  private def formatEndpoints(endpoints: Map[String, (String, String)]): String = {
    val formatted =
      for {
        (label, (from, to)) <- endpoints
        quotedLabel = quote(label)
        fromTo = format(Seq(from, to))
      } yield s"$quotedLabel = $fromTo"
    formatted.mkString("{ ", ", ", " }")
  }

  private def quote(s: String): String =
    "\"" + s + "\""

  private def getConfig: Def.Initialize[Task[String]] = Def.task {
    s"""|version        = "1.0.0"
        |system         = "${system.value}"
        |status-command = "${statusCommand.value}"
        |components = {
        |  "${(packageName in Universal).value}" = {
        |    description      = "${projectInfo.value.description}"
        |    file-system-type = "${bundleType.value}"
        |    start-command    = ${format(startCommand.value)}
        |    endpoints        = ${formatEndpoints(endpoints.value)}
        |  }
        |}
        |""".stripMargin
  }

  private def stageBundle(bundleTypeConfig: Configuration): Def.Initialize[Task[File]] = Def.task {
    val bundleTarget = (NativePackagerKeys.stagingDirectory in ReactiveRuntime).value
    writeConfig(bundleTarget, bundleConf.value)
    val componentTarget = bundleTarget / (packageName in Universal).value
    IO.copy((mappings in bundleTypeConfig).value.map(p => (p._1, componentTarget / p._2)))
    componentTarget
  }

  private def writeConfig(target: File, contents: String): File = {
    val configFile = target / "bundle.conf"
    IO.write(configFile, contents, utf8Charset)
    configFile
  }
}
