(ns harja.tiedot.urakka.laadunseuranta.tarkastukset-kartalla
  (:require [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [harja.tiedot.urakka.laadunseuranta.tarkastukset :as tarkastukset])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [harja.tiedot.urakka.laadunseuranta.tarkastukset :as tarkastukset]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defonce tarkastukset-kartalla
         (reaction
           @tarkastukset/urakan-tarkastukset
           (when @tarkastukset/karttataso-tarkastukset
             (kartalla-esitettavaan-muotoon
               (map #(assoc % :tyyppi-kartalla :tarkastus) @tarkastukset/urakan-tarkastukset)
               @tarkastukset/valittu-tarkastus))))

