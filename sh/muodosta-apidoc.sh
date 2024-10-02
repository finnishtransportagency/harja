#!/usr/bin/env bash

echo "Muodostetaan uusin Harja API:n dokumentaatio.";

rm -f ../resources/api/api.html
mkdir -p ../apidoc
raml2html ../resources/api/api.raml > ../apidoc/api.html
cp -r ../resources/api/ ../apidoc
zip -r ../apidoc/api.zip ../resources/api/

echo "Uusi Harjan API-dokumentaatio muodostettu.";
