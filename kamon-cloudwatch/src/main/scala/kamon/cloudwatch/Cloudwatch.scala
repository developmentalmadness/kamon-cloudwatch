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

import akka.actor._
import akka.event.Logging
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder
import kamon.Kamon

object Cloudwatch extends ExtensionId[CloudwatchExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = Cloudwatch
  override def createExtension(system: ExtendedActorSystem): CloudwatchExtension = new CloudwatchExtension(system)
}

class CloudwatchExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  implicit val as = system
  val log = Logging(system, classOf[CloudwatchExtension])
  log.info("Starting the Kamon(CloudWatch) extension")
  val cloudwatchConfig = system.settings.config.getConfig("kamon.cloudwatch")
  var cloudwatchClient = AmazonCloudWatchClientBuilder.defaultClient()
  var sdkClient = system.actorOf(SdkClient.props(cloudwatchClient))
  var agent = new Agent(system, cloudwatchConfig, sdkClient)
}

