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
package io.glutenproject.execution

import org.apache.spark.{SPARK_VERSION_SHORT, SparkConf}
import org.apache.spark.sql.execution.InputIteratorTransformer
import org.apache.spark.sql.execution.adaptive.AdaptiveSparkPlanHelper

import org.apache.commons.io.FileUtils

import java.io.File

// Some sqls' line length exceeds 100
// scalastyle:off line.size.limit

class TPCHBucketScanSuite
  extends VeloxWholeStageTransformerSuite {

  override protected val resourcePath: String =
    "../../../../gluten-core/src/test/resources/tpch-data"

  override protected val tablesPath: String = basePath + "/tpch-data"
  override protected val tpchQueries: String =
    rootPath + "../../../../gluten-core/src/test/resources/tpch-queries"
  override protected val queriesResults: String = rootPath + "queries-output"

  protected val bucketTableResourcePath: String = rootPath + "tpch-data-bucket/parquet_bucket"
  protected val bucketTableDataPath: String = basePath + "/tpch-parquet-bucket"
  protected lazy val sparkVersion: String = {
    val version = SPARK_VERSION_SHORT.split("\\.")
    version(0) + "." + version(1)
  }

  override protected def sparkConf: SparkConf = {
    super.sparkConf
      .set("spark.shuffle.manager", "org.apache.spark.shuffle.sort.ColumnarShuffleManager")
      .set("spark.io.compression.codec", "LZ4")
      .set("spark.sql.shuffle.partitions", "5")
      .set("spark.sql.autoBroadcastJoinThreshold", "-1") // for test bucket join
      .set("spark.sql.adaptive.enabled", "true")
  }

  override protected val createNullableTables = true

  override def beforeAll(): Unit = {
    super.beforeAll()
    FileUtils.copyDirectory(new File(bucketTableResourcePath), new File(bucketTableDataPath))
    createTPCHParquetBucketTables()
  }

  protected def createTPCHParquetBucketTables(): Unit = {
    spark.sql(s"""
                 |CREATE DATABASE IF NOT EXISTS tpch_parquet_bucket
                 |""".stripMargin)
    spark.sql("use tpch_parquet_bucket")
    val customerData = bucketTableDataPath + "/customer"
    spark.sql(s"DROP TABLE IF EXISTS customer")
    spark.sql(s"""
                 | CREATE EXTERNAL TABLE IF NOT EXISTS customer (
                 | c_custkey    bigint,
                 | c_name       string,
                 | c_address    string,
                 | c_nationkey  bigint,
                 | c_phone      string,
                 | c_acctbal    double,
                 | c_mktsegment string,
                 | c_comment    string)
                 | USING PARQUET
                 | LOCATION '$customerData'
                 | CLUSTERED BY (c_custkey) SORTED BY (c_custkey) INTO 2 BUCKETS;
                 |""".stripMargin)

    val customerData1 = bucketTableDataPath + "/customer_6_buckets"
    spark.sql(s"DROP TABLE IF EXISTS customer_6_buckets")
    spark.sql(s"""
                 | CREATE EXTERNAL TABLE IF NOT EXISTS customer_6_buckets (
                 | c_custkey    bigint,
                 | c_name       string,
                 | c_address    string,
                 | c_nationkey  bigint,
                 | c_phone      string,
                 | c_acctbal    double,
                 | c_mktsegment string,
                 | c_comment    string)
                 | USING PARQUET
                 | LOCATION '$customerData1'
                 | CLUSTERED BY (c_custkey) SORTED BY (c_custkey) INTO 6 BUCKETS;
                 |""".stripMargin)

    spark.sql(s"""
                 |INSERT INTO customer_6_buckets SELECT * FROM customer;
                 |""".stripMargin)

    val lineitemData = bucketTableDataPath + "/lineitem"
    spark.sql(s"DROP TABLE IF EXISTS lineitem")
    spark.sql(s"""
                 | CREATE EXTERNAL TABLE IF NOT EXISTS lineitem (
                 | l_orderkey      bigint,
                 | l_partkey       bigint,
                 | l_suppkey       bigint,
                 | l_linenumber    bigint,
                 | l_quantity      double,
                 | l_extendedprice double,
                 | l_discount      double,
                 | l_tax           double,
                 | l_returnflag    string,
                 | l_linestatus    string,
                 | l_shipdate      date,
                 | l_commitdate    date,
                 | l_receiptdate   date,
                 | l_shipinstruct  string,
                 | l_shipmode      string,
                 | l_comment       string)
                 | USING PARQUET
                 | LOCATION '$lineitemData'
                 | CLUSTERED BY (l_orderkey) SORTED BY (l_shipdate, l_orderkey) INTO 2 BUCKETS;
                 |""".stripMargin)

    val nationData = bucketTableDataPath + "/nation"
    spark.sql(s"DROP TABLE IF EXISTS nation")
    spark.sql(s"""
                 | CREATE EXTERNAL TABLE IF NOT EXISTS nation (
                 | n_nationkey bigint,
                 | n_name      string,
                 | n_regionkey bigint,
                 | n_comment   string)
                 | USING PARQUET
                 | LOCATION '$nationData'
                 | CLUSTERED BY (n_nationkey) SORTED BY (n_nationkey) INTO 1 BUCKETS;
                 |""".stripMargin)

    val regionData = bucketTableDataPath + "/region"
    spark.sql(s"DROP TABLE IF EXISTS region")
    spark.sql(s"""
                 | CREATE EXTERNAL TABLE IF NOT EXISTS region (
                 | r_regionkey bigint,
                 | r_name      string,
                 | r_comment   string)
                 | USING PARQUET
                 | LOCATION '$regionData'
                 | CLUSTERED BY (r_regionkey) SORTED BY (r_regionkey) INTO 1 BUCKETS;
                 |""".stripMargin)

    val ordersData = bucketTableDataPath + "/orders"
    spark.sql(s"DROP TABLE IF EXISTS orders")
    spark.sql(s"""
                 | CREATE EXTERNAL TABLE IF NOT EXISTS orders (
                 | o_orderkey      bigint,
                 | o_custkey       bigint,
                 | o_orderstatus   string,
                 | o_totalprice    double,
                 | o_orderdate     date,
                 | o_orderpriority string,
                 | o_clerk         string,
                 | o_shippriority  bigint,
                 | o_comment       string)
                 | USING PARQUET
                 | LOCATION '$ordersData'
                 | CLUSTERED BY (o_orderkey) SORTED BY (o_orderkey, o_orderdate) INTO 2 BUCKETS;
                 |""".stripMargin)

    val ordersData1 = bucketTableDataPath + "/orders_6_buckets"
    spark.sql(s"DROP TABLE IF EXISTS orders_6_buckets")
    spark.sql(s"""
                 | CREATE EXTERNAL TABLE IF NOT EXISTS orders_6_buckets (
                 | o_orderkey      bigint,
                 | o_custkey       bigint,
                 | o_orderstatus   string,
                 | o_totalprice    double,
                 | o_orderdate     date,
                 | o_orderpriority string,
                 | o_clerk         string,
                 | o_shippriority  bigint,
                 | o_comment       string)
                 | USING PARQUET
                 | LOCATION '$ordersData1'
                 | CLUSTERED BY (o_orderkey) SORTED BY (o_orderkey, o_orderdate) INTO 6 BUCKETS;
                 |""".stripMargin)

    spark.sql(s"""
                 |INSERT INTO orders_6_buckets SELECT * FROM orders;
                 |""".stripMargin)

    val partData = bucketTableDataPath + "/part"
    spark.sql(s"DROP TABLE IF EXISTS part")
    spark.sql(s"""
                 | CREATE EXTERNAL TABLE IF NOT EXISTS part (
                 | p_partkey     bigint,
                 | p_name        string,
                 | p_mfgr        string,
                 | p_brand       string,
                 | p_type        string,
                 | p_size        bigint,
                 | p_container   string,
                 | p_retailprice double,
                 | p_comment     string)
                 | USING PARQUET
                 | LOCATION '$partData'
                 | CLUSTERED BY (p_partkey) SORTED BY (p_partkey) INTO 2 BUCKETS;
                 |""".stripMargin)

    val partsuppData = bucketTableDataPath + "/partsupp"
    spark.sql(s"DROP TABLE IF EXISTS partsupp")
    spark.sql(s"""
                 | CREATE EXTERNAL TABLE IF NOT EXISTS partsupp (
                 | ps_partkey    bigint,
                 | ps_suppkey    bigint,
                 | ps_availqty   bigint,
                 | ps_supplycost double,
                 | ps_comment    string)
                 | USING PARQUET
                 | LOCATION '$partsuppData'
                 | CLUSTERED BY (ps_partkey) SORTED BY (ps_partkey) INTO 2 BUCKETS;
                 |""".stripMargin)

    val supplierData = bucketTableDataPath + "/supplier"
    spark.sql(s"DROP TABLE IF EXISTS supplier")
    spark.sql(s"""
                 | CREATE EXTERNAL TABLE IF NOT EXISTS supplier (
                 | s_suppkey   bigint,
                 | s_name      string,
                 | s_address   string,
                 | s_nationkey bigint,
                 | s_phone     string,
                 | s_acctbal   double,
                 | s_comment   string)
                 | USING PARQUET
                 | LOCATION '$supplierData'
                 | CLUSTERED BY (s_suppkey) SORTED BY (s_suppkey) INTO 1 BUCKETS;
                 |""".stripMargin)

    val result = spark
      .sql(s"""
              | show tables;
              |""".stripMargin)
      .collect()
    assert(result.length == 10)
  }

  test("TPCH Q1") {
    compareTPCHQueryAgainstVanillaSpark(
      1,
      tpchQueries,
      df => {
        val plans = collect(df.queryExecution.executedPlan) {
          case scanExec: BasicScanExecTransformer => scanExec
        }
        assert(!(plans(0).asInstanceOf[FileSourceScanExecTransformer].bucketedScan))
        assert(plans(0).metrics("numFiles").value === 4)
        assert(plans(0).metrics("pruningTime").value === -1)
        assert(plans(0).metrics("outputRows").value === 600572)
      }
    )
  }
}
// scalastyle:on line.size.limit
