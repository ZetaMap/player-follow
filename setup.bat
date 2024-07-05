@echo off
call .\gradlew :build
move /y .\build\libs\*.jar .\
rd /s /q .\build