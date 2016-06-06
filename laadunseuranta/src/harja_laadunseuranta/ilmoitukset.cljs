(ns harja-laadunseuranta.ilmoitukset
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async :refer [timeout <!]]
            [harja-laadunseuranta.utils :as utils]
            [harja-laadunseuranta.sovellus :as sovellus])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def +ilmoituksen-nakymisaika+ 4000)

(defn lisaa-ajastettu-ilmoitus [atomi aika teksti]
  (swap! atomi #(conj % teksti))
  (utils/timed-swap! aika atomi #(vec (rest %))))

(defn ilmoita [teksti]
  (lisaa-ajastettu-ilmoitus sovellus/ilmoitukset +ilmoituksen-nakymisaika+ teksti))

(defn ilmoituskomponentti [atomi]
  (when-not (empty? @atomi)
    [:div.ilmoitukset
     (for [ilmoitus @atomi]
       ^{:key (hash ilmoitus)}
       [:div.ilmoitus ilmoitus])]))

