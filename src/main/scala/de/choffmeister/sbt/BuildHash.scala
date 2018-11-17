package de.choffmeister.sbt

import java.io.File
import java.security.MessageDigest

import sbt.IO._
import sbt._

import scala.util.Try

object BuildHash {
  private val `UTF-8` = "UTF-8"
  private val `SHA-1` = "SHA-1"

  def listFiles(file: File): Seq[File] = file match {
    case f if !f.exists()   => Seq.empty
    case f if f.isFile      => Seq(f)
    case f if f.isDirectory => f.listFiles().flatMap(listFiles)
  }

  def createShaSum(files: Seq[(String, Seq[File])]): ShaSum = {
    ShaSum(files.map { group =>
      group._1 -> group._2
        .flatMap(listFiles)
        .map(file => Path(file) -> Hash(calculateHash(readBytes(file))))
        .toMap
    }.toMap)
  }

  def writeShaSum(file: File, shaSum: ShaSum): Unit = {
    if (!readShaSum(file).contains(shaSum)) {
      write(file, ShaSum.serialize(shaSum).getBytes(`UTF-8`))
    }
  }

  def readShaSum(file: File): Option[ShaSum] = {
    Try(readBytes(file))
      .map(bs => ShaSum.deserialize(new String(bs, `UTF-8`)))
      .toOption
  }

  private def calculateHash(content: Array[Byte]): Array[Byte] =
    MessageDigest.getInstance(`SHA-1`).digest(content)
}

