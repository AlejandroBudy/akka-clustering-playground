ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

lazy val akkaVersion     = "2.6.19"
lazy val protobufVersion = "3.6.1"

lazy val root = (project in file("."))
  .settings(
    name := "akka-remote-clustering",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor"            % akkaVersion,
      "com.typesafe.akka" %% "akka-remote"           % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster"          % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-tools"    % akkaVersion
    )
  )
