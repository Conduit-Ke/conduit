#!/bin/sh
DEFAULT_JVM_OPTS='-Xmx64m -Xms64m'
APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
APP_HOME="`pwd -P`"
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

exec java $DEFAULT_JVM_OPTS $JAVA_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
