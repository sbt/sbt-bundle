package com.typesafe.sbt.bundle

import org.scalatest.{ Matchers, WordSpec }

class SbtBundleSpec extends WordSpec with Matchers {
  "SbtBundle.PathMatching.pathConfigKeyValue" should {
    "return a path" in {
      SbtBundle.PathMatching.pathConfigKeyValue(Left("/my-path")) shouldBe ("path" -> "/my-path")
    }

    "return a path-regex" in {
      SbtBundle.PathMatching.pathConfigKeyValue(Right("""^/my-path/\.+.html$""".r)) shouldBe ("path-regex" -> """^/my-path/\.+.html$""")
    }

    Seq(
      "root path" -> """^/""".r -> "/",
      "sub path" -> """^/mypath""".r -> "/mypath",
      "sub path with dash" -> """^/my-path""".r -> "/my-path",
      "sub path with underscore" -> """^/my_path""".r -> "/my_path"
    ).foreach {
        case ((scenario, pattern), expectedPathBeg) =>
          s"return a path-beg - $scenario" in {
            SbtBundle.PathMatching.pathConfigKeyValue(Right(pattern)) shouldBe ("path-beg" -> expectedPathBeg)
          }
      }

    Seq(
      "additional regex patterns" -> """^/my-path/\w+/foo""".r,
      "starts with double slash" -> """^//my-path""".r,
      "contains double slash" -> """^/my-path//foo""".r
    ).foreach {
        case (scenario, pattern) =>
          s"fail given invalid path-beg - $scenario" in {
            intercept[IllegalArgumentException] {
              SbtBundle.PathMatching.pathConfigKeyValue(Right(pattern))
            }
          }
      }
  }
}
