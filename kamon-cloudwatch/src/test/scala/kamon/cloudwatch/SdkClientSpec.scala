package kamon.cloudwatch

import akka.testkit.TestActorRef
import com.amazonaws.services.cloudwatch.AmazonCloudWatch
import com.amazonaws.services.cloudwatch.model.{PutMetricDataRequest, PutMetricDataResult}
import com.typesafe.config.ConfigFactory
import kamon.testkit.BaseKamonSpec
import org.easymock.EasyMock._
import org.scalatest.easymock.EasyMockSugar

class SdkClientSpec extends BaseKamonSpec("sdk-client-spec") with EasyMockSugar {
  override lazy val config = ConfigFactory.parseString(
    """
      |akka {
      |  loggers = ["akka.testkit.TestEventListener"]
      |  loglevel = "INFO"
      |}
      |kamon {
      |  metric {
      |    tick-interval = 1 second
      |  }
      |
      |  modules.kamon-cloudwatch.auto-start = no
      |}
      |
      """.stripMargin
  )

  "SdkClient" should {
    "call putMetricData" in {
      val mock = createMock(classOf[AmazonCloudWatch])
      expecting {
        mock.putMetricData(new PutMetricDataRequest())
        expectLastCall().andReturn(new PutMetricDataResult())
      }
      whenExecuting(mock) {
        val target = TestActorRef(SdkClient.props(mock))
        target ! new SdkPutRequest(new PutMetricDataRequest())
      }
    }
  }

}
