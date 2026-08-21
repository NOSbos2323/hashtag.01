#!/bin/sh

# Attempt to locate gradle or use gradle wrapper
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
DEFAULT_JVM_OPTS=""

if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -f "$CLASSPATH" ]; then
    exec "$JAVACMD" $DEFAULT_JVM_OPTS -jar "$CLASSPATH" "$@"
elif command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
else
    echo "Error: Gradle wrapper jar or gradle command not found." >&2
    exit 1
fi
