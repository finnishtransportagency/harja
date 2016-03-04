#!/bin/bash

echo "Ajetaan unit-testit"
tulos="$(lein test2junit 2> /dev/null)"

output="$(for i in test2junit/xml/*.xml;
do xpath $i "/testsuite[not(@errors = '0') or not(@failures = '0')]" 2>&1 | grep -v "No nodes found";
done;)"

if [ -z "$output" ]; then
  echo "$tulos" | tail -2; else
    echo "$output"
  echo ""
  echo "$tulos" | tail -2
  exit 1
fi
