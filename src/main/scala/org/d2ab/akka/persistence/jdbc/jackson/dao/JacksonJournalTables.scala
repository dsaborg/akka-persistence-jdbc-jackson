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
  NOTE: This is a modified version of JournalTables.scala from the original work akka-persistence-jdbc.
  See: https://github.com/dnvriend/akka-persistence-jdbc/tree/e001c73cd3e7e7e7cbdd824692a0511436b15e6c/src/main/scala/akka/persistence/jdbc/journal/dao/JournalTables.scala
  See: https://github.com/dnvriend/akka-persistence-jdbc
 */

package org.d2ab.akka.persistence.jdbc.jackson.dao

import akka.persistence.jdbc.config.JournalTableConfiguration
import org.json4s.JValue

trait JacksonJournalTables {
  val profile: SlickPgPostgresProfile

  import profile.api._

  def journalTableCfg: JournalTableConfiguration

  private val jacksonColumnNames = JacksonColumnNames()

  class Journal(_tableTag: Tag) extends Table[JacksonJournalRow](_tableTag, _schemaName = journalTableCfg.schemaName, _tableName = journalTableCfg.tableName) {
    def * = (ordering, deleted, persistenceId, sequenceNumber, payload, manifest, writerUuid, tags) <> (JacksonJournalRow.tupled, JacksonJournalRow.unapply)

    val ordering = column[Long](journalTableCfg.columnNames.ordering, O.AutoInc)
    val persistenceId = column[String](journalTableCfg.columnNames.persistenceId, O.Length(255, varying = true))
    val sequenceNumber = column[Long](journalTableCfg.columnNames.sequenceNumber)
    val deleted = column[Boolean](journalTableCfg.columnNames.deleted, O.Default(false))
    val tags = column[Option[String]](journalTableCfg.columnNames.tags, O.Length(255, varying = true))

    val payload = column[JValue](jacksonColumnNames.payload, O.SqlType("JSONB"))
    val manifest = column[String](jacksonColumnNames.manifest, O.Length(255, varying = true))
    val writerUuid = column[String](jacksonColumnNames.writerUuid, O.Length(36))

    val pk = primaryKey("journal_pk", (persistenceId, sequenceNumber))
    val orderingIdx = index("journal_ordering_idx", ordering, unique = true)
  }

  lazy val JournalTable = new TableQuery(tag => new Journal(tag))
}

case class JacksonColumnNames(payload: String = "payload", manifest: String = "manifest", writerUuid: String = "writer_uuid")
