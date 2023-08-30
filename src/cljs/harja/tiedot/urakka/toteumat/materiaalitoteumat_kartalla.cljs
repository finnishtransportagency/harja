(ns harja.tiedot.urakka.toteumat.materiaalitoteumat-kartalla
  (:require [reagent.core :refer [atom]]
            [harja.tiedot.urakka.suunnittelu.materiaalit :as materiaali-tiedot]
            [harja.ui.kartta.esitettavat-asiat :refer [maarittele-feature kartalla-esitettavaan-muotoon]])
  (:require-macros [reagent.ratom :refer [reaction]]))

(defonce karttataso-materiaalitoteumat (atom false))

(def materiaalitoteumat-kartalla (reaction
                                   (when (and @karttataso-materiaalitoteumat
                                           (not-empty @materiaali-tiedot/valitun-materiaalitoteuman-tiedot))
                                     (let [toteuma @materiaali-tiedot/valitun-materiaalitoteuman-tiedot]
                                       (kartalla-esitettavaan-muotoon
                                         [(assoc toteuma :tyyppi-kartalla :materiaalitoteuma)])))))
