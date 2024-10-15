#!/usr/bin/env bash
while true; do
read -p "Onko harja/apidoc-kansiossa uusin API-dokumentaatio? (K/E)" ke
case $ke in
[Kk]* )
  githaara_nykyinen=$(git rev-parse --abbrev-ref HEAD)
  githaara_vaadittu="gh-pages"
  if [ "$githaara_nykyinen" == "$githaara_vaadittu" ]; then
      echo "Julkaistaan Harja API:n dokumentaatio apidoc-kansiosta viemällä muutokset versiohallintaan.";
      sgit commit -m 'Päivitä Harja API dokumentaatio'
      sgit push
  else
      echo "Aja tämä skripti gh-pages-haarassa. Nykyinen git-haara: $githaara_nykyinen"
  fi
  break;;
[Ee]* )
    echo "Päivitä harja/apidoc-kansion sisältö skriptillä ./muodosta-apidoc.sh"
    exit;;
* )
    echo "Vastaa kyllä (k) tai ei (e).";;
esac
done


