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
BundleKeys.startCommand := Seq("this\\is\\my\\start-command", "with", "arguments")

val checkBundleConf = taskKey[Unit]("")

checkBundleConf := {
  val contents = IO.read((target in Bundle).value / "bundle" / "tmp" / "bundle.conf")
  // Content should be parseable into Typesafe Config
  ConfigFactory.parseString(contents)
  val expectedContents = """|version              = "1.2.0"
                            |name                 = "simple-test"
                            |compatibilityVersion = "0"
                            |system               = "simple-test"
                            |systemVersion        = "0"
                            |nrOfCpus             = 1.0
                            |memory               = 67108864
                            |diskSpace            = 10000000
                            |roles                = ["web"]
                            |components = {
                            |  simple-test = {
                            |    description      = "simple-test"
                            |    file-system-type = "universal"
                            |    start-command    = ["this/is/my/start-command", "with", "arguments"]
                            |    endpoints = {
                            |      "web" = {
                            |        bind-protocol = "http"
                            |        bind-port     = 0
                            |        services      = ["http://:9000"]
                            |      }
                            |    }
                            |  }
                            |}""".stripMargin
  contents should include(expectedContents)
}
