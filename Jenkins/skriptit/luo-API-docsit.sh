#!/usr/bin/env bash
mkdir -p resources/public/apidoc
cp -r resources/api/documentation/ resources/public/apidoc/
raml2html resources/api/api.raml > resources/public/apidoc/api.html
rm -f resources/api/api.html
cp resources/public/apidoc/api.html resources/api/
zip -r resources/public/apidoc/api.zip resources/api/ 
cp resources/api/api.raml resources/public/apidoc
