#!/usr/bin/env bash
rm -f ../../resources/api/api.html
raml2html ../../resources/api/api.raml > ../../resources/api/api.html
mkdir -p ../../apidoc
cp -r ../../resources/api/ ../../apidoc
zip -r ../../apidoc/api.zip ../../resources/api/
