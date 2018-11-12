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

  def diff(from: ShaSum, to: ShaSum): Unit = {
    def diffSets[T](a: Set[T], b: Set[T]): (Set[T], Set[T], Set[T]) = (a.diff(b), b.diff(a), a.intersect(b))

    val (removedGroups, addedGroups, keptGroups) = diffSets(from.groups.keySet, to.groups.keySet)
    removedGroups.foreach(g => println(s"- $g"))
    addedGroups.foreach(g => println(s"+ $g"))
    keptGroups.foreach { g =>
      val (removedFiles, addedFiles, keptFiles) = diffSets(from.groups(g).keySet, to.groups(g).keySet)
      val changedFiles = keptFiles.filter(f => from.groups(g)(f) != to.groups(g)(f))
      removedFiles.foreach(f => println(s"- $g:$f"))
      addedFiles.foreach(f => println(s"+ $g:$f"))
      changedFiles.foreach(f => println(s"~ $g:$f"))
    }
  }

  def writeShaSum(directory: File, key: String, shaSum: ShaSum): Unit = {
    if (!readShaSum(directory, key).contains(shaSum)) {
      write(hashFilePath(directory, key), ShaSum.serialize(shaSum).getBytes(`UTF-8`))
    }
  }

  def readShaSum(directory: File, key: String): Option[ShaSum] = {
    Try(readBytes(hashFilePath(directory, key)))
      .map(bs => ShaSum.deserialize(new String(bs, `UTF-8`)))
      .toOption
  }

  private def hashFilePath(directory: File, key: String) =
    directory / s"$key.sha1"
  private def calculateHash(content: Array[Byte]): Array[Byte] =
    MessageDigest.getInstance(`SHA-1`).digest(content)
}

