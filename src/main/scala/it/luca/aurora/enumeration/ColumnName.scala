package it.luca.aurora.enumeration

object ColumnName extends Enumeration {

  protected case class Val(name: String) extends super.Val

  implicit def toVal(x: Value): Val = x.asInstanceOf[Val]

  val ApplicationId: Val = Val("application_id")
  val ApplicationType: Val = Val("application_type")
  val ApplicationUser: Val = Val("application_user")
  val DtInsert: Val = Val("dt_insert")
  val TsInsert: Val = Val("ts_insert")
  val Version: Val = Val("version")
  val ValidityEndDate: Val = Val("validity_end_date")
  val ValidityEndTime: Val = Val("validity_end_time")
  val ValidityStartDate: Val = Val("validity_start_date")
  val ValidityStartTime: Val = Val("validity_start_time")

}
