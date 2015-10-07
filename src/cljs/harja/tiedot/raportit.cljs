(ns harja.tiedot.raportit
  "Raportit"
  (:require [reagent.core :refer [atom] :as reagent]
            [cljs.core.async :refer [<! >! chan close!]]
            
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            )
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defn hae-raportit []
  (k/get! :hae-raportit))

(defn suorita-raportti-urakka-parametrit [urakka-id nimi parametrit]
  (log "SUORITA RAPORTTI PARAMETREILLA: " (pr-str parametrit))
  {:nimi nimi
   :konteksti "urakka"
   :urakka-id urakka-id
   :parametrit parametrit})
  
(defn suorita-raportti-urakka
  "Suorittaa raportin annetun urakan kontekstissa."
  [urakka-id nimi parametrit]
  (k/post! :suorita-raportti
           (suorita-raportti-urakka-parametrit urakka-id nimi parametrit)))

(defn suorita-raportti-koko-maa
  "Suorittaa raportin koko maan kontekstissa."
  [nimi parametrit]
  (k/post! :suorita-raportti
           {:nimi nimi
            :konteksti "koko maa"
            :parametrit parametrit}))

(defn suorita-raportti-hallintayksikko
  "Suorittaa raportin hallintayksikon kontekstisssa."
  [hallintayksikko-id nimi parametrit]
  (k/post! :suorita-raportti
           {:nimi nimi
            :konteksti "hallintayksikko"
            :hallintayksikko-id hallintayksikko-id
            :parametrit parametrit}))

(defn suorita-raportti
  "Suorittaa raportin valmiiksi tehdyllä parametri payloadilla.
ks. esim. suorita-raportti-urakka-parametrit"
  [parametrit]
  (k/post! :suorita-raportti parametrit))

(defn hae-yksikkohintaisten-toiden-kuukausiraportti [urakka-id alkupvm loppupvm]
  (k/post! :yksikkohintaisten-toiden-kuukausiraportti
           {:urakka-id urakka-id
            :alkupvm alkupvm
            :loppupvm loppupvm}))

(defn hae-materiaaliraportti-koko-maalle [alkupvm loppupvm]
  (k/post! :materiaaliraportti-koko-maalle
           {:alkupvm alkupvm
            :loppupvm loppupvm}))

(defn hae-materiaaliraportti-hallintayksikolle [hallintayksikko-id alkupvm loppupvm]
  (k/post! :materiaaliraportti-hallintayksikolle
           {:hallintayksikko-id hallintayksikko-id
            :alkupvm alkupvm
            :loppupvm loppupvm}))

(defn hae-materiaaliraportti-urakalle [urakka-id alkupvm loppupvm]
  (k/post! :materiaaliraportti-urakalle
           {:urakka-id urakka-id
            :alkupvm alkupvm
            :loppupvm loppupvm}))
