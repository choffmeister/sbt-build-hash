package de.choffmeister.sbt

import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

object BuildHashPlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = JvmPlugin

  object autoImport {
    val buildHashKey = settingKey[String]("Key to be able to distinguish different build hash (for example master vs. some other branch)")
    val buildHashFiles = taskKey[Seq[(String, Seq[File])]]("Files and directories to include into the build hash")
    val buildHash = taskKey[ShaSum]("Calculates a hash including all sources, resources and classpath dependencies")
    val buildHashStoreDirectory = settingKey[File]("Directory to store build hashs to")
    val buildHashStore = taskKey[Unit]("Stores the build hash to disk")
    val buildHashDiff = taskKey[Set[DiffEntry]]("Compares the calculated build hash with the one stored on disk")
    val buildHashWriteChanged = taskKey[Unit]("Writes a file listing only the changed modules")
    val buildHashOverview = taskKey[Map[String, Set[DiffEntry]]]("Generates a overview over all modules with name, hash and changed flag")
  }

  import autoImport._

  override lazy val projectSettings = Seq(
    buildHashKey := "default",
    buildHashStoreDirectory := target.value / "build-hashes",
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
    buildHashStore := {
      BuildHash.writeShaSum(buildHashStoreDirectory.value, buildHashKey.value, buildHash.value)
    },
    buildHashDiff := {
      val log = streams.value.log
      val prev = BuildHash.readShaSum(buildHashStoreDirectory.value, buildHashKey.value)
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
    buildHashOverview := {
      val names = name.all(ScopeFilter(inAnyProject, inConfigurations(Compile))).value
      val diffs = (buildHashDiff ?? Set.empty[DiffEntry]).all(ScopeFilter(inAnyProject, inConfigurations(Compile))).value
      names.zip(diffs).toMap
    },
    buildHashWriteChanged := {
      val changed =
        buildHashOverview.value.collect {
          case (name, diff) if diff.nonEmpty => name
        }
      sbt.IO.writeLines(buildHashStoreDirectory.value / "changed", changed.toSeq.sorted)
    }
  )
}
