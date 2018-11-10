package de.choffmeister.sbt

import java.io.File
import java.security.MessageDigest

import sbt._
import sbt.IO._

import scala.util.Try

object BuildHash {
  private val `UTF-8` = "UTF-8"
  private val `SHA-1` = "SHA-1"

  def calculate(files: Seq[File]): Array[Byte] = {
    hashBytes(files.foldLeft(Array.empty[Byte]) { (acc, file) =>
      if (file.exists()) {
        val fileHash = if (file.isDirectory) {
          hashFilenameAndBytes(file, calculate(file.listFiles()))
        } else {
          hashFilenameAndBytes(file, readBytes(file))
        }
        acc ++ fileHash
      } else acc
    })
  }

  def store(directory: File, key: String, hash: Array[Byte]): Unit = {
    if (!check(directory, key, hash)) {
      write(hashFile(directory, key), Hex.fromBytes(hash).getBytes(`UTF-8`))
    }
  }

  def check(directory: File, key: String, hash: Array[Byte]): Boolean = {
    val stored = Try(readBytes(hashFile(directory, key))).toOption.map(bs => new String(bs, `UTF-8`))
    stored.contains(Hex.fromBytes(hash))
  }

  private def hashFile(directory: File, key: String) =
    directory / s"build-hash-$key"
  private def hashBytes(content: Array[Byte]): Array[Byte] =
    MessageDigest.getInstance(`SHA-1`).digest(content)
  private def hashFilenameAndBytes(file: File, content: Array[Byte]): Array[Byte] =
    hashBytes(file.getAbsolutePath.getBytes(`UTF-8`) ++ hashBytes(content))
}

object Hex {
  def fromBytes(bs: Array[Byte]): String = bs.map("%02x" format _).mkString
}
