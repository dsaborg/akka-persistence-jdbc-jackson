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
  NOTE: This is a modified version of ProjectAutoPlugin.scala from the original work akka-persistence-jdbc.
  See: https://github.com/dnvriend/akka-persistence-jdbc/tree/e001c73cd3e7e7e7cbdd824692a0511436b15e6c/project/ProjectAutoPlugin.scala
  See: https://github.com/dnvriend/akka-persistence-jdbc
 */

name := "akka-persistence-jdbc-jackson"

organization := "org.d2ab"

version := "1.0.1"

scalaVersion := "2.12.4"

resolvers += Resolver.mavenLocal

libraryDependencies ++= {
  val akkaVersion = "2.5.6"
  val slickPgVersion = "0.15.4"
  val json4sVersion = "3.5.3"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
    "com.github.dnvriend" %% "akka-persistence-jdbc" % "3.0.1-ORDERING",
    "com.github.tminglei" %% "slick-pg" % slickPgVersion,
    "com.github.tminglei" %% "slick-pg_json4s" % slickPgVersion,
    "org.json4s" %% "json4s-native" % json4sVersion
  )
}

licenses += ("Apache-2.0", url("http://opensource.org/licenses/apache2.0.php"))
