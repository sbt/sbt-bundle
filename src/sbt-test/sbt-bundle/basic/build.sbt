import ByteConversions._
import com.typesafe.sbt.bundle.SbtBundle._
import org.scalatest.Matchers._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

name := "simple-test"

version := "0.1.0-SNAPSHOT"

BundleKeys.nrOfCpus := 1.0
BundleKeys.memory := 64.MiB
BundleKeys.diskSpace := 10.MB
BundleKeys.roles := Set("web-server")

BundleKeys.endpoints += "other" -> Endpoint("http", 0, Set(URI("http://:9001/simple-test")))
BundleKeys.endpoints += "akka-remote" -> Endpoint("tcp")

val checkBundleConf = taskKey[Unit]("")

checkBundleConf := {
  val contents = IO.read((target in Bundle).value / "bundle" / "tmp" / "bundle.conf")
  val expectedContents = """|version = "1.1.0"
                            |name = "simple-test"
                            |compatibilityVersion = "0"
                            |system = "simple-test"
                            |systemVersion = "0"
                            |nrOfCpus = 1.0
                            |memory = 67108864
                            |diskSpace = 10000000
                            |roles = ["web-server"]
                            |components."simple-test".description = "simple-test"
                            |components."simple-test"."file-system-type" = "universal"
                            |components."simple-test"."start-command" = ["simple-test/bin/simple-test", "-J-Xms67108864", "-J-Xmx67108864"]
                            |components."simple-test".endpoints = {
                            |  "web" = {
                            |    bind-protocol  = "http"
                            |    bind-port = 0
                            |    services  = ["http://:9000"]
                            |  },
                            |  "other" = {
                            |    bind-protocol  = "http"
                            |    bind-port = 0
                            |    services  = ["http://:9001/simple-test"]
                            |  },
                            |  "akka-remote" = {
                            |    bind-protocol  = "tcp"
                            |    bind-port = 0
                            |    services  = []
                            |  }
                            |}""".stripMargin
  contents should include(expectedContents)
}
