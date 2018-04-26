package eu.jjst

import akka.actor._
import akka.event.Logging
import akka.stream.OverflowStrategy
import akka.stream.scaladsl._
import eu.jjst.GameActor._
import eu.jjst.models.Move

import scala.collection.mutable

trait Game {
  def gameFlow(player: Player): Flow[GameMessage, GameMessage, Any]
}

object GameActor {
  case class AddPlayer(player: Player, subscriber: ActorRef)
  case class RemovePlayer(player: Player)
  case class TransmitMove(player: Player, move: Move)
}

class GameActor extends Actor {
  val log = Logging(context.system, this)
  val subscribers = mutable.Map[Player, ActorRef]()

  override def receive: Receive = {
    case AddPlayer(player, subscriber) =>
      context.watch(subscriber)
      subscribers += player -> subscriber
      log.info(s"Player joined: $player")
      dispatch(player, GameMessage.PlayerJoined)
    case RemovePlayer(player) =>
      val ref = subscribers(player)
      // report downstream of completion, otherwise, there's a risk of leaking the
      // downstream when the TCP connection is only half-closed
      ref ! Status.Success(Unit)
      subscribers -= player
      log.info(s"Player left: $player")
      dispatch(player, GameMessage.PlayerLeft)
    case TransmitMove(player, move) =>
      log.info(s"Player moved: $player - $move")
      dispatch(player, GameMessage.PlayerMoved(move))
    case Terminated(sub) =>
      subscribers
        .find { case (_, a) => a == sub }
        .foreach { case (p, _) => subscribers.remove(p) }
  }

  def dispatch(origin: Player, msg: GameMessage): Unit = {
    subscribers
      .filterNot(_._1 == origin)
      .values.foreach(_ ! msg)
  }
}

object Game {
  def create(system: ActorSystem): Game = {
    val gameActor = system.actorOf(Props[GameActor])

    new Game {
      def gameFlow(player: Player): Flow[GameMessage, GameMessage, Any] = {
        val in =
          Flow[GameMessage]
            .collect { case GameMessage.PlayerMoved(move) => TransmitMove(player, move) }
            .to(Sink.actorRef[TransmitMove](gameActor, onCompleteMessage = RemovePlayer(player)))

        // The counter-part which is a source that will create a target ActorRef per
        // materialization where the chatActor will send its messages to.
        // This source will only buffer one element and will fail if the client doesn't read
        // messages fast enough.
        val out =
          Source.actorRef[GameMessage](1, OverflowStrategy.fail)
            .mapMaterializedValue(gameActor ! AddPlayer(player, _))

        Flow.fromSinkAndSource(in, out)
      }
    }
  }

}
