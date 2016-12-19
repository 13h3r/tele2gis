import sbt._

lazy val bottele = project in file(".")

val metricsV = "3.0.2"

val akkaV = "2.4.14"

scalaVersion := "2.11.8"

val additionalResolvers = Seq(
  "Bounless" at "http://repo.boundlessgeo.com/main/",
  Resolver.jcenterRepo,
  "WebAPI Releases" at "http://repo.ms.ostack.test/repository/releases",
  "Scala Tools Snapshots" at "http://scala-tools.org/repo-snapshots/",
  "Sonatype Releases" at "http://oss.sonatype.org/content/repositories/releases/",
  "OpenGeo repo" at "http://download.osgeo.org/webdav/geotools/"
)

resolvers ++= additionalResolvers

val geoToolsVersion = "15.1"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-http" % "10.0.0",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.0",
  "com.typesafe.akka" %% "akka-slf4j" % akkaV,
  "org.typelevel" %% "cats" % "0.8.1",
  "org.postgresql" % "postgresql" % "9.4.1212",
  "ru.dgis.ipa" %% "lib-rest-sapphire" % "1.1.1",
  "org.geotools" % "gt-referencing" % geoToolsVersion,
  "org.geotools" % "gt-api" % geoToolsVersion,
  "org.geotools" % "gt-epsg-hsql" % geoToolsVersion
)

assemblyMergeStrategy in assembly <<= (assemblyMergeStrategy in assembly) { old => {
  case "application.conf" => MergeStrategy.concat
  case PathList("org", "apache", xs @ _*) => MergeStrategy.last
  case x => old(x)
}}

enablePlugins(DockerPlugin)

dockerfile in docker := {
  val artifact: File = assembly.value
  val base: File = baseDirectory.value

  val artifactTargetPath = s"/app/${artifact.name}"

  new Dockerfile {
    from("frolvlad/alpine-oraclejdk8")
    add(artifact, artifactTargetPath)
    entryPoint("sh", "-c", s"java $$JAVA_D_OPTIONS -cp $artifactTargetPath $$MAIN_CLASS")
  }
}

buildOptions in docker := BuildOptions(
  removeIntermediateContainers = BuildOptions.Remove.Always
)

imageNames in docker := Seq(
  // Sets the latest tag
  ImageName(s"13h3r/bottele:latest")
)

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3")

