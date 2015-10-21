(ns harja.tiedot.urakka.toteumat.kokonaishintaiset-tyot
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.urakka.toteumat :as toteumat]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def nakymassa? (atom true))
(defonce valittu-toteuma (atom nil))
(def karttataso (atom false))

(defonce haetut-toteumat
         (reaction<!
           [urakka-id (:id @nav/valittu-urakka)
            sopimus-id (first @urakka/valittu-sopimusnumero)
            hoitokausi @urakka/valittu-hoitokausi
            toimenpide @urakka/valittu-toimenpideinstanssi
            nakymassa? @nakymassa?]
           (when nakymassa?
             (toteumat/hae-urakan-toteumat urakka-id sopimus-id hoitokausi "kokonaishintainen"))))

;; todo: poista
(tarkkaile! "---- TOTEUMAT: " haetut-toteumat)
