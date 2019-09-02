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
  (osan-tila [this]))

(defprotocol Tila
  (hae-tila [this])
  (aseta-tila! [this]))

(defprotocol Taulukko
  (piirra-taulukko [this])
  (rivin-skeema [this jana])
  (otsikon-index [this otsikko])
  (osan-polku-taulukossa [this osa] "Palauttaa vektorin, jossa ensimmäinen elementti on polku janaan ja toinen polku janasta osaan")
  (paivita-taulukko! [this] [this a1] [this a1 a2] [this a1 a2 a3] [this a1 a2 a3 a4] [this a1 a2 a3 a4 a5] [this a1 a2 a3 a4 a5 a6] [this a1 a2 a3 a4 a5 a6 a7])
  (paivita-rivi! [this paivitetty-rivi] [this paivitetty-rivi a1] [this paivitetty-rivi a1 a2] [this paivitetty-rivi a1 a2 a3] [this paivitetty-rivi a1 a2 a3 a4] [this paivitetty-rivi a1 a2 a3 a4 a5] [this paivitetty-rivi a1 a2 a3 a4 a5 a6] [this paivitetty-rivi a1 a2 a3 a4 a5 a6 a7])
  (paivita-solu! [this paivitetty-osa] [this paivitetty-osa a1] [this paivitetty-osa a1 a2] [this paivitetty-osa a1 a2 a3] [this paivitetty-osa a1 a2 a3 a4] [this paivitetty-osa a1 a2 a3 a4 a5] [this paivitetty-osa a1 a2 a3 a4 a5 a6] [this paivitetty-osa a1 a2 a3 a4 a5 a6 a7]))

(defprotocol TilanSeuranta
  "Tämän avulla lisätään taulukon asiaan derefable renderöinti funktioon, jonka seurauksena asia renderöidään uudestaan.
   Hyödyllinen, jos asian arvo on riippuvainen jostain siitä riippumattomasta arvosta.
   Esim. 'summa'/'yhteensä' osat ovat riippuvaisia muista arvoista."
  (lisaa-renderointi-derefable! [this tila polut] [this tila polut alkutila] "Palauttaa tämän muutettuna siten, että jokainen muutos poluissa aiheuttaa tämän re-renderöinnin.")
  (lisaa-muodosta-arvo [this f] "Muodostaa asian arvon tämän funktion perusteella."))

(defprotocol Fmt
  (lisaa-fmt [this f] "Lisää asiaan formatointi funktion"))