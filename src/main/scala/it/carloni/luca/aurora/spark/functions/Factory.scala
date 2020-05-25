package it.carloni.luca.aurora.spark.functions

import org.apache.log4j.Logger
import org.apache.spark.sql.Column

object Factory {

  private final val logger = Logger.getLogger(getClass)

  def apply(column: Column, functionToApply: String): Column = {

    val matchingSignatures: Signature.ValueSet = Signature.values
      .filter(_.signatureRegex
        .findFirstMatchIn(functionToApply)
        .nonEmpty)

    // IF A FUNCTION MATCHES
    if (matchingSignatures.nonEmpty) {

      val matchingSignature: Signature.Value = matchingSignatures.head
      matchingSignature match {

        case Signature.dateFormat => new DateFormat(column, functionToApply).transform
        case Signature.lpad => new Lpad(column, functionToApply).transform
        case Signature.rpad => new Rpad(column, functionToApply).transform
      }
    }

    else {

      // TODO: lancio eccezione personalizzata
      logger.error(s"Unable to match such function: $functionToApply")
      throw new Exception
    }
  }
}