package it.carloni.luca.aurora.spark.engine

import java.io.File
import java.sql.{Connection, Date, DriverManager, Timestamp}
import java.time.{Instant, LocalDate, ZoneId, ZonedDateTime}

import it.carloni.luca.aurora.spark.data.LogRecord
import it.carloni.luca.aurora.utils.{ColumnName, DateFormat}
import org.apache.commons.configuration.PropertiesConfiguration
import org.apache.log4j.Logger
import org.apache.spark.SparkContext
import org.apache.spark.sql.{DataFrame, DataFrameReader, SaveMode, SparkSession}

import scala.util.{Failure, Success, Try}

abstract class AbstractEngine(private final val jobPropertiesFile: String) {

  private final val logger = Logger.getLogger(getClass)
  protected final val sparkSession: SparkSession = getOrCreateSparkSession
  protected final val jobProperties: PropertiesConfiguration = loadJobProperties(jobPropertiesFile)

  // JDBC OPTIONS AND DATAFRAME READER
  private final val jdbcOptions: Map[String, String] = Map(

    "url" -> jobProperties.getString("jdbc.url"),
    "driver" -> jobProperties.getString("jdbc.driver.className"),
    "user" -> jobProperties.getString("jdbc.user"),
    "password" -> jobProperties.getString("jdbc.password"),
    "useSSL" -> jobProperties.getString("jdbc.useSSL")
  )

  private final val jdbcReader: DataFrameReader = sparkSession.read
    .format("jdbc")
    .options(jdbcOptions)

  // DATABASES
  protected final val pcAuroraDBName: String = jobProperties.getString("database.pc_aurora")
  protected final val lakeCedacriDBName: String = jobProperties.getString("database.lake_cedacri")

  // TABLES
  protected final val mappingSpecificationTBLName: String = jobProperties.getString("table.mapping_specification.name")
  protected final val dataLoadLogTBLName: String = jobProperties.getString("table.sourceload_log.name")
  protected final val lookupTBLName: String = jobProperties.getString("table.lookup.name")

  // FUNCTION FOR GENERATING LOG RECORDS
  protected val createLogRecord: (String, Option[String], Option[String], String, Option[String]) => LogRecord =
    (branchName, bancllNameOpt, dtRiferimentoOpt, impactedTable, exceptionMsgOpt) => {

      val applicationId: String = sparkSession.sparkContext.applicationId
      val applicationName: String = sparkSession.sparkContext.appName
      val dtRiferimentoSQLDateOpt: Option[Date] = if (dtRiferimentoOpt.nonEmpty) {

        Some(Date.valueOf(
          LocalDate.parse(dtRiferimentoOpt.get,
            DateFormat.DT_RIFERIMENTO.getFormatter)))

      } else None

      val applicationStartTime: Timestamp = Timestamp.from(Instant.ofEpochMilli(sparkSession.sparkContext.startTime))
      val applicationEndTime: Timestamp = Timestamp.from(ZonedDateTime.now(ZoneId.of("Europe/Rome")).toInstant)
      val applicationFinishCode: Int = if (exceptionMsgOpt.isEmpty) 0 else -1
      val applicationFinishStatus: String = if (exceptionMsgOpt.isEmpty) "successed" else "failed"

      LogRecord(applicationId,
        applicationName,
        branchName,
        applicationStartTime,
        applicationEndTime,
        bancllNameOpt,
        dtRiferimentoSQLDateOpt,
        impactedTable,
        exceptionMsgOpt,
        applicationFinishCode,
        applicationFinishStatus)
    }

  protected def getJDBCConnection: java.sql.Connection = {

    val jdbcURL: String = jobProperties.getString("jdbc.url")
    val jdbcUser: String = jobProperties.getString("jdbc.user")
    val jdbcPassword: String = jobProperties.getString("jdbc.password")
    val jdbcUseSSL: String = jobProperties.getString("jdbc.useSSL")

    Class.forName("com.mysql.jdbc.Driver")

    val jdbcUrlConnectionStr: String = s"$jdbcURL/?useSSL=$jdbcUseSSL"
    logger.info(s"Attempting to connect to JDBC url $jdbcUrlConnectionStr with credentials ($jdbcUser, $jdbcPassword)")

    val jdbcConnection: Connection = DriverManager.getConnection(jdbcUrlConnectionStr,
      jobProperties.getString("jdbc.user"),
      jobProperties.getString("jdbc.password"))

    logger.info(s"Successfully connected to JDBC url $jdbcUrlConnectionStr with credentials ($jdbcUser, $jdbcPassword)")
    jdbcConnection
  }

  protected def readFromJDBC(databaseName: String, tableName: String): DataFrame = {

    logger.info(s"Starting to load table '$databaseName'.'$tableName'")
    Try {

      jdbcReader
        .option("dbtable", s"$databaseName.$tableName")
        .load()

    } match {

      case Failure(exception) =>

        logger.error(s"Error while trying to read table '$databaseName'.'$tableName'. Stack trace: ", exception)
        throw exception

      case Success(value) =>

        logger.info(s"Successfully loaded table '$databaseName'.'$tableName'")
        value
    }
  }

  protected def writeToJDBCAndLog[T](db: String,
                                     table: String,
                                     saveMode: SaveMode,
                                     truncateFlag: Boolean,
                                     logRecordGenerationFunction: (String, Option[String]) => LogRecord,
                                     dfGenerationFunction: T => DataFrame,
                                     dfGenerationFunctionArg: T): Unit = {

    import sparkSession.implicits._

    val exceptionMsgOpt: Option[String] = Try {

      val dfToWrite: DataFrame = dfGenerationFunction(dfGenerationFunctionArg)
      val timestampColumnsExceptTsInserimento: Seq[String] = dfToWrite.dtypes
        .filter(_._2 equalsIgnoreCase "timestampType")
        .filterNot(_._1 equalsIgnoreCase ColumnName.TS_INSERIMENTO.getName)
        .map(_._1)

      /**
       * If a dataframe column (different from "ts_inserimento") is a timestamp,
       * we must first create the table via JDBC connection in order to set "datetime" type for such column.
       * This is due to the limited timestamp range on MySQL.
       *
       * Indeed, from MySQL docs:
       *
       * The TIMESTAMP data type has a range of '1970-01-01 00:00:01' UTC to '2038-01-09 03:14:07' UTC
       * The DATETIME data type has a range of '1000-01-01 00:00:00' to '9999-12-31 23:59:59'.
       */

      if (timestampColumnsExceptTsInserimento.nonEmpty) {

        logger.info(s"Identified ${timestampColumnsExceptTsInserimento.size} timestamp column(s) different from " +
          s"'${ColumnName.TS_INSERIMENTO.getName}': ${timestampColumnsExceptTsInserimento.map(x => s"'$x'").mkString(", ")})")

        val jdbcConnection: java.sql.Connection = getJDBCConnection
        val existsCurrentTable: Boolean = jdbcConnection.getMetaData.getTables(db, null, table, null).next()
        if (existsCurrentTable) {

          logger.info(s"Table '$db'.'$table' has some timestamp columns but exists already. Thus, not creating again")
        } else {

          logger.warn(s"Table '$db'.'$table' has some timestamp columns but does not exist yet. Thus, defining it now")
          val createTableStatement: java.sql.Statement = jdbcConnection.createStatement
          createTableStatement.execute(getCreateTableStatementFromDfSchema(dfToWrite, db, table))
          logger.info(s"Successfully created table '$db'.'$table'")
        }
      }

      writeToJDBC(dfToWrite,
        db,
        table,
        saveMode,
        truncateFlag)

    } match {
      case Failure(exception) =>

        val details: String = s"'$db'.'$table' with savemode '$saveMode'"
        logger.error(s"Caught exception while trying to save data into $details. Stack trace: ", exception)
        val firstNStackTraceStrings: String = exception.getStackTrace
          .toSeq
          .take(10)
          .map(_.toString)
          .mkString("\n")

        Some(s"${exception.toString}. Stack trace: \n" + firstNStackTraceStrings)

      case Success(_) => None
    }

    val logRecordDf: DataFrame = Seq(logRecordGenerationFunction(table, exceptionMsgOpt)).toDF
    writeToJDBC(logRecordDf,
      pcAuroraDBName,
      dataLoadLogTBLName,
      SaveMode.Append,
      truncate = false)
  }

  /* PRIVATE AREA */

  private def getCreateTableStatementFromDfSchema(df: DataFrame, database: String, table: String): String = {

    val fromSparkToMySQLType: ((String, String)) => String = tuple2 => {

      val columnName: String = tuple2._1
      val columnType: String = tuple2._2.toLowerCase
      columnType match {

        case "stringtype" => "VARCHAR(50)"
        case "integertype" => "INT"
        case "doubletype" => "DOUBLE"
        case "longtype" => "BIGINT"
        case "datetype" => "DATE"
        case "timestamptype" => if (columnName equalsIgnoreCase ColumnName.TS_INSERIMENTO.getName) "TIMESTAMP" else "DATETIME"
      }
    }

    val createTableStateMent: String = s" CREATE TABLE IF NOT EXISTS $database.$table (\n " +
      df.dtypes.map(x => s"    ${x._1} ${fromSparkToMySQLType(x)}").mkString(",\n    ") + "  \n    )\n"

    logger.info(s"Create table statement for table '$database'.'$table': \n\n $createTableStateMent")
    createTableStateMent
  }

  private def writeToJDBC(outputDataFrame: DataFrame, databaseName: String, tableName: String,
                          saveMode: SaveMode, truncate: Boolean): Unit = {

    val truncateOptionValue: String = if (truncate & (saveMode == SaveMode.Overwrite)) "true" else "false"
    val savingDetails: String = s"table: '$databaseName'.'$tableName', savemode: '$saveMode', truncate: '$truncateOptionValue'"
    logger.info(s"Starting to save dataframe into $savingDetails")
    logger.info(f"Dataframe schema:\n\n${outputDataFrame.schema.treeString}")

    outputDataFrame.write
      .format("jdbc")
      .options(jdbcOptions)
      .option("dbtable", s"$databaseName.$tableName")
      .option("truncate", truncateOptionValue)
      .mode(saveMode)
      .save()

    logger.info(s"Successfully saved data into $savingDetails")
  }

  private def getOrCreateSparkSession: SparkSession = {

    logger.info("Trying to get or create SparkSession")

    val sparkSession: SparkSession = SparkSession
      .builder()
      .getOrCreate()

    val sparkContext: SparkContext = sparkSession.sparkContext
    logger.info(s"Successfully created SparkSession for application '${sparkContext.appName}'. " +
      s"Application Id: '${sparkContext.applicationId}', UI url: ${sparkContext.uiWebUrl.get}")
    sparkSession
  }

  private def loadJobProperties(propertiesFile: String): PropertiesConfiguration = {

    val propertiesConfiguration: PropertiesConfiguration = new PropertiesConfiguration
    Try {

      propertiesConfiguration.load(new File(propertiesFile))

    } match {

      case Failure(exception) =>

        logger.error("Exception occurred while loading properties file. Stack trace: ", exception)
        throw exception

      case Success(_) =>

        logger.info("Successfully loaded properties file")
        propertiesConfiguration
    }
  }
}
