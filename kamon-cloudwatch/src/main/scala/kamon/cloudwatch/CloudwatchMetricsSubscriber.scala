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

import java.util.Date

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import com.amazonaws.services.cloudwatch.model._
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.instrument.UnitOfMeasurement.Unknown
import kamon.metric.instrument.{ UnitOfMeasurement, _ }
import kamon.metric.{ Entity, MetricKey, SingleInstrumentEntityRecorder }

import scala.collection.JavaConverters._
import scala.collection.mutable

object CloudWatchMetricConfig {
  /*
  CloudWatch max size for PutMetricsDataRequest is 40KB. The AWS CloudWatchMetrics SDK mitigates this using
  the CloudWatchMetricConfig.MAX_METRICS_DATUM_SIZE constant (currently set to 20). Unfortunately, this
  value isn't public so we have to define our own value based on the original.

  see: https://github.com/aws/aws-sdk-java/blob/master/aws-java-sdk-cloudwatchmetrics/src/main/java/com/amazonaws/metrics/internal/cloudwatch/CloudWatchMetricConfig.java
   */
  val MAX_METRICS_DATUM_SIZE = 20

  val MAX_DIMENSIONS_SIZE = 10
}

class CloudwatchMetricsSubscriber(namespace: String, sdkClient: ActorRef)
    extends Actor
    with ActorLogging {

  override def receive: Receive = {
    case tick: TickMetricSnapshot ⇒ sendMetrics(tick)
  }

  def sendMetrics(tick: TickMetricSnapshot): Unit = {
    log.info(s"Subscriber received snapshot $tick")
    val categories = mutable.Map[String, List[MetricDatum]]()

    for {
      (groupIdentity, groupSnapshot) ← tick.metrics
      (metricKey, metricSnapshot) ← groupSnapshot.metrics
    } {

      val ns = s"$namespace/${groupIdentity.category}"
      val metricName = buildMetricName(groupIdentity, metricKey)
      val unit = metricKey.unitOfMeasurement
      val date = new Date(tick.to.millis)
      val scalar = scaleFunction(unit, metricName)
      val statSet = buildStatisticSet(metricSnapshot, scalar)
      var dimensions = groupIdentity.tags.map { case (k, v) ⇒ new Dimension().withName(k).withValue(v) }

      if (dimensions.size > CloudWatchMetricConfig.MAX_DIMENSIONS_SIZE) {
        log.error(s"CloudWatch does not support more than ${CloudWatchMetricConfig.MAX_DIMENSIONS_SIZE} tags (${dimensions.size}). Metric '$ns/$metricName' will only log the first ${CloudWatchMetricConfig.MAX_DIMENSIONS_SIZE} tags in alphabetical order")
        dimensions = dimensions.toList.sortBy(i ⇒ i.getName).take(CloudWatchMetricConfig.MAX_DIMENSIONS_SIZE)
      }

      log.debug(s"adding statSet: $statSet")
      val ls = categories
        .getOrElse(ns, List[MetricDatum]())

      val datum = new MetricDatum()
        .withMetricName(metricName)
        .withUnit(mapUnit(unit))
        .withTimestamp(date)
        .withDimensions(dimensions.asJavaCollection)

      if (statSet.getSampleCount == 0D)
        datum.withValue(0D)
      else
        datum.withStatisticValues(statSet)

      categories(ns) = datum :: ls
    }

    categories foreach {
      case (k, v) ⇒
        v.grouped(CloudWatchMetricConfig.MAX_METRICS_DATUM_SIZE) foreach { b ⇒
          val put = new PutMetricDataRequest()
            .withNamespace(k)
            .withMetricData(b.asJavaCollection)

          sdkClient ! SdkPutRequest(put)
        }
    }
  }

  def scaleFunction(uom: UnitOfMeasurement, metricName: String): Double ⇒ Double = uom match {
    case time: Time if (time.label == Time.Nanoseconds.label) ⇒
      log.info(s"scaleFunction[$metricName]: Nano -> Micro"); time.scale(Time.Microseconds)
    case other ⇒ log.info(s"scaleFunction[$metricName]: $other"); a ⇒ a
  }

  def mapUnit(uom: UnitOfMeasurement): StandardUnit = uom match {
    case Time(_, label) ⇒ label match {
      case Time.Microseconds.label ⇒ StandardUnit.Microseconds
      case Time.Milliseconds.label ⇒ StandardUnit.Milliseconds
      case Time.Seconds.label      ⇒ StandardUnit.Seconds
      case Time.Nanoseconds.label ⇒
        //FIXME: The decorator is being called, but the result is still Nanoseconds?
        log.warning(s"Nanoseconds label encountered while mapping UnitOfMeasurement to StandardUnit. Should have been handled by MetricScaleDecorator.")
        StandardUnit.Microseconds
    }
    case Memory(_, label) ⇒ label match {
      case Memory.Bytes.label     ⇒ StandardUnit.Bytes
      case Memory.KiloBytes.label ⇒ StandardUnit.Kilobytes
      case Memory.MegaBytes.label ⇒ StandardUnit.Megabytes
      case Memory.GigaBytes.label ⇒ StandardUnit.Gigabytes
    }
    case Unknown ⇒ StandardUnit.None
  }

  def buildStatisticSet(metricSnapshot: InstrumentSnapshot, scalar: Double ⇒ Double): StatisticSet = {
    metricSnapshot match {
      case hs: Histogram.Snapshot ⇒
        var sum: Double = 0D
        var sampleCount: Double = 0D

        val scaledMin = scalar(hs.min)
        val scaledMax = scalar(hs.max)

        hs.recordsIterator.foreach { record ⇒
          val scaledValue = scalar(record.level)

          sum += scaledValue * record.count
          sampleCount += record.count
        }

        new StatisticSet()
          .withSampleCount(sampleCount)
          .withSum(sum)
          .withMinimum(scaledMin)
          .withMaximum(scaledMax)

      case cs: Counter.Snapshot ⇒

        new StatisticSet()
          .withSampleCount(cs.count.toDouble)
          .withSum(cs.count.toDouble)
          .withMinimum(0D)
          .withMaximum(cs.count.toDouble)
    }
  }

  def isSingleInstrumentEntity(entity: Entity): Boolean = {
    SingleInstrumentEntityRecorder.AllCategories.contains(entity.category)
  }

  def buildMetricName(entity: Entity, metricKey: MetricKey): String = {
    if (isSingleInstrumentEntity(entity)) {
      entity.name
    } else {
      s"${metricKey.name}/${entity.name}"
    }
  }
}

object CloudwatchMetricsSubscriber {
  def props(namespace: String, sdkClient: ActorRef): Props =
    Props(new CloudwatchMetricsSubscriber(namespace, sdkClient))
}

