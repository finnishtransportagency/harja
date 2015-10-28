(ns harja.tiedot.urakka.toteumat.kokonaishintaiset-tyot
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.asiakas.kommunikaatio :as k]
            [harja.pvm :as pvm])
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
(def karttataso (atom false))
(defonce valittu-toteuma (atom nil))

(defonce haetut-toteumat
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

(def kokonaishintainen-toteuma-kartalla-xf
  (map #(do
         (assoc %
           :type :kokonaishintainen-toteuma
           :alue {:type   :arrow-line
                  :points (mapv (comp :coordinates :sijainti)
                                (sort-by
                                  :aika
                                  pvm/ennen?
                                  (:reittipisteet %)))}))))

(defonce kokonaishintainen-toteuma-kartalla
         (reaction
           @haetut-toteumat
           (when @karttataso-kokonaishintainen-toteuma
             (into [] kokonaishintainen-toteuma-kartalla-xf haetut-toteumat))))