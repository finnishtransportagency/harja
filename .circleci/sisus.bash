#!/usr/bin/env bash

set -e
set -x
set -u
export TZ=EET
cd /tmp
cd harja
# git pull origin develop
function cmd_phantom
{
    lein doo phantom test once
}

function cmd_test
{
    lein clean
    lein test
}

function cmd_back
{
    lein tuotanto-notest
    java -jar target/harja-0.0.1-SNAPSHOT-standalone.jar
}

function cmd_help {
    echo komennot: phantom, test, back, help
}

SUBCMD="$1"
shift
BRANCH="$1"
shift
ARGS="$@"

git fetch origin
pwd
rm -vf asetukset.edn
git checkout -b "t_$BRANCH" "origin/$BRANCH"
git checkout asetukset.edn
sed -i -e 's!:palvelin "localhost"!:palvelin "harjadb"!' asetukset.edn
sed -i -e 's!"localhost"!"harjadb"!' test/clj/harja/testi.clj
sed -i -e 's!jdbc:postgresql://localhost/!jdbc:postgresql://harjadb/!' src/clj/harja/kyselyt/specql_db.clj tietokanta/pom.xml

mkdir -p ../.harja
echo aaaa > ../.harja/anti-csrf-token
touch ../.harja/{mml,google-static-maps-key,turi-salasana,ava-salasana,yha-salasana,labyrintti-salasana}
# todo: kokeile lein trampoline "$@"
cd tietokanta && mvn flyway:migrate && cd ..

eval "cmd_$SUBCMD"
