package com.typesafe.sbt.bundle

import com.typesafe.sbt.bundle.Import.Http.Request
import org.scalatest.{ Matchers, WordSpec }
import sbt.IO
import java.io.File

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

  "SbtBundle.Http implicit methods" should {
    "map HTTP method to path" in {
      val request: Request = "GET" -> "/my-path"
      request shouldBe Request(method = Some(Import.Http.Method.GET), path = Left("/my-path"), rewrite = None)
    }

    "map path to rewrite" in {
      val request: Request = "/path-a" -> "/path-b"
      request shouldBe Request(method = None, path = Left("/path-a"), rewrite = Some("/path-b"))
    }

    "reject invalid mapping of two strings" in {
      intercept[IllegalArgumentException] {
        val request: Request = "BLARG" -> "/path-b"
      }

    }
  }

  "SbtBundle.escapeHttpRewrite" should {
    "preserve rewrite string as-is" in {
      SbtBundle.escapeHttpRewrite("/some-path") shouldBe "/some-path"
    }

    "escape backslash" in {
      SbtBundle.escapeHttpRewrite("/foo/\\1/bar") shouldBe "/foo/\\\\1/bar"
    }
  }

  "SbtBundle.recursiveListFiles" should {

    import SbtBundle._

    "list files in single directory" in {
      withTemporaryDirectory { dir =>
        val bundleConf = new File(s"$dir/bundle.conf")
        bundleConf.createNewFile()
        val runtimeConfig = new File(s"$dir/runtime-config.sh")
        runtimeConfig.createNewFile()

        val expectedFiles = Array(bundleConf, runtimeConfig)

        recursiveListFiles(Array(dir), NonDirectoryFilter).sorted shouldBe expectedFiles.sorted
      }
    }

    "list files in one sub directory" in {
      withTemporaryDirectory { dir =>
        val bundleConf = new File(s"$dir/bundle.conf")
        bundleConf.createNewFile()
        val runtimeConfig = new File(s"$dir/runtime-config.sh")
        runtimeConfig.createNewFile()
        val subDir = new File(s"$dir/conf")
        subDir.mkdir()
        val subFile = new File(s"$subDir/logback.xml")
        subFile.createNewFile()

        val expectedFiles = Array(bundleConf, runtimeConfig, subFile)

        recursiveListFiles(Array(dir), NonDirectoryFilter).sorted shouldBe expectedFiles.sorted
      }
    }

    "list files in multiple sub directories" in {
      withTemporaryDirectory { dir =>
        val bundleConf = new File(s"$dir/bundle.conf")
        bundleConf.createNewFile()
        val runtimeConfig = new File(s"$dir/runtime-config.sh")
        runtimeConfig.createNewFile()
        val subDir1 = new File(s"$dir/conf")
        subDir1.mkdir()
        val subDir1File = new File(s"$subDir1/logback.xml")
        subDir1File.createNewFile()
        val subDir2 = new File(s"$dir/logs")
        subDir2.mkdir()
        val subDir2File1 = new File(s"$subDir2/application.log")
        subDir2File1.createNewFile()
        val subDir2File2 = new File(s"$subDir2/system.log")
        subDir2File2.createNewFile()

        val expectedFiles = Array(bundleConf, runtimeConfig, subDir1File, subDir2File1, subDir2File2).sorted

        recursiveListFiles(Array(dir), NonDirectoryFilter).sorted shouldBe expectedFiles.sorted
      }
    }
  }

  private def withTemporaryDirectory[T](body: => File => T): T = {
    val tempDir = IO.createTemporaryDirectory
    try {
      body(tempDir)
    } finally {
      tempDir.delete()
    }
  }

}
