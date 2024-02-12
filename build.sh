#!/bin/bash
./gradlew build
docker build -t transaction-service .
