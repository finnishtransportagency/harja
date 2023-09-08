#!/usr/bin/env bash

set -e
set -x
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
jarfile="$1"
asetukset=$DIR/../asetukset.edn

if grep -q kehitysmoodi "$asetukset" ; then
    echo kaytetaan asetuksia $asetukset
else
    echo huonot asetukset
    exit 1
fi


if unzip -q -v "$jarfile" > /dev/null 2>&1; then

    echo "ajetaan cypress jar-tiedostoa vasten"
    jardir=$DIR/jardir.$$
    mkdir -p $jardir
    chmod a+rwxt $jardir
    trap "rm -vf $jardir/{harja.jar,aja-cypress-kontissa.bash,asetukset.edn,cypress.tar}; rmdir $jardir" EXIT
    cp -v $jarfile $jardir/harja.jar
    tar -C $DIR/.. -cf $jardir/cypress.tar cypress.config.js cypress
    cp -v $DIR/aja-cypress-kontissa.bash $asetukset  $jardir/
    docker run --link harjadb:postgres -v "$jardir:/jar" --rm solita/harja-cypress /jar/aja-cypress-kontissa.bash
    chmod go-rwxt $jardir
    test -d $jardir/screenshots && cp -rv $jardir/screenshots screenshots.$$
else
    echo "huono jar"
    exit 1
fi
