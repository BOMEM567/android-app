@echo off
setlocal

set "APP_HOME=%~dp0"
set "DEFAULT_TASK=assembleDebug"

if "%1"=="" (
  set "GRADLE_ARGS=%DEFAULT_TASK%"
) else (
  set "GRADLE_ARGS=%*"
)

if defined JAVA_HOME (
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_EXE=java.exe"
)

if exist "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" (
  "%JAVA_EXE%" -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %GRADLE_ARGS%
  exit /b %ERRORLEVEL%
)

where gradle >nul 2>nul
if %ERRORLEVEL%==0 (
  gradle %GRADLE_ARGS%
  exit /b %ERRORLEVEL%
)

echo.
echo Gradle wrapper jar was not found:
echo   %APP_HOME%gradle\wrapper\gradle-wrapper.jar
echo.
echo Install Android Studio and Gradle, then run:
echo   gradle wrapper
echo   gradlew.bat assembleDebug
echo.
echo Or open this folder in Android Studio and choose Build ^> Build APK(s).
exit /b 1
