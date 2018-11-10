# sbt-build-signature

An sbt plugin to detect which submodule actually has changes. It uses information about sources, resources, and
classpath dependencies from SBT to calculate a SHA-1 signature for every module. The intend of this plugin is for usage
with multi-module SBT projects that have continuous deployment attached. With this plugin one can reliably decide which
microservices have to be updated after successful building.

## Usage

This plugin requires sbt 1.0.0+

```scala
// plugins.sbt
resolvers += Resolver.bintrayIvyRepo("choffmeister", "sbt-plugins")
addSbtPlugin("de.choffmeister" % "sbt-build-signature" % "0.1.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.0")
```

```scala
// build.sbt
lazy val common = project.in(file("common"))
lazy val service = project.in(file("service")).dependsOn(common)
  .settings(
    // Make sure that the key properly separates your relevant builds from irrelevant ones. Example:
    // You have continuous deployment in place which operates on your master branch. Then the key should probably
    // be the git branch. This way pushes to master are only compared to the last push to master, not to pushed to other
    // branches.
    buildSignatureKey := s"service-${git.gitCurrentBranch.value}",
    // Make sure that this folder is kept across multiple builds of your project.
    buildSignatureStoreDirectory := "/var/sbt/cache",
    // If the signature should depend on more file than just sources, resources and classpath dependencies, you can add
    // custom files here.
    buildSignatureFiles += baseDirectory.value / "some-special-file.txt"
  )

// this is a custom example task that you might want to add
lazy val buildSignatureGenerateChangeList = taskKey[Unit]("this task is used to prepare a file for continuous deployment")
buildSignatureGenerateChangeList := {
  val changed =
    buildSignatureOverview.value.collect {
      case (name, _, false) => name
    }
  sbt.IO.writeLines(file(".") / "changed.txt", changed)
}
```

```bash
# continuous-deployment.sh
sbt buildSignatureGenerateChangeList

CHANGED_MODULES=$(cat changed.txt)
for MODULE in $CHANGED_MODULES; do
  # deploy module $MODULE here
done

sbt buildSignatureStore
```

### Testing

Run `test` for regular unit tests.

Run `scripted` for [sbt script tests](http://www.scala-sbt.org/1.x/docs/Testing-sbt-plugins.html).

### Publishing

1. publish your source to GitHub
2. [create a bintray account](https://bintray.com/signup/index) and [set up bintray credentials](https://github.com/sbt/sbt-bintray#publishing)
3. create a bintray repository `sbt-plugins` 
4. update your bintray publishing settings in `build.sbt`
5. `sbt publish`
6. [request inclusion in sbt-plugin-releases](https://bintray.com/sbt/sbt-plugin-releases)
7. [Add your plugin to the community plugins list](https://github.com/sbt/website#attention-plugin-authors)
8. [Claim your project an Scaladex](https://github.com/scalacenter/scaladex-contrib#claim-your-project)
