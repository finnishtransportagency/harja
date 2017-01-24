(ns harja.tiedot.urakka.laadunseuranta.laatupoikkeamat-kartalla
  (:require [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat :as laatupoikkeamat]
            [harja.loki :as log :refer [log]])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defonce karttataso-laatupoikkeamat (atom false))

(defonce laatupoikkeamat-kartalla
  (reaction
   (let [laatupoikkeamat @laatupoikkeamat/urakan-laatupoikkeamat
         valittu-laatupoikkeama-id (:id @laatupoikkeamat/valittu-laatupoikkeama)]
     (when @karttataso-laatupoikkeamat
       (kartalla-esitettavaan-muotoon
        laatupoikkeamat
        #(= valittu-laatupoikkeama-id (:id %))
        (comp
         (filter #(not (nil? (:sijainti %))))
         (map #(assoc % :tyyppi-kartalla :laatupoikkeama))))))))
