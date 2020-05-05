#!/usr/bin/env bash

set -e
set -x
set -u
cd /tmp/cypress-run
cp /jar/asetukset.edn .
tar xf /jar/cypress.tar
sed -i -e 's/;:salli-oletuskayttaja. false/:salli-oletuskayttaja? true/g' asetukset.edn
sed -i -e 's/:kehitysmoodi true/:kehitysmoodi false/g' asetukset.edn
sed -i -e "s/:palvelin \"localhost\"/:palvelin \"postgres\"/g" asetukset.edn
mkdir -p ../.harja
echo aaaa > ../.harja/anti-csrf-token
touch ../.harja/{mml,google-static-maps-key,turi-salasana,ava-salasana,yha-salasana,labyrintti-salasana}
java -jar /jar/harja.jar > harja.out 2>&1 &
javapid=$!
for i in $(seq 4); do
    curl localhost:3000 > /dev/null 2>&1 && break
    if kill -0 $javapid; then
        echo "appis ei käynnissä, odotellaan..."
        sleep 10
    else
        echo "appis kuupahtanut"
        cat harja.out
        exit 1
    fi
done
echo "pätkä appiksen logia:"
echo +++++++++++++++++++
tail -150 harja.out
echo +++++++++++++++++++
echo
mkdir -p cypress/screenshots
set +e
$(npm bin)/cypress run --browser electron # --spec cypress/integration/nakymien_avaus_spec.js
cypress_status=$?
cp -vr cypress/screenshots /jar/
exit $cypress_status
