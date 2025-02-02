package org.elasticmq.actor.queue.operations

import org.elasticmq._
import org.elasticmq.actor.queue.{QueueActorStorage, QueueEvent}
import org.elasticmq.util.Logging

trait UpdateVisibilityTimeoutOps extends Logging {
  this: QueueActorStorage =>

  def updateVisibilityTimeout(
      messageId: MessageId,
      visibilityTimeout: VisibilityTimeout
  ): ResultWithEvents[Either[MessageDoesNotExist, Unit]] = {
    updateNextDelivery(messageId, CommonOperations.computeNextDelivery(visibilityTimeout, queueData, nowProvider))
  }

  private def updateNextDelivery(
      messageId: MessageId,
      newNextDelivery: MillisNextDelivery
  ): ResultWithEvents[Either[MessageDoesNotExist, Unit]] = {
    messageQueue.byId.get(messageId.id) match {
      case Some(internalMessage) =>
        // Updating
        val oldNextDelivery = internalMessage.nextDelivery
        internalMessage.nextDelivery = newNextDelivery.millis

        if (newNextDelivery.millis < oldNextDelivery) {
          // We have to re-insert the msg, as another msg with a bigger next delivery may be now before it,
          // so the msg wouldn't be correctly received.
          // (!) This may be slow (!)
          messageQueue = messageQueue.filterNot(_.id == internalMessage.id)
          messageQueue += internalMessage
        }
        // Else:
        // Just increasing the next delivery. Common case. It is enough to increase the value in the object. No need to
        // re-insert the msg into the queue, as it will be reinserted if needed during receiving.

        logger.debug(s"${queueData.name}: Updated next delivery of $messageId to $newNextDelivery")

        ResultWithEvents.valueWithEvents(
          Right(()),
          List(QueueEvent.MessageUpdated(queueData.name, internalMessage))
        )

      case None =>
        ResultWithEvents.onlyValue(Left(new MessageDoesNotExist(queueData.name, messageId)))
    }
  }

}
