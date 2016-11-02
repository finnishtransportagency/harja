(ns harja.tiedot.hallinta.api-jarjestelmatunnukset
  "Hallinnoi integraatiolokin tietoja"
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.urakoitsijat :refer [urakoitsijat]]
            [harja.atom :refer [paivita!]])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defonce nakymassa? (atom false))

(defonce jarjestelmatunnukset
  (reaction<! [nakymassa? @nakymassa?]
              (when nakymassa?
                (k/post! :hae-jarjestelmatunnukset nil))))

(defn- urakoitsijavalinnat []
  (distinct (map #(select-keys % [:id :nimi]) @urakoitsijat)))

(defn- tallenna-jarjestelmatunnukset [muuttuneet-tunnukset]
  (go (let [uudet-tunnukset (<! (k/post! :tallenna-jarjestelmatunnukset
                                         muuttuneet-tunnukset))]
        (log "SAIN: " (pr-str uudet-tunnukset))
        (reset! jarjestelmatunnukset uudet-tunnukset))))