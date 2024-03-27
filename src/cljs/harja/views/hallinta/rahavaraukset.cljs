(ns harja.views.hallinta.rahavaraukset
  (:require [clojure.string :as str]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [reagent.core :as r]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.hallinta.rahavaraukset :as tiedot]))

(defn rahavaraukset* [e! app]
  [:div
   [harja.ui.debug/debug app]
   "Hei maailma!"])

(defn rahavaraukset []
  [tuck tiedot/tila rahavaraukset*])
