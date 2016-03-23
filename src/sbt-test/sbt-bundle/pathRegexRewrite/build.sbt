import ByteConversions._
import com.typesafe.sbt.bundle.SbtBundle._
import org.scalatest.Matchers._
import com.typesafe.config.ConfigFactory

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

name := "simple-test"

version := "0.1.0-SNAPSHOT"
BundleKeys.bundleConfVersion := BundleConfVersions.V_1_2_0

BundleKeys.nrOfCpus := 1.0
BundleKeys.memory := 64.MiB
BundleKeys.diskSpace := 10.MB
BundleKeys.roles := Set("web-server")

BundleKeys.endpoints += "other" -> Endpoint("http", 0, Set(URI("http://:9001/simple-test")))
BundleKeys.endpoints += "akka-remote" -> Endpoint("tcp")
BundleKeys.endpoints += "extras" -> Endpoint("http", 0, "ping-service",
  RequestAcl(
    Http(
      "^/fee/(.*)/fi$".r -> "/foo/\\1/bar"
    ))
)

val checkBundleConf = taskKey[Unit]("")

checkBundleConf := {
  val contents = IO.read((target in Bundle).value / "bundle" / "tmp" / "bundle.conf")

  // Ensure content can be parsed into typesafe-config successfully
  ConfigFactory.parseString(contents)

  val expectedContents = """|version              = "1.2.0"
                            |name                 = "simple-test"
                            |compatibilityVersion = "0"
                            |system               = "simple-test"
                            |systemVersion        = "0"
                            |nrOfCpus             = 1.0
                            |memory               = 67108864
                            |diskSpace            = 10000000
                            |roles                = ["web-server"]
                            |components = {
                            |  simple-test = {
                            |    description      = "simple-test"
                            |    file-system-type = "universal"
                            |    start-command    = ["simple-test/bin/simple-test", "-J-Xms67108864", "-J-Xmx67108864"]
                            |    endpoints = {
                            |      "web" = {
                            |        bind-protocol = "http"
                            |        bind-port     = 0
                            |        services      = ["http://:9000"]
                            |      },
                            |      "other" = {
                            |        bind-protocol = "http"
                            |        bind-port     = 0
                            |        services      = ["http://:9001/simple-test"]
                            |      },
                            |      "akka-remote" = {
                            |        bind-protocol = "tcp"
                            |        bind-port     = 0
                            |        services      = []
                            |      },
                            |      "extras" = {
                            |        bind-protocol = "http"
                            |        bind-port     = 0
                            |        service-name  = "ping-service"
                            |        acls          = [
                            |          {
                            |            http = {
                            |              requests = [
                            |                {
                            |                  path-regex = "^/fee/(.*)/fi$"
                            |                  rewrite = "/foo/\\1/bar"
                            |                }
                            |              ]
                            |            }
                            |          }
                            |        ]
                            |      }
                            |    }
                            |  }
                            |}""".stripMargin
  contents should include(expectedContents)
}

val rewriteHasCorrectValue = taskKey[Unit]("")
rewriteHasCorrectValue := {
  val contents = IO.read((target in Bundle).value / "bundle" / "tmp" / "bundle.conf")
  val config = ConfigFactory.parseString(contents)
  val aclConfig = config.getConfigList("components.simple-test.endpoints.extras.acls").get(0)
  val requestConfig = aclConfig.getConfigList("http.requests").get(0)
  requestConfig.getString("rewrite") shouldBe "/foo/\\1/bar"
}