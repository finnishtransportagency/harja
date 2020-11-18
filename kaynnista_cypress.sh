#!/bin/bash
set -x

npm install

sed -i .bak 's/;:salli-oletuskayttaja. false/:salli-oletuskayttaja? true/g' asetukset.edn

$(npm bin)/cypress open

sed -i .bak 's/:salli-oletuskayttaja. true/;:salli-oletuskayttaja? false/g' asetukset.edn
