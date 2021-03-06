/*
 * Copyright 2016 Bloomberg LP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bloomberg.sparkflow.dc

import com.bloomberg.sparkflow._
import com.holdenkarau.spark.testing.SharedSparkContext
import org.scalatest._

/**
  * Created by ngoehausen on 4/19/16.
  */
class PairDCFunctionsTest extends FunSuite with SharedSparkContext with ShouldMatchers{

  test("combineByKey"){
    val input = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val result = input.combineByKey[Array[Int]](Array(_), _ ++ Array(_), _ ++ _, 2)

    Seq(1,2) should contain theSameElementsAs result.getRDD(sc).lookup(1)(0)
    Seq(3,4) should contain theSameElementsAs result.getRDD(sc).lookup(2)(0)
    result.getRDD(sc).partitions.size shouldEqual 2
  }

  test("aggregateByKey"){
    val input = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val result = input.aggregateByKey(0)(_ + _, _ + _)

    Seq((1,3), (2,7)) should contain theSameElementsAs result.getRDD(sc).collect()
  }

  test("aggregateByKey(numPartitions)"){
    val input = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val result = input.aggregateByKey(0, 2)(_ + _, _ + _)

    Seq((1,3), (2,7)) should contain theSameElementsAs result.getRDD(sc).collect()
    result.getRDD(sc).partitions.size shouldEqual 2
  }

  test("foldByKey"){
    val input = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val result = input.foldByKey(0)(_ + _)

    Seq((1,3), (2,7)) should contain theSameElementsAs result.getRDD(sc).collect()
  }

  test("foldByKey(numPartitions)"){
    val input = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val result = input.foldByKey(0, 2)(_ + _)

    Seq((1,3), (2,7)) should contain theSameElementsAs result.getRDD(sc).collect()
    result.getRDD(sc).partitions.size shouldEqual 2
  }

  test("sampleByKey"){
    val input = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val result = input.sampleByKeyExact(withReplacement = false, fractions = Map((1, 0.5), (2, 0.5)), seed = 20L)

    2 shouldEqual result.getRDD(sc).collect().size
  }

  test("sampleByKeyExact"){
    val input = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val result = input.sampleByKey(withReplacement = false, fractions = Map((1, 0.5), (2, 0.5)), seed = 20L)

    2 shouldEqual result.getRDD(sc).collect().size
  }

  test("reduceByKey"){
    val input = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val result = input.reduceByKey(_ + _)

    Seq((1,3), (2,7)) should contain theSameElementsAs result.getRDD(sc).collect()
  }

  test("reduceByKey(numPartitions)"){
    val input = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val result = input.reduceByKey(_ + _, 2)

    Seq((1,3), (2,7)) should contain theSameElementsAs result.getRDD(sc).collect()
    result.getRDD(sc).partitions.size shouldEqual 2
  }

  test("countApproxDistinctByKey"){
    val input = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val result = input.countApproxDistinctByKey(0.3)

    Seq((1,2), (2,2)) should contain theSameElementsAs result.getRDD(sc).collect()
  }

  test("countApproxDistinctByKey(numPartitions)"){
    val input = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val result = input.countApproxDistinctByKey(0.3, 2)

    Seq((1,2), (2,2)) should contain theSameElementsAs result.getRDD(sc).collect()
    result.getRDD(sc).partitions.size shouldEqual 2
  }

  test("groupByKey"){
    val input = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val result = input.groupByKey()

    Seq((1, Seq(1,2)), (2, Seq(3,4))) should contain theSameElementsAs result.getRDD(sc).collect()
  }


  test("groupByKey(numPartitions)"){
    val input = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val result = input.groupByKey(2)

    Seq((1, Seq(1,2)), (2, Seq(3,4))) should contain theSameElementsAs result.getRDD(sc).collect()
    result.getRDD(sc).partitions.size shouldEqual 2
  }

  test("join"){
    val left = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val right = parallelize(Seq((1,"a"), (2,"b")))
    val result = left.join(right)

    val expected = Seq((1,(1, "a")), (1,(2,"a")), (2,(3,"b")), (2,(4,"b")))
    expected should contain theSameElementsAs result.getRDD(sc).collect()
  }

  test("join(numPartitions)"){
    val left = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val right = parallelize(Seq((1,"a"), (2,"b")))
    val result = left.join(right, 2)

    val expected = Seq((1,(1, "a")), (1,(2,"a")), (2,(3,"b")), (2,(4,"b")))
    expected should contain theSameElementsAs result.getRDD(sc).collect()
    result.getRDD(sc).partitions.size shouldEqual 2
  }

  test("leftOuterJoin"){
    val left = parallelize(Seq((1,1), (1,2), (2,3)))
    val right = parallelize(Seq((1,"a"), (3,"b")))
    val result = left.leftOuterJoin(right)

    val expected = Seq((1,(1,Some("a"))), (1,(2,Some("a"))), (2,(3,None)))
    expected should contain theSameElementsAs result.getRDD(sc).collect()
  }

  test("leftOuterJoin(numPartitions)"){
    val left = parallelize(Seq((1,1), (1,2), (2,3)))
    val right = parallelize(Seq((1,"a"), (3,"b")))
    val result = left.leftOuterJoin(right, 2)

    val expected = Seq((1,(1,Some("a"))), (1,(2,Some("a"))), (2,(3,None)))
    expected should contain theSameElementsAs result.getRDD(sc).collect()
    result.getRDD(sc).partitions.size shouldEqual 2
  }

  test("rightOuterJoin"){
    val left = parallelize(Seq((1,1), (1,2), (2,3)))
    val right = parallelize(Seq((1,"a"), (3,"b")))
    val result = left.rightOuterJoin(right)

    val expected = Seq((1,(Some(1),"a")), (1,(Some(2),"a")), (3,(None,"b")))
    expected should contain theSameElementsAs result.getRDD(sc).collect()
  }

  test("rightOuterJoin(numPartitions)"){
    val left = parallelize(Seq((1,1), (1,2), (2,3)))
    val right = parallelize(Seq((1,"a"), (3,"b")))
    val result = left.rightOuterJoin(right, 2)

    val expected = Seq((1,(Some(1),"a")), (1,(Some(2),"a")), (3,(None,"b")))
    expected should contain theSameElementsAs result.getRDD(sc).collect()
    result.getRDD(sc).partitions.size shouldEqual 2
  }

  test("fullOuterJoin"){
    val left = parallelize(Seq((1,1), (1,2), (2,3)))
    val right = parallelize(Seq((1,"a"), (3,"b")))
    val result = left.fullOuterJoin(right)

    val expected = Seq((1,(Some(1),Some("a"))), (1,(Some(2),Some("a"))), (2,(Some(3),None)), (3,(None,Some("b"))))
    expected should contain theSameElementsAs result.getRDD(sc).collect()
  }

  test("fullOuterJoin(numPartitions)"){
    val left = parallelize(Seq((1,1), (1,2), (2,3)))
    val right = parallelize(Seq((1,"a"), (3,"b")))
    val result = left.fullOuterJoin(right, 2)

    val expected = Seq((1,(Some(1),Some("a"))), (1,(Some(2),Some("a"))), (2,(Some(3),None)), (3,(None,Some("b"))))
    expected should contain theSameElementsAs result.getRDD(sc).collect()
    result.getRDD(sc).partitions.size shouldEqual 2
  }

  test("mapValues"){
    val input = parallelize(Seq((1,1), (1,2), (2,3)))
    val result = input.mapValues(_.toString)

    Seq((1,"1"), (1,"2"), (2,"3")) should contain theSameElementsAs result.getRDD(sc).collect()
  }

  test("cogroup(other)"){
    val first = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val second = parallelize(Seq((1,"a"), (2,"b")))
    val result = first.cogroup(second)

    val expected = Seq((1, (Seq(1,2), Seq("a"))), (2, (Seq(3,4), Seq("b"))))
    expected should contain theSameElementsAs result.getRDD(sc).collect()
  }


  test("cogroup(other1,other2)"){
    val first = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val second = parallelize(Seq((1,"a"), (2,"b")))
    val third = parallelize(Seq((1,"c"), (2,"d")))
    val result = first.cogroup(second, third)

    val expected = Seq((1, (Seq(1,2), Seq("a"), Seq("c"))), (2, (Seq(3,4), Seq("b"), Seq("d"))))
    expected should contain theSameElementsAs result.getRDD(sc).collect()
  }

  test("cogroup(other1,other2,other3)"){
    val first = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val second = parallelize(Seq((1,"a"), (2,"b")))
    val third = parallelize(Seq((1,"c"), (2,"d")))
    val fourth = parallelize(Seq((1,true), (2,false)))
    val result = first.cogroup(second, third, fourth)

    val expected = Seq((1, (Seq(1,2), Seq("a"), Seq("c"), Seq(true))), (2, (Seq(3,4), Seq("b"), Seq("d"), Seq(false))))
    expected should contain theSameElementsAs result.getRDD(sc).collect()
  }

  test("cogroup(other,numPartitions)"){
    val first = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val second = parallelize(Seq((1,"a"), (2,"b")))
    val result = first.cogroup(second, 2)

    val expected = Seq((1, (Seq(1,2), Seq("a"))), (2, (Seq(3,4), Seq("b"))))
    expected should contain theSameElementsAs result.getRDD(sc).collect()
    result.getRDD(sc).partitions.size shouldEqual 2
  }

  test("cogroup(other1,other2,numPartitions)"){
    val first = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val second = parallelize(Seq((1,"a"), (2,"b")))
    val third = parallelize(Seq((1,"c"), (2,"d")))
    val result = first.cogroup(second, third, 2)

    val expected = Seq((1, (Seq(1,2), Seq("a"), Seq("c"))), (2, (Seq(3,4), Seq("b"), Seq("d"))))
    expected should contain theSameElementsAs result.getRDD(sc).collect()
    result.getRDD(sc).partitions.size shouldEqual 2
  }

  test("cogroup(other1,other2,other3,numPartitions)"){
    val first = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val second = parallelize(Seq((1,"a"), (2,"b")))
    val third = parallelize(Seq((1,"c"), (2,"d")))
    val fourth = parallelize(Seq((1,true), (2,false)))
    val result = first.cogroup(second, third, fourth, 2)

    val expected = Seq((1, (Seq(1,2), Seq("a"), Seq("c"), Seq(true))), (2, (Seq(3,4), Seq("b"), Seq("d"), Seq(false))))
    expected should contain theSameElementsAs result.getRDD(sc).collect()
    result.getRDD(sc).partitions.size shouldEqual 2
  }

  test("groupWith(other)"){
    val first = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val second = parallelize(Seq((1,"a"), (2,"b")))
    val result = first.groupWith(second)

    val expected = Seq((1, (Seq(1,2), Seq("a"))), (2, (Seq(3,4), Seq("b"))))
    expected should contain theSameElementsAs result.getRDD(sc).collect()
  }

  test("groupWith(other1,other2)"){
    val first = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val second = parallelize(Seq((1,"a"), (2,"b")))
    val third = parallelize(Seq((1,"c"), (2,"d")))
    val result = first.groupWith(second, third)

    val expected = Seq((1, (Seq(1,2), Seq("a"), Seq("c"))), (2, (Seq(3,4), Seq("b"), Seq("d"))))
    expected should contain theSameElementsAs result.getRDD(sc).collect()
  }

  test("groupWith(other1,other2,other3)"){
    val first = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val second = parallelize(Seq((1,"a"), (2,"b")))
    val third = parallelize(Seq((1,"c"), (2,"d")))
    val fourth = parallelize(Seq((1,true), (2,false)))
    val result = first.groupWith(second, third, fourth)

    val expected = Seq((1, (Seq(1,2), Seq("a"), Seq("c"), Seq(true))), (2, (Seq(3,4), Seq("b"), Seq("d"), Seq(false))))
    expected should contain theSameElementsAs result.getRDD(sc).collect()
  }

  test("subtractByKey"){
    val left = parallelize(Seq((1,1), (1,2), (2,3)))
    val right = parallelize(Seq((1,"a"), (3,"b")))
    val result = left.subtractByKey(right)

    val expected = Seq((2,3))
    expected should contain theSameElementsAs result.getRDD(sc).collect()
  }

  test("subtractByKey(numPartitions)"){
    val left = parallelize(Seq((1,1), (1,2), (2,3)))
    val right = parallelize(Seq((1,"a"), (3,"b")))
    val result = left.subtractByKey(right, 2)

    val expected = Seq((2,3))
    expected should contain theSameElementsAs result.getRDD(sc).collect()
    result.getRDD(sc).partitions.size shouldEqual 2
  }

  test("keys"){
    val input = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val result = input.keys

    Seq(1, 1, 2, 2) should contain theSameElementsAs result.getRDD(sc).collect()
  }

  test("values"){
    val input = parallelize(Seq((1,1), (1,2), (2,3), (2,1)))
    val result = input.values

    Seq(1, 2, 3, 1) should contain theSameElementsAs result.getRDD(sc).collect()
  }

  test("sortByKey"){
    val input = parallelize(Seq((1,4), (3,6), (2,5)))
    val ascending = input.sortByKey()
    val descending = input.sortByKey(ascending = false)
    val ascending2Part = input.sortByKey(ascending = true, 2)

    Seq((1,4), (2,5), (3,6)) should contain theSameElementsInOrderAs ascending.getRDD(sc).collect()
    Seq((3,6), (2,5), (1,4)) should contain theSameElementsInOrderAs descending.getRDD(sc).collect()
    ascending2Part.getRDD(sc).partitions.size shouldEqual 2
  }

  test("partitionBy"){
    val input = parallelize(Seq((2,3), (1,2), (1,1), (2,1)), 2)

    val partitioned = input.partitionByKey()

    val result = partitioned.mapPartitions(in => Iterator(in.toList))

    Seq(List((1,2), (1,1)), List((2,3), (2,1))) should contain theSameElementsAs result.getRDD(sc).collect()
  }

  test("keyBy"){
    val input = parallelize(Seq("dog", "fish", "horse"))
    val result = input.keyBy(_.size)

    Seq((3, "dog"), (4, "fish"), (5, "horse")) should contain theSameElementsAs result.getRDD(sc).collect()
  }

//  Actions

  test("countByKey"){
    val input = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val result = input.countByKey

    Seq((1,2), (2,2)) should contain theSameElementsAs result.get(sc)
  }

  test("collectAsMap"){
    val input = parallelize(Seq((1,2), (3,4)))
    val result = input.collectAsMap

    Map((1,2), (3,4)) should contain theSameElementsAs result.get(sc)
  }

  test("reduceByKeyLocally"){
    val input = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val result = input.reduceByKeyLocally(_ + _)

    Seq((1,3), (2,7)) should contain theSameElementsAs result.get(sc)
  }

  test("lookup"){
    val input = parallelize(Seq((1,1), (1,2), (2,3), (2,4)))
    val result = input.lookup(2)

    Seq(3, 4) should contain theSameElementsAs result.get(sc)
  }

}
