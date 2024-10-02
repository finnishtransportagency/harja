#!/usr/bin/env bash

while true; do
read -p "Muodostetaanko API-dokumentaatio resources-kansioon tallennettujen tietojen pohjalta (raml ja skeemat)? " ke
case $ke in
[Kk]* )
    echo "Muodostetaan uusin Harja API:n dokumentaatio.";
    rm -f ../resources/api/api.html
    mkdir -p ../apidoc
    raml2html ../resources/api/api.raml > ../apidoc/api.html
    cp -r ../resources/api/ ../apidoc
    zip -r ../apidoc/api.zip ../resources/api/
    echo "Uusi Harjan API-dokumentaatio muodostettu.";
    break;;
[Ee]* )
    exit;;
* )
    echo "Vastaa kyllä (k) tai ei (e).";;
esac
done



