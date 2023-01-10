ThisBuild / scalaVersion := "2.13.10"
ThisBuild / name := (server / name).value
name := (ThisBuild / name).value

val V = new {
  val betterMonadicFor = "0.3.1"
  val cats = "2.9.0"
  val catsEffect = "3.4.4"
  val catsEffectUtils = "0.3.0"
  val circe = "0.14.3"
  val fs2Utils = "0.3.0"
  val http4s = "0.23.17"
  val http4sBrotli = "0.4.0"
  val http4sDom = "0.2.3"
  val http4sJdkHttpClient = "0.7.0"
  val http4sProxy = "0.4.0"
  val http4sSpa = "0.6.1"
  val logbackClassic = "1.4.5"
  val proxyVole = "1.0.18"
  val remoteIo = "0.0.1"
  val scalajsDom = "2.3.0"
  val scalajsReact = "2.1.1"
  val scodecBits = "1.1.34"
}

lazy val commonSettings: Seq[Setting[_]] = Seq(
  version := {
    val Tag = "refs/tags/v?([0-9]+(?:\\.[0-9]+)+(?:[+-].*)?)".r
    sys.env.get("CI_VERSION").collect { case Tag(tag) => tag }
      .getOrElse("0.0.1-SNAPSHOT")
  },
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % V.betterMonadicFor),
  assembly / assemblyJarName := s"${name.value}-${version.value}.sh.bat",
  assembly / assemblyOption := (assembly / assemblyOption).value
    .withPrependShellScript(Some(AssemblyPlugin.defaultUniversalScript(shebang = false))),
  assembly / assemblyMergeStrategy := {
    case PathList(paths@_*) if paths.last == "module-info.class" => MergeStrategy.discard
    case x =>
      val oldStrategy = (assembly / assemblyMergeStrategy).value
      oldStrategy(x)
  },
)

lazy val root = project.in(file("."))
  .settings(
    publishArtifact := false
  )
  .aggregate(server, sharedJvm, sharedJs, frontend)

lazy val shared = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "de.lolhens" %%% "cats-effect-utils" % V.catsEffectUtils,
      "de.lolhens" %%% "remote-io-http4s" % V.remoteIo,
      "io.circe" %%% "circe-core" % V.circe,
      "io.circe" %%% "circe-generic" % V.circe,
      "io.circe" %%% "circe-parser" % V.circe,
      "org.http4s" %%% "http4s-circe" % V.http4s,
      "org.http4s" %%% "http4s-client" % V.http4s,
      "org.scodec" %%% "scodec-bits" % V.scodecBits,
      "org.typelevel" %%% "cats-core" % V.cats,
      "org.typelevel" %%% "cats-effect" % V.catsEffect,
    )
  )

lazy val sharedJvm = shared.jvm
lazy val sharedJs = shared.js

import org.scalajs.linker.interface.OutputPatterns

lazy val frontend = project
  .enablePlugins(ScalaJSWebjarPlugin)
  .dependsOn(sharedJs)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "core-bundle-cats_effect" % V.scalajsReact,
      "com.github.japgolly.scalajs-react" %%% "extra" % V.scalajsReact,
      "org.scala-js" %%% "scalajs-dom" % V.scalajsDom,
      "org.http4s" %%% "http4s-dom" % V.http4sDom
    ),

    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
        .withOutputPatterns(OutputPatterns.fromJSFile("%s.mjs"))
    },
    scalaJSUseMainModuleInitializer := true,
  )

lazy val frontendWebjar = frontend.webjar
  .settings(
    webjarAssetReferenceType := Some("http4s"),
    libraryDependencies += "org.http4s" %% "http4s-server" % V.http4s,
  )

lazy val server = project
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(sharedJvm, frontendWebjar)
  .settings(commonSettings)
  .settings(
    name := "cdn-cache",

    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % V.logbackClassic,
      "de.lolhens" %% "fs2-utils" % V.fs2Utils,
      "de.lolhens" %% "http4s-brotli" % V.http4sBrotli,
      "de.lolhens" %% "http4s-proxy" % V.http4sProxy,
      "de.lhns" %% "http4s-spa" % V.http4sSpa,
      "org.bidib.com.github.markusbernhardt" % "proxy-vole" % V.proxyVole,
      "org.http4s" %% "http4s-ember-server" % V.http4s,
      "org.http4s" %% "http4s-dsl" % V.http4s,
      "org.http4s" %% "http4s-jdk-http-client" % V.http4sJdkHttpClient,
    )
  )
