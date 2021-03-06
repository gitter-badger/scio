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

package com.spotify.scio

import com.google.api.services.bigquery.model.TableReference
import com.google.cloud.dataflow.sdk.io.BigQueryIO
import com.google.cloud.dataflow.sdk.io.BigQueryIO.Write.{CreateDisposition, WriteDisposition}
import com.spotify.scio.bigquery.BigQueryClient
import com.spotify.scio.bigquery.types.BigQueryType.HasAnnotation
import com.spotify.scio.io.Tap
import com.spotify.scio.values.SCollection

import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

/**
 * Main package for experimental APIs. Import all.
 *
 * {{{
 * import com.spotify.scio.experimental._
 * }}}
 */
package object experimental {

  /** Typed BigQuery annotations and converters. */
  val BigQueryType = com.spotify.scio.bigquery.types.BigQueryType

  /** Enhanced version of [[ScioContext]] with experimental features. */
  // TODO: scala 2.11
  // implicit class ExperimentalDataflowContext(private val self: ScioContext) extends AnyVal {
  implicit class ExperimentalDataflowContext(val self: ScioContext) {

    /**
     * Get a typed SCollection for a BigQuery SELECT query or table.
     *
     * Note that `T` must be annotated with [[BigQueryType.fromSchema]],
     * [[BigQueryType.fromTable]], or [[BigQueryType.fromQuery]].
     *
     * By default the source (table or query) specified in the annotation will be used, but it can
     * be overridden with the `newSource` parameter. For example:
     *
     * {{{
     * @BigQueryType.fromTable("publicdata:samples.gsod")
     * class Row
     *
     * // Read from [publicdata:samples.gsod] as specified in the annotation.
     * sc.typedBigQuery[Row]()
     *
     * // Read from [myproject:samples.gsod] instead.
     * sc.typedBigQuery[Row]("myproject:samples.gsod")
     * }}}
     */
    def typedBigQuery[T <: HasAnnotation : ClassTag : TypeTag](newSource: String = null): SCollection[T] = {
      val bqt = BigQueryType[T]

      if (bqt.isTable) {
        val table = if (newSource != null) BigQueryIO.parseTableSpec(newSource) else bqt.table.get
        self.bigQueryTable(table).map(bqt.fromTableRow)
      } else if (bqt.isQuery) {
        val query = if (newSource != null) newSource else bqt.query.get
        self.bigQuerySelect(query).map(bqt.fromTableRow)
      } else {
        throw new IllegalArgumentException(s"Missing table or query field in companion")
      }
    }

  }

  /**
   * Enhanced version of [[com.spotify.scio.values.SCollection SCollection]] with
   * experimental features.
   */
  // TODO: scala 2.11
  // implicit class ExperimentalSCollection[T](private val self: SCollection[T]) extends AnyVal {
  implicit class ExperimentalSCollection[T](val self: SCollection[T]) {

    /**
     * Save this SCollection as a BigQuery table. Note that element type `T` must be a case class
     * annotated with [[BigQueryType.toTable]].
     */
    def saveAsTypedBigQuery(table: TableReference,
                            writeDisposition: WriteDisposition,
                            createDisposition: CreateDisposition)
                           (implicit ct: ClassTag[T], tt: TypeTag[T], ev: T <:< HasAnnotation): Future[Tap[T]] = {
      val bqt = BigQueryType[T]
      import scala.concurrent.ExecutionContext.Implicits.global
      self
        .map(bqt.toTableRow)
        .saveAsBigQuery(table, bqt.schema, writeDisposition, createDisposition)
        .map(_.map(bqt.fromTableRow))
    }

    /**
     * Save this SCollection as a BigQuery table. Note that element type `T` must be annotated with
     * [[BigQueryType]].
     *
     * This could be a complete case class with [[BigQueryType.toTable]]. For example:
     *
     * {{{
     * @BigQueryType.toTable()
     * case class Result(name: String, score: Double)
     *
     * val p: SCollection[Result] = // process data and convert elements to Result
     * p.saveAsTypedBigQuery("myproject:mydataset.mytable")
     * }}}
     *
     * It could also be an empty class with schema from [[BigQueryType.fromSchema]],
     * [[BigQueryType.fromTable]], or [[BigQueryType.fromQuery]]. For example:
     *
     * {{{
     * @BigQueryType.fromTable("publicdata:samples.gsod")
     * class Row
     *
     * sc.typedBigQuery[Row]()
     *   .sample(withReplacement = false, fraction = 0.1)
     *   .saveAsTypedBigQuery("myproject:samples.gsod")
     * }}}
     */
    def saveAsTypedBigQuery(tableSpec: String,
                            writeDisposition: WriteDisposition = null,
                            createDisposition: CreateDisposition = null)
                           (implicit ct: ClassTag[T], tt: TypeTag[T], ev: T <:< HasAnnotation): Future[Tap[T]] =
      saveAsTypedBigQuery(BigQueryIO.parseTableSpec(tableSpec), writeDisposition, createDisposition)

  }

  /** Enhanced version of [[BigQueryClient]] with type-safe features. */
  implicit class TypedBigQueryClient(self: BigQueryClient) {

    /**
     * Get a typed iterator for a BigQuery SELECT query or table.
     *
     * Note that `T` must be annotated with [[BigQueryType.fromSchema]],
     * [[BigQueryType.fromTable]], or [[BigQueryType.fromQuery]].
     *
     * By default the source (table or query) specified in the annotation will be used, but it can
     * be overridden with the `newSource` parameter. For example:
     *
     * {{{
     * @BigQueryType.fromTable("publicdata:samples.gsod")
     * class Row
     *
     * // Read from [publicdata:samples.gsod] as specified in the annotation.
     * bq.getTypedRows[Row]()
     *
     * // Read from [myproject:samples.gsod] instead.
     * bq.getTypedRows[Row]("myproject:samples.gsod")
     * }}}
     */
    def getTypedRows[T <: HasAnnotation : ClassTag : TypeTag](newSource: String = null): Iterator[T] = {
      val bqt = BigQueryType[T]
      if (bqt.isTable) {
        val table = if (newSource != null) BigQueryIO.parseTableSpec(newSource) else bqt.table.get
        self.getTableRows(table).map(bqt.fromTableRow)
      } else if (bqt.isQuery) {
        val query = if (newSource != null) newSource else bqt.query.get
        self.getQueryRows(query).map(bqt.fromTableRow)
      } else {
        throw new IllegalArgumentException(s"Missing table or query field in companion")
      }
    }

    /**
     * Write a List to a BigQuery table. Note that element type `T` must be annotated with [[BigQueryType]].
     */

    def writeTypedRows[T <: HasAnnotation : ClassTag : TypeTag](table: TableReference,
                                                                rows: List[T],
                                                                writeDisposition: WriteDisposition,
                                                                createDisposition: CreateDisposition): Unit = {

      val bqt = BigQueryType[T]
      self.writeTableRows(table, rows.map(bqt.toTableRow), bqt.schema, writeDisposition, createDisposition)
    }

    def writeTypedRows[T <: HasAnnotation : ClassTag : TypeTag](tableSpec: String,
                                                                rows: List[T],
                                                                writeDisposition: WriteDisposition = WRITE_EMPTY,
                                                                createDisposition: CreateDisposition = CREATE_IF_NEEDED): Unit =
      writeTypedRows(BigQueryIO.parseTableSpec(tableSpec), rows, writeDisposition, createDisposition)

  }

}
