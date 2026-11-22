#!/bin/sh
set -eu
# Run with: sh test.sh. Requires a Java 21 JDK, not only a JRE.
BUILD=$(mktemp -d)
trap 'rm -rf "$BUILD"' EXIT HUP INT TERM
javac -Xlint:all -Werror -d "$BUILD" app/App.java app/AppTest.java
java -cp "$BUILD" AppTest
