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

package org.d2ab.akka.persistence.jdbc.jackson

import java.nio.charset.Charset

import akka.actor.ActorRef
import akka.persistence.PersistentRepr
import akka.persistence.jdbc.journal.dao.{decodeTags, encodeTags}
import akka.persistence.jdbc.serialization.FlowPersistentReprSerializer
import akka.serialization.{Serialization, SerializerWithStringManifest}
import org.d2ab.akka.persistence.jdbc.jackson.JacksonJournalSerializer.UTF8
import org.json4s._

import scala.collection.immutable._
import scala.util.{Success, Try}

object JacksonJournalSerializer {
  private val UTF8: Charset = Charset.forName("UTF-8")
}

class JacksonJournalSerializer(serialization: Serialization, separator: String) extends FlowPersistentReprSerializer[JacksonJournalRow] {
  import SlickPgPostgresProfile.jsonMethods._

  override def serialize(persistentRepr: PersistentRepr, tags: Set[String]): Try[JacksonJournalRow] = {
    val payload = persistentRepr.payload.asInstanceOf[AnyRef]
    val serializer = serialization.findSerializerFor(payload).asInstanceOf[SerializerWithStringManifest]
    for {
      jsonPayload <- serialization.serialize(payload).map(new String(_, UTF8)).map(parse(_))
      sender <- if (persistentRepr.sender == null) Success(None) else serialization.serialize(persistentRepr.sender).map(Some(_))
    } yield {
      JacksonJournalRow(
        ordering = Long.MinValue, // initialized by database
        persistentRepr.deleted,
        persistentRepr.persistenceId,
        persistentRepr.sequenceNr,
        jsonPayload,
        serializer.identifier,
        serializer.manifest(payload),
        persistentRepr.writerUuid,
        sender,
        encodeTags(tags, separator))
    }
  }

  override def deserialize(journalRow: JacksonJournalRow): Try[(PersistentRepr, Set[String], JacksonJournalRow)] = {
    val jsonPayload = compact(render(journalRow.payload))
    for {
      javaPayload <- serialization.deserialize(jsonPayload.getBytes(UTF8), journalRow.identifier, journalRow.manifest)
      sender <- journalRow.sender.map(s => serialization.deserialize(s, classOf[ActorRef])).getOrElse(Success(null))
    } yield (
      PersistentRepr(
        javaPayload,
        journalRow.sequenceNumber,
        journalRow.persistenceId,
        journalRow.manifest,
        journalRow.deleted,
        sender,
        journalRow.writerUuid
      ),
      decodeTags(journalRow.tags, separator),
      journalRow
    )
  }
}
