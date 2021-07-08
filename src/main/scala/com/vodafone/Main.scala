package com.vodafone

import java.net.InetAddress
import java.nio.file._
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.kafka._
import akka.kafka.scaladsl.Consumer
import akka.stream.alpakka.file.scaladsl.LogRotatorSink
import akka.stream.alpakka.ftp.scaladsl.Sftp
import akka.stream.alpakka.ftp.{FtpCredentials, SftpIdentity, SftpSettings}
import akka.stream.scaladsl.{Flow, Keep}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization._
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object Main extends App {
  final val log = LoggerFactory.getLogger(getClass)
  val typeConf = ConfigFactory.load
  import scala.jdk.CollectionConverters._

  implicit val actorSystem: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "fromKafkaToSftpFile")
  implicit val executionContext: ExecutionContext = actorSystem.executionContext

  val inputTopic = typeConf getString "app-conf.source-topic"
  private val groupId = typeConf getString "app-conf.group-id"

  // Set up Kafka producer sink
  val kafkaServers = typeConf getStringList "app-conf.broker-list" asScala
  val kafkaSecurityProtocol = typeConf getString "app-conf.security-protocol"
  val kafkaSecurityMechanism = typeConf getString "app-conf.sasl-mechanism"

  log.info(s"kafkaServers: $kafkaServers")
  log.info(s"kafkaSecurityProtocol: $kafkaSecurityProtocol")
  log.info(s"inputTopic: $inputTopic")
  log.info(s"groupId: $groupId")
  log.info(s"kafkaSecurityMechanism: $kafkaSecurityMechanism")

  val kafkaProperties = Map(
    "bootstrap.servers" -> kafkaServers.mkString(","),
    "security.protocol" -> kafkaSecurityProtocol,
    "sasl.mechanism" -> kafkaSecurityMechanism,
    "sasl.kerberos.service.name" -> "kafka"
  )

  // configure Kafka consumer
  val kafkaConsumerSettings = ConsumerSettings(actorSystem.toClassic, new StringDeserializer, new StringDeserializer)
    .withProperties(kafkaProperties)
    .withGroupId(groupId)
    .withProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG,
      typeConf getString "app-conf.consumed-message-auto-commit-ms-interval")
    .withStopTimeout(0.seconds)

  //LogRotation strategy
  val destinationDir = FileSystems.getDefault.getPath(typeConf getString "app-conf.remote-output-dir")
  val filePrefix = typeConf getString "app-conf.file-prefix"
  val fileExtension = typeConf getString "app-conf.file-extension"
  val formatter = DateTimeFormatter.ofPattern(s"'$filePrefix'ddMMyyyy'$fileExtension'")

  val timeBasedTriggerCreator: () => ByteString => Option[Path] = () => {
    var currentFilename: Option[String] = None
    (_: ByteString) => {
      val newName = LocalDateTime.now().format(formatter)
      if (currentFilename.contains(newName)) {
        None
      } else {
        currentFilename = Some(newName)
        Some(destinationDir.resolve(newName))
      }
    }
  }

  //sftp settings
  private val pathToIdentityFile = typeConf getString "app-conf.localhost-private-or-public-key-path"
  private val hostname = typeConf getString "app-conf.remote-host"
  private val sftpPort = typeConf getInt "app-conf.sftp-server-port"
  //TODO -- secrets to be managed
  private val username = typeConf getString "app-conf.remote-host-user"
  private val password = typeConf getString "app-conf.remote-host-user-passwd"

  val identity = SftpIdentity.createFileSftpIdentity(pathToIdentityFile)
  val credentials = FtpCredentials.create(username, password)
  val sftpSettings: SftpSettings = SftpSettings(InetAddress.getByName(hostname))
    .withPort(sftpPort)
    .withSftpIdentity(identity)
    .withStrictHostKeyChecking(false)
    .withCredentials(credentials)

  //Remote file sink
  val sink = (path: Path) =>
    Flow[ByteString]
//      .via(Compression.gzip)
      .toMat(Sftp.toPath(s"$destinationDir/${path.getFileName.toString}", sftpSettings))(Keep.right)

  //Consume from kafka and write to remote host
  val read = Consumer
//      .atMostOnceSource(kafkaConsumerSettings, Subscriptions.topics(inputTopic))
    .plainSource(kafkaConsumerSettings, Subscriptions.topics(inputTopic))
//    .sourceWithOffsetContext(kafkaConsumerSettings, Subscriptions.topics(inputTopic))
    .map { consumedRecord =>
      ByteString(s"${consumedRecord.value}\n")
    }
    .runWith(LogRotatorSink.withSinkFactory(timeBasedTriggerCreator, sink))

  read
    .recover {
      case f =>
        f.printStackTrace()
    }

}