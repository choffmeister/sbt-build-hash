package de.choffmeister.sbt

import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

object BuildSignaturePlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = JvmPlugin

  object autoImport {
    val buildSignatureKey = settingKey[String]("Key to be able to distinguish different build signature (for example master vs. some other branch)")
    val buildSignatureFiles = taskKey[Seq[File]]("Files and directories to include into the build signature")
    val buildSignatureBytes = taskKey[Array[Byte]]("Calculates a signature including all sources, resources and classpath dependencies")
    val buildSignature = taskKey[String]("Calculates a signature including all sources, resources and classpath dependencies")
    val buildSignatureStoreDirectory = settingKey[File]("Directory to store build signatures to")
    val buildSignatureStore = taskKey[Unit]("Stores the build signature to disk")
    val buildSignatureCheck = taskKey[Boolean]("Compares the calculated build signature with the one stored on disk")
    val buildSignatureWriteChanged = taskKey[Unit]("Writes a file listing only the changed modules")
    val buildSignatureOverview = taskKey[Seq[(String, Option[String], Boolean)]]("Generates a overview over all modules with name, signature and changed flag")
  }

  import autoImport._

  override lazy val projectSettings = Seq(
    buildSignatureKey := "default",
    buildSignatureStoreDirectory := target.value,
    buildSignatureFiles := {
      val s = (sources in Compile).value
      val r = (resources in Compile).value
      val d = (dependencyClasspath in Compile).value.map(_.data)
      s ++ r ++ d
    },
    buildSignatureBytes := {
      BuildSignature.calculate(buildSignatureFiles.value)
    },
    buildSignature := {
      Hex.fromBytes(buildSignatureBytes.value)
    },
    buildSignatureStore := {
      BuildSignature.store(buildSignatureStoreDirectory.value, buildSignatureKey.value, buildSignatureBytes.value)
    },
    buildSignatureCheck := {
      BuildSignature.check(buildSignatureStoreDirectory.value, buildSignatureKey.value, buildSignatureBytes.value)
    },
    buildSignatureOverview := {
      val names = name.all(ScopeFilter(inAnyProject, inConfigurations(Compile))).value
      val signatures = (buildSignature ?).all(ScopeFilter(inAnyProject, inConfigurations(Compile))).value
      val checks = (buildSignatureCheck ?? false).all(ScopeFilter(inAnyProject, inConfigurations(Compile))).value
      names.zip(signatures).zip(checks).map { case ((name, signature), check) =>
        (name, signature, check)
      }
    }
  )
}
