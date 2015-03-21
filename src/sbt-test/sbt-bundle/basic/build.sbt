import ByteConversions._
import com.typesafe.sbt.bundle.SbtBundle._
import akka.http.model.Uri
import org.scalatest.Matchers._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

name := "simple-test"

version := "0.1.0-SNAPSHOT"

BundleKeys.nrOfCpus := 1.0
BundleKeys.memory := 64.MiB
BundleKeys.diskSpace := 10.MB
BundleKeys.roles := Set("web-server")

BundleKeys.endpoints += "other" -> Endpoint("http", 0, Set(URI("http://:9001/simple-test")))

val checkBundleConf = taskKey[Unit]("check-main-css-contents")

checkBundleConf := {
  val contents = IO.read(target.value / "typesafe-conductr" / "tmp" / "bundle.conf")
  val expectedContents = """|version    = "1.0.0"
                            |name       = "simple-test"
                            |system     = "simple-test-0.1.0-SNAPSHOT"
                            |nrOfCpus   = 1.0
                            |memory     = 67108864
                            |diskSpace  = 10000000
                            |roles      = ["web-server"]
                            |components = {
                            |  "simple-test-0.1.0-SNAPSHOT" = {
                            |    description      = "simple-test"
                            |    file-system-type = "universal"
                            |    start-command    = ["simple-test-0.1.0-SNAPSHOT/bin/simple-test", "-J-Xms67108864", "-J-Xmx67108864"]
                            |    endpoints        = {
                            |      "web" = {
                            |        protocol  = "http"
                            |        bind-port = 0
                            |        services  = ["http://:9000"]
                            |      },
                            |      "other" = {
                            |        protocol  = "http"
                            |        bind-port = 0
                            |        services  = ["http://:9001/simple-test"]
                            |      }
                            |    }
                            |  }
                            |}""".stripMargin
  contents should include(expectedContents)
}
