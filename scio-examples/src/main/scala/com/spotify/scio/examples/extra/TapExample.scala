/*
 * Copyright 2016 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.scio.examples.extra

import com.google.cloud.dataflow.sdk.testing.DataflowAssert
import com.spotify.scio._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object TapExample {
  def main(cmdlineArgs: Array[String]): Unit = {
    // first job
    val (sc1, args) = ContextAndArgs(cmdlineArgs)
    val f1 = sc1.parallelize(1 to 10)
      .sum
      .materialize  // save data to a temporary location for use later
    val f2 = sc1.parallelize(1 to 100)
      .sum
      .map(_.toString)
      .saveAsTextFile(args("output"))  // save data for use later
    sc1.close()

    // wait for future completion in case job is non-blocking
    val t1 = Await.result(f1, Duration.Inf)
    val t2 = Await.result(f2, Duration.Inf)

    // scalastyle:off regex
    // fetch tap values directly
    println(t1.value.mkString(", "))
    println(t2.value.mkString(", "))
    // scalastyle:on regex

    // second job
    val (sc2, _) = ContextAndArgs(cmdlineArgs)
    // re-open taps in new context
    val s = (t1.open(sc2) ++ t2.open(sc2).map(_.toInt)).sum
    DataflowAssert.thatSingleton(s.internal).isEqualTo((1 to 10).sum + (1 to 100).sum)
    val result = sc2.close()

    // scalastyle:off regex
    // block main() until second job completes
    println(Await.result(result.finalState, Duration.Inf))
    // scalastyle:on regex
  }
}