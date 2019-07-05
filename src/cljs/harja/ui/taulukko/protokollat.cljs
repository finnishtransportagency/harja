(ns harja.ui.taulukko.protokollat)

(defprotocol Jana
  (piirra-jana [this]
               "Tämän funktion pitäisi luoda html elementti, joka edustaa joko riviä tai saraketta.
                Todennäiköisesti kutsuu myös osa/piirra-osa funktiota")
  (janan-id? [this id])
  (janan-osat [this]))

(defprotocol Osa
  (piirra-osa [this])
  (osan-id? [this id])
  (osan-janan-id [this]))