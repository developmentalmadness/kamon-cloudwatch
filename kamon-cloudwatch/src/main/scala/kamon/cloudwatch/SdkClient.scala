package kamon.cloudwatch

import akka.actor._
import akka.pattern.pipe
import com.amazonaws.{ AmazonClientException, AmazonServiceException }
import com.amazonaws.services.cloudwatch.AmazonCloudWatch
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest
import kamon.cloudwatch.SdkClient.{ PutFailed, PutSucceeded }

import scala.concurrent.Future

case class SdkPutRequest(putMetricDataRequest: PutMetricDataRequest)

class SdkClient(client: AmazonCloudWatch) extends Actor with ActorLogging {
  import context.dispatcher

  override def receive = {
    case r: SdkPutRequest ⇒
      log.info("received SdkPutRequest"); pipe {
        log.info("piping SdkPutRequest")
        Future {
          client.putMetricData(r.putMetricDataRequest)
        }
          .map { _ ⇒ PutSucceeded }
          .recover { case e ⇒ PutFailed(e, r) }
      } to self
    case PutSucceeded ⇒ log.info("PutSucceeded") // no-op
    case PutFailed(e, r) ⇒ e match {
      case e: AmazonServiceException ⇒
        log.error(e, s"Failed to PUT metrics data to CloudWatch: $r")
      case e: AmazonClientException ⇒
        log.error(e, s"Fatal error attempting to PUT metrics data to CloudWatch: $r")
      case e: Exception ⇒
        log.error(e, s"Fatal error in PutMetricsData batch logic. Not all metrics were sent to CloudWatch: $r")
    }
  }
}

object SdkClient {

  trait SdkClientPutResult

  case object PutSucceeded extends SdkClientPutResult

  case class PutFailed(e: Throwable, r: SdkPutRequest) extends SdkClientPutResult

  def props(client: AmazonCloudWatch): Props = {
    Props(new SdkClient(client))
  }
}
