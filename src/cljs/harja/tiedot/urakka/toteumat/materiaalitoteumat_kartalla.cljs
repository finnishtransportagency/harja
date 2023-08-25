(ns harja.tiedot.urakka.toteumat.materiaalitoteumat-kartalla
  (:require [reagent.core :refer [atom]]
            [harja.tiedot.urakka.toteumat :as toteumat]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]]
            [harja.ui.kartta.esitettavat-asiat :refer [maarittele-feature kartalla-esitettavaan-muotoon]]
            [harja.ui.kartta.asioiden-ulkoasu :as asioiden-ulkoasu])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defonce karttataso-materiaalitoteumat (atom false))

(def materiaalitoteumat-kartalla (reaction
                                   (when (and @karttataso-materiaalitoteumat
                                           (not-empty @toteumat/valitun-materiaalitoteuman-tiedot))
                                     (let [toteuma @toteumat/valitun-materiaalitoteuman-tiedot]
                                       (kartalla-esitettavaan-muotoon
                                         [(assoc toteuma :tyyppi-kartalla :materiaalitoteuma)])))))
