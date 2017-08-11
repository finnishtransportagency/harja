(ns harja.tiedot.hallinta.hairiot
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.hallinta.yhteydenpito :as tiedot]
            [cljs.core.async :refer [<! >! timeout chan]]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.komponentti :as komp]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [reagent.ratom :refer [reaction]]))

(def nakymassa? (atom false))
(def hairiot (atom nil))
(def asetetaan-hairioilmoitus? (atom false))
(def tuore-hairioviesti (atom nil))

(defn hae-hairiot []
  (go (let [vastaus (<! (k/post! :hae-hairioilmoitukset {}))]
        (when-not (k/virhe? vastaus)
          (reset! hairiot vastaus)))))

(defn aseta-hairioilmoitus [viesti]
  (log "TODO ASETA"))

(defn poista-hairioilmoitus []
  (go (let [vastaus (<! (k/post! :aseta-kaikki-hairioilmoitukset-pois {}))]
        (when-not (k/virhe? vastaus)
          (reset! hairiot vastaus)))))