import org.scalatest.Matchers._
import com.typesafe.sbt.bundle.SbtBundle._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

name := "simple-test"

version := "0.1.0-SNAPSHOT"

BundleKeys.endpoints := Map(
  "web" -> Endpoint("http", 0, 9000),
  "other" -> Endpoint("http", 0, 9001)
)

val checkBundleConf = taskKey[Unit]("check-main-css-contents")

checkBundleConf := {
  val contents = IO.read(target.value / "typesafe-conductr" / "tmp" / "bundle.conf")
  val expectedContents = """|version              = "1.0.0"
                            |system               = "simple-test-0.1.0-SNAPSHOT"
                            |start-status-command = "exit 0"
                            |components = {
                            |  "simple-test-0.1.0-SNAPSHOT" = {
                            |    description      = "simple-test"
                            |    file-system-type = "universal"
                            |    start-command    = ["bin/simple-test"]
                            |    endpoints        = {
                            |      "web" = {
                            |        protocol     = "http"
                            |        bind-port    = 0
                            |        service-port = 9000
                            |      },
                            |      "other" = {
                            |        protocol     = "http"
                            |        bind-port    = 0
                            |        service-port = 9001
                            |      }
                            |    }
                            |  }
                            |}""".stripMargin
  contents should include(expectedContents)
}
