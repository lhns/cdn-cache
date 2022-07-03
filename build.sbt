ThisBuild / scalaVersion := "2.13.7"
ThisBuild / name := (server / name).value
name := (ThisBuild / name).value

lazy val commonSettings: Seq[Setting[_]] = Seq(
  version := {
    val Tag = "refs/tags/(.*)".r
    sys.env.get("CI_VERSION").collect { case Tag(tag) => tag }
      .getOrElse("0.0.1-SNAPSHOT")
  },

  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),

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

val V = new {
  val circe = "0.14.1"
  val http4s = "0.23.11"
  val scalajsReact = "2.0.0"
}

lazy val root = project.in(file("."))
  .settings(
    publishArtifact := false
  )
  .aggregate(server)

lazy val common = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "de.lolhens" %%% "cats-effect-utils" % "0.2.0",
      "de.lolhens" %%% "remote-io-http4s" % "0.0.1",
      "io.circe" %%% "circe-core" % V.circe,
      "io.circe" %%% "circe-generic" % V.circe,
      "io.circe" %%% "circe-parser" % V.circe,
      "org.http4s" %%% "http4s-circe" % V.http4s,
      "org.http4s" %%% "http4s-client" % V.http4s,
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
      "com.github.japgolly.scalajs-react" %%% "core-bundle-cats_effect" % V.scalajsReact,
      "com.github.japgolly.scalajs-react" %%% "extra" % V.scalajsReact,
      "org.scala-js" %%% "scalajs-dom" % "2.1.0",
      "org.http4s" %%% "http4s-dom" % "0.2.0"
    ),

    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
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
  .dependsOn(commonJvm, frontendWebjar)
  .settings(commonSettings)
  .settings(
    name := "cdn-cache",

    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.11",
      "de.lolhens" %% "fs2-utils" % "0.2.0",
      "de.lolhens" %% "http4s-brotli" % "0.4.0",
      "de.lolhens" %% "http4s-proxy" % "0.4.0",
      "de.lolhens" %% "http4s-spa" % "0.2.1",
      "org.bidib.com.github.markusbernhardt" % "proxy-vole" % "1.0.17",
      "org.http4s" %% "http4s-blaze-server" % V.http4s,
      "org.http4s" %% "http4s-dsl" % V.http4s,
      "org.http4s" %% "http4s-scalatags" % V.http4s,
      "org.http4s" %% "http4s-jdk-http-client" % "0.7.0",
    )
  )
