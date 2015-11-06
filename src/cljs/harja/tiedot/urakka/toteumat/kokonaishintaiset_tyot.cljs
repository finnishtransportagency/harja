(ns harja.tiedot.urakka.toteumat.kokonaishintaiset-tyot
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

(defn hae-toteumat [urakka-id sopimus-id [alkupvm loppupvm] toimenpide tehtava]
  (k/post! :urakan-kokonaishintaisten-toteumien-tehtavat
           {:urakka-id  urakka-id
            :sopimus-id sopimus-id
            :alkupvm    alkupvm
            :loppupvm   loppupvm
            :toimenpide toimenpide
            :tehtava    tehtava}))

(def nakymassa? (atom false))
(def valittu-toteuma (atom nil))

(def haetut-toteumat
         (reaction<!
           [urakka-id (:id @nav/valittu-urakka)
            sopimus-id (first @urakka/valittu-sopimusnumero)
            hoitokausi @urakka/valittu-hoitokausi
            kuukausi @urakka/valittu-hoitokauden-kuukausi
            toimenpide (first (first @urakka/valittu-kokonaishintainen-toimenpide))
            tehtava (:t4_id @urakka/valittu-kokonaishintainen-tehtava)
            nakymassa? @nakymassa?]
           (when nakymassa?
             (hae-toteumat urakka-id sopimus-id (or kuukausi hoitokausi) toimenpide tehtava))))

(def karttataso-kokonaishintainen-toteuma (atom false))

(def kokonaishintainen-toteuma-kartalla
         (reaction
           (when @karttataso-kokonaishintainen-toteuma
             (kartalla-esitettavaan-muotoon
               (map
                 #(assoc % :tyyppi-kartalla :toteuma)
                 @haetut-toteumat)))))