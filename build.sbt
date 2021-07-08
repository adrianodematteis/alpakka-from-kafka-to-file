//##########

import sbt._
import sbt.Keys._
import com.typesafe.sbt.packager.SettingsHelper._
import NativePackagerHelper._

makeDeploymentSettings(Universal, packageZipTarball in Universal, "zip")

val AkkaVersion = "2.6.4"
val AlpakkaVersion = "2.0.0"
val AlpakkaKafkaVersion = "2.0.2"

lazy val alpakkaFromKafkaToFile = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(UniversalPlugin)
  .enablePlugins(UniversalDeployPlugin)
  .settings(
    name := "alpakka-from-kafka-to-file",
    version := "0.1",
    version := "0.1",
    scalaVersion := "2.12.11"
  )
  .settings(
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-language:implicitConversions",
      "-language:postfixOps",
      "-language:reflectiveCalls"
    ),
    libraryDependencies ++= Seq(
      "com.lightbend.akka" %% "akka-stream-alpakka-file" % AlpakkaVersion,
      "com.lightbend.akka" %% "akka-stream-alpakka-ftp" % "2.0.2",
      "com.typesafe.akka" %% "akka-stream-kafka" % AlpakkaKafkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.typesafe" % "config" % "1.3.1",
      //ftp dependencies
      "org.apache.sshd" % "sshd-common" % "2.5.1",
      "org.apache.sshd" % "sshd-core" % "2.5.1",
      "org.apache.sshd" % "sshd-scp" % "2.5.1",
      "org.apache.sshd" % "sshd-sftp" % "2.5.1",
      "org.apache.sshd" % "sshd-mina" % "2.5.1"
    )
  )
  .settings(
    resourceDirectories in Compile <+= baseDirectory / "src/main/resources",
//    mappings in Universal ++= directory("src/main/resources/"),
    assemblyJarName in assembly := 	"alpakka-from-kafka-to-sftp-file-assembly-0.1.jar",
    mappings in Universal := {
      val universalMappings = (mappings in Universal).value
      val fatJar = (assembly in Compile).value
      val filtered = universalMappings filter {
        case (file, fileName) => !fileName.endsWith(".jar")
      }
      filtered :+ (fatJar -> ("lib/" + fatJar.getName))
    },
    mappings in Universal <++= sourceDirectory map (src => directory (src / "main" / "resources"))
  )