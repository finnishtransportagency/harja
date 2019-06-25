(ns harja.tiedot.raportit
  "Raportit"
  (:require [reagent.core :refer [atom] :as reagent]
            [cljs.core.async :refer [<! >! chan close!]]

            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log tarkkaile!]]
            [taoensso.timbre :as log])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

;; Tähän asetetaan suoritetun raportin elementit, jotka renderöidään
(defonce suoritettu-raportti (atom nil))
(defonce suorituksessa-olevan-raportin-parametrit (atom nil))

(def raportit-nakymassa? (atom false))

(defn hae-raportit []
  (k/get! :hae-raportit))

(defn urakkaraportin-parametrit [urakka-id nimi parametrit]
  {:nimi nimi
   :konteksti "urakka"
   :urakka-id urakka-id
   :parametrit parametrit})

(defn usean-urakan-raportin-parametrit [urakoiden-nimet nimi parametrit]
  {:nimi nimi
   :konteksti "monta-urakkaa"
   :urakoiden-nimet urakoiden-nimet
   :parametrit parametrit})
  
(defn suorita-raportti-urakka
  "Suorittaa raportin annetun urakan kontekstissa."
  [urakka-id nimi parametrit]
  (log "Suorita raportti "  (pr-str nimi) " urakalle " urakka-id " parametreilla " (pr-str parametrit))
  (k/post! :suorita-raportti
           (urakkaraportin-parametrit urakka-id nimi parametrit)))

(defn koko-maa-raportin-parametrit [nimi parametrit]
  {:nimi nimi
   :konteksti "koko maa"
   :parametrit parametrit})

(defn suorita-raportti-koko-maa
  "Suorittaa raportin koko maan kontekstissa."
  [nimi parametrit]
  (log "Suorita raportti "  (pr-str nimi) " koko maalle parametreilla " (pr-str parametrit))
  (k/post! :suorita-raportti
           (koko-maa-raportin-parametrit nimi parametrit)))

(defn hallintayksikon-raportin-parametrit [hallintayksikko-id nimi parametrit]
  {:nimi nimi
   :konteksti "hallintayksikko"
   :hallintayksikko-id hallintayksikko-id
   :parametrit parametrit})

(defn suorita-raportti-hallintayksikko
  "Suorittaa raportin hallintayksikon kontekstisssa."
  [hallintayksikko-id nimi parametrit]
  (log "Suorita raportti "  (pr-str nimi) " hallintayksikölle " hallintayksikko-id " parametreilla " (pr-str parametrit))
  (k/post! :suorita-raportti
           (hallintayksikon-raportin-parametrit hallintayksikko-id nimi parametrit)))

(defn suorita-raportti
  "Suorittaa raportin valmiiksi tehdyllä parametri payloadilla.
   ks. esim. suorita-raportti-urakka-parametrit"
  [parametrit]
  (k/post! :suorita-raportti parametrit))
