package akka.statsd
package transport

import org.scalatest.funspec.AnyFunSpecLike

class MultiMetricQueueFunSpec
  extends TestKit("mmq-spec")
  with AnyFunSpecLike {

  describe("A MultiMetricQueue") {
    describe("when empty") {
      it("returns a None payload") {
        assert(MultiMetricQueue(512).payload() == None)
      }
    }

    describe("when having a single element") {
      it("returns that element with no newline") {
        val subject = MultiMetricQueue(512).enqueue("message")
        assert(subject.payload() == Some("message"))
      }
    }

    describe("when having two elements") {
      it("returns the elements separated by a newline") {
        val subject = MultiMetricQueue(512).enqueue("message1").enqueue("message2")
        assert(subject.payload() ==
          Some("""message1
            |message2""".stripMargin))
      }
    }

    describe("when the elements cross the packetSize boundary") {
      it("first returns one element then another") {
        val subject = MultiMetricQueue(4).enqueue("dog").enqueue("cat")
        assert(subject.payload() == Some("dog"))
        assert(subject.payload() == Some("cat"))
      }
    }

    describe("when a UTF-8 character crosses the packetSize boundary") {
      it("first returns one element then another") {
        val subject = MultiMetricQueue(2).enqueue("ü").enqueue("u")
        assert(subject.payload() == Some("ü"))
        assert(subject.payload() == Some("u"))
      }
    }

    describe("when a single message goes over the packetSize boundary") {
      it("drops the message") {
        val subject = MultiMetricQueue(4).enqueue("12345")
        assert(subject.payload() == None)
        assert(subject.size == 0)
      }
    }

    describe("when the first message goes over the packetSize boundary") {
      it("drops the oversized message but continues with other messages") {
        val subject = MultiMetricQueue(4).enqueue("12345").enqueue("1").enqueue("2")
        assert(subject.payload() ==
          Some("""1
            |2""".stripMargin))
      }
    }

    describe("when any message goes over the packetSize boundary") {
      it("drops the oversized message but continues with other messages") {
        val subject = MultiMetricQueue(4).enqueue("1").enqueue("12345").enqueue("2")
        assert(subject.payload() ==
          Some("""1
            |2""".stripMargin))
      }
    }

    describe("when the queue is larger than the packet size") {
      it("returns as many payloads as necessary to empty the queue when calling flushQueue") {
        val subject = MultiMetricQueue(4).enqueue("12").enqueue("34").enqueue("56").enqueue("7")
        assert(subject.flushQueue() ==
          Stream("""12
                 |34""".stripMargin,
                """56
                  |7""".stripMargin))
      }
    }
  }
}
