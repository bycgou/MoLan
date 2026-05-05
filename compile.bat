@echo off
cd /d D:\DEV\MoYun\backend
set JAVA_HOME=E:\jdk\jdk-17.0.1
set PATH=%JAVA_HOME%\bin;%PATH%
E:\Maven\apache-maven-3.9.9\bin\mvn.cmd compile -q
pause