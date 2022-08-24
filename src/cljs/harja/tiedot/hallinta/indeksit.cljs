(ns harja.tiedot.hallinta.indeksit
  "Indeksien tiedot"
  (:require [reagent.core :refer [atom] :as reagent]
            [cljs.core.async :refer [<! >! chan close!]]
            [harja.tiedot.urakka :as urakka]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.viesti :as viesti])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]
                   [harja.domain.urakka :as urakka]
                   [harja.domain.indeksit :as indeksit]))


(def indeksit (atom nil))

(def indeksin-tallennus-kaynnissa
  "Sisältää niiden indeksien nimet, joita ollaan tallentamassa"
  (atom #{}))

 (defn hae-indeksit []
   (when (nil? @indeksit)
     (go (reset! indeksit (<! (k/get! :indeksit))))))

(defn tallenna-indeksi
  "Tallentaa indeksiarvot, palauttaa kanavan, josta vastauksen voi lukea."
  [nimi uudet-indeksivuodet]
  (swap! indeksin-tallennus-kaynnissa conj nimi)
  (go (let [tallennettavat
            (into []
                  (comp (filter #(not (:poistettu %))))
                  uudet-indeksivuodet)
            res (<! (k/post! :tallenna-indeksi
                             {:nimi nimi
                              :indeksit tallennettavat}))]
        (when (nil? res)
          (viesti/nayta-toast! "Indeksien tallennus epäonnistui" :varoitus viesti/viestin-nayttoaika-aareton))
        (reset! indeksit res)
        (swap! indeksin-tallennus-kaynnissa disj nimi)
        true)))

(defonce kaikkien-urakkatyyppien-indeksit
  (let [a (atom nil)]
    (go (reset! a (<! (k/get! :urakkatyypin-indeksit))))
    a))

(defn urakkatyypin-indeksit
  [urakkatyyppi]
  (filter #(= urakkatyyppi (:urakkatyyppi %))
          @kaikkien-urakkatyyppien-indeksit))

(defn hae-paallystysurakan-indeksitiedot
  [urakka-id]
  (when @kaikkien-urakkatyyppien-indeksit
    (k/post! :paallystysurakan-indeksitiedot {::urakka/id urakka-id})))

(defn tallenna-paallystysurakan-indeksit
  [{:keys [urakka-id tiedot]}]
  (log "tallenna päällystysurakan indeksit urakkaan " urakka-id " tiedot" (pr-str tiedot))
  (go (let [res (<! (k/post! :tallenna-paallystysurakan-indeksitiedot
                             (into []
                                   (comp (filter #(not (and (neg? (:id %))
                                                            (:poistettu %))))
                                         (map #(assoc % :urakka urakka-id)))
                                   tiedot)))]
        (reset! urakka/paallystysurakan-indeksitiedot res))))

(defn raakaaineen-indeksit
  "Palauttaa raakaaineelle mahdolliset indeksit"
  [raakaaine paallystysurakoiden-indeksitiedot]
  (filter #(or (nil? (:raakaaine %))
                           (= raakaaine (:raakaaine %)))
          paallystysurakoiden-indeksitiedot))
