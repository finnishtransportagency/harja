#!/usr/bin/env bash

set -x

## Valmistellaan ylimääräiset dependencyt, joita cypress testit tarvitsevat ##
## NOTE: Jos ylimääräisiä depsuja on joskus paljon, voisi komennot korvata vaikka yhdellä "npm install" komennolla
## NOTE: GH Actions cachetusta ei voi tässä hyödyntää, koska depsut asennetaan irtaallaan olevan kontin sisällä.

# Asenna transit-js riippuvuus cypress docker-imagen workdirectoryyn Harja-projektin package.jsonista
# Tässä oletetaan, että workdir on asetettu ja että workdiristä löytyy Harjan checkoutatut koodit

echo "Asennetaan transit-js..."
NPM_TRANSITJS_VERSION=$(node -pe 'require("./package").dependencies["transit-js"]')
npm install "transit-js@${NPM_TRANSITJS_VERSION}"

if [[ -z "$NPM_TRANSITJS_VERSION" ]]; then
    echo "Transit-js NPM-paketin versiota ei löytynyt. Lopetetaan ajo..."
    exit 1
fi


echo "Ajetaan CMD"
# Ajetaan ulkopuolelta annettu CMD (esim. cypress run --browser chrome)
exec "$@"
