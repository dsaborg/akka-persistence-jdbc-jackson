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


package org.d2ab.akka.persistence.jdbc.jackson

import akka.NotUsed
import akka.persistence.PersistentRepr
import akka.persistence.jdbc.OrderedJournalRow
import akka.persistence.jdbc.config.ReadJournalConfig
import akka.persistence.jdbc.query.dao._
import akka.serialization.Serialization
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import slick.jdbc.JdbcBackend._
import slick.jdbc.JdbcProfile

import scala.collection.immutable._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class JacksonReadJournalDao(val db: Database, val jdbcProfile: JdbcProfile, val readJournalConfig: ReadJournalConfig, serialization: Serialization)(implicit ec: ExecutionContext, mat: Materializer) extends ReadJournalDao {
  private val profile = jdbcProfile.asInstanceOf[SlickPgPostgresProfile]
  val queries = new JacksonReadJournalQueries(profile, readJournalConfig.journalTableConfiguration)
  val serializer = new JacksonJournalSerializer(serialization, readJournalConfig.pluginConfig.tagSeparator)

  import profile.api._

  override def allPersistenceIdsSource(max: Long): Source[String, NotUsed] =
    Source.fromPublisher(db.stream(queries.allPersistenceIdsDistinct(max).result))

  override def eventsByTag(tag: String, offset: Long, maxOffset: Long, max: Long): Source[Try[(PersistentRepr, Set[String], OrderedJournalRow)], NotUsed] =
    Source.fromPublisher(db.stream(queries.eventsByTag(s"%$tag%", offset, maxOffset, max).result))
      .via(serializer.deserializeFlow)

  override def messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Source[Try[PersistentRepr], NotUsed] =
    Source.fromPublisher(db.stream(queries.messagesQuery(persistenceId, fromSequenceNr, toSequenceNr, max).result))
      .via(serializer.deserializeFlowWithoutTags)

  override def journalSequence(offset: Long, limit: Long): Source[Long, NotUsed] =
    Source.fromPublisher(db.stream(queries.journalSequenceQuery(offset, limit).result))

  override def maxJournalSequence(): Future[Long] =
    db.run(queries.maxJournalSequenceQuery.result)
}
