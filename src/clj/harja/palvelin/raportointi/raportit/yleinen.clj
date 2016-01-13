(ns harja.palvelin.raportointi.raportit.yleinen
  (:require [yesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [clj-time.local :as l]
            [clj-time.format :as tf]
            [clj-time.coerce :as tc]
            [clj-time.core :as t]))

(defn raportin-otsikko
  [konteksti nimi alkupvm loppupvm]
  (let [kk-vali? (pvm/kyseessa-kk-vali? alkupvm loppupvm)
        kkna-ja-vuonna (pvm/kuukautena-ja-vuonna (l/to-local-date-time alkupvm))]
    (if kk-vali?
      (str konteksti ", " nimi " " kkna-ja-vuonna)
      (str konteksti ", " nimi " ajalta "
           (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm)))))

(defn ryhmittele-tulokset-raportin-taulukolle
  "rivit                   ryhmiteltävät rivit
   ryhmittely-avain        avain, jonka perusteella rivit ryhmitellään. Ryhmän otsikko otetaan suoraan tästä avaimesta.
   rivi-fn                 funktio, joka ottaa parametrina yhden rivin ja palauttaa sen taulukossa esitettävässä
                           muodossa (eli vektori, jossa arvot sarakkeiden mukaisessa järjestyksessä, esim [Seppo, 42]"
  [rivit ryhmittely-avain rivi-fn]
  (let [ryhmat (group-by ryhmittely-avain rivit)]
    (into [] (mapcat
               (fn [ryhma]
                 (reduce
                   conj
                   [{:otsikko ryhma}]
                   (map
                     rivi-fn
                     (get ryhmat ryhma))))
               (keys ryhmat)))))

(def vuosi-ja-kk-fmt (tf/with-zone (tf/formatter "YYYY/MM") (t/time-zone-for-id "EET")))
(def kk-ja-vuosi-fmt (tf/with-zone (tf/formatter "MM/YYYY") (t/time-zone-for-id "EET")))

(defn vuosi-ja-kk [pvm]
  (tf/unparse vuosi-ja-kk-fmt (l/to-local-date-time pvm)))

(defn kk-ja-vuosi [pvm]
  (tf/unparse kk-ja-vuosi-fmt (l/to-local-date-time pvm)))

(defn kuukaudet [alku loppu]
  (let [alku  (l/to-local-date-time alku)
        loppu  (l/to-local-date-time loppu)]
    (letfn [(kuukaudet [kk]
              (when-not (t/after? kk loppu)
                (lazy-seq
                  (cons (tf/unparse vuosi-ja-kk-fmt kk)
                        (kuukaudet (t/plus kk (t/months 1)))))))]
      (kuukaudet alku))))

(defn pylvaat
  [{:keys [otsikko alkupvm loppupvm kuukausittainen-data piilota-arvo? legend]}]
  [:pylvaat {:otsikko (str otsikko " " (pvm/pvm alkupvm) "-" (pvm/pvm loppupvm))
             :piilota-arvo? piilota-arvo? :legend legend}
   (into []
         (map (juxt identity #(or (kuukausittainen-data %)
                                  (if legend ;jos on monta sarjaa, on mukana legend: tällöin tyhjä on oltava vektori
                                    []
                                    0))))
         (kuukaudet alkupvm loppupvm))])

(defn ei-osumia-aikavalilla-teksti
  [nimi alkupvm loppupvm]
  [:otsikko-kuin-pylvaissa (str "Ei " nimi " aikana " (pvm/pvm alkupvm) "-" (pvm/pvm loppupvm))])

(defn rivi [& asiat]
  (into [] (keep identity asiat)))
