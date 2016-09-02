#!/bin/sh

echo "Julkaistaan uusin Harja API:n dokumentaatio GH.pagesiin (http://finnishtransportagency.github.io/harja/)";

cd ../harja-sivut/apidoc;
git pull;
rm -rf *;
curl -O https://harja-test.solitaservices.fi/apidoc/api.zip;
unzip api.zip;
cp -r resources/api/ ./;
rm -rf api.html;
cp resources/public/apidoc/api.html ./api.html;
rm -rf resources/;
git add -A
git commit -m 'Päivitä Harja API dokumentaatio';
git push;

echo "Uusi Harjan API-dokumentaatio julkaistu GH Pagesiin (http://finnishtransportagency.github.io/harja/apidoc/api.html)";