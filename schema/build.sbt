name := "schema"

libraryDependencies += "io.shiftleft" %% "overflowdb-traversal" % "1.19"
// TODO define in root build.sbt
val cpgVersion = "1.2.17+2-344ab09b"
libraryDependencies += "io.shiftleft" %% "codepropertygraph-schema" % cpgVersion

import better.files.FileExtensions

lazy val mergeSchemaTask = taskKey[File]("Merge schemas")
mergeSchemaTask := {
  val outputRoot = new File(sourceManaged.in(Compile).value.getAbsolutePath)
  val outputFile = outputRoot / "cpg.json"
  val schemasDir = new File("schema/src/main/resources/schema")
  val schemaFiles = schemasDir.listFiles.toSeq :+ extractOriginalSchema.value
  val mergedSchema = overflowdb.codegen.SchemaMerger.mergeCollections(schemaFiles)
  outputFile.mkdirs
  mergedSchema.toScala.copyTo(outputFile.toScala, overwrite = true)
  println(s"successfully merged schemas into $outputFile")
  outputFile
}

Compile / sourceGenerators += Def.task {
  val mergedSchemaFile = mergeSchemaTask.value
  val outputRoot = new File(sourceManaged.in(Compile).value.getAbsolutePath + "/io/shiftleft/codepropertygraph/generated")

  println(s"generating domain classes from $mergedSchemaFile")
  val basePackage = "io.shiftleft.codepropertygraph.generated"
  val outputDir = (Compile / sourceManaged).value
  new overflowdb.codegen.CodeGen(mergedSchemaFile.getAbsolutePath, basePackage).run(outputDir)

  FileUtils.listFilesRecursively(outputRoot)
}.taskValue

lazy val extractOriginalSchema = taskKey[File]("extract original cpg schema from dependency")
extractOriginalSchema := {
  val artifactName = "codepropertygraph-schema_2.13"
  val fullSchemaFilename = "cpg.json"
  val cpgSourceJar = updateClassifiers.value
    .configurations
    .filter(_.configuration.name == "compile")
    .flatMap { config =>
      config.modules.filter(_.module.name == artifactName).flatMap { module =>
        module.artifacts.collect { case (artifact, file) if artifact.`type` == "src" => file }
      }
    }
    .headOption
    .getOrElse(throw new AssertionError(s"unable to find $artifactName from dependencies"))

  val tmpDir = IO.temporaryDirectory
  IO.unzip(cpgSourceJar, tmpDir, _ == fullSchemaFilename)
  tmpDir.listFiles(new java.io.FilenameFilter {
    override def accept(dir: File, name: String) = name == fullSchemaFilename
  }).headOption.getOrElse(throw new AssertionError(s"unable to find original $fullSchemaFilename"))
}

