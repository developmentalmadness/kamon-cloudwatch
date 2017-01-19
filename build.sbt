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
val akkaVersion = "2.4.14"

lazy val root = (project in file("."))
  .settings(moduleName := "kamon-cloudwatch")
  .aggregate(kamonCloudwatch)

lazy val kamonCloudwatch = Project("kamon-cloudwatch", file("kamon-cloudwatch"))
  .settings(Seq(
    moduleName := "kamon-cloudwatch",
    version := "0.6.5-" + sys.env.get("BUILD_NUMBER").getOrElse("SNAPSHOT"),
    scalaVersion := "2.11.8",
    crossScalaVersions := Seq("2.10.6", "2.11.8")))
  .settings(
    libraryDependencies ++=
      Seq(
        "com.amazonaws" % "aws-java-sdk-cloudwatch" % awssdkVersion,
        "io.kamon" %% "kamon-core" % "0.6.5",
        "com.typesafe.akka" %% "akka-actor" % akkaVersion,
        "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
        "ch.qos.logback" % "logback-classic" % "1.1.8",

        // test dependencies
        "org.easymock" % "easymock" % "3.2" % "test",
        "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
        "org.scalatest" %% "scalatest" % "3.0.1" % "test"
      ))

