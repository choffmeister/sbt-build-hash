package de.choffmeister.sbt

import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

object BuildHashPlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = JvmPlugin

  object autoImport {
    val buildHashKey = settingKey[String]("Key to be able to distinguish different build hashes (for example master vs. some other branch)")
    val buildHashFiles = taskKey[Seq[(String, Seq[File])]]("Files and directories to include into the build hash")
    val buildHash = taskKey[ShaSum]("Calculates a hash including all sources, resources and classpath dependencies")
    val buildHashDiff = taskKey[Set[DiffEntry]]("Compares the calculated build hash with the one stored on disk")
    val buildHashTargetDirectory = settingKey[File]("Directory to store build hashes to")
    val buildHashSave = taskKey[Unit]("Save the build hashes to disk")
    val buildHashChangesInAggregates = taskKey[Map[String, Set[DiffEntry]]]("List changes across all aggregated projects")
    val buildHashWriteChangedInAggregates = taskKey[Unit]("Writes a file listing changed aggregated projects")
  }

  import autoImport._

  override lazy val projectSettings = Seq(
    buildHashKey := name.value,
    buildHashTargetDirectory := target.value / "build-hashes",
    buildHashFiles := {
      Seq(
        "sources" -> (sources in Compile).value,
        "resources" -> (resources in Compile).value,
        "dependencyClasspath" -> (dependencyClasspath in Compile).value.map(_.data)
      )
    },
    buildHash := {
      BuildHash.createShaSum(buildHashFiles.value)
    },
    buildHashDiff := {
      val file = hashFilePath(buildHashTargetDirectory.value, buildHashKey.value)
      val log = streams.value.log
      val prev = BuildHash.readShaSum(file)
      val current = buildHash.value
      log.info(s"Diffing ${name.value}")
      val diff = ShaSum.diff(prev.getOrElse(ShaSum.empty), current)
      diff.foreach {
        case DiffEntry(DiffKind.Added, group, None) => log.info(s"+ $group")
        case DiffEntry(DiffKind.Added, group, Some(path)) => log.info(s"+ $group:$path")
        case DiffEntry(DiffKind.Removed, group, None) => log.info(s"- $group")
        case DiffEntry(DiffKind.Removed, group, Some(path)) => log.info(s"- $group:$path")
        case DiffEntry(DiffKind.Changed, group, None) => log.info(s"~ $group")
        case DiffEntry(DiffKind.Changed, group, Some(path)) => log.info(s"~ $group:$path")
      }
      diff
    },
    buildHashSave := {
      val keys = buildHashKey.all(ScopeFilter(inAggregates(ThisProject, transitive = true, includeRoot = false), inConfigurations(Compile))).value
      val buildHashes = buildHash.all(ScopeFilter(inAggregates(ThisProject, transitive = true, includeRoot = false), inConfigurations(Compile))).value
      keys.zip(buildHashes).toMap.foreach { case (key, shaSum) =>
        val file = hashFilePath(buildHashTargetDirectory.value, key)
        BuildHash.writeShaSum(file, shaSum)
      }
    },
    buildHashChangesInAggregates := {
      val names = name.all(ScopeFilter(inAggregates(ThisProject, transitive = true, includeRoot = false), inConfigurations(Compile))).value
      val diffs = (buildHashDiff ?? Set.empty[DiffEntry]).all(ScopeFilter(inAggregates(ThisProject, transitive = true, includeRoot = false), inConfigurations(Compile))).value
      names.zip(diffs).toMap
    },
    buildHashWriteChangedInAggregates := {
      val file = buildHashTargetDirectory.value / "changed"
      val changed =
        buildHashChangesInAggregates.value.collect {
          case (name, diff) if diff.nonEmpty => name
        }
      sbt.IO.writeLines(file, changed.toSeq.sorted)
    }
  )

  private def hashFilePath(directory: File, key: String) =
    directory / s"$key.sha1"
}
