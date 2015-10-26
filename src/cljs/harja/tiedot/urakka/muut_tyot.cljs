(ns harja.tiedot.urakka.muut-tyot
  "Tämä nimiavaruus hallinnoi urakan yksikköhintaisia töitä."
  (:require [harja.asiakas.kommunikaatio :as k]
            [reagent.core :refer [atom]]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon kartalla-xf]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))


(defn hae-urakan-muutoshintaiset-tyot [urakka-id]
  (k/post! :muutoshintaiset-tyot urakka-id))


(defn tallenna-muutoshintaiset-tyot
  "Tallentaa muutoshintaiset työt, palauttaa kanavan, josta vastauksen voi lukea."
  [urakka-id tyot]
  (let [hyotykuorma {:urakka-id urakka-id
                     :tyot      tyot}]
    (k/post! :tallenna-muutoshintaiset-tyot
             hyotykuorma)))

(defn tallenna-muiden-toiden-toteuma
  [toteuma]
  (k/post! :tallenna-muiden-toiden-toteuma
           toteuma))

(defonce karttataso-muut-tyot (atom false))
(defonce haetut-muut-tyot (atom nil))
(defonce valittu-toteuma (atom nil))
(defonce muut-tyot-kartalla (reaction
                              (when @karttataso-muut-tyot
                                (kartalla-esitettavaan-muotoon
                                  (map
                                    #(assoc % :tyyppi-kartalla :toteuma)
                                    @haetut-muut-tyot)
                                  @valittu-toteuma
                                  [:toteuma :id]))))