(ns harja.tiedot.hallinta.hairiot
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.hallinta.yhteydenpito :as tiedot]
            [cljs.core.async :refer [<! >! timeout chan]]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.komponentti :as komp]
            [harja.domain.hairioilmoitus :as hairio]
            [harja.loki :refer [log]]
            [harja.tiedot.hairioilmoitukset :as hairio-ui]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [reagent.ratom :refer [reaction]]))

(def nakymassa? (atom false))
(def hairiot (atom nil))
(def asetetaan-hairioilmoitus? (atom false))
(def tuore-hairioviesti (atom nil))
(def tallennus-kaynnissa? (atom false))

(defn hae-hairiot []
  (go (let [vastaus (<! (k/post! :hae-hairioilmoitukset {}))]
        (when-not (k/virhe? vastaus)
          (reset! hairiot vastaus)))))

(defn aseta-hairioilmoitus [viesti]
  (reset! tallennus-kaynnissa? true)
  (go (let [vastaus (<! (k/post! :aseta-hairioilmoitus {::hairio/viesti viesti}))]
        (reset! tallennus-kaynnissa? false)
        (reset! asetetaan-hairioilmoitus? false)
        (when-not (k/virhe? vastaus)
          (reset! hairiot vastaus)
          (hairio-ui/hae-tuorein-hairioilmoitus)))))

(defn poista-hairioilmoitus []
  (reset! tallennus-kaynnissa? true)
  (go (let [vastaus (<! (k/post! :aseta-kaikki-hairioilmoitukset-pois {}))]
        (reset! tallennus-kaynnissa? false)
        (when-not (k/virhe? vastaus)
          (reset! hairiot vastaus)))))