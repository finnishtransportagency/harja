(ns harja.tiedot.urakka.toteumat.muut-tyot-kartalla
  (:require [reagent.core :refer [atom]]
            [harja.tiedot.urakka.suunnittelu.muut-tyot :as muut-tyot]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defonce karttataso-muut-tyot (atom false))

(defonce muut-tyot-kartalla (reaction
                             (let [valittu-toteuma-id (get-in @muut-tyot/valittu-toteuma [:toteuma :id])]
                               (when karttataso-muut-tyot
                                 (kartalla-esitettavaan-muotoon
                                  @muut-tyot/haetut-muut-tyot
                                  #(when-let [taman-id (get-in % [:toteuma :id])]
                                     (= valittu-toteuma-id taman-id))
                                  (map
                                   #(assoc % :tyyppi-kartalla :toteuma)))))))
