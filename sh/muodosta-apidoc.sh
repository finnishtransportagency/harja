#!/usr/bin/env bash
while true; do
read -p "Muodostetaanko API-dokumentaatio resources-kansioon tallennettujen tietojen pohjalta (K/E)? " ke
case $ke in
[Kk]* )
    echo "Muodostetaan uusin Harja API:n dokumentaatio.";
    mkdir -p ../apidoc
    rm -f ../resources/api/api.html
    raml2html ../resources/api/api.raml > ../apidoc/api.html
    cp -r ../resources/api/ ../apidoc
    zip -r ../apidoc/api.zip ../resources/api/
    echo "Uusi Harjan API-dokumentaatio muodostettu kansioon harja/apidoc.";
    break;;
[Ee]* )
    exit;;
* )
    echo "Vastaa kyllä (k) tai ei (e).";;
esac
done



