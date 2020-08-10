package it.carloni.luca.aurora

import it.carloni.luca.aurora.option.ScoptParser.{BranchConfig, InitialLoadConfig, ReloadConfig, SourceLoadConfig}
import it.carloni.luca.aurora.option.{Branch, ScoptParser}
import it.carloni.luca.aurora.spark.engine.{InitialLoadEngine, ReLoadEngine, SourceLoadEngine}
import org.apache.log4j.Logger

object Main extends App {

  val logger: Logger = Logger.getRootLogger

  logger.info("Starting application main program")

  // FIRST, PARSE ARGUMENTS IN ORDER TO DETECT BRANCH TO BE RUN
  ScoptParser.branchParser.parse(args, BranchConfig()) match {

    case None => logger.error("Error during parsing of first set of arguments (application branch)")
    case Some(value) =>

      logger.info("Successfully parsed first set of arguments (application branch)")
      logger.info(value)

      // DETECT BRANCH TO BE RUN
      val branchesSet: Set[Branch] = Branch.values()
        .filter(_.getName.equalsIgnoreCase(value.applicationBranch))
        .toSet

      if (branchesSet.nonEmpty) {

        branchesSet.head match {

          // [a] INITIAL_LOAD
          case Branch.INITIAL_LOAD =>

            val branchName: String = Branch.INITIAL_LOAD.getName
            logger.info(s"Matched branch '$branchName'")
            ScoptParser.initialLoadOptionParser.parse(args, InitialLoadConfig()) match {

              case None => logger.error("Error during parsing of second set of arguments (branch arguments)")
              case Some(value) =>

                logger.info(value)
                logger.info("Successfully parsed second set of arguments (branch arguments)")
                new InitialLoadEngine(value.propertiesFile).run()
                logger.info(s"Successfully executed operations on branch '$branchName'")
            }

          // [b] SOURCE_LOAD
          case Branch.SOURCE_LOAD =>

            val branchName: String = Branch.SOURCE_LOAD.getName
            logger.info(s"Matched branch '$branchName'")
            ScoptParser.sourceLoadOptionParser.parse(args, SourceLoadConfig()) match {

              case None => logger.error("Error during parsing of second set of arguments (branch arguments)")
              case Some(value) =>

                logger.info(value)
                logger.info("Successfully parsed second set of arguments (branch arguments)")
                new SourceLoadEngine(value.propertiesFile).run(value)
                logger.info(s"Successfully executed operations on branch '$branchName'")
            }

          // [c] RE_LOAD
          case Branch.RE_LOAD =>

            val branchName: String = Branch.RE_LOAD.getName
            logger.info(s"Matched branch '$branchName'")

            ScoptParser.reloadOptionParser.parse(args, ReloadConfig()) match {

              case None => logger.error("Error during parsing of second set of arguments (branch arguments)")
              case Some(value) =>

                logger.info(value)
                logger.info("Successfully parsed second set of arguments (branch arguments)")
                new ReLoadEngine(value.propertiesFile).run(value)
                logger.info(s"Successfully executed operations on branch '$branchName'")
            }
        }
      }
  }
}
