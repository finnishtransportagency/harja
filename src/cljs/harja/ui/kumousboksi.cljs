(ns harja.ui.kumousboksi
  (:require [reagent.core :as r]
            [reagent.core :refer [atom] :as r]
            [harja.ui.napit :as napit]
            [harja.ui.komponentti :as komp]
            [harja.ui.dom :as dom]
            [harja.fmt :as fmt]
            [goog.events.EventType :as EventType]
            [harja.loki :refer [log]]
            [harja.ui.ikonit :as ikonit])
  (:require-macros [harja.tyokalut.ui :refer [for*]]
                   [cljs.core.async.macros :refer [go]]))

(defn kumousboksi [{:keys [sijainti-y kumoa-fn]}]
  [:div.kumousboksi {:style {:right 0
                             :top sijainti-y}}
   [napit/sulje-ruksi (constantly nil)]
   [:p "Muutos tallennettu!"]
   [napit/kumoa "Kumoa" kumoa-fn]])