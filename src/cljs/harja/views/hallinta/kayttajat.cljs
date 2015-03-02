(ns harja.views.hallinta.kayttajat
  "Käyttäjähallinnan näkymä"
  (:require [reagent.core :refer [atom] :as re]
            [cljs.core.async :refer [<! chan]]

            [harja.tiedot.kayttajat :as k]
            [harja.ui.grid :as grid])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn kayttajat
  "Käyttäjälistauskomponentti"
  []
  [grid/grid
   {:otsikko "Käyttäjät"
    }])
