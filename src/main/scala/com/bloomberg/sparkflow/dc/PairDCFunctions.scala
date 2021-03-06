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
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Encoder
import org.apache.spark.sql.EncoderUtil.encoderFor
import org.apache.spark.sql.catalyst.encoders.ExpressionEncoder
import org.apache.spark.{HashPartitioner, Partitioner}

import scala.collection.Map
import scala.reflect.ClassTag
import scala.util.Random

/**
  * Created by ngoehausen on 4/19/16.
  */
class PairDCFunctions[K: Encoder, V: Encoder](self: DC[(K, V)])
                                             (implicit kt: ClassTag[K],
                                              vt: ClassTag[V],
                                              ord: Ordering[K] = null,
                                              kSeqEncoder: Encoder[(K, Seq[V])],
                                              kArrEncoder: Encoder[(K, Array[V])]) {

  private val longEnc = encoderFor[Long]

  private val kEncoder = encoderFor[K]
  private val vEncoder = encoderFor[V]
  private val kvEncoder = ExpressionEncoder.tuple(kEncoder, encoderFor[V])
  private val kLongEncoder = ExpressionEncoder.tuple(kEncoder, longEnc)


  def combineByKey[C](createCombiner: V => C,
                      mergeValue: (C, V) => C,
                      mergeCombiners: (C, C) => C,
                      numPartitions: Int)(implicit kcEncoder: Encoder[(K, C)]): DC[(K, C)] = {
    val encoder = encoderFor[(K, C)]
    val function = (rdd: RDD[(K, V)]) => rdd.combineByKey(createCombiner, mergeValue, mergeCombiners, numPartitions)
    val functionHashTargets = Seq(createCombiner, mergeValue, mergeCombiners)
    val hashTargets = Seq("combineByKey", numPartitions.toString)
    new RDDTransformDC(encoder, self, function, functionHashTargets, hashTargets)
  }

  def aggregateByKey[U: ClassTag](zeroValue: U)(seqOp: (U, V) => U, combOp: (U, U) => U)
                                 (implicit kUEncoder: Encoder[(K, U)]): DC[(K, U)] = {
    val encoder = encoderFor[(K, U)]
    val resultFunction = (rdd: RDD[(K, V)]) => rdd.aggregateByKey(zeroValue)(seqOp, combOp)
    new RDDTransformDC(encoder, self, resultFunction, Seq(seqOp, combOp), Seq("aggregateByKey", zeroValue.toString))
  }

  def aggregateByKey[U: ClassTag](zeroValue: U, numPartitions: Int)
                                 (seqOp: (U, V) => U, combOp: (U, U) => U)
                                 (implicit kUEncoder: Encoder[(K, U)]): DC[(K, U)] = {
    val encoder = encoderFor[(K, U)]
    val resultFunction = (rdd: RDD[(K, V)]) => rdd.aggregateByKey(zeroValue, numPartitions)(seqOp, combOp)
    new RDDTransformDC(encoder, self, resultFunction, Seq("aggregateByKey", zeroValue.toString, numPartitions.toString))
  }

  def foldByKey(zeroValue: V)(func: (V, V) => V): DC[(K, V)] = {
    val resultFunction = (rdd: RDD[(K, V)]) => rdd.foldByKey(zeroValue)(func)
    new RDDTransformDC(kvEncoder, self, resultFunction, func, Seq("foldByKey", zeroValue.toString))
  }

  def foldByKey(zeroValue: V, numPartitions: Int)(func: (V, V) => V): DC[(K, V)] = {
    val resultFunction = (rdd: RDD[(K, V)]) => rdd.foldByKey(zeroValue, numPartitions)(func)
    new RDDTransformDC(kvEncoder, self, resultFunction, func, Seq("foldByKey", zeroValue.toString, numPartitions.toString))
  }

  def sampleByKey(withReplacement: Boolean,
                  fractions: Map[K, Double],
                  seed: Long = Random.nextLong): DC[(K, V)] = {
    val resultFunction = (rdd: RDD[(K, V)]) => rdd.sampleByKey(withReplacement, fractions, seed)
    new RDDTransformDC(kvEncoder, self, resultFunction, Seq("sampleByKey", withReplacement.toString, fractions.toString, seed.toString))
  }

  ////  Experimental
  def sampleByKeyExact(withReplacement: Boolean,
                       fractions: Map[K, Double],
                       seed: Long = Random.nextLong): DC[(K, V)] = {
    val resultFunction = (rdd: RDD[(K, V)]) => rdd.sampleByKeyExact(withReplacement, fractions, seed)
    new RDDTransformDC(kvEncoder, self, resultFunction, Seq("sampleByKeyExact", withReplacement.toString, fractions.toString, seed.toString))
  }

  def reduceByKey(func: (V, V) => V): DC[(K, V)] = {
    val resultFunction = (rdd: RDD[(K, V)]) => rdd.reduceByKey(func)
    new RDDTransformDC(kvEncoder, self, resultFunction, func, Seq("reduceByKey"))
  }

  def reduceByKey(func: (V, V) => V, numPartitions: Int): DC[(K, V)] = {
    val resultFunction = (rdd: RDD[(K, V)]) => rdd.reduceByKey(func, numPartitions)
    new RDDTransformDC(kvEncoder, self, resultFunction, func, Seq("reduceByKey", numPartitions.toString))
  }

  def countApproxDistinctByKey(relativeSD: Double = 0.05): DC[(K, Long)] = {
    val resultFunction = (rdd: RDD[(K, V)]) => rdd.countApproxDistinctByKey(relativeSD)
    new RDDTransformDC(kLongEncoder, self, resultFunction, Seq("countApproxDistinctByKey", relativeSD.toString))
  }

  def countApproxDistinctByKey(relativeSD: Double, numPartitions: Int): DC[(K, Long)] = {
    val resultFunction = (rdd: RDD[(K, V)]) => rdd.countApproxDistinctByKey(relativeSD, numPartitions)
    new RDDTransformDC(kLongEncoder, self, resultFunction, Seq("countApproxDistinctByKey", relativeSD.toString, numPartitions.toString))
  }

  def groupByKey(): DC[(K, Seq[V])] = {
    val encoder = encoderFor[(K, Seq[V])]
    val resultFunction = (rdd: RDD[(K, V)]) => rdd.groupByKey().map { case (k, values) => (k, values.toSeq) }
    new RDDTransformDC(encoder, self, resultFunction, Seq("groupByKey"))
  }

  def groupByKey(numPartitions: Int): DC[(K, Seq[V])] = {
    val encoder = encoderFor[(K, Seq[V])]
    val resultFunction = (rdd: RDD[(K, V)]) => rdd.groupByKey(numPartitions).map { case (k, values) => (k, values.toSeq) }
    new RDDTransformDC(encoder, self, resultFunction, Seq("groupByKey", "groupByKey", numPartitions.toString))
  }

  def join[W](other: DC[(K, W)])(implicit kvwEncoder: Encoder[(K, (V, W))]): DC[(K, (V, W))] = {
    val encoder = encoderFor[(K, (V, W))]
    val resultFunc = (rdds: Seq[RDD[_ <: Product2[K, _]]]) => {
      val left = rdds(0).asInstanceOf[RDD[(K, V)]]
      val right = rdds(1).asInstanceOf[RDD[(K, W)]]
      left.join(right)
    }
    new MultiInputPairDC[(K, (V, W)), K](encoder, Seq(self, other), resultFunc)
  }

  def join[W](other: DC[(K, W)], numPartitions: Int)(implicit kvwEncoder: Encoder[(K, (V, W))]): DC[(K, (V, W))] = {
    val encoder = encoderFor[(K, (V, W))]
    val resultFunc = (rdds: Seq[RDD[_ <: Product2[K, _]]]) => {
      val left = rdds(0).asInstanceOf[RDD[(K, V)]]
      val right = rdds(1).asInstanceOf[RDD[(K, W)]]
      left.join(right, numPartitions)
    }
    new MultiInputPairDC[(K, (V, W)), K](encoder, Seq(self, other), resultFunc)
  }

  def leftOuterJoin[W](other: DC[(K, W)])(implicit kvwEncoder: Encoder[(K, (V, Option[W]))]): DC[(K, (V, Option[W]))] = {
    val encoder = encoderFor[(K, (V, Option[W]))]
    val resultFunc = (rdds: Seq[RDD[_ <: Product2[K, _]]]) => {
      val left = rdds(0).asInstanceOf[RDD[(K, V)]]
      val right = rdds(1).asInstanceOf[RDD[(K, W)]]
      left.leftOuterJoin(right)
    }
    new MultiInputPairDC[(K, (V, Option[W])), K](encoder, Seq(self, other), resultFunc)
  }

  def leftOuterJoin[W](other: DC[(K, W)], numPartitions: Int)(implicit kvwEncoder: Encoder[(K, (V, Option[W]))]): DC[(K, (V, Option[W]))] = {
    val encoder = encoderFor[(K, (V, Option[W]))]
    val resultFunc = (rdds: Seq[RDD[_ <: Product2[K, _]]]) => {
      val left = rdds(0).asInstanceOf[RDD[(K, V)]]
      val right = rdds(1).asInstanceOf[RDD[(K, W)]]
      left.leftOuterJoin(right, numPartitions)
    }
    new MultiInputPairDC[(K, (V, Option[W])), K](encoder, Seq(self, other), resultFunc)
  }

  def rightOuterJoin[W](other: DC[(K, W)])(implicit kvwEncoder: Encoder[(K, (Option[V], W))]): DC[(K, (Option[V], W))] = {
    val encoder = encoderFor[(K, (Option[V], W))]
    val resultFunc = (rdds: Seq[RDD[_ <: Product2[K, _]]]) => {
      val left = rdds(0).asInstanceOf[RDD[(K, V)]]
      val right = rdds(1).asInstanceOf[RDD[(K, W)]]
      left.rightOuterJoin(right)
    }
    new MultiInputPairDC[(K, (Option[V], W)), K](encoder, Seq(self, other), resultFunc)
  }

  def rightOuterJoin[W](other: DC[(K, W)], numPartitions: Int)(implicit kvwEncoder: Encoder[(K, (Option[V], W))]): DC[(K, (Option[V], W))] = {
    val encoder = encoderFor[(K, (Option[V], W))]
    val resultFunc = (rdds: Seq[RDD[_ <: Product2[K, _]]]) => {
      val left = rdds(0).asInstanceOf[RDD[(K, V)]]
      val right = rdds(1).asInstanceOf[RDD[(K, W)]]
      left.rightOuterJoin(right, numPartitions)
    }
    new MultiInputPairDC[(K, (Option[V], W)), K](encoder, Seq(self, other), resultFunc)
  }

  def fullOuterJoin[W](other: DC[(K, W)])(implicit kvwEncoder: Encoder[(K, (Option[V], Option[W]))]): DC[(K, (Option[V], Option[W]))] = {
    val encoder = encoderFor[(K, (Option[V], Option[W]))]
    val resultFunc = (rdds: Seq[RDD[_ <: Product2[K, _]]]) => {
      val left = rdds(0).asInstanceOf[RDD[(K, V)]]
      val right = rdds(1).asInstanceOf[RDD[(K, W)]]
      left.fullOuterJoin(right)
    }
    new MultiInputPairDC[(K, (Option[V], Option[W])), K](encoder, Seq(self, other), resultFunc)
  }

  def fullOuterJoin[W](other: DC[(K, W)], numPartitions: Int)(implicit kvwEncoder: Encoder[(K, (Option[V], Option[W]))]): DC[(K, (Option[V], Option[W]))] = {
    val encoder = encoderFor[(K, (Option[V], Option[W]))]
    val resultFunc = (rdds: Seq[RDD[_ <: Product2[K, _]]]) => {
      val left = rdds(0).asInstanceOf[RDD[(K, V)]]
      val right = rdds(1).asInstanceOf[RDD[(K, W)]]
      left.fullOuterJoin(right, numPartitions)
    }
    new MultiInputPairDC[(K, (Option[V], Option[W])), K](encoder, Seq(self, other), resultFunc)
  }

  def mapValues[U: Encoder](f: V => U): DC[(K, U)] = {
    val encoder = ExpressionEncoder.tuple(encoderFor[K], encoderFor[U])
    new RDDTransformDC(encoder, self, (rdd: RDD[(K, V)]) => rdd.mapValues(f), f, Seq("mapValues"))
  }

  def flatMapValues[U: Encoder](f: V => TraversableOnce[U])(implicit kUEncoder: Encoder[(K, U)]): DC[(K, U)] = {
    val encoder = ExpressionEncoder.tuple(encoderFor[K], encoderFor[U])
    new RDDTransformDC(encoder, self, (rdd: RDD[(K, V)]) => rdd.flatMapValues(f), f, Seq("flatMapValues"))
  }

  def cogroup[W](other: DC[(K, W)])
                (implicit kvwEncoder: Encoder[(K, (Seq[V], Seq[W]))]): DC[(K, (Seq[V], Seq[W]))] = {
    val encoder = encoderFor[(K, (Seq[V], Seq[W]))]
    val resultFunc = (rdds: Seq[RDD[_ <: Product2[K, _]]]) => {
      val left = rdds(0).asInstanceOf[RDD[(K, V)]]
      val right = rdds(1).asInstanceOf[RDD[(K, W)]]
      left.cogroup(right).map { case (k, (v, w)) => (k, (v.toSeq, w.toSeq)) }
    }
    new MultiInputPairDC[((K, (Seq[V], Seq[W]))), K](encoder, Seq(self, other), resultFunc)
  }

  def cogroup[W1, W2](other1: DC[(K, W1)], other2: DC[(K, W2)])
                     (implicit kvwEncoder: Encoder[(K, (Seq[V], Seq[W1], Seq[W2]))])
  : DC[(K, (Seq[V], Seq[W1], Seq[W2]))] = {
    val encoder = encoderFor[(K, (Seq[V], Seq[W1], Seq[W2]))]
    val resultFunc = (rdds: Seq[RDD[_ <: Product2[K, _]]]) => {
      val first = rdds(0).asInstanceOf[RDD[(K, V)]]
      val second = rdds(1).asInstanceOf[RDD[(K, W1)]]
      val third = rdds(2).asInstanceOf[RDD[(K, W2)]]
      first.cogroup(second, third).map { case (k, (v, w1, w2)) => (k, (v.toSeq, w1.toSeq, w2.toSeq)) }
    }
    new MultiInputPairDC[((K, (Seq[V], Seq[W1], Seq[W2]))), K](encoder, Seq(self, other1, other2), resultFunc)
  }

  def cogroup[W1, W2, W3](other1: DC[(K, W1)], other2: DC[(K, W2)], other3: DC[(K, W3)])
                         (implicit kvwEncoder: Encoder[(K, (Seq[V], Seq[W1], Seq[W2], Seq[W3]))]):
  DC[(K, (Seq[V], Seq[W1], Seq[W2], Seq[W3]))] = {
    val encoder = encoderFor[(K, (Seq[V], Seq[W1], Seq[W2], Seq[W3]))]
    val resultFunc = (rdds: Seq[RDD[_ <: Product2[K, _]]]) => {
      val first = rdds(0).asInstanceOf[RDD[(K, V)]]
      val second = rdds(1).asInstanceOf[RDD[(K, W1)]]
      val third = rdds(2).asInstanceOf[RDD[(K, W2)]]
      val fourth = rdds(3).asInstanceOf[RDD[(K, W3)]]
      first.cogroup(second, third, fourth).map { case (k, (v, w1, w2, w3)) => (k, (v.toSeq, w1.toSeq, w2.toSeq, w3.toSeq)) }
    }
    new MultiInputPairDC[((K, (Seq[V], Seq[W1], Seq[W2], Seq[W3]))), K](encoder, Seq(self, other1, other2, other3), resultFunc)
  }

  def cogroup[W: ClassTag](other: DC[(K, W)], numPartitions: Int)
                          (implicit kvwEncoder: Encoder[(K, (Seq[V], Seq[W]))]): DC[(K, (Seq[V], Seq[W]))] = {
    val encoder = encoderFor[(K, (Seq[V], Seq[W]))]
    val resultFunc = (rdds: Seq[RDD[_ <: Product2[K, _]]]) => {
      val left = rdds(0).asInstanceOf[RDD[(K, V)]]
      val right = rdds(1).asInstanceOf[RDD[(K, W)]]
      left.cogroup(right, numPartitions).map { case (k, (v, w)) => (k, (v.toSeq, w.toSeq)) }
    }
    new MultiInputPairDC[((K, (Seq[V], Seq[W]))), K](encoder, Seq(self, other), resultFunc)
  }

  def cogroup[W1, W2](other1: DC[(K, W1)], other2: DC[(K, W2)], numPartitions: Int)
                     (implicit kvwEncoder: Encoder[(K, (Seq[V], Seq[W1], Seq[W2]))])
  : DC[(K, (Seq[V], Seq[W1], Seq[W2]))] = {
    val encoder = encoderFor[(K, (Seq[V], Seq[W1], Seq[W2]))]

    val resultFunc = (rdds: Seq[RDD[_ <: Product2[K, _]]]) => {
      val first = rdds(0).asInstanceOf[RDD[(K, V)]]
      val second = rdds(1).asInstanceOf[RDD[(K, W1)]]
      val third = rdds(2).asInstanceOf[RDD[(K, W2)]]
      first.cogroup(second, third, numPartitions).map { case (k, (v, w1, w2)) => (k, (v.toSeq, w1.toSeq, w2.toSeq)) }
    }
    new MultiInputPairDC[((K, (Seq[V], Seq[W1], Seq[W2]))), K](encoder, Seq(self, other1, other2), resultFunc)
  }

  def cogroup[W1, W2, W3](other1: DC[(K, W1)], other2: DC[(K, W2)], other3: DC[(K, W3)], numPartitions: Int)
                         (implicit kvwEncoder: Encoder[(K, (Seq[V], Seq[W1], Seq[W2], Seq[W3]))]):
  DC[(K, (Seq[V], Seq[W1], Seq[W2], Seq[W3]))] = {
    val encoder = encoderFor[(K, (Seq[V], Seq[W1], Seq[W2], Seq[W3]))]
    val resultFunc = (rdds: Seq[RDD[_ <: Product2[K, _]]]) => {
      val first = rdds(0).asInstanceOf[RDD[(K, V)]]
      val second = rdds(1).asInstanceOf[RDD[(K, W1)]]
      val third = rdds(2).asInstanceOf[RDD[(K, W2)]]
      val fourth = rdds(3).asInstanceOf[RDD[(K, W3)]]
      first.cogroup(second, third, fourth, numPartitions).map { case (k, (v, w1, w2, w3)) => (k, (v.toSeq, w1.toSeq, w2.toSeq, w3.toSeq)) }
    }
    new MultiInputPairDC[((K, (Seq[V], Seq[W1], Seq[W2], Seq[W3]))), K](encoder, Seq(self, other1, other2, other3), resultFunc)
  }


  def groupWith[W](other: DC[(K, W)])(implicit kvwEncoder: Encoder[(K, (Seq[V], Seq[W]))]): DC[(K, (Seq[V], Seq[W]))] = {
    this.cogroup(other)
  }

  def groupWith[W1, W2](other1: DC[(K, W1)], other2: DC[(K, W2)])(implicit kvwEncoder: Encoder[(K, (Seq[V], Seq[W1], Seq[W2]))])
  : DC[(K, (Seq[V], Seq[W1], Seq[W2]))] = {
    this.cogroup(other1, other2)
  }

  def groupWith[W1, W2, W3](other1: DC[(K, W1)], other2: DC[(K, W2)], other3: DC[(K, W3)])
                           (implicit kvwEncoder: Encoder[(K, (Seq[V], Seq[W1], Seq[W2], Seq[W3]))]): DC[(K, (Seq[V], Seq[W1], Seq[W2], Seq[W3]))] = {
    this.cogroup(other1, other2, other3)
  }

  def subtractByKey[W: ClassTag](other: DC[(K, W)]): DC[(K, V)] = {
    val resultFunc = (rdds: Seq[RDD[_ <: Product2[K, _]]]) => {
      val left = rdds(0).asInstanceOf[RDD[(K, V)]]
      val right = rdds(1).asInstanceOf[RDD[(K, W)]]
      left.subtractByKey(right)
    }
    new MultiInputPairDC[(K, V), K](kvEncoder, Seq(self, other), resultFunc)
  }

  def subtractByKey[W: ClassTag](other: DC[(K, W)], numPartitions: Int): DC[(K, V)] = {
    val resultFunc = (rdds: Seq[RDD[_ <: Product2[K, _]]]) => {
      val left = rdds(0).asInstanceOf[RDD[(K, V)]]
      val right = rdds(1).asInstanceOf[RDD[(K, W)]]
      left.subtractByKey(right, numPartitions)
    }
    new MultiInputPairDC[(K, V), K](kvEncoder, Seq(self, other), resultFunc)
  }

  def keys: DC[K] = {
    new RDDTransformDC(kEncoder, self, (rdd: RDD[(K, V)]) => rdd.keys, Seq("keys"))
  }

  def values: DC[V] = {
    new RDDTransformDC(vEncoder, self, (rdd: RDD[(K, V)]) => rdd.values, Seq("values"))
  }

  def partitionBy(partitioner: Partitioner): DC[(K, V)] = {
    new RDDTransformDC(kvEncoder, self, (rdd: RDD[(K, V)]) => rdd.partitionBy(partitioner), Seq("partitionBy", partitioner.numPartitions.toString))
  }

  def partitionByKey(): DC[(K, V)] = {
    new RDDTransformDC(kvEncoder, self, (rdd: RDD[(K, V)]) => rdd.partitionBy(new HashPartitioner(rdd.partitions.length)), Seq("partitionByKey"))
  }

  def repartitionAndSortWithinPartitions(partitioner: Partitioner): DC[(K, V)] = {
    new RDDTransformDC(kvEncoder, self, (rdd: RDD[(K, V)]) => rdd.repartitionAndSortWithinPartitions(partitioner), Seq("repartitionAndSortWithinPartitions", partitioner.numPartitions.toString))
  }

  def sortByKey(ascending: Boolean = true): DC[(K, V)] = {
    new RDDTransformDC(kvEncoder, self, (rdd: RDD[(K, V)]) => rdd.sortByKey(ascending), Seq("sortByKey", ascending.toString))
  }

  def sortByKey(ascending: Boolean, numPartitions: Int): DC[(K, V)] = {
    new RDDTransformDC(kvEncoder, self, (rdd: RDD[(K, V)]) => rdd.sortByKey(ascending, numPartitions), Seq("sortByKey", ascending.toString, numPartitions.toString))
  }

  //  Actions

  def countByKey: DR[Map[K, Long]] = {
    self.mapToResult(_.countByKey)
  }

  def collectAsMap: DR[Map[K, V]] = {
    self.mapToResult(_.collectAsMap)
  }

  def reduceByKeyLocally(func: (V, V) => V): DR[Map[K, V]] = {
    self.mapToResult(_.reduceByKeyLocally(func))
  }

  def lookup(key: K): DR[Seq[V]] = {
    self.mapToResult(_.lookup(key))
  }

}
