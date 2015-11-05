(ns harja.tiedot.urakka.toteumat.varusteet
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.asiakas.kommunikaatio :as k]
            [harja.pvm :as pvm]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon kartalla-xf]])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defn hae-toteumat [urakka-id sopimus-id [alkupvm loppupvm] tienumero]
  (k/post! :urakan-varustetoteumat
           {:urakka-id  urakka-id
            :sopimus-id sopimus-id
            :alkupvm    alkupvm
            :loppupvm   loppupvm
            :tienumero tienumero}))

(defonce tienumero (atom nil))

(def nakymassa? (atom false))

(def haetut-toteumat
         (reaction<!
           [urakka-id (:id @nav/valittu-urakka)
            sopimus-id (first @urakka/valittu-sopimusnumero)
            hoitokausi @urakka/valittu-hoitokausi
            kuukausi @urakka/valittu-hoitokauden-kuukausi
            tienumero @tienumero
            nakymassa? @nakymassa?]
           (when nakymassa?
             (hae-toteumat urakka-id sopimus-id (or kuukausi hoitokausi) tienumero))))

(tarkkaile! "Haetut toteumat: " haetut-toteumat)

(def karttataso-varustetoteuma (atom false))

(def varusteet-kartalla
  (reaction
    (when karttataso-varustetoteuma
      (kartalla-esitettavaan-muotoon
        (map
          #(assoc % :tyyppi-kartalla :tarkastus) ; FIXME Vaihda tyyppi oikeaksi kun näkyy kartalla
          @haetut-toteumat)))))