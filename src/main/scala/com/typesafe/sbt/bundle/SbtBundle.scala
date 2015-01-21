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

  case class Endpoint(protocol: String, bindPort: Int, servicePort: Int)

  object BundleKeys {

    val bundleConf = TaskKey[String](
      "bundle-conf",
      "The bundle configuration file contents"
    )

    val system = SettingKey[String](
      "bundle-system",
      "A logical name that can be used to associate multiple bundles with each other. This could be an application or service association and should include a version e.g. myapp-1.0.0."
    )

    val startStatusCommand = SettingKey[String](
      "bundle-start-status-command",
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

    val endpoints = SettingKey[Map[String, Endpoint]](
      "bundle-endpoints",
      """Declares endpoints. The default is Map("web" -> Endpoint("http", 0, 9000))"""
    )
  }

  val Bundle = config("bundle") extend Universal
}

object SbtBundle extends AutoPlugin {

  import Import._
  import BundleKeys._
  import SbtNativePackager.autoImport._

  val autoImport = Import

  private final val Sha256 = "SHA-256"

  private val utf8 = Charset.forName("utf-8")

  override def `requires` = SbtNativePackager

  override def trigger = AllRequirements

  override def projectSettings = Seq(
    bundleConf := getConfig.value,
    system := (packageName in Universal).value,
    startStatusCommand := "exit 0",
    bundleType := Universal,
    startCommand := Seq((file("bin") / (executableScriptName in Universal).value).getPath),
    endpoints := Map("web" -> Endpoint("http", 0, 9000)),
    NativePackagerKeys.dist in Bundle := Def.taskDyn {
      Def.task {
        createDist(bundleType.value)
      }.value
    }.value,
    NativePackagerKeys.stage in Bundle := Def.taskDyn {
      Def.task {
        stageBundle(bundleType.value)
      }.value
    }.value,
    NativePackagerKeys.stagingDirectory in Bundle := (target in Bundle).value / "stage",
    target in Bundle := target.value / "typesafe-conductr"
  )

  private def createDist(bundleTypeConfig: Configuration): Def.Initialize[Task[File]] = Def.task {
    val bundleTarget = (target in Bundle).value
    val configTarget = bundleTarget / "tmp"
    def relParent(p: (File, String)): (File, String) =
      (p._1, (packageName in Universal).value + java.io.File.separator + p._2)
    val configFile = writeConfig(configTarget, bundleConf.value)
    val bundleMappings =
      configFile.pair(relativeTo(configTarget)) ++ (mappings in bundleTypeConfig).value.map(relParent)
    val archive = Archives.makeZip(bundleTarget, (packageName in Universal).value, bundleMappings)
    val archiveName = archive.getName
    val exti = archiveName.lastIndexOf('.')
    val hash = Hash.toHex(digestFile(archive))
    val hashName = archiveName.take(exti) + "-" + hash + archiveName.drop(exti)
    val hashArchive = archive.getParentFile / hashName
    IO.move(archive, hashArchive)
    hashArchive
  }

  private def digestFile(f: File): Array[Byte] = {
    val digest = MessageDigest.getInstance(Sha256)
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

  private def formatSeq(strings: Seq[String]): String =
    strings.map(s => s""""$s"""").mkString("[", ", ", "]")

  private def formatEndpoints(name: String, endpoints: Map[String, Endpoint]): String = {
    val formatted =
      for {
        (label, Endpoint(protocol, bindPort, servicePort)) <- endpoints
      } yield s"""|      "$label" = {
                  |        protocol     = "$protocol"
                  |        bind-port    = $bindPort
                  |        service-port = $servicePort
                  |        service-name = "/$name"
                  |      }""".stripMargin
    formatted.mkString(f"{%n", f",%n", f"%n    }")
  }

  private def getConfig: Def.Initialize[Task[String]] = Def.task {
    s"""|version              = "1.0.0"
        |system               = "${system.value}"
        |start-status-command = "${startStatusCommand.value}"
        |components = {
        |  "${(packageName in Universal).value}" = {
        |    description      = "${projectInfo.value.description}"
        |    file-system-type = "${bundleType.value}"
        |    start-command    = ${formatSeq(startCommand.value)}
        |    endpoints        = ${formatEndpoints(name.value, endpoints.value)}
        |  }
        |}
        |""".stripMargin
  }

  private def stageBundle(bundleTypeConfig: Configuration): Def.Initialize[Task[File]] = Def.task {
    val bundleTarget = (NativePackagerKeys.stagingDirectory in Bundle).value
    writeConfig(bundleTarget, bundleConf.value)
    val componentTarget = bundleTarget / (packageName in Universal).value
    IO.copy((mappings in bundleTypeConfig).value.map(p => (p._1, componentTarget / p._2)))
    componentTarget
  }

  private def writeConfig(target: File, contents: String): File = {
    val configFile = target / "bundle.conf"
    IO.write(configFile, contents, utf8)
    configFile
  }
}
