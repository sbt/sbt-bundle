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
import scala.concurrent.duration._
import scala.util.matching.Regex

object Import {

  /**
    * Common interface which describes request mapping.
    */
  trait RequestMapping

  /**
    * Common interface which describes HTTP based request mapping
    */
  trait HttpRequestMapping extends RequestMapping {
    def method: Option[String]
    def rewrite: Option[String]
  }

  /**
    * Common interface which describes request mapping(s) that can be exposed by a service endpoint, i.e. sequence of
    * HTTP paths in case of HTTP-based endpoint, or set of ports in case of TCP-based endpoint
    */
  trait ProtocolFamilyRequestMapping {
    def protocolFamily: String
  }

  object Http {
    /**
      * Represents HTTP methods
      */
    object Method extends Enumeration {
      val GET, POST, PUT, DELETE, HEAD, TRACE, CONNECT = Value
    }

    implicit def method(m: String): Method.Value =
      Method.withName(m)

    case class Request(method: Option[Method.Value], path: Either[String, Regex], rewrite: Option[String])

    implicit def request1(r: String): Request =
      Request(None, Left(r), None)
    implicit def regexRequest1(r: Regex): Request =
      Request(None, Right(r), None)

    implicit def request2(r: (String, String)): Request =
      Request(Some(r._1), Left(r._2), None)
    implicit def regexRequest2(r: (String, Regex)): Request =
      Request(Some(r._1), Right(r._2), None)

    implicit def request3(r: ((String, String), String)): Request =
      Request(Some(r._1._1), Left(r._1._2), Some(r._2))
    implicit def regexRequest3(r: ((String, Regex), String)): Request =
      Request(Some(r._1._1), Right(r._1._2), Some(r._2))
  }

  /**
    * Represents HTTP request mapping, i.e. sequence of HTTP path(s) exposed by a service endpoint
    *
    * @param requestMappings sequence of HTTP path(s) exposed by a service endpoint
    */
  case class Http(requestMappings: Http.Request*) extends ProtocolFamilyRequestMapping {
    val protocolFamily = "http"
  }

  object Tcp {
    /**
      * Represents TCP port exposed by a service endpoint
 *
      * @param port tcp port exposed by the service endpoint
      */
    case class Request(port: Int) extends RequestMapping

    /**
      * Represents TCP request mapping, i.e. set of TCP port(s) exposed by a service endpoint
 *
      * @param ports set of TCP port(s) exposed by a service endpoint
      */
    def apply(ports: Int*): Tcp =
      Tcp(ports.toSet.map(Tcp.Request))
  }

  /**
    * Represents TCP request mapping, i.e. set of TCP port(s) exposed by a service endpoint
 *
    * @param requestMappings set of TCP port(s) exposed by a service endpoint
    */
  case class Tcp(requestMappings: Set[Tcp.Request]) extends ProtocolFamilyRequestMapping {
    val protocolFamily = "tcp"
  }

  object Udp {
    /**
      * Represents UDP port exposed by a service endpoint
      *
      * @param port udp port exposed by the service endpoint
      */
    case class Request(port: Int) extends RequestMapping

    /**
      * Represents UDP request mapping, i.e. set of UDP port(s) exposed by a service endpoint
 *
      * @param ports set of UDP port(s) exposed by a service endpoint
      */
    def apply(ports: Int*): Udp =
      Udp(ports.toSet.map(Udp.Request))
  }

  /**
    * Represents UDP request mapping, i.e. set of UDP port(s) exposed by a service endpoint
 *
    * @param requestMappings set of UDP port(s) exposed by a service endpoint
    */
  case class Udp(requestMappings: Set[Udp.Request]) extends ProtocolFamilyRequestMapping {
    val protocolFamily = "udp"
  }

  object RequestAcl {
    /**
      * Represents a set of request ACL exposed by a service endpoint.
      * Request ACL can be either a sequence of HTTP paths or set of TCP ports.
 *
      * @param protocolFamilyRequestMappings request ACL exposed by a service endpoint
      */
    def apply(protocolFamilyRequestMappings: ProtocolFamilyRequestMapping*): RequestAcl =
      RequestAcl(protocolFamilyRequestMappings.toSet)
  }

  /**
    * Represents a set of request ACL exposed by a service endpoint.
    * Request ACL can be either a sequence of HTTP paths or set of TCP ports.
 *
    * @param protocolFamilyRequestMappings request ACL exposed by a service endpoint
    */
  case class RequestAcl(protocolFamilyRequestMappings: Set[ProtocolFamilyRequestMapping])

  object Endpoint {
    /**
     * Represents a service endpoint.
 *
     * @param bindProtocol the protocol to bind for this endpoint, e.g. "http"
     * @param bindPort the port the bundle component’s application or service actually binds to; when this is 0 it will be dynamically allocated (which is the default)
     * @param services the public-facing ways to access the endpoint form the outside world with protocol, port, and/or path
     */
    def apply(bindProtocol: String, bindPort: Int = 0, services: Set[URI] = Set.empty): Endpoint =
      new Endpoint(bindProtocol, bindPort, Some(services), None, None)

    /**
      * Represents a service endpoint.
 *
      * @param bindProtocol the protocol to bind for this endpoint, e.g. "http"
      * @param bindPort the port the bundle component’s application or service actually binds to; when this is 0 it will be dynamically allocated (which is the default)
      * @param serviceName the name of the service exposed by this service endpoint
      * @param acls list of protocol and its corresponding paths (for http) or ports (for tcp) exposed by the service endpoint
      */
    def apply(bindProtocol: String, bindPort: Int, serviceName: String, acls: RequestAcl*): Endpoint =
      new Endpoint(bindProtocol, bindPort, None, Some(serviceName), Some(acls.toSet))
  }

  /**
   * Represents a service endpoint.
   *
   * @param bindProtocol the protocol to bind for this endpoint, e.g. "http"
   * @param bindPort the port the bundle component’s application or service actually binds to; when this is 0 it will be dynamically allocated (which is the default)
   * @param services **deprecated** - the public-facing ways to access the endpoint form the outside world with protocol, port, and/or path
   * @param serviceName the name of the service exposed by this service endpoint
   * @param acls list of protocol and its corresponding paths (for http) or ports (for tcp) exposed by the service endpoint
   */
  case class Endpoint(
    bindProtocol: String,
    bindPort: Int,
    services: Option[Set[URI]],
    serviceName: Option[String],
    acls: Option[Set[RequestAcl]])

  object BundleConfVersions extends Enumeration {
    val V_1_1_0 = Value("1.1.0")
    val V_1_2_0 = Value("1.2.0")
  }

  object BundleKeys {

    // Scheduling settings
    val bundleConfVersion = SettingKey[BundleConfVersions.Value](
      "bundle-conf-version",
      "The format of the bundle configuration file to generate. By default this is 1.1.0."
    )

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
      """The types of node in the cluster that this bundle can be deployed to. Defaults to "web"."""
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

    val executableScriptPath = SettingKey[String](
      "bundle-executable-script-path",
      "The relative path of the executableScript within the bundle."
    )

    val startCommand = TaskKey[Seq[String]](
      "bundle-start-command",
      "Command line args required to start the component. Paths are expressed relative to the component's bin folder. The default is to use the bash script in the bin folder."
    )

    val endpoints = SettingKey[Map[String, Endpoint]](
      "bundle-endpoints",
      """Declares endpoints. The default is Map("web" -> Endpoint("http", 0, Set("http://:9000"))) where the service name is the name of this project. The "web" key is used to form a set of environment variables for your components. For example you will have a `WEB_BIND_PORT` in this example."""
    )

    val overrideEndpoints = TaskKey[Option[Map[String, Endpoint]]](
      "bundle-override-endpoints",
      "Overrides the endpoints settings key with new endpoints. This task should be used if the endpoints need to be specified programmatically. The default is None."
    )

    val checkInitialDelay = SettingKey[FiniteDuration](
      "bundle-check-initial-delay",
      "Initial delay before the check uris are triggered. The 'FiniteDuration' value gets rounded up to full seconds. Default is 3 seconds."
    )

    val checks = SettingKey[Seq[URI]](
      "bundle-check-uris",
      """Declares uris to check to signal to ConductR that the bundle components have started for situations where component doesn't do that. For example Seq(uri("$WEB_HOST?retry-count=5&retry-delay=2")) will check that a endpoint named "web" will be checked given its host environment var. Once that URL becomes available then ConductR will be signalled that the bundle is ready. Optional params are: 'retry-count': Number of retries, 'retry-delay': Delay in seconds between retries, 'docker-timeout': Timeout in seconds for docker container start."""
    )

    val configurationName = SettingKey[String](
      "bundle-configuration-name",
      "The name of the directory of the additional configuration to use. Defaults to 'default'"
    )

    val compatibilityVersion = SettingKey[String](
      "bundle-compatibility-version",
      "A versioning scheme that will be included in a bundle's name that describes the level of compatibility with bundles that go before it. By default we take the major version component of a version as defined by http://semver.org/. However you can make this mean anything that you need it to mean in relation to bundles produced prior to it. We take the notion of a compatibility version from http://ometer.com/parallel.html."
    )

    val systemVersion = SettingKey[String](
      "bundle-system-version",
      "A version to associate with a system. This setting defaults to the value of compatibilityVersion."
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

  val Bundle = config("bundle") extend Universal

  val BundleConfiguration = config("configuration") extend Universal

  val DefaultEndpoints = Map("web" -> Endpoint("http", 0, Set(URI("http://:9000"))))
}

object SbtBundle extends AutoPlugin {

  import Import._
  import BundleKeys._
  import SbtNativePackager.autoImport._

  val autoImport = Import

  private final val Sha256 = "SHA-256"
  private final val Utf8 = Charset.forName("utf-8")

  override def requires = SbtNativePackager

  override def trigger = AllRequirements

  override def projectSettings =
    bundleSettings(Bundle) ++ configurationSettings(BundleConfiguration) ++
      Seq(
        bundleConfVersion := BundleConfVersions.V_1_1_0,
        bundleType := Universal,
        checkInitialDelay := 3.seconds,
        checks := Seq.empty,
        compatibilityVersion := (version in Bundle).value.takeWhile(_ != '.'),
        configurationName := "default",
        endpoints := DefaultEndpoints,
        javaOptions in Bundle ++= Seq(
          s"-J-Xms${(memory in Bundle).value.round1k.underlying}",
          s"-J-Xmx${(memory in Bundle).value.round1k.underlying}"
        ),
        projectTarget := target.value,
        roles := Set("web"),
        system := (normalizedName in Bundle).value,
        systemVersion := (compatibilityVersion in Bundle).value,
        startCommand := Seq((executableScriptPath in Bundle).value) ++ (javaOptions in Bundle).value
      )

  override def projectConfigurations: Seq[Configuration] =
    Seq(
      Bundle,
      BundleConfiguration,
      Universal // `Universal` is added here due to this issue: (https://github.com/sbt/sbt-native-packager/issues/676
    )

  /**
   * Build out the bundle settings for a given sbt configuration.
   */
  def bundleSettings(config: Configuration): Seq[Setting[_]] =
    inConfig(config)(Seq(
      bundleConf := getConfig(config, forAllSettings = true).value,
      executableScriptPath := (file((normalizedName in config).value) / "bin" / (executableScriptName in config).value).getPath,
      NativePackagerKeys.packageName := (normalizedName in config).value + "-v" + (compatibilityVersion in config).value,
      NativePackagerKeys.dist := Def.taskDyn {
        Def.task {
          createDist(config, (bundleType in config).value)
        }.value
      }.value,
      NativePackagerKeys.stage := Def.taskDyn {
        Def.task {
          stageBundle(config, (bundleType in config).value)
        }.value
      }.value,
      NativePackagerKeys.stagingDirectory := (target in config).value / "stage",
      target := projectTarget.value / "bundle"
    )) ++ configNameSettings(config)

  /**
   * Build out the bundle configuration settings for a given sbt configuration.
   */
  def configurationSettings(config: Configuration): Seq[Setting[_]] =
    inConfig(config)(Seq(
      bundleConf := getConfig(config, forAllSettings = false).value,
      checks := Seq.empty,
      compatibilityVersion := (version in config).value.takeWhile(_ != '.'),
      executableScriptPath := (file((normalizedName in config).value) / "bin" / (executableScriptName in config).value).getPath,
      NativePackagerKeys.dist := Def.taskDyn {
        Def.task {
          createConfiguration(config, "Bundle configuration has been created")
        }.value
      }.value,
      NativePackagerKeys.stage := Def.taskDyn {
        Def.task {
          stageConfiguration(config)
        }.value
      }.value,
      NativePackagerKeys.stagingDirectory := (target in config).value / "stage",
      target := projectTarget.value / "bundle-configuration",
      sourceDirectory := (sourceDirectory in config).value.getParentFile / "bundle-configuration"
    )) ++ configNameSettings(config)

  private val projectTarget = settingKey[File]("")

  private val bundleTypeConfigName = taskKey[(Configuration, Option[String])]("")
  private val diskSpaceConfigName = taskKey[(Bytes, Option[String])]("")
  private val endpointsConfigName = taskKey[(Map[String, Endpoint], Option[String])]("")
  private val memoryConfigName = taskKey[(Bytes, Option[String])]("")
  private val projectInfoConfigName = taskKey[(ModuleInfo, Option[String])]("")
  private val rolesConfigName = taskKey[(Set[String], Option[String])]("")
  private val startCommandConfigName = taskKey[(Seq[String], Option[String])]("")
  private val checksConfigName = taskKey[(Seq[URI], Option[String])]("")
  private val compatibilityVersionConfigName = taskKey[(String, Option[String])]("")
  private val normalizedNameConfigName = taskKey[(String, Option[String])]("")
  private val nrOfCpusConfigName = taskKey[(Double, Option[String])]("")
  private val systemConfigName = taskKey[(String, Option[String])]("")
  private val systemVersionConfigName = taskKey[(String, Option[String])]("")

  private def configNameSettings(config: Configuration): Seq[Setting[_]] =
    inConfig(config)(Seq(
      overrideEndpoints := None,
      bundleTypeConfigName := (bundleType in config).value -> toConfigName(bundleType in (thisProjectRef.value, config), state.value),
      diskSpaceConfigName := (diskSpace in config).value -> toConfigName(diskSpace in (thisProjectRef.value, config), state.value),
      endpointsConfigName := collectEndpoints(config).value -> toConfigName(endpoints in (thisProjectRef.value, config), state.value),
      memoryConfigName := (memory in config).value -> toConfigName(memory in (thisProjectRef.value, config), state.value),
      projectInfoConfigName := (projectInfo in config).value -> toConfigName(projectInfo in (thisProjectRef.value, config), state.value),
      rolesConfigName := (roles in config).value -> toConfigName(roles in (thisProjectRef.value, config), state.value),
      startCommandConfigName := (startCommand in config).value -> toConfigName(startCommand in (thisProjectRef.value, config), state.value),
      checksConfigName := (checks in config).value -> toConfigName(checks in (thisProjectRef.value, config), state.value),
      compatibilityVersionConfigName := (compatibilityVersion in config).value -> toConfigName(compatibilityVersion in (thisProjectRef.value, config), state.value),
      normalizedNameConfigName := (normalizedName in config).value -> toConfigName(normalizedName in (thisProjectRef.value, config), state.value),
      nrOfCpusConfigName := (nrOfCpus in config).value -> toConfigName(nrOfCpus in (thisProjectRef.value, config), state.value),
      systemConfigName := (system in config).value -> toConfigName(system in (thisProjectRef.value, config), state.value),
      systemVersionConfigName := (systemVersion in config).value -> toConfigName(systemVersion in (thisProjectRef.value, config), state.value)
    ))

  private def toConfigName(scoped: Scoped, state: State): Option[String] = {
    val extracted = Project.extract(state)
    extracted.structure.data.definingScope(scoped.scope, scoped.key).flatMap(_.config.toOption.map(_.name))
  }

  private def createDist(config: Configuration, bundleTypeConfig: Configuration): Def.Initialize[Task[File]] = Def.task {
    val bundleTarget = (target in config).value
    val configTarget = bundleTarget / config.name / "tmp"
    def relParent(p: (File, String)): (File, String) =
      (p._1, (normalizedName in config).value + java.io.File.separator + p._2)
    val configFile = writeConfig(configTarget, (bundleConf in config).value)
    val bundleMappings =
      configFile.pair(relativeTo(configTarget)) ++ (mappings in bundleTypeConfig).value.map(relParent)
    shazar(bundleTarget,
      (packageName in config).value,
      bundleMappings,
      f => streams.value.log.info(s"Bundle has been created: $f"))
  }

  /**
    * Creates a bundle configuration in the specified config target directory
    * @param config under which the bundle configuration is created
    * @param message that is printed if the bundle configuration has been successfully created.
    * @return the created bundle configuration file
    */
  def createConfiguration(config: Configuration, message: String): Def.Initialize[Task[File]] = Def.task {
    val bundleTarget = (target in config).value
    val configurationTarget = (NativePackagerKeys.stage in config).value
    val configChildren = recursiveListFiles(Array(configurationTarget), NonDirectoryFilter)
    val bundleMappings: Seq[(File, String)] = configChildren.flatMap(_.pair(relativeTo(configurationTarget)))
    shazar(bundleTarget,
      (configurationName in config).value,
      bundleMappings,
      f => streams.value.log.info(s"$message: $f"))
  }

  @tailrec
  private[bundle] def recursiveListFiles(currentDirs: Array[File], filter: FileFilter, files: Array[File] = Array.empty): Array[File] =
    if(currentDirs.isEmpty)
      files
    else
      recursiveListFiles(currentDirs.flatMap(_.listFiles(DirectoryFilter)), filter, files ++ currentDirs.flatMap(_.listFiles(filter)))

  private[bundle] object NonDirectoryFilter extends FileFilter {
    def accept(file: File) = !file.isDirectory
  }

  // By default use the BundleKeys.endpoints settings key as endpoints
  // Override this method to change behaviour how to collect the endpoints
  private def collectEndpoints(config: Configuration): Def.Initialize[Task[Map[String, Endpoint]]] = Def.task {
    (overrideEndpoints in config).value.getOrElse((endpoints in config).value)
  }

  private def shazar(archiveTarget: File,
    archiveName: String,
    bundleMappings: Seq[(File, String)],
    logMessage: File => Unit): File = {
    val archived = Archives.makeZip(archiveTarget, archiveName, bundleMappings, Some(archiveName))
    val exti = archived.name.lastIndexOf('.')
    val hashName = archived.name.take(exti) + "-" + hash(digestFile(archived)) + archived.name.drop(exti)
    val hashArchive = archived.getParentFile / hashName
    IO.move(archived, hashArchive)
    logMessage(hashArchive)
    hashArchive
  }

  /**
    * Create a hash based on a UTF-8 string
    */
  def hash(content: String): String =
    hash(content.getBytes(Utf8))

  private def hash(bytes: Array[Byte]): String =
    Hash.toHex(bytes)

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

  object PathMatching {
    val ValidUriCharacters = """^[a-zA-Z0-9-._]+$"""

    private [bundle] def pathConfigKeyValue(path: Either[String, Regex]): (String, String) = {
      path match {
        case Left(value) =>
          "path" -> value

        case Right(value) if value.pattern.pattern.startsWith("^/") && !value.pattern.pattern.endsWith("$") =>
          val pathBeg = value.pattern.pattern.tail

          val isValidPathBegPattern = !pathBeg.contains("//") &&
            pathBeg.split("/").forall(v => v.isEmpty || v.matches(ValidUriCharacters))

          if (isValidPathBegPattern)
            "path-beg" -> pathBeg
          else
            throw new IllegalArgumentException(s"${value.pattern.pattern} is not a valid pattern for path beg")

        case Right(value) =>
          "path-regex" -> value.pattern.pattern
      }
    }
  }


  private def formatHttpRequestMapping(requestMapping: Http.Request): String = {
    val (pathConfigKey, pathConfigValue) = PathMatching.pathConfigKeyValue(requestMapping.path)
    val lines =
      Seq(
        Option(s"""$pathConfigKey = "$pathConfigValue""""),
        requestMapping.method.map(v => s"""method = "$v""""),
        requestMapping.rewrite.map(v => s"""rewrite = "$v""""))
        .collect {
          case Some(value) => value
        }
        .map(v => s"                  $v")
        .mkString("\n")

    s"""                {
       |$lines
       |                }""".stripMargin
  }

  private def formatHttpRequestMappings(requestMappings: Http): String =
    s"""            ${requestMappings.protocolFamily} = {
       |              requests = [
       |${requestMappings.requestMappings.map(formatHttpRequestMapping).mkString(",\n")}
       |              ]
       |            }""".stripMargin

  private def formatTcpRequestMappings(requestMappings: Tcp): String =
    s"""            ${requestMappings.protocolFamily} = {
       |              requests = [${requestMappings.requestMappings.map(_.port).mkString(", ")}]
       |            }""".stripMargin

  private def formatUdpRequestMappings(requestMappings: Udp): String =
    s"""            ${requestMappings.protocolFamily} = {
       |              requests = [${requestMappings.requestMappings.map(_.port).mkString(", ")}]
       |            }""".stripMargin

  private def formatRequestMappings(requestMappings: ProtocolFamilyRequestMapping): String =
    requestMappings match {
      case v: Http => formatHttpRequestMappings(v)
      case v: Tcp  => formatTcpRequestMappings(v)
      case v: Udp  => formatUdpRequestMappings(v)
    }

  private def formatAcl(acl: RequestAcl): String =
    s"""          {
       |${acl.protocolFamilyRequestMappings.map(formatRequestMappings).mkString(",\n")}
       |          }""".stripMargin

  private def formatAcls(acls: Set[RequestAcl]): String =
    s"""        acls          = [
      |${acls.map(formatAcl).mkString(",\n")}
      |        ]""".stripMargin

  private def formatSeq(strings: Iterable[String]): String =
    strings.map(s => s""""$s"""").mkString("[", ", ", "]")

  private def formatServices(services: Set[URI]): String =
    s"        services      = ${formatSeq(services.map(_.toString))}"

  private def formatServiceName(serviceName: String): String =
    s"""        service-name  = "$serviceName""""

  private def formatEndpoints(bundleConfVersion: BundleConfVersions.Value, endpoints: Map[String, Endpoint]): String = {
    val formatted =
      for {
        (label, Endpoint(bindProtocol, bindPort, services, serviceName, acls)) <- endpoints
      } yield {
        if (acls.exists(_.nonEmpty) && bundleConfVersion != BundleConfVersions.V_1_2_0)
          throw new IllegalArgumentException(s"Invalid configuration for endpoint $label - request ACL may only be specified for bundle.conf version 1.2.0")
        else if (acls.exists(_.nonEmpty) && services.exists(_.nonEmpty))
          throw new IllegalArgumentException(s"Invalid configuration for endpoint $label - either Services or Request ACL can be set")

        val servicesOrAcls =
          Seq(
            serviceName.map(formatServiceName),
            services.map(formatServices),
            acls.map(formatAcls)
          ).collect {
            case Some(value) => value
          }
          .mkString("\n")

        s"""|      "$label" = {
            |        bind-protocol = "$bindProtocol"
            |        bind-port     = $bindPort
            |$servicesOrAcls
            |      }""".stripMargin
      }

    formatted.mkString(f"{%n", f",%n", f"%n    }")
  }

  private def getConfig(config: Configuration, forAllSettings: Boolean): Def.Initialize[Task[String]] = Def.task {
    val checkComponents = (checksConfigName in config).value match {
      case (value, configName) if (forAllSettings || configName.isDefined) && value.nonEmpty =>
        val checkInitialDelayValue = (checkInitialDelay in config).value
        val checkInitialDelayInSeconds =
          if (checkInitialDelayValue.toMillis % 1000 > 0)
            checkInitialDelayValue.toSeconds + 1 // always round up
          else
            checkInitialDelayValue.toSeconds
        Seq(
          value.map(uri => s""""$uri"""").mkString(
            s"""components = {
               |  ${(normalizedName in config).value}-status = {
               |    description      = "Status check for the bundle component"
               |    file-system-type = "universal"
               |    start-command    = ["check", "--initial-delay", "$checkInitialDelayInSeconds", """.stripMargin,
            ", ",
            s"""]
               |    endpoints        = {}
               |  }
               |}""".stripMargin)
        )
      case _ =>
        Seq.empty[String]
    }

    def formatValue[T](format: String, valueAndConfigName: (T, Option[String])): Seq[String] =
      valueAndConfigName match {
        case (value, configName) if forAllSettings || configName.isDefined => Seq(format.format(value))
        case _                                                             => Seq.empty[String]
      }

    def toString[T](valueAndConfigName: (T, Option[String]), f: T => String): (String, Option[String]) =
      f(valueAndConfigName._1) -> valueAndConfigName._2

    val componentPrefix = s"""components."${(normalizedName in config).value}""""

    val declarations =
      Seq(s"""version              = "${bundleConfVersion.value}"""") ++
        formatValue("""name                 = "%s"""", (normalizedNameConfigName in config).value) ++
        formatValue("""compatibilityVersion = "%s"""", (compatibilityVersionConfigName in config).value) ++
        formatValue("""system               = "%s"""", (systemConfigName in config).value) ++
        formatValue("""systemVersion        = "%s"""", (systemVersionConfigName in config).value) ++
        formatValue("nrOfCpus             = %s", (nrOfCpusConfigName in config).value) ++
        formatValue("memory               = %s", toString((memoryConfigName in config).value, (v: Bytes) => v.underlying.toString)) ++
        formatValue("diskSpace            = %s", toString((diskSpaceConfigName in config).value, (v: Bytes) => v.underlying.toString)) ++
        formatValue(s"roles                = %s", toString((rolesConfigName in config).value, (v: Set[String]) => formatSeq(v))) ++
        Seq("components = {", s"  ${(normalizedName in config).value} = {") ++
        formatValue(s"""    description      = "%s"""", toString((projectInfoConfigName in config).value, (v: ModuleInfo) => v.description)) ++
        formatValue(s"""    file-system-type = "%s"""", (bundleTypeConfigName in config).value) ++
        formatValue(s"""    start-command    = %s""", toString((startCommandConfigName in config).value, (v: Seq[String]) => formatSeq(v))) ++
        formatValue(s"""    endpoints = %s""", toString((endpointsConfigName in config).value, (v: Map[String, Endpoint]) => formatEndpoints(bundleConfVersion.value, v))) ++
        Seq("  }", "}") ++
        checkComponents

    declarations.mkString("\n")
  }

  private def stageBundle(config: Configuration, bundleTypeConfig: Configuration): Def.Initialize[Task[File]] = Def.task {
    val bundleTarget = (NativePackagerKeys.stagingDirectory in config).value / config.name
    writeConfig(bundleTarget, (bundleConf in config).value)
    val componentTarget = bundleTarget / (normalizedName in config).value
    IO.copy((mappings in bundleTypeConfig).value.map(p => (p._1, componentTarget / p._2)))
    componentTarget
  }

  private def stageConfiguration(config: Configuration): Def.Initialize[Task[File]] = Def.task {
    val configurationTarget = (NativePackagerKeys.stagingDirectory in config).value / config.name
    val generatedConf = (bundleConf in config).value
    val srcDir = (sourceDirectory in config).value / (configurationName in config).value
    if (generatedConf.isEmpty && !srcDir.exists()) sys.error(
      s"""Directory $srcDir does not exist.
                             | Specify the desired configuration directory name
                             |  with the 'configurationName' setting given that it is not "default"""".stripMargin)
    IO.createDirectory(configurationTarget)
    if (generatedConf.nonEmpty) writeConfig(configurationTarget, generatedConf)
    IO.copyDirectory(srcDir, configurationTarget, overwrite = true, preserveLastModified = true)
    configurationTarget
  }

  private def writeConfig(target: File, contents: String): File = {
    val configFile = target / "bundle.conf"
    IO.write(configFile, contents, Utf8)
    configFile
  }
}
