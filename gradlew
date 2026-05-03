#!/usr/bin/env sh

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEFAULT_TASK=assembleDebug

if [ "$#" -eq 0 ]; then
  set -- "$DEFAULT_TASK"
fi

if [ -n "$JAVA_HOME" ]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
else
  JAVA_EXE=java
fi

if [ -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
  exec "$JAVA_EXE" -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
fi

if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi

cat >&2 <<EOF

Gradle wrapper jar was not found:
  $APP_HOME/gradle/wrapper/gradle-wrapper.jar

Install Android Studio and Gradle, then run:
  gradle wrapper
  ./gradlew assembleDebug

Or open this folder in Android Studio and choose Build > Build APK(s).
EOF
exit 1
