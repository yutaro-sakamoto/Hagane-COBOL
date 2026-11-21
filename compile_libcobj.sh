#!/bin/sh

BASE_DIR=$(pwd)

javac -encoding UTF-8 \
    -classpath $1 \
    ${BASE_DIR}/libcobj/src/org/haganecobol/libcobj/*.java \
    ${BASE_DIR}/libcobj/src/org/haganecobol/libcobj/data/*.java \
    ${BASE_DIR}/libcobj/src/org/haganecobol/libcobj/common/*.java \
    ${BASE_DIR}/libcobj/src/org/haganecobol/libcobj/exceptions/*.java \
    ${BASE_DIR}/libcobj/src/org/haganecobol/libcobj/termio/*.java \
    ${BASE_DIR}/libcobj/src/org/haganecobol/libcobj/call/*.java \
    ${BASE_DIR}/libcobj/src/org/haganecobol/libcobj/file/*.java \
    ${BASE_DIR}/libcobj/src/*.java \
    -d ${BASE_DIR}/libcobj/bin

echo ${BASE_DIR}
