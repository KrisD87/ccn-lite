package nfn

import akka.actor.{Actor, ActorRef}
import akka.event.Logging
import ccn.ccnlite.CCNLite
import ccn.packet.{Interest, Content}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import nfn.service.{NFNValue, NFNService, CallableNFNService}
import scala.util.{Failure, Success}
import myutil.IOHelper
import ComputeWorker._

/**
 * Created by basil on 23/04/14.
 */
object ComputeWorker {
  case class Exit()
}

/**
 *
 */
case class ComputeWorker(ccnWorker: ActorRef) extends Actor {

  val name = self.path.name
  val logger = Logging(context.system, this)
  val ccnIf = CCNLite

  private var result : Option[String] = None

  def receivedContent(content: Content) = {
    // Received content from request (sendrcv)
    logger.error(s"ComputeWorker received content, discarding it because it does not know what to do with it")
  }

  // Received compute request
  // Make sure it actually is a compute request and forward to the handle method
  def receivedInterest(interest: Interest, requestor: ActorRef) = {
    logger.debug(s"received compute interest: $interest")
    val cmps = interest.name.cmps
    val computeCmps = cmps match {
      case Seq("COMPUTE", reqNfnCmps @ _ *) => {
        assert(reqNfnCmps.last == "NFN")
        val computeCmpsMaybeThunk = reqNfnCmps.init

        val (computeCmps, isThunkReq) = if(computeCmpsMaybeThunk.last == "THUNK") {
          computeCmpsMaybeThunk.init -> true
        } else {
          computeCmpsMaybeThunk -> false
        }

        handleComputeRequest(computeCmps, interest, isThunkReq, requestor)
      }
      case _ => logger.error(s"Dropping interest $interest because it is not a compute request")
    }
  }


  /*
   * Parses the compute request and instantiates a callable service.
   * On success, sends a thunk back if required, executes the services and sends the result back.
   */
  def handleComputeRequest(computeCmps: Seq[String], interest: Interest, isThunkRequest: Boolean, requestor: ActorRef) = {
    logger.debug(s"Handling compute request for cmps: $computeCmps")
    val callableServ: Future[CallableNFNService] = NFNService.parseAndFindFromName(computeCmps.mkString(" "), ccnWorker)

    callableServ onComplete {
      case Success(callableServ) => {
        if(isThunkRequest) {
          requestor ! NFNMaster.Thunk(interest)
        }

        val result: NFNValue = callableServ.exec
        val content = Content(interest.name, result.toStringRepresentation.getBytes)
        logger.debug(s"Finished computation, result: $content")
        requestor ! NFNMaster.ComputeResult(content)
      }
      case Failure(e) => {

        logger.error(IOHelper.exceptionToString(e))
      }
    }
  }

  override def receive: Actor.Receive = {
    case content: Content => receivedContent(content)
    case interest: Interest => {
      // Just to make sure we are not closing over sender
      val requestor = sender
      receivedInterest(interest, requestor)
    }
    case Exit() => context.stop(self)
  }
}
