(ns harja.tiedot.hallinta.hairiot
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.hallinta.yhteydenpito :as tiedot]
            [cljs.core.async :refer [<! >! timeout chan]]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.komponentti :as komp]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [reagent.ratom :refer [reaction]]))

(def nakymassa? (atom false))
(def hairiot (atom nil))

(defn hae-hairiot []
  (go (let [vastaus (<! (k/post! :hae-hairioilmoitukset {}))]
        (when-not (k/virhe? vastaus)
          (reset! hairiot vastaus)))))