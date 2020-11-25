# Käyttö

Tämä toimii CirceCI:lle pääkonttina, jossa kaikki `config.yml` tiedostossa määritetyt `step`:it ajetaan.
Kontti sisältää Harja sovelluksen ja sen ajamiseen sekä testaamiseen tarvittavat softat.

# Skriptit

#### sisus.sh
Tälle annetaan argumentiksi git branch ja komento, joka halutaan ajaa kussakin stepissä.
Nämä komennot liittyy jar:in buildaamiseen ja Harjan testaamiseen.