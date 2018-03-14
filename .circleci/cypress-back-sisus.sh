#!/usr/bin/env bash

set -e
set -x
set -u

cd /tmp
git clone harja harja-build
cd harja-build
(cd /tmp/harja && git remote -v) | while read n u crap; do git remote rm $n >& /dev/null|| true; git remote add $n $u; done
sed -i -e 's!jdbc:postgresql://localhost/!jdbc:postgresql://harjadb/!' src/clj/harja/kyselyt/specql_db.clj
sed -i -e 's!:palvelin "localhost"!:palvelin "harjadb"!' asetukset.edn
lein tuotanto
java -jar target/harja-jotain.jar
