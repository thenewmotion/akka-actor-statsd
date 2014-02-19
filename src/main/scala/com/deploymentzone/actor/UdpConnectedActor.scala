package com.deploymentzone.actor

import java.net.InetSocketAddress
import akka.actor._
import akka.io.{UdpConnected, IO}
import akka.util.ByteString

/* originated from: http://doc.akka.io/docs/akka/snapshot/scala/io-udp.html */

/* by using a connected form instead of a simple sender, security checks are cached instead of verified on every send */
private[actor] class UdpConnectedActor(remote: InetSocketAddress, requester: ActorRef)
  extends Actor
  with ActorLogging {

  import context.system

  def receive = {
    case UdpConnected.Connect =>
      IO(UdpConnected) ! UdpConnected.Connect(self, remote)
    case connected @ UdpConnected.Connected =>
      context.become(ready(sender))
      requester ! connected
  }

  def ready(connection: ActorRef): Receive = {
    case msg: String =>
      connection ! UdpConnected.Send(ByteString(msg))
    case d @ UdpConnected.Disconnect => connection ! d
    case UdpConnected.Disconnected   => context.stop(self)
    case f : UdpConnected.CommandFailed =>
      f.cmd match {
        case send: UdpConnected.Send => log warning s"Unable to deliver payload: ${send.payload.decodeString("utf-8")}"
        case _                       => log warning s"CommandFailed: ${f.cmd} (${f.cmd.getClass})"
      }
  }

  override def unhandled(message: Any) = {
    log.warning(s"Unhandled message: $message (${message.getClass})")
  }

  private case class Retry(message: String, attempts: Int) {
    private val MAXIMUM_RETRIES = 2

    def shouldRetry: Boolean = attempts <= MAXIMUM_RETRIES
  }
}

private[actor] object UdpConnectedActor {
  def props(remote: InetSocketAddress, requester: ActorRef) = Props(new UdpConnectedActor(remote, requester))
}
