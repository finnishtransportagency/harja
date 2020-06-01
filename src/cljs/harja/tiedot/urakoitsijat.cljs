(ns harja.tiedot.urakoitsijat
  "Harjan urakoitsijoiden tietojen hallinta"
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.domain.urakka :as urakka-domain]
            [harja.asiakas.tapahtumat :as t]
            [harja.loki :refer [tarkkaile!]]

            [cljs.core.async :refer [chan <! >! close!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def urakoitsijat "Urakoitsijat" (atom #{}))

(def urakoitsijat-hoito
  (reaction (into #{} (filter #(or (= (:urakkatyyppi %) "hoito")
                                   (= (:urakkatyyppi %) "teiden-hoito")) @urakoitsijat))))
(def urakoitsijat-paallystys
  (reaction (into #{} (filter #(= (:urakkatyyppi %) "paallystys") @urakoitsijat))))
(def urakoitsijat-tiemerkinta
  (reaction (into #{} (filter #(= (:urakkatyyppi %) "tiemerkinta") @urakoitsijat))))
(def urakoitsijat-valaistus
  (reaction (into #{} (filter #(= (:urakkatyyppi %) "valaistus") @urakoitsijat))))
(def urakoitsijat-siltakorjaus
  (reaction (into #{} (filter #(= (:urakkatyyppi %) "siltakorjaus") @urakoitsijat))))
(def urakoitsijat-tekniset-laitteet
  (reaction (into #{} (filter #(= (:urakkatyyppi %) "tekniset-laitteet") @urakoitsijat))))
(def urakoitsijat-vesivaylat
  (reaction (into #{} (filter (comp urakka-domain/vesivayla-urakkatyypit keyword :urakkatyyppi) @urakoitsijat))))

(def urakoitsijat-kaikki
  ;; Koska urakoitsijat setti sisältää saman urakoitsijan jokaiselle
  ;; eri urakkatyypille, jota urakoitsija tekee, ryhmitellään tässä
  ;; :id mukaan ja kerätään urakkatyypit settiin.
  (reaction (into #{}
                  (map #(-> (first %)
                            (assoc :urakkatyypit (into #{}
                                                       (map :urakkatyyppi)
                                                       %))
                            (dissoc :urakkatyyppi)))
                  (vals (group-by :id @urakoitsijat)))))
(defn ^:export hae-urakoitsijat []
  (let [ch (chan)]
    (go
      (let [res (<! (k/post! :hae-urakoitsijat nil))]
        (>! ch res))
      (close! ch))
    ch))

(t/kuuntele! :harja-ladattu (fn [_]
                              (go (reset! urakoitsijat (<! (k/post! :hae-urakoitsijat
                                                                     nil))))))
