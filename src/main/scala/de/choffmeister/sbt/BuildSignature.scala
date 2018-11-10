package de.choffmeister.sbt

import java.io.File
import java.security.MessageDigest

import sbt._
import sbt.IO._

import scala.util.Try

object BuildSignature {
  private val `UTF-8` = "UTF-8"
  private val `SHA-1` = "SHA-1"

  def calculate(files: Seq[File]): Array[Byte] = {
    hashBytes(files.foldLeft(Array.empty[Byte]) { (acc, file) =>
      if (file.exists()) {
        val fileSignature = if (file.isDirectory) {
          hashFilenameAndBytes(file, calculate(file.listFiles()))
        } else {
          hashFilenameAndBytes(file, readBytes(file))
        }
        acc ++ fileSignature
      } else acc
    })
  }

  def store(directory: File, key: String, signature: Array[Byte]): Unit = {
    if (!check(directory, key, signature)) {
      write(signatureFile(directory, key), Hex.fromBytes(signature).getBytes(`UTF-8`))
    }
  }

  def check(directory: File, key: String, signature: Array[Byte]): Boolean = {
    val stored = Try(readBytes(signatureFile(directory, key))).toOption.map(bs => new String(bs, `UTF-8`))
    stored.contains(Hex.fromBytes(signature))
  }

  private def signatureFile(directory: File, key: String) =
    directory / s".build-$key.sig"
  private def hashBytes(content: Array[Byte]): Array[Byte] =
    MessageDigest.getInstance(`SHA-1`).digest(content)
  private def hashFilenameAndBytes(file: File, content: Array[Byte]): Array[Byte] =
    hashBytes(file.getAbsolutePath.getBytes(`UTF-8`) ++ hashBytes(content))
}

object Hex {
  def fromBytes(bs: Array[Byte]): String = bs.map("%02x" format _).mkString
}
