package de.choffmeister.sbt

import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

object BuildHashPlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = JvmPlugin

  object autoImport {
    val buildHashKey = settingKey[String]("Key to be able to distinguish different build hash (for example master vs. some other branch)")
    val buildHashFiles = taskKey[Seq[File]]("Files and directories to include into the build hash")
    val buildHashBytes = taskKey[Array[Byte]]("Calculates a hash including all sources, resources and classpath dependencies")
    val buildHash = taskKey[String]("Calculates a hash including all sources, resources and classpath dependencies")
    val buildHashStoreDirectory = settingKey[File]("Directory to store build hashs to")
    val buildHashStore = taskKey[Unit]("Stores the build hash to disk")
    val buildHashCheck = taskKey[Boolean]("Compares the calculated build hash with the one stored on disk")
    val buildHashWriteChanged = taskKey[Unit]("Writes a file listing only the changed modules")
    val buildHashOverview = taskKey[Seq[(String, Option[String], Boolean)]]("Generates a overview over all modules with name, hash and changed flag")
  }

  import autoImport._

  override lazy val projectSettings = Seq(
    buildHashKey := "default",
    buildHashStoreDirectory := target.value,
    buildHashFiles := {
      val s = (sources in Compile).value
      val r = (resources in Compile).value
      val d = (dependencyClasspath in Compile).value.map(_.data)
      s ++ r ++ d
    },
    buildHashBytes := {
      BuildHash.calculate(buildHashFiles.value)
    },
    buildHash := {
      Hex.fromBytes(buildHashBytes.value)
    },
    buildHashStore := {
      BuildHash.store(buildHashStoreDirectory.value, buildHashKey.value, buildHashBytes.value)
    },
    buildHashCheck := {
      BuildHash.check(buildHashStoreDirectory.value, buildHashKey.value, buildHashBytes.value)
    },
    buildHashOverview := {
      val names = name.all(ScopeFilter(inAnyProject, inConfigurations(Compile))).value
      val hashs = (buildHash ?).all(ScopeFilter(inAnyProject, inConfigurations(Compile))).value
      val checks = (buildHashCheck ?? false).all(ScopeFilter(inAnyProject, inConfigurations(Compile))).value
      names.zip(hashs).zip(checks).map { case ((name, hash), check) =>
        (name, hash, check)
      }
    }
  )
}
