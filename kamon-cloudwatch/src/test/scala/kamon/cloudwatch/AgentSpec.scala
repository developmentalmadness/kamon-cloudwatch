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

import akka.testkit.{TestActorRef, TestProbe}
import com.typesafe.config.{Config, ConfigFactory}
import kamon.metric.instrument.{Memory, Time}
import kamon.testkit.BaseKamonSpec

import scala.concurrent.duration._

class AgentSpec extends BaseKamonSpec("agent-spec") {

  override lazy val config =
    ConfigFactory.load("application")
  lazy val testConfig = ConfigFactory.load("application").getConfig("kamon.cloudwatch")
  lazy val invalidFlushConfig = ConfigFactory.load("application").getConfig("kamon.cloudwatch-invalid-flush")
  lazy val invalidTimeUnitConfig = ConfigFactory.load("application").getConfig("kamon.cloudwatch-invalid-time-unit")
  lazy val defaultMemoryUnitConfig = ConfigFactory.load("application").getConfig("kamon.cloudwatch-default-memory-unit")
  lazy val kilobyteMemoryUnitConfig = ConfigFactory.load("application").getConfig("kamon.cloudwatch-kilobyte-memory-unit")

  class AgentSettingsFixture(config: Config) {
    val settings =
      AgentSettings.fromConfig(config)
  }

  "CloudWatch Agent" should {
    "parse the configuration settings" in {
      val sdkProbe = TestProbe()

      val target = new Agent(system, testConfig, sdkProbe.ref)
      target.settings shouldNot be(null)
    }

    "throw AssertionError when flush-interval < tick-interval" in {
      val sdkProbe = TestProbe()
      a[AssertionError] should be thrownBy {
        new Agent(system, invalidFlushConfig, sdkProbe.ref)
      }
    }
  }

  "AgentSettings.fromConfig" when {
    //todo: add test to ensure missing config options are replaced with defaults
    "config file is valid" should {
      "parse the flushInterval" in new AgentSettingsFixture(testConfig) {
        settings.flushInterval shouldEqual 1.second
      }

      "parse the namespace" in new AgentSettingsFixture(testConfig) {
        settings.namespace shouldEqual "kamon-test"
      }

      "parse time-units" in new AgentSettingsFixture(testConfig) {
        settings.scaleTimeTo.label shouldEqual Time.Microseconds.label
      }

      "parse memory-units" in new AgentSettingsFixture(testConfig) {
        settings.scaleMemoryTo.isDefined shouldEqual false
      }
    }

    "time-units are configured as Nanoseconds" should {
      "scale to Microseconds" in new AgentSettingsFixture(invalidTimeUnitConfig) {
        settings.scaleTimeTo.label shouldEqual Time.Microseconds.label
      }
    }

    "memory-units are configured as Bytes" should {
      "scale to None" in new AgentSettingsFixture(defaultMemoryUnitConfig) {
        settings.scaleMemoryTo.isDefined shouldEqual false
      }
    }

    "memory-units are configured as Kilobytes" should {
      "scale to Kilobytes" in new AgentSettingsFixture(kilobyteMemoryUnitConfig) {
        settings.scaleMemoryTo.isDefined shouldEqual true
        settings.scaleMemoryTo.get.label shouldEqual Memory.KiloBytes.label
      }
    }
  }
}
