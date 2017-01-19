# AWS CloudWatch Integration

Kamon's integration with [CloudWatch](https://aws.amazon.com/cloudwatch/) comes in the form of `kamon-cloudwatch` module that publishes your Kamon metrics to AWS CloudWatch.

The <b>kamon-cloudwatch</b> module requires you to start your application using the AspectJ Weaver Agent. Kamon will warn you at startup if you failed to do so.

### Getting Started

Kamon CloudWatch module is currently available for Scala 2.10, 2.11.

Supported releases and dependencies are shown below.

| kamon-cloudwatch  | status | jdk  | scala            | akka   |
|:------:|:------:|:----:|------------------|:------:|
|  0.6.5 | stable | 1.7+, 1.8+ | 2.10, 2.11  | 2.3.x |

To get started with SBT, simply add the following to your `build.sbt`
file:

```scala
libraryDependencies += "kamon.io" %% "kamon-cloudwatch" % "0.6.5"
```


