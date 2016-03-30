#!/bin/bash

echo "Ajetaan unit-testit"
# tulos="$(lein test2junit 2> /dev/null)"

terminaloutput="$(for i in test2junit/xml/*.xml;
do xpath $i "/testsuite[not(@errors = '0') or not(@failures = '0')]" 2>&1 | grep -v "No nodes found";
done;)"

if [ -z "$terminaloutput" ]; then
  echo "$tulos" | tail -2; else
    echo "$terminaloutput"
  echo ""
  echo "$tulos" | tail -2
fi

notificationoutput=`find test2junit -name "*.xml" | xargs xmllint --xpath "string(/testsuite[not(@errors = '0') or not(@failures = '0')]/@name)"`
if [ -n "$notificationoutput" ];
then
  terminal-notifier -title "Harjan yksikkötesteissä virheitä!" -message "$notificationoutput" -open "file:///`pwd`/test2junit/html/index.html"
  exit 1
else
  terminal-notifier -title "Harjan yksikkötestit ajettu onnistuneesti" -message "Kaikki testit ajettu onnistuneesti." -open "file:///`pwd`/test2junit/html/index.html"
fi