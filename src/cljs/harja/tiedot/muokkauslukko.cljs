(ns harja.tiedot.muokkauslukko
  "Geneerisen muokkauslukon hallinta"
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.istunto :as istunto]
            [harja.asiakas.tapahtumat :as t]
            [harja.loki :refer [tarkkaile!]]
            
            [cljs.core.async :refer [chan <! >! close!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defn- kayttaja-omistaa-lukon? [lukko]
  (= (:kayttaja lukko) (:id istunto/kayttaja)))

(defn muodosta-lukon-id
  "nakyma Näkymän nimi, joka halutaan lukita. Esim. paallystysilmoitus.
  item-id Vapaaehtoinen lukittavan itemin id (tämä on sama id jolla item yksilöidään tietokannassa)."
  ([nakyma]
   nakyma)
  ([nakyma item-id]
   (str nakyma "_" item-id)))

(defn- hae-lukko-idlla [lukko-id]
  (k/post! :hae-lukko-idlla {:id  lukko-id}))

(defn- lukitse
  "Merkitsee tietyn näkymän lukituksi, tarkoituksena että vain näkymän lukinnut käyttäjä voi muokata sitä.
  Jos onnistuu, palauttaa mapin, jossa lukon id.
  Jos epäonnistuu, palauttaa mapin, jossa lukon id on nill"
  [id]
  (k/post! :lukitse {:id id}))

(defn virkista-lukko [lukko-id]
  (k/post! :virkista-lukko {:id lukko-id}))

(defn vapauta-lukko [lukko-id]
  (k/post! :vapauta-lukko {:id lukko-id}))

(defn tarkista-muokkauslukko
  "Hakee näkymään liitetyn lukon.
  Jos lukko löytyy, tarkistaa kuuluuko se tälle käyttäjälle. Jos kuuluu, palauttaa true. Jos ei, false.
  Jos lukkoa ei löydy, lukitsee näkymän ja palauttaa true."
  [lukko-id]
  (let [lukko (hae-lukko-idlla lukko-id)]
        (if lukko
          (kayttaja-omistaa-lukon? lukko)
          (do (lukitse lukko-id)
              true))))