version := "0.1"
scalaVersion := "2.12.1"

lazy val common = project.in(file("common"))
lazy val app = project.in(file("app")).dependsOn(common)
lazy val root = project.in(file(".")).aggregate(common, app)
