name := "Scala template"

version := "1.0.0"

Linux / maintainer := "Kotaro Sakamoto <sakamoto.github@besna.institute>"

scalaVersion := "2.13.6"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "UTF-8", // yes, this is 2 args
  "-feature",
  "-unchecked",
  "-Xlint"
)

Compile / doc / sources := Seq.empty
Test / doc / sources := Seq.empty

lazy val akkaVersion     = "2.6.15"
lazy val akkaHttpVersion = "10.2.6"

lazy val scalafmtSettings = Seq(
  scalafmtOnCompile := true,
  version := "2.7.5"
)

lazy val wartremoverSettings = Seq(
  Compile / compile / wartremoverWarnings ++= Warts.allBut(Wart.Throw),
  Test / compile / wartremoverWarnings ++= Warts.allBut(Wart.Throw),
  wartremoverExcluded += baseDirectory.value / "target"
)

lazy val root = (project in file("."))
  .settings(scalafmtSettings: _*)
  .settings(wartremoverSettings: _*)
  .enablePlugins(AkkaGrpcPlugin, DockerPlugin, JavaAppPackaging)
  .settings(
    dockerExposedPorts := Seq(8080),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-discovery" % akkaVersion,
      "com.typesafe.akka" %% "akka-pki" % akkaVersion,

      // The Akka HTTP overwrites are required because Akka-gRPC depends on 10.1.x
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http2-support" % akkaHttpVersion,
      "org.typelevel" %% "cats-core" % "2.6.1",
      "ch.qos.logback" % "logback-classic" % "1.2.5",
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
      "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
      "org.scalatest" %% "scalatest" % "3.2.9" % Test
    )
  )
