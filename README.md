akka-persistence-jdbc-jackson
=============================

Simple extension of [akka-persistence-jdbc](https://github.com/dnvriend/akka-persistence-jdbc)
to support writing [Akka](https://akka.io/) message fields across multiple additional columns,
with the payload serialized to JSON using [slick-pg](https://github.com/tminglei/slick-pg)
and [json4s](http://json4s.org/).
