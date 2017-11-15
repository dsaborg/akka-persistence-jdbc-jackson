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
  NOTE: This is a modified version of ByteArrayJournalSerializer.scala from the original work akka-persistence-jdbc.
  See: https://github.com/dnvriend/akka-persistence-jdbc/tree/e001c73cd3e7e7e7cbdd824692a0511436b15e6c/src/main/scala/akka/persistence/jdbc/journal/dao/ByteArrayJournalSerializer.scala
  See: https://github.com/dnvriend/akka-persistence-jdbc
 */

package org.d2ab.akka.persistence.jdbc.jackson.dao

import akka.persistence.PersistentRepr
import akka.persistence.jdbc.journal.dao.{decodeTags, encodeTags}
import akka.persistence.jdbc.serialization.FlowPersistentReprSerializer
import akka.serialization.Serialization
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import org.json4s._
import org.json4s.native.Document

import scala.collection.immutable._
import scala.util.Try

class JacksonJournalSerializer(serialization: Serialization, separator: String) extends FlowPersistentReprSerializer[JacksonJournalRow] {
  val objectMapper: ObjectMapper = {
    val mapper = new ObjectMapper
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    mapper.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false)
    mapper.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false)
    mapper.registerModules(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES), new JavaTimeModule)
    mapper
  }

  val jsonMethods = org.json4s.native.JsonMethods.asInstanceOf[JsonMethods[Document]]

  import SlickPgPostgresProfile.jsonMethods._

  override def serialize(persistentRepr: PersistentRepr, tags: Set[String]): Try[JacksonJournalRow] = {
    for {
      jsonPayload <- Try(parse(objectMapper.writeValueAsString(persistentRepr.payload)))
    } yield JacksonJournalRow(
      ordering = Long.MinValue, // initialized by database
      persistentRepr.deleted,
      persistentRepr.persistenceId,
      persistentRepr.sequenceNr,
      jsonPayload,
      manifest = persistentRepr.payload.getClass.getName,
      persistentRepr.writerUuid,
      encodeTags(tags, separator))
  }

  override def deserialize(journalRow: JacksonJournalRow): Try[(PersistentRepr, Set[String], JacksonJournalRow)] =
    for {
      javaPayload <- Try(objectMapper.readValue(compact(render(journalRow.payload)), Class.forName(journalRow.manifest)))
    } yield (
      PersistentRepr(
        javaPayload,
        journalRow.sequenceNumber,
        journalRow.persistenceId,
        journalRow.manifest,
        journalRow.deleted,
        sender = null,
        journalRow.writerUuid
      ),
      decodeTags(journalRow.tags, separator),
      journalRow
    )
}
