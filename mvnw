#!/bin/sh
set -e
BASEDIR=$(cd "$(dirname "$0")"; pwd -P)
WRAPPER_JAR="$BASEDIR/.mvn/wrapper/maven-wrapper.jar"
JAVA_EXE="java"
exec "$JAVA_EXE" "-Dmaven.multiModuleProjectDirectory=$BASEDIR" -classpath "$WRAPPER_JAR" org.apache.maven.wrapper.MavenWrapperMain "$@"
