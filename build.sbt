lazy val akkaHttpVersion = "10.1.11"
lazy val akkaVersion    = "2.5.26"
lazy val circeVersion = "0.13.0"

onChangedBuildSource := ReloadOnSourceChanges

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "eu.jjst",
      scalaVersion    := "2.13.1"
    )),
    name := "akka http websockets",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-xml"        % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream"          % akkaVersion,

      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-generic-extras" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,

      "com.typesafe.akka" %% "akka-http-testkit"    % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-testkit"         % akkaVersion     % Test,
      "com.typesafe.akka" %% "akka-stream-testkit"  % akkaVersion     % Test,
      "org.scalatest"     %% "scalatest"            % "3.1.1"         % Test
    )
  )
