(ns harja.ui.taulukko.protokollat)


(defn aseta-asian-arvo [asia avain-arvo muuta-avain]
  (let [asian-avain-arvo (map (fn [[avain arvo]]
                                [(muuta-avain avain) arvo])
                              (partition 2 avain-arvo))]
    (reduce (fn [asia [polku arvo]]
              (assoc-in asia polku arvo))
            asia asian-avain-arvo)))

(defprotocol Asia
  "Taulukon asioille haku ja päivitys funktioita"
  (arvo [this avain])
  (aseta-arvo [this k1 a1]
              [this k1 a1 k2 a2]
              [this k1 a1 k2 a2 k3 a3]
              [this k1 a1 k2 a2 k3 a3 k4 a4]
              [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5]
              [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6]
              [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7]
              [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8]
              [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8 k9 a9])
  (paivita-arvo [this avain f]
                [this avain f a1]
                [this avain f a1 a2]
                [this avain f a1 a2 a3]
                [this avain f a1 a2 a3 a4]
                [this avain f a1 a2 a3 a4 a5]
                [this avain f a1 a2 a3 a4 a5 a6]
                [this avain f a1 a2 a3 a4 a5 a6 a7]
                [this avain f a1 a2 a3 a4 a5 a6 a7 a8]
                [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9]
                [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9 a10]))

(defprotocol CollectionKasittely
  (lisaa-kokoelman-kasittely [this f] "Kutsuu annettua funktiota arvolle, jos coll? palauttaa true arvolle")
  ;; Nämä eroaa Asia protokollasta siten, että Asia:n funktioiden tulisi palauttaa relevantti arvo kokoelmasta
  ;; käyttäen kokoelman käsittely funktiota. Nämä alla olevat taasen koskee koko kokoelmaa.
  (kokoelma [this] "Palauttaa kokoelman")
  (aseta-kokoelma [this coll] "Asettaa uuden kokoelman")
  (paivita-kokoelma [this f] "Päivittaa kokoelmaa funktiolla"))

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
  (aseta-tila! [this tila])
  (paivita-tila! [this tila])
  (luo-tila! [this]))

(defprotocol Taulukko
  (piirra-taulukko [this])
  (taulukon-id [this])
  (taulukon-id? [this id])
  (rivin-skeema [this jana])
  (otsikon-index [this otsikko])
  (osan-polku-taulukossa [this osa] "Palauttaa vektorin, jossa ensimmäinen elementti on polku janaan ja toinen polku janasta osaan")
  (paivita-taulukko! [this] [this a1] [this a1 a2] [this a1 a2 a3] [this a1 a2 a3 a4] [this a1 a2 a3 a4 a5] [this a1 a2 a3 a4 a5 a6] [this a1 a2 a3 a4 a5 a6 a7]
                     "Tulisi palauttaa taulukko")
  (paivita-rivi! [this paivitetty-rivi] [this paivitetty-rivi a1] [this paivitetty-rivi a1 a2] [this paivitetty-rivi a1 a2 a3] [this paivitetty-rivi a1 a2 a3 a4] [this paivitetty-rivi a1 a2 a3 a4 a5] [this paivitetty-rivi a1 a2 a3 a4 a5 a6] [this paivitetty-rivi a1 a2 a3 a4 a5 a6 a7]
                 "Tulisi palauttaa taulukko")
  (paivita-solu! [this paivitetty-osa] [this paivitetty-osa a1] [this paivitetty-osa a1 a2] [this paivitetty-osa a1 a2 a3] [this paivitetty-osa a1 a2 a3 a4] [this paivitetty-osa a1 a2 a3 a4 a5] [this paivitetty-osa a1 a2 a3 a4 a5 a6] [this paivitetty-osa a1 a2 a3 a4 a5 a6 a7]
                 "Tulisi palauttaa taulukko")
  (lisaa-rivi! [this rivin-avain]
               [this rivin-avain a1]
               [this rivin-avain a1 a2]
               [this rivin-avain a1 a2 a3]
               [this rivin-avain a1 a2 a3 a4]
               [this rivin-avain a1 a2 a3 a4 a5] [this rivin-avain a1 a2 a3 a4 a5 a6] [this rivin-avain a1 a2 a3 a4 a5 a6 a7]))

(defprotocol TilanSeuranta
  "Tämän avulla lisätään taulukon asiaan derefable renderöinti funktioon, jonka seurauksena asia renderöidään uudestaan.
   Hyödyllinen, jos asian arvo on riippuvainen jostain siitä riippumattomasta arvosta.
   Esim. 'summa'/'yhteensä' osat ovat riippuvaisia muista arvoista."
  (lisaa-renderointi-derefable! [this tila polut] [this tila polut alkutila] "Palauttaa tämän muutettuna siten, että jokainen muutos poluissa aiheuttaa tämän re-renderöinnin.")
  (lisaa-muodosta-arvo [this f] "Muodostaa asian arvon tämän funktion perusteella."))

(defprotocol Fmt
  (lisaa-fmt [this f] "Lisää asiaan formatointi funktion")
  (lisaa-fmt-aktiiviselle [this f] "Jos osa on aktiivinen, minkälainen formatointi?"))

