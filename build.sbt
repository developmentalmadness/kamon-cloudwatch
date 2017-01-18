/* =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
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

val awssdkVersion = "1.11.66"

val kamonCore = "io.kamon" %% "kamon-core" % "0.6.5"
val kamonScala = "io.kamon" %% "kamon-scala" % "0.6.5"
val cloudwatch = "com.amazonaws" % "aws-java-sdk-cloudwatch" % awssdkVersion
val easyMock = "org.easymock" % "easymock" % "3.2"
 
lazy val `kamon-cloudwatch` = (project in file("."))
    .settings(noPublishing: _*)
    .aggregate(kamonCloudwatch)

lazy val kamonCloudwatch = Project("kamon-cloudwatch", file("kamon-cloudwatch"))
  .settings(Seq(
    moduleName := "kamon-cloudwatch",
    scalaVersion := "2.11.8",
    crossScalaVersions := Seq("2.10.6", "2.11.8")))
  .settings(
    libraryDependencies ++=
    compileScope(akkaDependency("actor").value, cloudwatch, kamonCore, kamonScala) ++
    optionalScope(logbackClassic) ++
    testScope(scalatest, akkaDependency("testkit").value, akkaDependency("slf4j").value, logbackClassic, easyMock))

