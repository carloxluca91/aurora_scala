package it.luca.aurora.option

import it.luca.aurora.enumeration.ScoptOption

case class SourceLoadConfig(dataSource: String = "N.P.",
                            dtBusinessDate: Option[String] = None,
                            specificationVersion: Option[String] = Some("LATEST"))
  extends BaseConfig {

  protected val scoptOptionMap: Map[ScoptOption.Value, String] = Map(ScoptOption.DataSource -> dataSource,
    ScoptOption.DtBusinessDate -> dtBusinessDate.orNull,
    ScoptOption.SpecificationVersion -> specificationVersion.orNull)
}