(ns harja.tiedot.kartta.infopaneelin-tila
  (:require [harja.tiedot.navigaatio :as nav]
            [reagent.core :refer [atom]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [reagent.ratom :refer [reaction]]))

(defonce nayta-infopaneeli? (atom false))
(defonce infopaneeli-nakyvissa? (reaction (and @nayta-infopaneeli? @nav/kartta-nakyvissa?)))

(defn piilota-infopaneeli! []
  ;; tasot kuuntelee tätä atomia, ja piilottaa :infopaneelin-merkki tason kun tämä muuttuu falseksi
  (reset! nayta-infopaneeli? false))

(defn nayta-infopaneeli! []
  (reset! nayta-infopaneeli? true))
