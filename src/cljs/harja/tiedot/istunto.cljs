(ns harja.tiedot.istunto
  "Harjan istunnon tiedot"
  (:require [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.loki :refer [log]]

            [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [cljs.core.async :refer [<! >! timeout chan]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def kayttaja (atom nil))

(def kayttajan-nimi
  (reaction (when-let [k @kayttaja]
              (str (:etunimi k) " " (:sukunimi k)))))

(def istunto-alkoi (atom nil))

(defn- aseta-kayttaja [k]
  (reset! kayttaja k)
  (tapahtumat/julkaise! (merge {:aihe :kayttajatiedot} k)))

(def kayttoaika-ilman-kayttajasyotteita-sekunteina (* 60 60 2))

(def ajastin-kaynnissa (atom false))

(def kayttoaikaa-jaljella-sekunteina (atom kayttoaika-ilman-kayttajasyotteita-sekunteina))

(defn kaynnista-ajastin []
  (if (false? @ajastin-kaynnissa)
    (go
      (reset! ajastin-kaynnissa true)
      (loop []
        (<! (timeout 1000))
        (if @ajastin-kaynnissa
          (do
            (log "Käyttöaikaa jäljellä sekunteina: @kayttoaikaa-jaljella-sekunteina")
            (reset! kayttoaikaa-jaljella-sekunteina (- @kayttoaikaa-jaljella-sekunteina 1))
            (recur)))))))

(defn pysayta-ajastin []
  (reset! ajastin-kaynnissa false))

(defn resetoi-ajastin []
  (reset! kayttoaikaa-jaljella-sekunteina kayttoaika-ilman-kayttajasyotteita-sekunteina))