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

import com.spotify.scio._

object AccumulatorExample {
  def main(cmdlineArgs: Array[String]): Unit = {
    val sc = ScioContext()

    val max = sc.maxAccumulator[Int]("max")
    val min = sc.minAccumulator[Int]("min")
    val sum = sc.sumAccumulator[Int]("sum")
    val count = sc.sumAccumulator[Int]("count")

    sc.parallelize(1 to 100)
      .withAccumulator(max, min, sum, count)
      .filter { (i, c) =>
        c.addValue(max, i).addValue(min, i).addValue(sum, i).addValue(count, 1)
        i <= 50
      }
      .map { (i, c) =>
        // reuse some accumulators here
        c.addValue(sum, i).addValue(count, 1)
        i
      }

    val r = sc.close()

    // scalastyle:off regex
    println("Max: " + r.accumulatorTotalValue(max))
    println("Min: " + r.accumulatorTotalValue(min))
    println("Sum: " + r.accumulatorTotalValue(sum))
    println("Count: " + r.accumulatorTotalValue(count))

    println("Sum per step:")
    r.accumulatorValuesAtSteps(sum).foreach(kv => println(kv._2 + " @ " + kv._1))

    println("Count per step:")
    r.accumulatorValuesAtSteps(count).foreach(kv => println(kv._2 + " @ " + kv._1))
    // scalastyle:on regex
  }
}
