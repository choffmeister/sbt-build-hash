version := "0.1"
scalaVersion := "2.12.1"

lazy val common = project.in(file("common")).settings(buildHashKey := name.value + "-custom")
lazy val app = project.in(file("app")).settings(buildHashKey := name.value + "-custom").dependsOn(common)
lazy val root = project.in(file(".")).aggregate(common, app)
