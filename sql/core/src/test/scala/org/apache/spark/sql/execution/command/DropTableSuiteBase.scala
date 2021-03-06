/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.execution.command

import org.apache.spark.sql.{AnalysisException, QueryTest, Row}

trait DropTableSuiteBase extends QueryTest with DDLCommandTestUtils {
  override val command = "DROP TABLE"

  protected def createTable(tableName: String): Unit = {
    sql(s"CREATE TABLE $tableName (c int) $defaultUsing")
    sql(s"INSERT INTO $tableName SELECT 0")
  }

  protected def checkTables(namespace: String, expectedTables: String*): Unit = {
    val tables = sql(s"SHOW TABLES IN $catalog.$namespace").select("tableName")
    val rows = expectedTables.map(Row(_))
    checkAnswer(tables, rows)
  }

  test("basic") {
    withNamespace(s"$catalog.ns") {
      sql(s"CREATE NAMESPACE $catalog.ns")

      createTable(s"$catalog.ns.tbl")
      checkTables("ns", "tbl")

      sql(s"DROP TABLE $catalog.ns.tbl")
      checkTables("ns") // no tables
    }
  }

  test("try to drop a nonexistent table") {
    withNamespace(s"$catalog.ns") {
      sql(s"CREATE NAMESPACE $catalog.ns")
      checkTables("ns") // no tables

      val errMsg = intercept[AnalysisException] {
        sql(s"DROP TABLE $catalog.ns.tbl")
      }.getMessage
      assert(errMsg.contains("Table or view not found"))
    }
  }

  test("with IF EXISTS") {
    withNamespace(s"$catalog.ns") {
      sql(s"CREATE NAMESPACE $catalog.ns")

      createTable(s"$catalog.ns.tbl")
      checkTables("ns", "tbl")
      sql(s"DROP TABLE IF EXISTS $catalog.ns.tbl")
      checkTables("ns")

      // It must not throw any exceptions
      sql(s"DROP TABLE IF EXISTS $catalog.ns.tbl")
      checkTables("ns")
    }
  }

  test("SPARK-33174: DROP TABLE should resolve to a temporary view first") {
    withNamespaceAndTable("ns", "t") { t =>
      withTempView("t") {
        sql(s"CREATE TABLE $t (id bigint) $defaultUsing")
        sql("CREATE TEMPORARY VIEW t AS SELECT 2")
        sql(s"USE $catalog.ns")
        try {
          // Check the temporary view 't' exists.
          checkAnswer(
            sql("SHOW TABLES FROM spark_catalog.default LIKE 't'")
              .select("tableName", "isTemporary"),
            Row("t", true))
          sql("DROP TABLE t")
          // Verify that the temporary view 't' is resolved first and dropped.
          checkAnswer(
            sql("SHOW TABLES FROM spark_catalog.default LIKE 't'")
              .select("tableName", "isTemporary"),
            Seq.empty)
        } finally {
          sql(s"USE spark_catalog")
        }
      }
    }
  }
}
