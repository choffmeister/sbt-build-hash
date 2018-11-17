package de.choffmeister.sbt

import java.io.{BufferedWriter, File, FileWriter}
import java.nio.file.Files

import org.apache.commons.codec.binary.Hex
import org.scalatest.{Matchers, WordSpec}
import sbt._

class BuildHashSpec extends WordSpec with Matchers {
  "list files" in {
    withTempDir { dir =>
      BuildHash.listFiles(dir) should have size 0

      createFile(dir, "1.txt", "")
      BuildHash.listFiles(dir) should have size 1

      createFile(dir, "2.txt", "2")
      BuildHash.listFiles(dir) should have size 2

      createFile(dir, "foo/3.txt", "3")
      BuildHash.listFiles(dir) should have size 3

      BuildHash.listFiles(dir).map(_.getAbsolutePath).toSet should be(Set(
        new File(dir, "1.txt").getAbsolutePath,
        new File(dir, "2.txt").getAbsolutePath,
        new File(dir, "foo/3.txt").getAbsolutePath
      ))
    }
  }

  "create shasum" in {
    withTempDir { dir =>
      createFile(dir, "1.txt", "")
      createFile(dir, "2.txt", "1")
      createFile(dir, "foo/3.txt", "2")

      val shaSum = BuildHash.createShaSum(Seq("files" -> Seq(dir)))
      shaSum should be(ShaSum(Map(
        "files" -> Map(
          Path(new File(dir, "1.txt")) -> Hash(Hex.decodeHex("da39a3ee5e6b4b0d3255bfef95601890afd80709".toCharArray)),
          Path(new File(dir, "2.txt")) -> Hash(Hex.decodeHex("356a192b7913b04c54574d18c28d46e6395428ab".toCharArray)),
          Path(new File(dir, "foo/3.txt")) -> Hash(Hex.decodeHex("da4b9237bacccdf19c0760cab7aec4a8359010b0".toCharArray))
        )
      )))
    }
  }

  "serialize and deserialize shasum" in {
    withTempDir { dir1 =>
      withTempDir { dir2 =>
        createFile(dir1, "1.txt", "")
        createFile(dir1, "2.txt", "2")
        createFile(dir1, "foo/3.txt", "3")
        createFile(dir2, "4.txt", "4")

        val shaSum1 = BuildHash.createShaSum(Seq("first" -> Seq(dir1), "second" -> Seq(dir2), "third" -> Seq.empty))
        val str = ShaSum.serialize(shaSum1)
        val shaSum2 = ShaSum.deserialize(str)

        shaSum1 should be(shaSum2)
      }
    }
  }
  
  "read and write shasum" in {
    withTempDir { dir =>
      val shaSum1 = ShaSum(Map("sources" -> Map(Path("/bar") -> Hash(Array[Byte](0, 1, 2, 3)))))
      BuildHash.writeShaSum(dir / "default", shaSum1)
      val shaSum2 = BuildHash.readShaSum(dir / "default")
      shaSum2.contains(shaSum1) should be(true)
    }
  }

  "diff shasum" in {
    val shaSum = ShaSum(Map("sources" -> Map(Path("/bar") -> Hash(Array[Byte](0, 1, 2, 3)))))

    val a = ShaSum.diff(shaSum, shaSum)
    a should be(Set.empty)

    val b = ShaSum.diff(shaSum, shaSum.addGroup("empty"))
    b should be(Set(DiffEntry(DiffKind.Added, "empty", None)))

    val c = ShaSum.diff(shaSum, shaSum.addEntry("sources", Path("/bar"), Hash(Array[Byte](3, 2, 1, 0))))
    c should be(Set(DiffEntry(DiffKind.Changed, "sources", Some(Path("/bar")))))

    val d = ShaSum.diff(shaSum, shaSum.addEntry("sources", Path("/bar2"), Hash(Array[Byte](3, 2, 1, 0))))
    d should be(Set(DiffEntry(DiffKind.Added, "sources", Some(Path("/bar2")))))

    val e = ShaSum.diff(shaSum, shaSum.removeGroup("sources"))
    e should be(Set(DiffEntry(DiffKind.Removed, "sources", None)))

    val f = ShaSum.diff(shaSum, shaSum.removeEntry("sources", Path("/bar")))
    f should be(Set(DiffEntry(DiffKind.Removed, "sources", Some(Path("/bar")))))
  }

  private def withTempDir[T](fn: File => T): T = {
    val tempDir = Files.createTempDirectory("sbt-build-hash")
    fn(tempDir.toFile)
  }

  private def createFile(tempDir: File, path: String, content: String): File = {
    val file = new File(tempDir, path)
    val dir = file.getParentFile
    dir.mkdirs()
    file.createNewFile()

    val writer = new BufferedWriter(new FileWriter(file))
    writer.write(content)
    writer.close()

    file
  }
}
