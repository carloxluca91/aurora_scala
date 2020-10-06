package it.carloni.luca.aurora.spark.functions.etl

import org.apache.spark.sql.Column
import org.apache.spark.sql.functions.concat_ws

case class LeftOrRightConcatWsFunction(functionToApply: String)
  extends ETLFunction(functionToApply, ETLSignatures.leftOrRightConcatWs.regex) {

  private final val concatColumn: Column = getColumnDefinitionAtGroup(4)
  private final val separator: String = signatureMatch.group(6)

  override protected val transformationFunction: Column => Column = {

    if (functionName startsWith "l") concat_ws(separator, concatColumn, _)
    else concat_ws(separator, _, concatColumn)
  }

  override protected def toStringRepr: String = {

    val concatSequenceQuoted: String = s"concat_column = '${concatColumn.toString()}'"
    val firstArgument: String = if (functionName startsWith "l") concatSequenceQuoted else nestedFunctionGroup3
    val secondArgument: String = if (functionName startsWith "r") concatSequenceQuoted else nestedFunctionGroup3

    s"concat($firstArgument, $secondArgument, separator = '$separator')"
  }
}