@echo off
rem Run UserService with JDK 21 without changing the machine-wide JAVA_HOME
set "JAVA_HOME=C:\Program Files\Java\latest\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
mvn spring-boot:run %*
