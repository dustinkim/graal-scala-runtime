#!/bin/sh
set -euo pipefail

[ -f function.zip ] && rm function.zip

sbt "graalvm-native-image:packageBin"

zip -j function.zip \
  target/graalvm-native-image/HelloWorld bootstrap function.sh

aws lambda update-function-code \
  --function-name aesa-test-custom-runtime  \
  --zip-file fileb://./function.zip

[ -f function.zip ] && rm function.zip
