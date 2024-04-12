(ns harja.tiedot.urakka.mpu-kustannukset
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto])
  (:require-macros [reagent.ratom :refer [reaction]]))


(defonce tila (atom {}))
(def nakymassa? (atom false))


;; Tuck 
(defrecord HaeTiedot [])


(extend-protocol tuck/Event
  
  HaeTiedot
  (process-event [_ app]
    (println "HaeTiedot")
    app))
