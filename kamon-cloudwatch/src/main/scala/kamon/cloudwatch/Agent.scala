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

import akka.actor.{ ActorRef, ActorSystem }
import akka.event.{ Logging, LoggingAdapter }
import com.typesafe.config.Config
import kamon.Kamon
import kamon.metric._
import kamon.metric.instrument.{ Memory, Time }
import kamon.util.ConfigTools._

import scala.concurrent.duration.FiniteDuration
import scala.collection.JavaConverters._

class Agent(system: ActorSystem, cloudwatchConfig: Config, sdkClient: ActorRef) {
  private val log = Logging(system, classOf[Agent])
  log.info("Cloudwatch Agent starting...")
  val settings = AgentSettings.fromConfig(cloudwatchConfig)

  assert(settings.flushInterval >= settings.tickInterval, "Cloudwatch flush-interval needs to be equal or greater than the tick-interval")

  log.info("building sender")
  private val metricsSender = system.actorOf(CloudwatchMetricsSubscriber.props(settings.namespace, sdkClient), s"cloudwatch-metrics-sender-${settings.namespace}")
  private val decoratedSender = system.actorOf(MetricScaleDecorator.props(
    Some(settings.scaleTimeTo),
    settings.scaleMemoryTo,
    metricsSender),
    "cloudwatch-metric-scale-decroator")

  log.info("building subscriber")
  private val metricsSubscriber = if (settings.flushInterval == settings.tickInterval) {
    decoratedSender
  } else {
    system.actorOf(TickMetricSnapshotBuffer.props(settings.flushInterval, decoratedSender), "cloudwatch-metrics-buffer")
  }

  log.info("subscribing")
  private val subscriptions = cloudwatchConfig.getConfig("subscriptions")
  log.info(s"subscriptions config: $subscriptions")
  subscriptions.firstLevelKeys.map { subscriptionCategory ⇒
    subscriptions.getStringList(subscriptionCategory).asScala.foreach { pattern ⇒
      log.info(s"subscribing to $subscriptionCategory[$pattern]")
      Kamon.metrics.subscribe(subscriptionCategory, pattern, metricsSubscriber, permanently = true)
    }
  }
}

object EnforceScale {
  val TimeUnits = "time-units"
  val MemoryUnits = "memory-units"

  def unapply(config: Config): Option[(Time, Option[Memory])] = {
    val scaleTimeTo: Time =
      Some(config.time(TimeUnits)).filter(_.label != Time.Nanoseconds.label) match {
        case None ⇒
          //todo: return an optional message that can be printed by caller
          //log.warning("kamon.cloudwatch.time-units does not support Nanoseconds. Values will be scaled to Microseconds")
          Time.Microseconds
        case Some(t) ⇒ t
      }

    // don't need to scale if it's set to Bytes (default)
    val scaleMemoryTo: Option[Memory] =
      Some(config.memory(MemoryUnits)).filter(_.label != Memory.Bytes.label)

    Some((scaleTimeTo, scaleMemoryTo))
  }
}

case class AgentSettings(namespace: String, flushInterval: FiniteDuration, tickInterval: FiniteDuration, scaleTimeTo: Time, scaleMemoryTo: Option[Memory])

object AgentSettings {

  import kamon.util.ConfigTools.Syntax

  case object RestartSdk

  def fromConfig(config: Config) = {
    val namespace = config.getString("application-name")
    val flushInterval = config.getFiniteDuration("flush-interval")
    val tickInterval = Kamon.metrics.settings.tickInterval
    val (scaleTimeTo: Time, scaleMemoryTo: Option[Memory]) = config match {
      case EnforceScale(scaleTimeTo, scaleMemoryTo) ⇒
        (scaleTimeTo, scaleMemoryTo)
      case _ ⇒ // just in case
        (Time.Microseconds, None)
    }
    AgentSettings(
      namespace,
      flushInterval,
      tickInterval,
      scaleTimeTo,
      scaleMemoryTo)
  }
}

