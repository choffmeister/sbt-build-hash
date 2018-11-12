package de.choffmeister.sbt

import java.io.File

import org.apache.commons.codec.binary.Hex

final case class ShaSum(groups: Map[String, Map[Path, Hash]]) {
  def addGroup(group: String): ShaSum = {
    copy(groups = groups + (group -> Map.empty))
  }
  def removeGroup(group: String): ShaSum = {
    copy(groups = groups - group)
  }
  def addEntry(group: String, path: Path, hash: Hash): ShaSum = {
    groups.get(group) match {
      case Some(entries) => copy(groups = groups + (group -> (entries + (path -> hash))))
      case None => copy(groups = groups + (group -> Map(path -> hash)))
    }
  }
  def removeEntry(group: String, path: Path): ShaSum = {
    groups.get(group) match {
      case Some(entries) => copy(groups = groups + (group -> (entries - path)))
      case None => this
    }
  }
}

object ShaSum {
  def empty: ShaSum = ShaSum(Map.empty)

  def serialize(map: ShaSum): String = {
    val stringBuilder = new StringBuilder()
    map.groups.foreach { case (group, files) =>
      stringBuilder.append("# " + group + "\n")
      files.foreach { case (path, hash) =>
        stringBuilder.append(hash + "  " + path + "\n")
      }
      stringBuilder.append("\n")
    }

    stringBuilder.toString
  }

  def deserialize(str: String): ShaSum = {
    val lines = str.lines.map(_.trim).filter(_.nonEmpty).toList
    val groupRegex = """#\s+(.*)""".r
    val fileRegex = """([0-9a-f]+)\s+(.*)""".r

    lines.foldLeft[(ShaSum, Option[String])]((ShaSum(Map.empty), None)) {
      case ((map, _), groupRegex(group)) =>
        (map.addGroup(group), Some(group))
      case ((map, Some(group)), fileRegex(hash, path)) =>
        (map.addEntry(group, Path(path), Hash(Hex.decodeHex(hash.toCharArray))), Some(group))
    }._1
  }

  def diff(from: ShaSum, to: ShaSum): Set[DiffEntry] = {
    def diffSets[T](a: Set[T], b: Set[T]): (Set[T], Set[T], Set[T]) = (a.diff(b), b.diff(a), a.intersect(b))

    val (removedGroups, addedGroups, keptGroups) = diffSets(from.groups.keySet, to.groups.keySet)
    val a = addedGroups.map(g => DiffEntry(DiffKind.Added, g, None))
    val b = removedGroups.map(g => DiffEntry(DiffKind.Removed, g, None))
    val c = keptGroups.foldLeft(Seq.empty[DiffEntry])((acc, g) => {
      val (removedFiles, addedFiles, keptFiles) = diffSets(from.groups(g).keySet, to.groups(g).keySet)
      val changedFiles = keptFiles.filter(f => from.groups(g)(f) != to.groups(g)(f))
      val d = addedFiles.map(f => DiffEntry(DiffKind.Added, g, Some(f)))
      val e = removedFiles.map(f => DiffEntry(DiffKind.Removed, g, Some(f)))
      val f = changedFiles.map(f => DiffEntry(DiffKind.Changed, g, Some(f)))
      acc ++ d ++ e ++ f
    })

    a ++ b ++ c
  }
}

sealed trait DiffKind
object DiffKind {
  case object Added extends DiffKind
  case object Removed extends DiffKind
  case object Changed extends DiffKind
}

final case class DiffEntry(kind: DiffKind, group: String, path: Option[Path])

final case class Path(path: String) {
  override def equals(that: Any): Boolean =
    that match {
      case that: Path => that.canEqual(this) && this.hashCode == that.hashCode && this.path == that.path
      case _ => false
    }

  override def hashCode: Int = path.hashCode

  override def toString: String = path
}

object Path {
  def apply(file: File): Path = Path(file.getAbsolutePath)
}

final case class Hash(bytes: Array[Byte]) {
  override def equals(that: Any): Boolean =
    that match {
      case that: Hash => that.canEqual(this) && this.hashCode == that.hashCode
      case _ => false
    }

  override def hashCode: Int = {
    val prime = 31
    var result = 1
    for (i <- bytes.indices) {
      result = prime * result + bytes(i).hashCode
    }
    result
  }

  override def toString: String = Hex.encodeHexString(bytes)
}

