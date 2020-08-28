name := "HelloWorld"

version := "1.0"

scalaVersion := "2.12.8"

enablePlugins(GraalVMNativeImagePlugin)
graalVMNativeImageGraalVersion := Some("19.1.1")