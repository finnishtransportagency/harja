(ns harja.views.tyokoneseuranta
  (:require [reagent.core :refer [atom] :as r]
            [harja.asiakas.kommunikaatio :as k]
            [harja.atom :refer-macros [reaction<!]]))

(def valittu-alue (atom {:xmin 0 :ymin 0 :xmax 0 :ymax 0}))

(def alueen-tyokoneet
  (reaction<! [alue @valittu-alue]
              {:odota 1000}
              (k/post! :hae-tyokoneseurantatiedot alue)))

