# Käyttö

Tämä toimii CirceCI:lle pääkonttina, jossa kaikki `config.yml` tiedostossa määritetyt `step`:it ajetaan.
Kontti sisältää Harja sovelluksen ja sen ajamiseen sekä testaamiseen tarvittavat softat.

Tässä hakemistossa voi buildata `solita/harja-testit:latest` imagen: 

    docker image build -t solita/harja-testit:latest .

Imagen täytyy pushata dockerhub:iin joka kerta kun sinne tehdään muutoksia:

    docker push solita/harja-testit:latest

# Skriptit

#### sisus.sh
Tälle annetaan argumentiksi git branch ja komento, joka halutaan ajaa kussakin stepissä.
Nämä komennot liittyy jar:in buildaamiseen ja Harjan testaamiseen.