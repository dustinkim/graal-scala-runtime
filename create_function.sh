#!/bin/sh
set -euo pipefail

[ -f function.zip ] && rm function.zip

sbt "graalvm-native-image:packageBin"

zip -j function.zip \
  target/graalvm-native-image/HelloWorld bootstrap function.sh

aws lambda create-function \
  --function-name aesa-test-custom-runtime \
  --runtime provided \
  --zip-file fileb://function.zip \
  --handler function.handler \
  --role "arn:aws:iam::617208391173:role/lambda_basic_execution"

[ -f function.zip ] && rm function.zip
