(ns harja.views.tyokoneseuranta
  (:require [reagent.core :refer [atom] :as r]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.navigaatio :as nav]
            [harja.loki :refer [log]]
            [harja.atom :as ha]
            [harja.atom :refer-macros [reaction<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

; {:xmin 0 :ymin 0 :xmax 0 :ymax 0 }
(defonce tyokonetaso-paalla (atom true))

(defn tyokone-xf [kone]
  (let [sijainti (:sijainti kone)
        x (sijainti 1)
        y (sijainti 0)]
    (assoc kone
           :type :tyokone
           :alue {:type :circle
                  :coordinates [x y]
                  :color "red"
                  :radius 200
                  :stroke {:color "black" :width 10}})))

(def alueen-tyokoneet
  (reaction<! [alue @nav/kartalla-nakyva-alue
               paalla? @tyokonetaso-paalla
               urakka @nav/valittu-urakka-id]
              {:odota 1000}
              (if (and paalla? alue)
                (do (log "Näytetään työkoneiden sijantia urakalle " urakka)
                    (go
                      (let [tyokoneet (<! (k/post! :hae-tyokoneseurantatiedot (assoc alue :urakkaid urakka)))]
                        (into [] (map tyokone-xf tyokoneet)))))
                nil)))

(defn aloita-tyokoneiden-paivitys []
  (ha/paivita-periodisesti alueen-tyokoneet 5000))


(aloita-tyokoneiden-paivitys)
