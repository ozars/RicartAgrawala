all: build run

build:
	mkdir -p bin/
	javac -d bin/ -cp aosproject src/aosproject/*.java
	
run:
	java -cp bin aosproject.Main
