#!/bin/bash

echo "Ajetaan unit-testit"
tulos="$(lein test2junit 2> /dev/null)"

output=`find test2junit -name "*.xml" | xargs xmllint --xpath "string(/testsuite[not(@errors = '0') or not(@failures = '0')]/@name)"`

if [ -z "$output" ]; then
  echo "$tulos" | tail -2; else
    echo "$output"
  echo ""
  echo "$tulos" | tail -2
fi

if [ -n "$output" ];
then
  terminal-notifier -title "Harjan yksikkötesteissä virheitä!" -message "$output" -open "file:///`pwd`/test2junit/html/index.html"
  exit 1
else
  terminal-notifier -title "Harjan yksikkötestit ajettu onnistuneesti" -message "Kaikki testit ajettu onnistuneesti." -open "file:///`pwd`/test2junit/html/index.html"
fi