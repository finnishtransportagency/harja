(ns harja.tiedot.urakka.yhatuonti
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.ui.yleiset :refer [ajax-loader vihje]]
            [harja.loki :refer [log logt tarkkaile!]]
            [cljs.core.async :refer [<! >! chan timeout]]
            [harja.ui.lomake :refer [lomake]]
            [harja.ui.grid :refer [grid]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tyokalut.vkm :as vkm])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

(def hakulomake-data (atom nil))
(def hakutulokset-data (atom []))
(def sidonta-kaynnissa? (atom false))

(defn hae-yha-urakat [{:keys [yhatunniste sampotunniste vuosi harja-urakka-id]}]
  (log "[YHA] Hae YHA-urakat...")
  (reset! hakutulokset-data nil)
  (k/post! :hae-urakat-yhasta {:harja-urakka-id harja-urakka-id
                               :yhatunniste yhatunniste
                               :sampotunniste sampotunniste
                               :vuosi vuosi}))


(defn- sido-yha-urakka-harja-urakkaan [harja-urakka-id yha-tiedot]
  (k/post! :sido-yha-urakka-harja-urakkaan {:harja-urakka-id harja-urakka-id
                                            :yha-tiedot yha-tiedot}))

(defn- tallenna-yha-kohteet [harja-urakka-id harja-sopimus-id kohteet]
  (k/post! :tallenna-yha-kohteet {:urakka-id harja-urakka-id
                                  :sopimus-id harja-sopimus-id
                                  :kohteet kohteet}))

(defn- hae-yha-kohteet [harja-urakka-id]
  (k/post! :hae-yha-kohteet {:urakka-id harja-urakka-id}))

(defn hae-ja-kasittele-yha-kohteet
  "Hakee YHA-kohteet, päivittää ne kutsumalla VMK-palvelua ja tallentaa ne Harjan kantaan.
   Suoritus tapahtuu asynkronisesti"
  [harja-urakka-id harja-sopimus-id]
  (go (let [yha-kohteet (hae-yha-kohteet harja-urakka-id)
            yha-kohteet (vkm/muunna-yha-kohteet yha-kohteet)]
        (tallenna-yha-kohteet harja-urakka-id harja-sopimus-id yha-kohteet))))