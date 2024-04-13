#! /bin/bash

javac \
-classpath ".:sqlite-jdbc-3.45.2.0.jar:slf4j-api-1.7.2.jar:sorma2.jar" \
org/example/TestSorma.java

java \
-classpath ".:sqlite-jdbc-3.45.2.0.jar:slf4j-api-1.7.2.jar:sorma2.jar" \
com/zoffcc/applications/sorm/Generator "gen"

#java \
#-classpath ".:sqlite-jdbc-3.45.2.0.jar:slf4j-api-1.7.2.jar:sorma2.jar" \
#org/example/TestSorma

