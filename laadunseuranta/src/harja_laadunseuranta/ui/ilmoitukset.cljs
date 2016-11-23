(ns harja-laadunseuranta.ui.ilmoitukset
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async :refer [timeout <!]]
            [harja-laadunseuranta.utils :as utils]
            [harja-laadunseuranta.tiedot.sovellus :as sovellus]
            [cljs-time.local :as l]
            [cljs-time.core :as t])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(def +ilmoituksen-nakymisaika-ms+ 4000)
(def ilmoitus-naytetty-aika (atom nil))

(defn- lisaa-ajastettu-ilmoitus [ilmoitukset-atom teksti]
  (swap! ilmoitukset-atom #(conj % {:ilmoitus teksti})))

(defn ilmoita [teksti ilmoitukset-atom]
  (lisaa-ajastettu-ilmoitus ilmoitukset-atom teksti))

(defn ilmoituskomponentti [ilmoitukset-atom]
  (when (and (not @ilmoitus-naytetty-aika)
             (not (empty? @ilmoitukset-atom)))
    (reset! ilmoitus-naytetty-aika (l/local-now))
    (go (<! (timeout +ilmoituksen-nakymisaika-ms+))
        (reset! ilmoitukset-atom (vec (rest @ilmoitukset-atom)))
        (reset! ilmoitus-naytetty-aika nil)))

  (when (first @ilmoitukset-atom)
     [:div.ilmoitukset
      [:div.ilmoitus (:ilmoitus (first @ilmoitukset-atom))]]))