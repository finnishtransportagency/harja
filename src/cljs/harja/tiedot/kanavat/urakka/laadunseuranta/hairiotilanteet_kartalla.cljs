(ns harja.tiedot.kanavat.urakka.laadunseuranta.hairiotilanteet-kartalla
  (:require [clojure.set :as set]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [harja.domain.kanavat.hairiotilanne :as ht]
            [harja.tiedot.kanavat.urakka.laadunseuranta.hairiotilanteet :as tiedot])
  (:require-macros [reagent.ratom :refer [reaction]]))


(defonce karttataso-hairiotilanteet-vapaassa-sijainnissa (atom false))

(defonce hairiot-kartalla
  (reaction
    (let [tila @tiedot/tila]
      (when @karttataso-hairiotilanteet-vapaassa-sijainnissa
        (kartalla-esitettavaan-muotoon
          (map
            #(set/rename-keys % {::ht/sijainti :sijainti})
            (:hairiotilanteet tila))
          #(= (get-in tila [:valittu-hairiotilanne ::ht/id]) (::ht/id %))
          (comp
            (remove #(nil? (:sijainti %)))
            (remove #(some? (::ht/kohde %)))
            (map #(assoc % :tyyppi-kartalla :kan-hairiotilanne))))))))
