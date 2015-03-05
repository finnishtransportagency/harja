(ns harja.ui.protokollat
  "Protokollat, joita UI komponenteille voi tarjota.")

(defprotocol Haku
  "Määrittelee yleisen rajapinnan autocomplete tyyppiselle haulle.
Haku yleisesti palauttaa ottaa merkkijonon ja tekee haun (esim palvelimella) ja 
  kirjoittaa tuloksen atomiin."

  (hae [this teksti]
    "Suorittaa haun annetulla tekstillä ja palauttaa core.async kanavan, josta tulokset voi lukea.
Tulokset on aina vektori. Jos tuloksia ei ole, tulokset on tyhjä vektori."))

