(ns harja.tiedot.urakka.suunnittelu
  "Tämä nimiavaruus hallinnoi urakan suunnittelun tietoja"
  (:require [reagent.core :refer [atom] :as r]
            
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]))

(def valittu-sopimusnumero "Sopimusnumero" (atom nil))

(defn valitse-sopimusnumero! [sn]
  (reset! valittu-sopimusnumero sn))

(def valittu-hoitokausi "Hoitokausi" (atom nil))

(defn valitse-hoitokausi! [hk]
  (reset! valittu-hoitokausi hk))

(defn hoitokaudet [ur]
  (let [ensimmainen-vuosi (.getYear (:alkupvm ur))
        viimeinen-vuosi (.getYear (:loppupvm ur))]
    (mapv (fn [vuosi]
            {:alkupvm (pvm/hoitokauden-alkupvm vuosi)
             :loppupvm (pvm/hoitokauden-loppupvm (inc vuosi))})
          (range ensimmainen-vuosi (inc viimeinen-vuosi)))))

(defn hoitokausien-sisalto-sama? 
  "Yleiskäyttöinen funktio, jolla vertaillaan onko eri hoitokausien sisältö sama.
  Suunniteltu käytettäväksi mm. yks.hint. ja kok.hint. töiden sekä materiaalien suunnittelussa."
  [hoitokausien-sisalto]
  (log "hoitokausien-sisalto-sama?" hoitokausien-sisalto)
  (log ("map?" (map #(dissoc (:alkupvm %) (:loppupvm %)) hoitokausien-sisalto)))
  (apply = (map #(dissoc (:alkupvm %) (:loppupvm %)) hoitokausien-sisalto))
  )