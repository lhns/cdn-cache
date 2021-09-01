ThisBuild / scalaVersion := "2.13.6"
ThisBuild / name := (server / name).value
name := (ThisBuild / name).value

lazy val commonSettings: Seq[Setting[_]] = Seq(
  version := {
    val Tag = "refs/tags/(.*)".r
    sys.env.get("CI_VERSION").collect { case Tag(tag) => tag }
      .getOrElse("0.0.1-SNAPSHOT")
  },

  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
)

lazy val root = project.in(file("."))
  .settings(
    publishArtifact := false
  )
  .aggregate(server)

val circeVersion = "0.14.1"
val doobieVersion = "1.0.0-M5"
val http4sVersion = "0.23.1"
val scalajsReactVersion = "2.0.0-RC2"

lazy val common = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "de.lolhens" %%% "cats-effect-utils" % "0.2.0",
      "io.circe" %%% "circe-core" % circeVersion,
      "io.circe" %%% "circe-generic" % circeVersion,
      "io.circe" %%% "circe-parser" % circeVersion,
      "org.scodec" %%% "scodec-bits" % "1.1.27",
      "org.typelevel" %%% "cats-core" % "2.6.1",
      "org.typelevel" %%% "cats-effect" % "3.2.0",
    )
  )

lazy val commonJvm = common.jvm
lazy val commonJs = common.js

lazy val frontend = project
  .enablePlugins(ScalaJSWebjarPlugin)
  .dependsOn(commonJs)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "core-bundle-cats_effect" % scalajsReactVersion,
      "com.github.japgolly.scalajs-react" %%% "extra" % scalajsReactVersion,
      "org.scala-js" %%% "scalajs-dom" % "1.1.0",
    ),

    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
    },
    scalaJSUseMainModuleInitializer := true,
  )

lazy val server = project
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(commonJvm, frontend.webjar)
  .settings(commonSettings)
  .settings(
    name := "cdn-cache",

    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.5",
      "de.lolhens" %% "fs2-utils" % "0.2.0",
      "de.lolhens" %% "http4s-brotli" % "0.4.0",
      "de.lolhens" %% "http4s-proxy" % "0.4.0",
      "org.bidib.com.github.markusbernhardt" % "proxy-vole" % "1.0.15",
      "org.http4s" %% "http4s-blaze-server" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-scalatags" % http4sVersion,
      "org.http4s" %% "http4s-client" % http4sVersion,
      "org.http4s" %% "http4s-jdk-http-client" % "0.5.0",
    ),

    buildInfoKeys := Seq(
      "frontendAsset" -> (frontend / Compile / webjarMainResourceName).value,
      "frontendName" -> (frontend / normalizedName).value,
      "frontendVersion" -> (frontend / version).value,
    ),

    assembly / assemblyJarName := s"${name.value}-${version.value}.sh.bat",

    assembly / assemblyOption := (assembly / assemblyOption).value
      .withPrependShellScript(Some(AssemblyPlugin.defaultUniversalScript(shebang = false))),

    assembly / assemblyMergeStrategy := {
      case PathList(paths@_*) if paths.last == "module-info.class" => MergeStrategy.discard
      case PathList("META-INF", "jpms.args") => MergeStrategy.discard
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    },
  )
