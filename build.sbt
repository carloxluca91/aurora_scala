val sparkVersion = "2.2.3"

lazy val auroraScala = (project in file("."))
  .settings(

    name := "aurora_scala",
    version := "0.0.1",
    scalaVersion := "2.11.8",
    scalacOptions ++= Seq(

      "-encoding", "UTF-8"
    ),

    libraryDependencies += ("org.apache.spark" %% "spark-core" % sparkVersion % "provided"),
    libraryDependencies += ("org.apache.spark" %% "spark-sql" % sparkVersion % "provided"),
    libraryDependencies += ("com.github.scopt" %% "scopt" % "3.3.0"),
    libraryDependencies += ("mysql" % "mysql-connector-java" % "5.1.47"),

    (unmanagedResources in Compile) := (unmanagedResources in Compile)
      .value
      .filterNot(_.getName.endsWith(".properties")),

    assemblyJarName in assembly := s"${name.value}_${version.value}.jar",
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", _*) => MergeStrategy.discard
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x) }
  )