@echo off
set DIR=%~dp0
if not exist "%DIR%gradle\wrapper\gradle-wrapper.jar" (
  echo gradle-wrapper.jar eksik. Android/Linux ortaminda bir kez .\gradlew calistirin veya standart Gradle wrapper JAR dosyasini ekleyin.
  exit /b 1
)
java -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
