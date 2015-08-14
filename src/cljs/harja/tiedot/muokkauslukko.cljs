(ns harja.tiedot.muokkauslukko
  "Geneerisen muokkauslukon hallinta"
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [harja.loki :refer [tarkkaile!]]
            
            [cljs.core.async :refer [chan <! >! close!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defn muodosta-lukon-id
  "nakyma Näkymän nimi, joka halutaan lukita. Esim. paallystysilmoitus.
  item-id Vapaaehtoinen lukittavan itemin id (tämä on sama id jolla item yksilöidään tietokannassa)."
  ([nakyma]
   nakyma)
  ([nakyma item-id]
   (str nakyma "_" item-id)))

(defn hae-lukko-idlla [id]
  (k/post! :hae-lukko-idlla {:id  id}))

(defn lukitse
  "Merkitsee tietyn näkymän lukituksi, tarkoituksena että vain näkymän lukinnut käyttäjä voi muokata sitä.
  Jos onnistuu, palauttaa mapin, jossa lukon id.
  Jos epäonnistuu, palauttaa mapin, jossa lukon id on nill"
  [id]
   (k/post! :lukitse {:id id}))

(defn virkista-lukko [id]
  (k/post! :virkista-lukko {:id id}))

(defn vapauta-lukko [id]
  (k/post! :vapauta-lukko {:id id}))