#!/usr/bin/env bash
while true; do
read -p "Onko ../apidoc-kansiossa uusin API-dokumentaatio? (K/E)" ke
case $ke in
[Kk]* )
  githaara_nykyinen=$(git rev-parse --abbrev-ref HEAD)
  githaara_vaadittu="gh-pages"
  if [ "$githaara_nykyinen" == "$githaara_vaadittu" ]; then
      echo "Julkaistaan Harja API:n dokumentaatio ../apidoc-kansiosta viemällä muutokset versiohallintaan.";
      cp ../apidoc/api.html apidoc
      cp ../apidoc/api.raml apidoc
      cp ../apidoc/api.zip apidoc
      cp -r ../apidoc/documentation apidoc
      cp -r ../apidoc/examples apidoc
      cp -r ../apidoc/schemas apidoc
      git commit -m 'Päivitä Harja API dokumentaatio'
      git push
  else
      echo "Aja tämä skripti gh-pages-haarassa. Nykyinen git-haara: $githaara_nykyinen"
  fi
  break;;
[Ee]* )
    echo "Päivitä ../apidoc-kansion sisältö ajamalla develop-haarassa skripti ./sh/muodosta-apidoc.sh"
    exit;;
* )
    echo "Vastaa kyllä (k) tai ei (e).";;
esac
done


