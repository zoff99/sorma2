#! /bin/bash

javac \
-classpath ".:sqlite-jdbc-3.45.2.0.jar:slf4j-api-1.7.2.jar:sorma2.jar" \
org/example/TestSorma.java

java \
-classpath ".:sqlite-jdbc-3.45.2.0.jar:slf4j-api-1.7.2.jar:sorma2.jar" \
com/zoffcc/applications/sorm/Generator "gen"

cd gen/
javac \
-cp "sqlite-jdbc-3.45.2.0.jar:slf4j-api-1.7.2.jar" \
com/zoffcc/applications/sorm/*.java

cd ../
jar cf sorma_generated.jar gen/com/zoffcc/applications/sorm/*.class

# java \
# -classpath ".:sqlite-jdbc-3.45.2.0.jar:slf4j-api-1.7.2.jar:sorma2.jar" \
# org/example/TestSorma

