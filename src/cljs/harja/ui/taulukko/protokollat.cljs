(ns harja.ui.taulukko.protokollat)

(defprotocol Jana
  (piirra-jana [this]
               "Tämän funktion pitäisi luoda html elementti, joka edustaa joko riviä tai saraketta.
                Todennäiköisesti kutsuu myös osa/piirra-osa funktiota")
  (janan-id? [this id])
  (janan-osat [this])
  (janan-id [this])
  (osan-polku [this osa]
              "Palauttaa nil, jos osa ei kuulu tähän janaan"))

(defprotocol Osa
  (piirra-osa [this])
  (osan-id? [this id])
  (osan-id [this])
  (osan-tila [this])
  (osan-arvo [this] [this polku] "Palauttaa osan arvon polusta. Jos polkua ei ole annettu, niin 'pääarvon'")
  (aseta-osan-arvo [this arvo] [this arvo polku]))

(defprotocol Tila
  (hae-tila [this])
  (aseta-tila! [this]))

(defprotocol Taulukko
  (piirra-taulukko [this])
  (rivin-skeema [this jana])
  (otsikon-index [this otsikko])
  (paivita-taulukko! [this] [this a1] [this a1 a2] [this a1 a2 a3] [this a1 a2 a3 a4] [this a1 a2 a3 a4 a5] [this a1 a2 a3 a4 a5 a6] [this a1 a2 a3 a4 a5 a6 a7])
  (paivita-rivi! [this paivitetty-rivi])
  (paivita-solu! [this paivitetty-osa]))