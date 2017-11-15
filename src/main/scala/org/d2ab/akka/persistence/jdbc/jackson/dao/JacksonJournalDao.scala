/*
 * Copyright 2016 Dennis Vriend, 2017 Daniel Skogquist Åborg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
  NOTE: This is a modified version of ByteArrayJournalDao.scala from the original work akka-persistence-jdbc.
  See: https://github.com/dnvriend/akka-persistence-jdbc/tree/e001c73cd3e7e7e7cbdd824692a0511436b15e6c/src/main/scala/akka/persistence/jdbc/journal/dao/ByteArrayJournalDao.scala
  See: https://github.com/dnvriend/akka-persistence-jdbc
 */

package org.d2ab.akka.persistence.jdbc.jackson.dao

import akka.NotUsed
import akka.persistence.jdbc.config._
import akka.persistence.jdbc.journal.dao.JournalDao
import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.serialization.Serialization
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy, QueueOfferResult}
import slick.jdbc.JdbcBackend._
import slick.jdbc.JdbcProfile

import scala.collection.immutable._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

class JacksonJournalDao(val db: Database, val profile: JdbcProfile, val journalConfig: JournalConfig, serialization: Serialization)(implicit val ec: ExecutionContext, val mat: Materializer) extends JournalDao {
  val queries = new JacksonJournalQueries(profile.asInstanceOf[SlickPgPostgresProfile], journalConfig.journalTableConfiguration)
  val serializer = new JacksonJournalSerializer(serialization, journalConfig.pluginConfig.tagSeparator)

  import journalConfig.daoConfig.{batchSize, bufferSize, parallelism}
  import profile.api._

  private val writeQueue = Source.queue[(Promise[Unit], Seq[JacksonJournalRow])](bufferSize, OverflowStrategy.dropNew)
    .batchWeighted[(Seq[Promise[Unit]], Seq[JacksonJournalRow])](batchSize, _._2.size, tup => Vector(tup._1) -> tup._2) {
    case ((promises, rows), (newPromise, newRows)) => (promises :+ newPromise) -> (rows ++ newRows)
  }.mapAsync(parallelism) {
    case (promises, rows) =>
      writeJournalRows(rows)
        .map(unit => promises.foreach(_.success(unit)))
        .recover { case t => promises.foreach(_.failure(t)) }
  }.toMat(Sink.ignore)(Keep.left).run()

  private def queueWriteJournalRows(xs: Seq[JacksonJournalRow]): Future[Unit] = {
    val promise = Promise[Unit]()
    writeQueue.offer(promise -> xs).flatMap {
      case QueueOfferResult.Enqueued =>
        promise.future
      case QueueOfferResult.Failure(t) =>
        Future.failed(new Exception("Failed to write journal row batch", t))
      case QueueOfferResult.Dropped =>
        Future.failed(new Exception(s"Failed to enqueue journal row batch write, the queue buffer was full ($bufferSize elements) please check the jdbc-journal.bufferSize setting"))
      case QueueOfferResult.QueueClosed =>
        Future.failed(new Exception("Failed to enqueue journal row batch write, the queue was closed"))
    }
  }

  private def writeJournalRows(xs: Seq[JacksonJournalRow]): Future[Unit] = for {
    _ <- db.run(queries.writeJournalRows(xs))
  } yield ()

  /**
    * @see [[akka.persistence.journal.AsyncWriteJournal.asyncWriteMessages(messages)]]
    */
  def asyncWriteMessages(messages: Seq[AtomicWrite]): Future[Seq[Try[Unit]]] = {
    val serializedTries = serializer.serialize(messages)

    // If serialization fails for some AtomicWrites, the other AtomicWrites may still be written
    val rowsToWrite = for {
      serializeTry <- serializedTries
      row <- serializeTry.getOrElse(Seq.empty)
    } yield row
    def resultWhenWriteComplete =
      if (serializedTries.forall(_.isSuccess)) Nil else serializedTries.map(_.map(_ => ()))

    queueWriteJournalRows(rowsToWrite).map(_ => resultWhenWriteComplete)
  }

  override def delete(persistenceId: String, maxSequenceNr: Long): Future[Unit] = for {
    _ <- db.run(queries.markJournalMessagesAsDeleted(persistenceId, maxSequenceNr))
  } yield ()

  override def highestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = for {
    maybeHighestSeqNo <- db.run(queries.highestSequenceNrForPersistenceId(persistenceId).result.headOption)
  } yield maybeHighestSeqNo.getOrElse(0L)

  override def messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Source[Try[PersistentRepr], NotUsed] =
    Source.fromPublisher(db.stream(queries.messagesQuery(persistenceId, fromSequenceNr, toSequenceNr, max).result))
      .via(serializer.deserializeFlowWithoutTags)
}
