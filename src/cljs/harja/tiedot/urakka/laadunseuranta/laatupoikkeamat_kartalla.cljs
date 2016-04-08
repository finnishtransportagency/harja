(ns harja.tiedot.urakka.laadunseuranta.laatupoikkeamat-kartalla
  (:require [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat :as laatupoikkeamat])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defonce karttataso-laatupoikkeamat (atom false))

(defonce laatupoikkeamat-kartalla
         (reaction
           @laatupoikkeamat/urakan-laatupoikkeamat
           (when @karttataso-laatupoikkeamat
             (kartalla-esitettavaan-muotoon
               @laatupoikkeamat/urakan-laatupoikkeamat
               @laatupoikkeamat/valittu-laatupoikkeama
               nil
               (comp
                 (filter #(not (nil? (:sijainti %))))
                 (map #(assoc % :tyyppi-kartalla :laatupoikkeama)))))))

