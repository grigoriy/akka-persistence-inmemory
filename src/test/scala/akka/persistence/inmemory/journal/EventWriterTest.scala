/*
 * Copyright 2016 Dennis Vriend
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

package akka.persistence.inmemory.journal

import akka.persistence.PersistentRepr
import akka.persistence.inmemory.TestSpec
import akka.persistence.query.scaladsl.EventWriter.WriteEvent
import akka.persistence.query.scaladsl.{ CurrentEventsByPersistenceIdQuery, CurrentEventsByTagQuery, EventWriter, ReadJournal }
import akka.persistence.query.{ EventEnvelope, PersistenceQuery }
import akka.stream.scaladsl.extension.Implicits._
import akka.stream.scaladsl.{ Sink, Source }

import scala.collection.immutable._

class EventWriterTest extends TestSpec {
  lazy val journal = PersistenceQuery(system).readJournalFor("inmemory-read-journal")
    .asInstanceOf[ReadJournal with EventWriter with CurrentEventsByPersistenceIdQuery with CurrentEventsByTagQuery]

  it should "write events without tags" in {
    Source(List("a", "b", "c", "d", "e", "f", "g")).zipWithIndex.map {
      case (pl, seqNr) =>
        WriteEvent(PersistentRepr(pl, seqNr, "foo"), Set.empty[String])
    }.via(journal.eventWriter).runWith(Sink.ignore).toTry should be a 'success

    withTestProbe(journal.currentEventsByPersistenceId("foo", 0, Long.MaxValue)) { tp =>
      tp.request(Long.MaxValue)
      tp.requestNext(EventEnvelope(1, "foo", 1, "a"))
      tp.requestNext(EventEnvelope(2, "foo", 2, "b"))
      tp.requestNext(EventEnvelope(3, "foo", 3, "c"))
      tp.requestNext(EventEnvelope(4, "foo", 4, "d"))
      tp.requestNext(EventEnvelope(5, "foo", 5, "e"))
      tp.requestNext(EventEnvelope(6, "foo", 6, "f"))
      tp.requestNext(EventEnvelope(7, "foo", 7, "g"))
      tp.expectComplete()
    }

    withTestProbe(journal.currentEventsByPersistenceId("foobar", 0, Long.MaxValue)) { tp =>
      tp.request(Long.MaxValue)
      tp.expectComplete()
    }
  }

  it should "write events with tags" in {
    Source(List("a", "b", "c", "d", "e", "f", "g")).zipWithIndex.map {
      case (pl, seqNr) =>
        WriteEvent(PersistentRepr(pl, seqNr, "foobar"), Set("bar"))
    }.via(journal.eventWriter).runWith(Sink.ignore).toTry should be a 'success

    withTestProbe(journal.currentEventsByTag("bar", 0)) { tp =>
      tp.request(Long.MaxValue)
      tp.requestNext(EventEnvelope(8, "foobar", 1, "a"))
      tp.requestNext(EventEnvelope(9, "foobar", 2, "b"))
      tp.requestNext(EventEnvelope(10, "foobar", 3, "c"))
      tp.requestNext(EventEnvelope(11, "foobar", 4, "d"))
      tp.requestNext(EventEnvelope(12, "foobar", 5, "e"))
      tp.requestNext(EventEnvelope(13, "foobar", 6, "f"))
      tp.requestNext(EventEnvelope(14, "foobar", 7, "g"))
      tp.expectComplete()
    }

    withTestProbe(journal.currentEventsByTag("unknown", 0)) { tp =>
      tp.request(Long.MaxValue)
      tp.expectComplete()
    }
  }
}
