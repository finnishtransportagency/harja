(ns harja.tiedot.urakka.toteumat.materiaalitoteumat-kartalla
  (:require [reagent.core :refer [atom]]
            [harja.tiedot.urakka.toteumat.muut-materiaalit :as toteumat]
            [harja.ui.kartta.esitettavat-asiat :refer [maarittele-feature kartalla-esitettavaan-muotoon]])
  (:require-macros [reagent.ratom :refer [reaction]]))

(defonce karttataso-muut-materiaalitoteumat (atom false))

(defonce muut-materiaalitoteumat-kartalla (reaction
                                            (when @karttataso-muut-materiaalitoteumat
                                              (let [toteuma @toteumat/valitun-materiaalitoteuman-tiedot]
                                                (kartalla-esitettavaan-muotoon
                                                  [(assoc toteuma :tyyppi-kartalla :materiaalitoteuma)])))))
