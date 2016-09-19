package io.github.qf6101.mfm.factorization.multinomial

import io.github.qf6101.mfm.baseframe.Coefficients
import io.github.qf6101.mfm.factorization.binomial.FmCoefficients
import org.apache.spark.sql.SparkSession
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

/**
  * Created by qfeng on 16-9-7.
  */
class MfmCoefficients(val initMean: Double,
                      val initStdev: Double,
                      var numFeatures: Int,
                      var numInteractFeatures: Int,
                      var numFactors: Int,
                      val k0: Boolean,
                      val k1: Boolean,
                      val k2: Boolean,
                      val numClasses: Int) extends Coefficients {
  var thetas = Array.fill[FmCoefficients](numClasses)(new FmCoefficients(
    initMean, initStdev, numFeatures, numInteractFeatures, numFactors, k0, k1, k2))

  def this(thetas: Array[FmCoefficients]) {
    this(thetas(0).initMean, thetas(0).initStdev, thetas(0).numFeatures, thetas(0).numInteractFeatures,
      thetas(0).numFactors, thetas(0).k0, thetas(0).k1, thetas(0).k2, thetas.length)
    this.thetas = thetas
  }

  /**
    * 只复制this的结构（比如参数个数），不复制内容
    *
    * @return 复制的拷贝
    */
  override def copyEmpty(): Coefficients = new MfmCoefficients(this.initMean, this.initStdev,
    this.numFeatures, this.numInteractFeatures, this.numFactors, this.k0, this.k1, this.k2, this.numClasses)

  /**
    * 对应系数加法，加至this上
    *
    * @param other 加数
    * @return this
    */
  override def +=(other: Coefficients): Coefficients = {
    val otherCoeffs = other.asInstanceOf[MfmCoefficients]
    (this.thetas zip otherCoeffs.thetas).foreach { case (me, other) =>
      me += other
    }
    this
  }

  /**
    * 对应系数减法，减至this上
    *
    * @param other 减数
    * @return this
    */
  override def -=(other: Coefficients): Coefficients = {
    val otherCoeffs = other.asInstanceOf[MfmCoefficients]
    (this.thetas zip otherCoeffs.thetas).foreach { case (me, other) =>
      me -= other
    }
    this
  }

  /**
    * 对应系数加上同一实数，加至复制this的类上
    *
    * @param addend 加数
    * @return 加法结果（拷贝）
    */
  override def +(addend: Double): Coefficients = {
    val me = this.copy.asInstanceOf[MfmCoefficients]
    val result = me.thetas.map { theta =>
      (theta + addend).asInstanceOf[FmCoefficients]
    }
    new MfmCoefficients(result)
  }

  /**
    * 对应系数乘上同一实数，加至复制this的类上
    *
    * @param multiplier 乘数
    * @return 乘法结果
    */
  override def *(multiplier: Double): Coefficients = {
    val me = this.copy.asInstanceOf[MfmCoefficients]
    val result = me.thetas.map { theta =>
      (theta * multiplier).asInstanceOf[FmCoefficients]
    }
    new MfmCoefficients(result)
  }

  /**
    * 对应系数除上同一实数，加至复制this的类上
    *
    * @param dividend 除数
    * @return 除法结果
    */
  override def /(dividend: Double): Coefficients = {
    val me = this.copy.asInstanceOf[MfmCoefficients]
    val result = me.thetas.map { theta =>
      (theta / dividend).asInstanceOf[FmCoefficients]
    }
    new MfmCoefficients(result)
  }

  /**
    * 计算L1的正则值
    *
    * @param regParam 正则参数
    * @return 参数绝对值加权后的L1正则值
    */
  override def L1RegValue(regParam: Array[Double]): Double = ???


  /**
    * 计算系数的2范数
    * sum(abs(A).^p)^(1/p) where p=2
    *
    * @return 系数的2范数
    */
  override def norm: Double = {
    this.thetas.map(_.norm).sum / this.thetas.length
  }

  /**
    * 用L1稀疏化系数
    *
    * @param regParam 正则参数值
    * @param stepSize 学习率
    * @return 稀疏化后的系数
    */
  override def L1Shrink(regParam: Array[Double], stepSize: Double): Coefficients = {
    thetas.foreach { theta =>
      theta.L1Shrink(regParam, stepSize)
    }
    this
  }


  /**
    * 同时复制this的结构和内容
    *
    * @return 复制的拷贝
    */
  override def copy: Coefficients = {
    new MfmCoefficients(this.thetas.map(_.copy.asInstanceOf[FmCoefficients]))
  }

  /**
    * 计算L2的正则值
    *
    * @param reg 正则参数
    * @return 参数加权后的L2正则值
    */
  override def L2RegValue(reg: Array[Double]): Double = {
    thetas.map { theta =>
      theta.L2RegValue(reg)
    }.sum
  }

  /**
    * 计算L2的正则梯度值
    *
    * @param reg 正则参数
    * @return 参数加权后的L2正则梯度值
    */
  override def L2RegGradient(reg: Array[Double]): Coefficients = {
    val me = this.copy.asInstanceOf[MfmCoefficients]
    val result = me.thetas.map { theta =>
      theta.L2RegGradient(reg).asInstanceOf[FmCoefficients]
    }
    new MfmCoefficients(result)
  }

  /**
    * 保存元数据至文件
    *
    * @param location 文件位置
    */
  override def saveMeta(location: String): Unit = {
    val json = (Coefficients.namingCoeffType -> MfmCoefficients.getClass.toString) ~
      (MfmCoefficients.namingNumClasses -> numClasses)
    SparkSession.builder().getOrCreate().sparkContext.
      makeRDD(List(compact(render(json)))).repartition(1).saveAsTextFile(location)
  }

  /**
    * 保存数据至文件
    *
    * @param location 文件位置
    */
  override def saveData(location: String): Unit = {
    thetas.zipWithIndex.foreach { case (theta, index) =>
      theta.saveMeta(location + "/" + index + "/" + Coefficients.namingMetaFile)
      theta.saveData(location + "/" + index + "/" + Coefficients.namingDataFile)
    }
  }

  /**
    * 与另一个系数是否相等
    *
    * @param other 另一个系数
    * @return 是否相等
    */
  override def equals(other: Coefficients): Boolean = {
    if(other.isInstanceOf[MfmCoefficients]) {
      val otherCoeffs = other.asInstanceOf[MfmCoefficients]
      (thetas zip otherCoeffs.thetas).foldLeft(true) { case (eq, (me, he)) =>
        eq && me.equals(he)
      }
    } else false
  }
}

object MfmCoefficients {
  val namingNumClasses = "num_classes"

  def apply(location: String): MfmCoefficients = {
    val spark = SparkSession.builder().getOrCreate()
    val meta = spark.read.json(location + "/" + Coefficients.namingMetaFile).first()
    val numClasses = meta.getAs[Long](namingNumClasses).toInt
    val thetas = Array.fill[FmCoefficients](numClasses)(null)
    for (index <- 0 until numClasses) {
      thetas(index) = FmCoefficients(location + "/" + Coefficients.namingDataFile + "/" + index)
    }
    new MfmCoefficients(thetas)
  }
}
