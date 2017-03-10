import sbt._

name := "aggregator-service"

organization := "cakesolutions"

version := "1.0.0-SNAPSHOT"

scalaVersion := "2.11.8"

lazy val http4sVersion = "0.14.2a"
lazy val circeVersion = "0.4.1"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "com.typesafe" % "config" % "1.3.0" % "compile",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0" % "compile",
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-core" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-blaze-client" % http4sVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "io.circe" %% "circe-jawn" % circeVersion,
  "io.circe" %% "circe-java8" % circeVersion,
  "org.mockito" % "mockito-core" % "1.10.19" % "test"
)


//--------------------------------
//---- sbt-assembly settings -----
//--------------------------------
mainClass in assembly := Some("com.markglh.blog.Bootstrap")

// Resolve duplicates for Sbt Assembly
assemblyMergeStrategy in assembly := {
  case PathList(xs @ _*) if xs.last == "io.netty.versions.properties" => MergeStrategy.rename
  case other => (assemblyMergeStrategy in assembly).value(other)
}


// publish to artifacts directory
publishArtifact in(Compile, packageDoc) := false

publishTo := Some(Resolver.file("file", new File("artifacts")))

cleanFiles <+= baseDirectory { base => base / "artifacts" }
// ------


scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation")



//----------------
//---- DOCKER ----
//----------------
enablePlugins(sbtdocker.DockerPlugin)

dockerfile in docker := {
  val baseDir = baseDirectory.value
  val artifact: File = assembly.value

  val imageAppBaseDir = "/app"
  val artifactTargetPath = s"$imageAppBaseDir/${artifact.name}"
  val artifactTargetPath_ln = s"$imageAppBaseDir/${name.value}.jar"


  //Define the resources which includes the entrypoint script
  val dockerResourcesDir = baseDir / "docker-resources"
  val dockerResourcesTargetPath = s"$imageAppBaseDir/"

  val appConfTarget = s"$imageAppBaseDir/conf/application" //boot-configuration.conf goes here
  val logConfTarget = s"$imageAppBaseDir/conf/logging" //logback.xml

  new Dockerfile {
    from("openjdk:8-jre")
    maintainer("markglh")
    expose(80, 8080)
    env("APP_BASE", s"$imageAppBaseDir")
    env("APP_CONF", s"$appConfTarget")
    env("LOG_CONF", s"$logConfTarget")
    copy(artifact, artifactTargetPath)
    copy(dockerResourcesDir, dockerResourcesTargetPath)
    copy(baseDir / "src" / "main" / "resources" / "logback.xml", logConfTarget) //Copy the default logback.xml
    //Symlink the service jar to a non version specific name
    run("ln", "-sf", s"$artifactTargetPath", s"$artifactTargetPath_ln")
    entryPoint(s"${dockerResourcesTargetPath}docker-entrypoint.sh")
  }
}
buildOptions in docker := BuildOptions(cache = false)

imageNames in docker := Seq(
  ImageName(
    //namespace = Some(organization.value),
    repository = name.value,
    // We parse the IMAGE_TAG env var which allows us to override the tag at build time
    tag = Some(sys.props.getOrElse("IMAGE_TAG", default = version.value))
  )
)

//---- end docker ----

