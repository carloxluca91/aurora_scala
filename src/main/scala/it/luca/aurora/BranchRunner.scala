package it.luca.aurora

import it.luca.aurora.enumeration.Branch
import it.luca.aurora.option.{BranchConfig, DataSourceLoadConfig, ScoptParser}
import it.luca.aurora.spark.job.{DataSourceLoadJob, InitialLoadJob, ReloadJob}
import it.luca.aurora.utils.className
import org.apache.spark.SparkContext
import org.apache.spark.sql.hive.HiveContext

object BranchRunner
  extends Logging {

  def apply(branchConfig: BranchConfig, args: Seq[String]): Unit = {

    val branchId: String = branchConfig.applicationBranch
    val propertiesFile: String = branchConfig.propertiesFile

    val sparkContext = new SparkContext()
    val hiveContext = new HiveContext(sparkContext)
    hiveContext.setConf("hive.exec.dynamic.partition", "true")
    hiveContext.setConf("hive.exec.dynamic.partition.mode", "nonstrict")
    log.info(s"""Initialized both ${className[SparkContext]} and ${className[HiveContext]}
         |
         |    appUser: ${sparkContext.sparkUser}
         |    appName: ${sparkContext.appName}
         |    appId: ${sparkContext.applicationId}
         |""".stripMargin)

    Branch.withId(branchId) match {
      case Branch.InitialLoad => InitialLoadJob(hiveContext, propertiesFile).run()
      case Branch.Reload => ReloadJob(hiveContext, propertiesFile).run()
      case Branch.DataSourceLoad =>
        ScoptParser.dataSourceLoadParser.parse(args, DataSourceLoadConfig()) match {
          case Some(x) =>
            log.info(s"Parsed second set of arguments $x")
            DataSourceLoadJob(hiveContext, propertiesFile, x).run()
          case None => // arguments are bad, error message will have been displayed
        }
    }
  }
}
