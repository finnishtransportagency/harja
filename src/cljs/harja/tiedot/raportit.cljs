(ns harja.tiedot.raportit
  "Raportit"
  (:require [reagent.core :refer [atom] :as reagent]
            [cljs.core.async :refer [<! >! chan close!]]
            
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log tarkkaile!]]
            )
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

;; Tähän asetetaan suoritetun raportin elementit, jotka renderöidään
(defonce suoritettu-raportti (atom nil))

(def raportit-nakymassa? (atom false))

(defn hae-raportit []
  (k/get! :hae-raportit))

(defn suorita-raportti-urakka-parametrit [urakka-id nimi parametrit]
  (log "SUORITA URAKAN " urakka-id " RAPORTTI " (pr-str nimi) " PARAMETREILLA: " (pr-str parametrit))
  {:nimi nimi
   :konteksti "urakka"
   :urakka-id urakka-id
   :parametrit parametrit})
  
(defn suorita-raportti-urakka
  "Suorittaa raportin annetun urakan kontekstissa."
  [urakka-id nimi parametrit]
  (log "suorita raportti urakka" urakka-id " nimi " (pr-str nimi) " parametrit" (pr-str parametrit))
  (k/post! :suorita-raportti
           (suorita-raportti-urakka-parametrit urakka-id nimi parametrit)))

(defn suorita-raportti-koko-maa-parametrit [nimi parametrit]
  {:nimi nimi
   :konteksti "koko maa"
   :parametrit parametrit})

(defn suorita-raportti-koko-maa
  "Suorittaa raportin koko maan kontekstissa."
  [nimi parametrit]
  (k/post! :suorita-raportti
           (suorita-raportti-koko-maa-parametrit nimi parametrit)))

(defn suorita-raportti-hallintayksikko-parametrit [hallintayksikko-id nimi parametrit]
  {:nimi nimi
   :konteksti "hallintayksikko"
   :hallintayksikko-id hallintayksikko-id
   :parametrit parametrit})

(defn suorita-raportti-hallintayksikko
  "Suorittaa raportin hallintayksikon kontekstisssa."
  [hallintayksikko-id nimi parametrit]
  (k/post! :suorita-raportti
           (suorita-raportti-hallintayksikko-parametrit hallintayksikko-id nimi parametrit)))

(defn suorita-raportti
  "Suorittaa raportin valmiiksi tehdyllä parametri payloadilla.
ks. esim. suorita-raportti-urakka-parametrit"
  [parametrit]
  (k/post! :suorita-raportti parametrit))