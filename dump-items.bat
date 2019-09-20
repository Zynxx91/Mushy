@echo off
@title Dump
set CLASSPATH=.;bin\*
java -Dwzpath=wz tools.wztosql.DumpItems
pause