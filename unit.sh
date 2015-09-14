#!/bin/bash

echo "Ajetaan unit-testit"
lein test2junit &> /dev/null

output="$(for i in test2junit/xml/*.xml;
do xpath $i "/testsuite[not(@errors = '0') or not(@failures = '0')]" 2>&1 | grep -v "No nodes found";
done;)"

if [ -z "$output" ]; then
  echo "Ei virheitä unit testeissä"; else
  echo ""
  echo "$output"
  echo ""
  echo "** Unit testit epäonnistuivat **"
  exit 1
fi
