(ns harja.tiedot.kanavat.urakka.toimenpiteet.kan-toimenpiteet-kartalla
  (:require [harja.domain.kanavat.kanavan-toimenpide :as to]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [harja.tiedot.kanavat.urakka.toimenpiteet.kokonaishintaiset :as kokonaishintaiset]
            [harja.tiedot.kanavat.urakka.toimenpiteet.muutos-ja-lisatyot :as lisatyot]
            [clojure.set :as set])
  (:require-macros [reagent.ratom :refer [reaction]]))


(defonce karttataso-toimenpiteet-vapaassa-sijainnissa (atom false))

(defonce toimenpiteet-kartalla
  (reaction
    (let [tila-kok @kokonaishintaiset/tila
          tila-lisa @lisatyot/tila
          kokonaishintaiset-nakymassa? (:nakymassa? tila-kok)
          lisatyot-nakymassa? (:nakymassa? tila-lisa)
          tila (cond
                 kokonaishintaiset-nakymassa? tila-kok
                 lisatyot-nakymassa? tila-lisa)]
      (when @karttataso-toimenpiteet-vapaassa-sijainnissa
        (kartalla-esitettavaan-muotoon
          (map
            #(set/rename-keys % {::to/sijainti :sijainti})
            (:toimenpiteet tila))
          #(= (get-in tila [:avattu-toimenpide ::to/id]) (::to/id %))
          (comp
            (remove #(nil? (:sijainti %)))
            (remove #(some? (::to/kohde %)))
            (map #(assoc % :tyyppi-kartalla :kan-toimenpide))))))))
