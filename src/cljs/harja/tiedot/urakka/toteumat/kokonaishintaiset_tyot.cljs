(ns harja.tiedot.urakka.toteumat.kokonaishintaiset-tyot
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon kartalla-xf]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as u])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defn hae-kokonaishintaisen-toteuman-tiedot
  "Hakee annetun toimenpidekoodin ja päivämäärän yksityiskohtaiset tiedot."
  [urakka-id pvm toimenpidekoodi]
  (k/post! :hae-kokonaishintaisen-toteuman-tiedot {:urakka-id urakka-id
                                                   :pvm pvm
                                                   :toimenpidekoodi toimenpidekoodi}))

(defn hae-toteumatehtavien-paivakohtaiset-summat [urakka-id sopimus-id [alkupvm loppupvm] toimenpide tehtava]
  (log "Haetaan " urakka-id sopimus-id toimenpide tehtava)
  (k/post! :hae-urakan-kokonaishintaisten-toteumien-tehtavien-paivakohtaiset-summat
           {:urakka-id  urakka-id
            :sopimus-id sopimus-id
            :alkupvm    alkupvm
            :loppupvm   loppupvm
            :toimenpide toimenpide
            :tehtava    tehtava}))

(defn hae-toteumareitit [urakka-id sopimus-id [alkupvm loppupvm] toimenpide tehtava]
  (k/post! :urakan-kokonaishintaisten-toteumien-reitit
           {:urakka-id  urakka-id
            :sopimus-id sopimus-id
            :alkupvm    alkupvm
            :loppupvm   loppupvm
            :toimenpide toimenpide
            :tehtava    tehtava}))

(def nakymassa? (atom false))
(def valittu-paivakohtainen-tehtava (atom nil))

(def haetut-toteumat
         (reaction<!
           [urakka-id (:id @nav/valittu-urakka)
            sopimus-id (first @urakka/valittu-sopimusnumero)
            hoitokausi @urakka/valittu-hoitokausi
            aikavali @urakka/valittu-aikavali
            toimenpide (:tpi_id @urakka/valittu-toimenpideinstanssi)
            tehtava (:t4_id @urakka/valittu-kokonaishintainen-tehtava)
            nakymassa? @nakymassa?]
           (when nakymassa?
             (hae-toteumatehtavien-paivakohtaiset-summat urakka-id sopimus-id (or aikavali hoitokausi) toimenpide tehtava))))

(def haetut-reitit
  (reaction<!
   [urakka-id (:id @nav/valittu-urakka)
    sopimus-id (first @urakka/valittu-sopimusnumero)
    hoitokausi @urakka/valittu-hoitokausi
    aikavali @urakka/valittu-aikavali
    toimenpide (:tpi_id @urakka/valittu-toimenpideinstanssi)
    tehtava (:t4_id @urakka/valittu-kokonaishintainen-tehtava)
    nakymassa? @nakymassa?]
   (when nakymassa?
     (hae-toteumareitit urakka-id sopimus-id (or aikavali hoitokausi) toimenpide tehtava))))

(def karttataso-kokonaishintainen-toteuma (atom false))

(defonce kokonaishintainen-toteuma-kartalla
  (reaction
   (let [taso-paalla? @karttataso-kokonaishintainen-toteuma
         valittu-paivakohtainen-tehtava @valittu-paivakohtainen-tehtava
         haetut-reitit @haetut-reitit]
     (when taso-paalla?
       (kartalla-esitettavaan-muotoon
        (map #(assoc % :tyyppi-kartalla :toteuma)
             (if valittu-paivakohtainen-tehtava
               (filter (fn [reitti]
                         ;; Reittiin liittyvä toteuma on tapahtunut samana päivänä kuin gridistä valitun summarivin
                         ;; pvm. Lisäksi reitillä on tehty kyseistä tehtävää.
                         (and (= (pvm/paivan-alussa (:pvm reitti))
                                 (pvm/paivan-alussa (:pvm valittu-paivakohtainen-tehtava)))
                              ((into #{} (mapv :nimi (:tehtavat reitti))) (:nimi valittu-paivakohtainen-tehtava))))
                       haetut-reitit)
               haetut-reitit)))))))
