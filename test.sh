#!/bin/sh

set -e

echo "\nLuodaan uudestaan harja ja harjatesti tietokannat\n"

cd vagrant
sh migrate_and_clean.sh
echo "Luotiin harja"
sh migrate_test.sh
echo "Luotiin harjatesti\n"
cd ..

echo "Tehdään clean"
lein clean > /dev/null

set +e

echo "Ajetaan unit testit"
lein test2junit > /dev/null

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

set -e

lein figwheel dev &
echo "\n\n*****************"
echo "Käynnistetään figwheel ja REPL"
echo "Odota rauhassa"
echo "Poistu kirjoittamalla (exit) tai painamalla ctrl-d"
echo "*****************\n"
lein repl

echo "Kiitos testaamisesta!"
kill `lsof -n -i4TCP:3449 | awk 'NR>1{printf "%s", $2}'`
