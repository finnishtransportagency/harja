#!/usr/bin/env bash

echo "Julkaistaan Harja API:n dokumentaatio paikallisesta apidoc-kansiosta. Muodosta sisältö skriptillä muodosta-apidoc.sh";

git checkout gh-pages
git add ../apidoc
git commit -m 'Päivitä Harja API dokumentaatio'
dgit push

echo "Harjan API-dokumentaatio julkaistu paikallisesta apidoc-kansiosta.";
