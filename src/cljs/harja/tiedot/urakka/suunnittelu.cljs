(ns harja.tiedot.urakka.suunnittelu
  "Tämä nimiavaruus hallinnoi urakan suunnittelun tietoja"
  (:require [reagent.core :refer [atom] :as r]
            
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [harja.loki :refer [log tarkkaile!]]
            [harja.pvm :as pvm]))

(def valittu-sopimusnumero "Sopimusnumero" (atom nil))

(tarkkaile! "valittu-sopimusnumero " valittu-sopimusnumero)
(defn valitse-sopimusnumero! [sn]
  (log "valitse-sopimusnumero!" sn)
  (reset! valittu-sopimusnumero sn))

(def +hoitokauden-alkukk-indeksi+ "9")
(def +hoitokauden-alkupv-indeksi+ "1")
(def +hoitokauden-loppukk-indeksi+ "8")
(def +hoitokauden-loppupv-indeksi+ "30")

(def valittu-hoitokausi "Hoitokausi" (atom nil))

(defn valitse-hoitokausi! [hk]
  (log "valitse-hoitokausi!" hk)
  (reset! valittu-hoitokausi hk))

(defn hoitokaudet [ur]
  (let [ensimmainen-vuosi (.getYear (:alkupvm ur))
        viimeinen-vuosi (.getYear (:loppupvm ur))]
    (mapv (fn [vuosi]
            {:alkupvm (pvm/luo-pvm vuosi +hoitokauden-alkukk-indeksi+ +hoitokauden-alkupv-indeksi+)
             :loppupvm (pvm/luo-pvm (inc vuosi) +hoitokauden-loppukk-indeksi+ +hoitokauden-loppupv-indeksi+)})
          (range ensimmainen-vuosi (inc viimeinen-vuosi)))))