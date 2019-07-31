(ns harja.ui.taulukko.protokollat)

(defprotocol Jana
  (piirra-jana [this]
               "Tämän funktion pitäisi luoda html elementti, joka edustaa joko riviä tai saraketta.
                Todennäiköisesti kutsuu myös osa/piirra-osa funktiota")
  (janan-id? [this id])
  (janan-osat [this])
  (janan-id [this])
  (osan-index [this osa]
              "Palauttaa nil, jos osa ei kuulu tähän janaan"))

(defprotocol Osa
  (piirra-osa [this])
  (osan-id? [this id])
  (osan-id [this])
  (osan-tila [this]))

(defprotocol Tila
  (hae-tila [this])
  (aseta-tila! [this]))