# Path to the JDBC driver located in the parent repo's test/ directory
JDBC_JAR = test/sqlite-jdbc-3.53.4.0.jar

all: run

compile_generator:
	@echo ">> Compiling Sorma2 Generator..."
	cd .. && javac -cp "$(JDBC_JAR)" com/zoffcc/applications/sorm/*.java

prepare_gen:
	@echo ">> Preparing schema files..."
	mkdir -p gen
	cp _sorm_Person.java gen/
	cp _sorm_ColumnMatch.java gen/
	cp _sorm_BoolTest.java gen/

generate_orm: prepare_gen compile_generator
	@echo ">> Generating ORM code..."
	# Run from repo root so the generator can find its template files (o1.txt, tbl01.txt, etc.)
	cd .. && java -cp "$(JDBC_JAR):." com.zoffcc.applications.sorm.Generator "unit_test/gen"

compile_orm: generate_orm
	@echo ">> Compiling GENERATED ORM classes only..."
	# Explicitly find only .java files inside the generated package directory
	# This prevents javac from trying to compile the _sorm_ template file
	find gen/com -name "*.java" > gen/sources.txt
	javac -cp ../"$(JDBC_JAR)" @gen/sources.txt

compile_test: compile_orm
	@echo ">> Compiling Unit Test files..."
	# Point classpath to 'gen' so it resolves com.zoffcc.applications.sorm.*
	# Compile the main test runner and all individual Test*.java files
	javac -cp ../"$(JDBC_JAR):gen" SormaUnitTest.java Test*.java

run: compile_test
	@echo ">> Running Unit Tests..."
	# Point runtime classpath to 'gen' for the generated ORM classes
	java -cp ".:../$(JDBC_JAR):gen" SormaUnitTest

clean:
	@echo ">> Cleaning up..."
	rm -rf gen
	# Remove all compiled test class files (SormaUnitTest.class + Test*.class)
	rm -f *.class
	# * DO NOT CLEAN THIS * # rm -f ../com/zoffcc/applications/sorm/*.class
