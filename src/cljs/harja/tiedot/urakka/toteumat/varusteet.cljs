(ns harja.tiedot.urakka.toteumat.varusteet
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.asiakas.kommunikaatio :as k]
            [harja.pvm :as pvm]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon kartalla-xf]])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defn hae-toteumat [urakka-id]
  (k/post! :urakan-varustetoteumat
           {:urakka-id  urakka-id}))

(def nakymassa? (atom false))

(def haetut-toteumat
         (reaction<!
           [urakka-id (:id @nav/valittu-urakka)
            nakymassa? @nakymassa?]
           (when nakymassa?
             (hae-toteumat urakka-id))))