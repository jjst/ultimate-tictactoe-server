lazy val circeVersion = "0.13.0"
lazy val http4sVersion = "0.21.0"

onChangedBuildSource := ReloadOnSourceChanges

organization    := "eu.jjst"
scalaVersion    := "2.13.1"
name := "akka http websockets"

libraryDependencies ++= Seq(
  "org.http4s"     %% "http4s-blaze-server" % http4sVersion,
  "org.http4s"     %% "http4s-dsl"          % http4sVersion,

  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-generic-extras" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,

  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",

  "org.scalatest"     %% "scalatest"            % "3.1.1"         % Test
)
