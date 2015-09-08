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
if [ -z "$1" ]; then
  output="$(lein test)"; else
  output="$(lein test 2>&1)" 
fi
if [[ $? -ne 0 ]] ; then
  echo "\n** Unit testien tulos **"
  echo $output | tail -c 80
  echo "** **\n"
  exit 1
fi
echo "\n** Unit testien tulos **"
echo $output | tail -c 80
echo "** **\n"

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
