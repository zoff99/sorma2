#! /bin/bash

java \
-classpath ".:sqlite-jdbc-3.47.1.0.jar:sorma2.jar" \
com/zoffcc/applications/sorm/Generator "gen"

cd gen/
javac \
-cp "sqlite-jdbc-3.47.1.0.jar" \
com/zoffcc/applications/sorm/*.java && \
jar cf sorma_generated.jar com/zoffcc/applications/sorm/*.class && \
cp sorma_generated.jar ../test/ && \
cd ../

# use generated custom jar
cd test/
javac \
-classpath ".:sqlite-jdbc-3.47.1.0.jar:sorma_generated.jar" \
org/example/TestSorma.java

rm -f main.db

java \
-classpath ".:sqlite-jdbc-3.47.1.0.jar:sorma_generated.jar" \
org/example/TestSorma

