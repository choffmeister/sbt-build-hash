name := "sbt-build-hash"
organization := "de.choffmeister"

enablePlugins(GitVersioning)

sbtPlugin := true

libraryDependencies ++= Seq(
  "commons-codec" % "commons-codec" % "1.9",
  "org.scalatest" %% "scalatest" % "3.0.5" % Test
)

bintrayPackageLabels := Seq("sbt","plugin")
bintrayVcsUrl := Some("git@github.com:choffmeister/sbt-build-hash.git")

initialCommands in console := "import de.choffmeister.sbt._"

enablePlugins(ScriptedPlugin)

scriptedLaunchOpts ++= Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
