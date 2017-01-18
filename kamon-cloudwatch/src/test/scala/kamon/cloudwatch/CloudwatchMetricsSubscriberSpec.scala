/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.cloudwatch

import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.{Entity, MetricScaleDecorator, TraceMetrics}
import kamon.metric.instrument.{Memory, Time}
import kamon.testkit.BaseKamonSpec
import kamon.util.MilliTimestamp

import scala.concurrent.duration._

class CloudwatchMetricsSubscriberSpec extends BaseKamonSpec("metric-subscriber-spec") {
  val namespace = "kamon-metric-subscriber/cloudwatch"

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

  "CloudwatchMetricsSubscriber" should {
    "send metrics to sdkClient actor" in new FakeTickSnapshotsFixture {
      val sdkClient = TestProbe()
      val target = system.actorOf(CloudwatchMetricsSubscriber.props(namespace, sdkClient.ref), "cloudwatch-metrics-subscriber")
      val decoratedTarget = system.actorOf(MetricScaleDecorator.props(
        Some(Time.Microseconds),
        None,
        target
      ))

      decoratedTarget ! firstSnapshot
      val result = sdkClient.expectMsgType[SdkPutRequest](1 second)
      val actual = result.putMetricDataRequest
      actual.getNamespace shouldEqual s"$namespace/${testTraceID.category}"
      // for some reason this is 2 - I only expected 1, but `firstSnapshot.metrics()._2.metrics` includes an "errors" Counter - not sure where it comes from
      actual.getMetricData.size shouldEqual 2
      actual.getMetricData.get(0).getDimensions.size shouldEqual testTraceID.tags.size
      actual.getMetricData.get(1).getDimensions.size shouldEqual testTraceID.tags.size
      actual.getMetricData.get(0).getTimestamp.getTime shouldEqual firstSnapshot.to.millis
      actual.getMetricData.get(1).getTimestamp.getTime shouldEqual firstSnapshot.to.millis

      actual.getMetricData.get(1).getMetricName shouldEqual s"${firstSnapshot.metrics(testTraceID).metrics.head._1.name}/${testTraceID.name}"
    }
  }

  trait FakeTickSnapshotsFixture {
    val testTraceID = Entity("example-trace", "trace")
    val recorder = Kamon.metrics.entity(TraceMetrics, testTraceID.name)
    val collectionContext = Kamon.metrics.buildDefaultCollectionContext

    def collectRecorder = recorder.collect(collectionContext)

    recorder.elapsedTime.record(1000000)
    recorder.elapsedTime.record(2000000)
    recorder.elapsedTime.record(3000000)
    val firstSnapshot = TickMetricSnapshot(new MilliTimestamp(1415587618000L), new MilliTimestamp(1415587678000L), Map(testTraceID -> collectRecorder))

    recorder.elapsedTime.record(6000000)
    recorder.elapsedTime.record(5000000)
    recorder.elapsedTime.record(4000000)
    val secondSnapshot = TickMetricSnapshot(new MilliTimestamp(1415587678000L), new MilliTimestamp(1415587738000L), Map(testTraceID -> collectRecorder))
  }

}
