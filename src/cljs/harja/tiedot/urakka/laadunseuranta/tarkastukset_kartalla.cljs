(ns harja.tiedot.urakka.laadunseuranta.tarkastukset-kartalla
  (:require [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [harja.tiedot.urakka.laadunseuranta.tarkastukset :as tarkastukset])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defonce karttataso-tarkastukset (atom false))

(defonce tarkastukset-kartalla
         (reaction
           @tarkastukset/urakan-tarkastukset
           (when @karttataso-tarkastukset
             (kartalla-esitettavaan-muotoon
               @tarkastukset/urakan-tarkastukset
               @tarkastukset/valittu-tarkastus
               nil
               (comp
                 (filter #(not (nil? (:sijainti %))))
                 (map #(assoc % :tyyppi-kartalla :tarkastus)))))))

