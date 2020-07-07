package it.carloni.luca.aurora

import it.carloni.luca.aurora.option.ScoptParser.{InitialLoadConfig, ReloadConfig, SourceLoadConfig}
import it.carloni.luca.aurora.option.{Branch, ScoptOption, ScoptParser}
import it.carloni.luca.aurora.spark.engine.{InitialLoadEngine, ReLoadEngine, SourceLoadEngine}
import org.apache.log4j.Logger

object Main extends App {

  val logger: Logger = Logger.getRootLogger

  logger.info("Starting application main program")

  case class BranchConfig(applicationBranch: String = "") {

    override def toString: String = s"${ScoptOption.applicationBranchOption.text} = $applicationBranch"
  }

  // FIRST, PARSE ARGUMENTS IN ORDER TO DETECT BRANCH TO BE RUN
  ScoptParser.branchParser.parse(args, BranchConfig()) match {

    case None => logger.error("Error during parsing of first set of arguments (application branch)")
    case Some(value) =>

      logger.info("Successfully parsed first set of arguments (application branch)")
      logger.info(value.toString)

      // DETECT BRANCH TO BE RUN
      Branch.asBranchName(Branch.withName(value.applicationBranch)) match {

        // [a] INITIAL_LOAD
        case Branch.InitialLoad =>

          logger.info(s"Matched branch \'${Branch.InitialLoad.name}\'")

          ScoptParser.initialLoadOptionParser.parse(args, InitialLoadConfig()) match {

            case None => logger.error("Error during parsing of second set of arguments (branch arguments)")
            case Some(value) =>

              logger.info(value)
              logger.info("Successfully parsed second set of arguments (branch arguments)")
              new InitialLoadEngine(value.propertiesFile).run()
          }

        // [b] SOURCE_LOAD
        case Branch.SourceLoad =>

          logger.info(s"Matched branch \'${Branch.SourceLoad.name}\'")

          ScoptParser.sourceLoadOptionParser.parse(args, SourceLoadConfig()) match {

            case None => logger.error("Error during parsing of second set of arguments (branch arguments)")
            case Some(value) =>

              logger.info(value)
              logger.info("Successfully parsed second set of arguments (branch arguments)")
              new SourceLoadEngine(value.propertiesFile).run(value.bancllName)
          }

        // [c] RE_LOAD
        case Branch.ReLoad =>

          logger.info(s"Matched branch \'${Branch.SourceLoad.name}\'")

          ScoptParser.reloadOptionParser.parse(args, ReloadConfig()) match {

            case None => logger.error("Error during parsing of second set of arguments (branch arguments)")
            case Some(value) =>

              logger.info(value)
              logger.info("Successfully parsed second set of arguments (branch arguments)")
              new ReLoadEngine(value.propertiesFile).run(mappingSpecificationFlag = value.mappingSpecificationFlag,
                lookupFlag = value.lookUpFlag,
                completeOverwriteFlag = value.completeOverwriteFlag)
          }
      }
  }
}
