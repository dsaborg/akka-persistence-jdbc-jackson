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
  NOTE: This is a modified version of JournalQueries.scala from the original work akka-persistence-jdbc.
  See: https://github.com/dnvriend/akka-persistence-jdbc/tree/e001c73cd3e7e7e7cbdd824692a0511436b15e6c/src/main/scala/akka/persistence/jdbc/journal/dao/JournalQueries.scala
  See: https://github.com/dnvriend/akka-persistence-jdbc
 */

package org.d2ab.akka.persistence.jdbc.jackson

import akka.persistence.jdbc.config.JournalTableConfiguration

class JacksonJournalQueries(val profile: SlickPgPostgresProfile, override val journalTableCfg: JournalTableConfiguration) extends JacksonJournalTables {
  import profile.api._

  private val JournalTableC = Compiled(JacksonJournalTable)

  def writeJournalRows(xs: Seq[JacksonJournalRow]) =
    JournalTableC ++= xs.sortBy(_.sequenceNumber)

  private def selectAllJournalForPersistenceId(persistenceId: Rep[String]) =
    JacksonJournalTable.filter(_.persistenceId === persistenceId).sortBy(_.sequenceNumber.desc)

  def markJournalMessagesAsDeleted(persistenceId: String, maxSequenceNr: Long) =
    JacksonJournalTable
      .filter(_.persistenceId === persistenceId)
      .filter(_.sequenceNumber <= maxSequenceNr)
      .filter(_.deleted === false)
      .map(_.deleted).update(true)

  private def _highestSequenceNrForPersistenceId(persistenceId: Rep[String]): Query[Rep[Long], Long, Seq] =
    selectAllJournalForPersistenceId(persistenceId).map(_.sequenceNumber).take(1)

  val highestSequenceNrForPersistenceId = Compiled(_highestSequenceNrForPersistenceId _)

  private def _selectByPersistenceIdAndMaxSequenceNumber(persistenceId: Rep[String], maxSequenceNr: Rep[Long]) =
    selectAllJournalForPersistenceId(persistenceId).filter(_.sequenceNumber <= maxSequenceNr)

  val selectByPersistenceIdAndMaxSequenceNumber = Compiled(_selectByPersistenceIdAndMaxSequenceNumber _)

  private def _allPersistenceIdsDistinct: Query[Rep[String], String, Seq] =
    JacksonJournalTable.map(_.persistenceId).distinct

  val allPersistenceIdsDistinct = Compiled(_allPersistenceIdsDistinct)

  def journalRowByPersistenceIds(persistenceIds: Iterable[String]): Query[Rep[String], String, Seq] = for {
    query <- JacksonJournalTable.map(_.persistenceId)
    if query inSetBind persistenceIds
  } yield query

  private def _messagesQuery(persistenceId: Rep[String], fromSequenceNr: Rep[Long], toSequenceNr: Rep[Long], max: ConstColumn[Long]) =
    JacksonJournalTable
      .filter(_.persistenceId === persistenceId)
      .filter(_.deleted === false)
      .filter(_.sequenceNumber >= fromSequenceNr)
      .filter(_.sequenceNumber <= toSequenceNr)
      .sortBy(_.sequenceNumber.asc)
      .take(max)

  val messagesQuery = Compiled(_messagesQuery _)
}
