#!/usr/bin/env bash

set -e
set -x
set -u
export TZ=EET
cd /tmp
cd harja
  git config --global user.email "you@example.com"
  git config --global user.name "Your Name"

git pull origin develop
sed -i -e 's!jdbc:postgresql://localhost/!jdbc:postgresql://harjadb/!' src/clj/harja/kyselyt/specql_db.clj tietokanta/pom.xml
sed -i -e 's!:palvelin "localhost"!:palvelin "harjadb"!' asetukset.edn
sed -i -e 's!"localhost"!"harjadb"!' test/clj/harja/testi.clj
mkdir -p ../.harja
echo aaaa > ../.harja/anti-csrf-token
touch ../.harja/{google-static-maps-key,turi-salasana,ava-salasana,yha-salasana,labyrintti-salasana}
cd tietokanta && mvn flyway:migrate && cd -
lein tuotanto-notest
java -jar target/harja-0.0.1-SNAPSHOT-standalone.jar
