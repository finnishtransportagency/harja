#!/usr/bin/env bash
while true; do
read -p "Muodostetaanko API-dokumentaatio resources-kansioon tallennettujen tietojen pohjalta (K/E)? " ke
case $ke in
[Kk]* )
      kansio_nykyinen=$(basename "$PWD")
      kansio_vaadittu="harja"
      if [ "$kansio_nykyinen" == "$kansio_vaadittu" ]; then
          echo "Muodostetaan uusin Harja API:n dokumentaatio.";
          mkdir -p apidoc
          rm -f resources/api/api.html
          raml2html resources/api/api.raml > resources/api/api.html
          cp -r resources/api/ apidoc
          cd apidoc
          zip -r api.zip schemas examples documentation api.html api.raml
          echo "Uusi Harjan API-dokumentaatio muodostettu kansioon harja/apidoc.";
      else
          echo "Aja tämä skripti harja-kansiosta komennolla sh ./sh/muodosta-apidoc.sh. Nykyinen kansio: $kansio_nykyinen"
      fi
    break;;
[Ee]* )
    exit;;
* )
    echo "Vastaa kyllä (k) tai ei (e).";;
esac
done
