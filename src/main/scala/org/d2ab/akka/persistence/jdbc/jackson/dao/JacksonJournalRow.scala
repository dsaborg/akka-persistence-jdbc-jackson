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
  NOTE: This is a modified version of package.scala from the original work akka-persistence-jdbc.
  See: https://github.com/dnvriend/akka-persistence-jdbc/tree/e001c73cd3e7e7e7cbdd824692a0511436b15e6c/src/main/scala/akka/persistence/jdbc/package.scala
  See: https://github.com/dnvriend/akka-persistence-jdbc
 */

package org.d2ab.akka.persistence.jdbc.jackson.dao

import org.json4s.JValue

final case class JacksonJournalRow(ordering: Long, deleted: Boolean, persistenceId: String, sequenceNumber: Long,
                                   payload: JValue, manifest: String, writerUuid: String, tags: Option[String] = None)
