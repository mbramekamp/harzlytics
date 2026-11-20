#!/bin/sh
# Gradle wrapper startup script for Unix/macOS
APP_HOME=$( cd "${0%/*}" && pwd -P ) || exit
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
exec java -Djava.net.preferIPv4Stack=true -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
