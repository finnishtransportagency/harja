#!/usr/bin/env bash
while true; do
read -p "Onko harja/apidoc-kansiossa uusin API-dokumentaatio? (K/E)" ke
case $ke in
[Kk]* )
    echo "Julkaistaan Harja API:n dokumentaatio paikallisesta apidoc-kansiosta.";
    git checkout gh-pages
    git add ../apidoc
    git commit -m 'Päivitä Harja API dokumentaatio'
    dgit push
    break;;
[Ee]* )
    echo "Päivitä harja/apidoc-kansion sisältö skriptillä ./muodosta-apidoc.sh"
    exit;;
* )
    echo "Vastaa kyllä (k) tai ei (e).";;
esac
done


