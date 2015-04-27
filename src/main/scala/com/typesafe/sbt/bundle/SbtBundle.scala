package com.typesafe.sbt.bundle

import com.typesafe.sbt.SbtNativePackager
import com.typesafe.sbt.packager.universal.Archives
import java.io.{ FileInputStream, BufferedInputStream }
import java.nio.charset.Charset
import java.security.MessageDigest
import sbt._
import sbt.Keys._
import SbtNativePackager.Universal

import scala.annotation.tailrec

object Import {

  /**
   * Represents a service endpoint.
   * @param bindProtocol the protocol to bind for this endpoint, e.g. "http"
   * @param bindPort the port the bundle component’s application or service actually binds to; when this is 0 it will be dynamically allocated (which is the default)
   * @param services the public-facing ways to access the endpoint form the outside world with protocol, port, and/or path
   */
  case class Endpoint(bindProtocol: String, bindPort: Int = 0, services: Set[URI] = Set.empty)

  object BundleKeys {

    // Scheduling settings

    val system = SettingKey[String](
      "bundle-system",
      "A logical name that can be used to associate multiple bundles with each other."
    )

    val nrOfCpus = SettingKey[Double](
      "bundle-nr-of-cpus",
      "The number of cpus required to run the bundle (can be fractions thereby expressing a portion of CPU). Required."
    )

    val memory = SettingKey[Bytes](
      "bundle-memory",
      "The amount of memory required to run the bundle. This value must a multiple of 1024 greater than 2 MB. Append the letter k or K to indicate kilobytes, or m or M to indicate megabytes. Required."
    )

    val diskSpace = SettingKey[Bytes](
      "bundle-disk-space",
      "The amount of disk space required to host an expanded bundle and configuration. Append the letter k or K to indicate kilobytes, or m or M to indicate megabytes. Required."
    )

    val roles = SettingKey[Set[String]](
      "bundle-roles",
      "The types of node in the cluster that this bundle can be deployed to. Defaults to having no specific roles."
    )

    val configurationPath = SettingKey[String](
      "configuration-path",
      "The Location of the additional configuration to use"
    )

    // General settings

    val bundleConf = TaskKey[String](
      "bundle-conf",
      "The bundle configuration file contents"
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
      """Declares endpoints. The default is Map("web" -> Endpoint("http", 0, Set("http://:9000"))) where the service name is the name of this project. The "web" key is used to form a set of environment variables for your components. For example you will have a `WEB_BIND_PORT` in this example."""
    )

    val checks = SettingKey[Seq[URI]](
      "bundle-check-uris",
      """Declares uris to check to signal to ConductR that the bundle components have started for situations where component doesn't do that. For example Seq("$WEB_HOST") will check that a endpoint named "web" will be checked given its host environment var. Once that URL becomes available then ConductR will be signalled that the bundle is ready."""
    )
  }

  case class Bytes(underlying: Long) extends AnyVal {
    def round1k: Bytes =
      Bytes((Math.max(underlying - 1, 0) >> 10 << 10) + 1024)
  }

  object ByteConversions {
    implicit class IntOps(value: Int) {
      def KB: Bytes =
        Bytes(value * 1000L)
      def MB: Bytes =
        Bytes(value * 1000000L)
      def GB: Bytes =
        Bytes(value * 1000000000L)
      def TB: Bytes =
        Bytes(value * 1000000000000L)
      def KiB: Bytes =
        Bytes(value.toLong << 10)
      def MiB: Bytes =
        Bytes(value.toLong << 20)
      def GiB: Bytes =
        Bytes(value.toLong << 30)
      def TiB: Bytes =
        Bytes(value.toLong << 40)
    }
  }

  object URI {
    def apply(uri: String): URI =
      new sbt.URI(uri)
  }

  val configurationName = SettingKey[String](
    "configuration-name",
    "The name of the directory of the additional configuration to use. Defaults to 'default'"
  )

  val Bundle = config("bundle") extend Universal

  val BundleConfiguration = config("config") extend Universal
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
    system := (packageName in Universal).value,
    roles := Set.empty,

    bundleConf := getConfig.value,
    bundleType := Universal,
    startCommand := Seq(
      (file((packageName in Universal).value) / "bin" / (executableScriptName in Universal).value).getPath,
      s"-J-Xms${memory.value.round1k.underlying}",
      s"-J-Xmx${memory.value.round1k.underlying}"
    ),
    endpoints := Map("web" -> Endpoint("http", 0, Set(URI("http://:9000")))),
    checks := Seq.empty,
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
    NativePackagerKeys.dist in BundleConfiguration := Def.taskDyn {
      Def.task {
        createConfiguration()
      }.value
    }.value,
    NativePackagerKeys.stage in BundleConfiguration := Def.taskDyn {
      Def.task {
        stageConfiguration
      }.value
    }.value,
    NativePackagerKeys.stagingDirectory in Bundle := (target in Bundle).value / "stage",
    NativePackagerKeys.stagingDirectory in BundleConfiguration := (target in BundleConfiguration).value / "stage",
    target in Bundle := target.value / "bundle",
    target in BundleConfiguration := target.value / "bundle-configuration",
    sourceDirectory in BundleConfiguration := sourceDirectory.value / "main" / "bundle-configuration",
    configurationName := "default "
  )

  private def createDist(bundleTypeConfig: Configuration): Def.Initialize[Task[File]] = Def.task {
    val bundleTarget = (target in Bundle).value
    val configTarget = bundleTarget / "tmp"
    def relParent(p: (File, String)): (File, String) =
      (p._1, (packageName in Universal).value + java.io.File.separator + p._2)
    val configFile = writeConfig(configTarget, bundleConf.value)
    val bundleMappings =
      configFile.pair(relativeTo(configTarget)) ++ (mappings in bundleTypeConfig).value.map(relParent)
    shazar(bundleTarget, (packageName in Universal).value,
      bundleMappings, streams.value.log, f => s"Bundle has been created: $f")
  }

  private def createConfiguration(): Def.Initialize[Task[File]] = Def.task {
    val bundleTarget = (target in BundleConfiguration).value
    val configurationTarget = (NativePackagerKeys.stage in BundleConfiguration).value
    def relParent(p: (File, String)): (File, String) =
      (p._1, configurationName.value + java.io.File.separator + p._2)
    val configChildren: List[File] = configurationTarget.listFiles().toList
    val bundleMappings: Seq[(File, String)] = configChildren.flatMap(_.pair(relativeTo(configurationTarget)))
    shazar(bundleTarget, configurationName.value,
      bundleMappings, streams.value.log, f => s"Bundle-Configuration has been created: $f")
  }

  private def shazar(bundleTarget: File,
    archiveName: String,
    bundleMappings: Seq[(File, String)],
    logger: Logger,
    message: File => String): File = {
    val archived = Archives.makeZip(bundleTarget, archiveName, bundleMappings, Some(archiveName))
    val exti = archived.name.lastIndexOf('.')
    val hash = Hash.toHex(digestFile(archived))
    val hashName = archived.name.take(exti) + "-" + hash + archived.name.drop(exti)
    val hashArchive = archived.getParentFile / hashName
    IO.move(archived, hashArchive)
    logger.info(message(hashArchive))
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

  private def formatSeq(strings: Iterable[String]): String =
    strings.map(s => s""""$s"""").mkString("[", ", ", "]")

  private def formatEndpoints(endpoints: Map[String, Endpoint]): String = {
    val formatted =
      for {
        (label, Endpoint(bindProtocol, bindPort, services)) <- endpoints
      } yield s"""|      "$label" = {
                  |        bind-protocol  = "$bindProtocol"
                  |        bind-port = $bindPort
                  |        services  = ${formatSeq(services.map(_.toString))}
                  |      }""".stripMargin
    formatted.mkString(f"{%n", f",%n", f"%n    }")
  }

  private def getConfig: Def.Initialize[Task[String]] = Def.task {
    val checkComponents = if (checks.value.nonEmpty)
      checks.value.map(uri => s""""$uri"""").mkString(
        s""",
           |  "${(packageName in Universal).value}-status" = {
           |    description      = "Status check for the bundle component"
           |    file-system-type = "universal"
           |    start-command    = ["check", """.stripMargin,
        ", ",
        s"""]
           |    endpoints        = {}
           |  }""".stripMargin)
    else
      ""
    s"""|version    = "1.0.0"
        |name       = "${name.value}"
        |system     = "${system.value}"
        |nrOfCpus   = ${nrOfCpus.value}
        |memory     = ${memory.value.underlying}
        |diskSpace  = ${diskSpace.value.underlying}
        |roles      = ${formatSeq(roles.value)}
        |components = {
        |  "${(packageName in Universal).value}" = {
        |    description      = "${projectInfo.value.description}"
        |    file-system-type = "${bundleType.value}"
        |    start-command    = ${formatSeq(startCommand.value)}
        |    endpoints        = ${formatEndpoints(endpoints.value)}
        |  }$checkComponents
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

  private def stageConfiguration(): Def.Initialize[Task[File]] = Def.task {
    val configurationTarget = (NativePackagerKeys.stagingDirectory in BundleConfiguration).value / configurationName.value
    val srcDir = new File((sourceDirectory in BundleConfiguration).value + java.io.File.separator + configurationName.value)
    if (!srcDir.exists()) sys.error(
      s"""Directory $srcDir does not exist.
         | Specify the desired configuration directory in ${sourceDirectory in BundleConfiguration}
         |  with the 'configurationName' setting""".stripMargin)
    IO.createDirectory(configurationTarget)
    IO.copyDirectory(srcDir, configurationTarget, true, true)
    configurationTarget
  }

  private def writeConfig(target: File, contents: String): File = {
    val configFile = target / "bundle.conf"
    IO.write(configFile, contents, utf8)
    configFile
  }
}
