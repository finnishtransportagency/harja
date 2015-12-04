(ns harja.tiedot.urakka.toteumat.yksikkohintaiset-tyot
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon kartalla-xf]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.toteumat :as toteumat])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defonce yksikkohintaiset-tyot-nakymassa? (atom false))

(defonce yks-hint-tehtavien-summat (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
                                                [valittu-sopimus-id _] @u/valittu-sopimusnumero
                                                nakymassa? @yksikkohintaiset-tyot-nakymassa?
                                                valittu-hoitokausi @u/valittu-hoitokausi]
                                               (when (and valittu-urakka-id valittu-sopimus-id valittu-hoitokausi nakymassa?)
                                                 (log "Haetaan urakan toteumat: " (pr-str valittu-urakka-id) (pr-str valittu-sopimus-id) (pr-str valittu-hoitokausi))
                                                 (toteumat/hae-urakan-toteumien-tehtavien-summat valittu-urakka-id valittu-sopimus-id valittu-hoitokausi :yksikkohintainen))))

(defonce yks-hint-tyot-tehtavittain
         (reaction
           (let [lisaa-yksikkohinta (fn [rivit] (map
                                                  (fn [rivi]
                                                    (assoc rivi :yksikkohinta
                                                                (or (:yksikkohinta (first (filter
                                                                                            (fn [tyo]
                                                                                              (and (= (:sopimus tyo) (first @u/valittu-sopimusnumero))
                                                                                                   (= (:tehtava tyo) (:id rivi))
                                                                                                   (pvm/sama-pvm? (:alkupvm tyo) (first @u/valittu-hoitokausi))))
                                                                                            @u/urakan-yks-hint-tyot)))
                                                                    nil)))
                                                  rivit))
                 lisa-suunniteltu-maara (fn [rivit] (map
                                                      (fn [rivi]
                                                        (assoc rivi :hoitokauden-suunniteltu-maara
                                                                    (or (:maara (first (filter
                                                                                         (fn [tyo]
                                                                                           (and (= (:sopimus tyo) (first @u/valittu-sopimusnumero))
                                                                                                (= (:tehtava tyo) (:id rivi))
                                                                                                (pvm/sama-pvm? (:alkupvm tyo) (first @u/valittu-hoitokausi))))
                                                                                         @u/urakan-yks-hint-tyot)))
                                                                        nil)))
                                                      rivit))
                 lisaa-suunnitellut-kustannukset (fn [rivit]
                                                   (map
                                                     (fn [rivi]
                                                       (assoc rivi :hoitokauden-suunnitellut-kustannukset
                                                                   (or (:yhteensa (first (filter
                                                                                           (fn [tyo]
                                                                                             (and (= (:sopimus tyo) (first @u/valittu-sopimusnumero))
                                                                                                  (= (:tehtava tyo) (:id rivi))
                                                                                                  (pvm/sama-pvm? (:alkupvm tyo) (first @u/valittu-hoitokausi))))
                                                                                           @u/urakan-yks-hint-tyot)))
                                                                       nil)))
                                                     rivit))
                 lisaa-toteutunut-maara (fn [rivit]
                                          (map
                                            (fn [rivi]
                                              (assoc rivi :hoitokauden-toteutunut-maara (or (:maara
                                                                                              (first (filter
                                                                                                       (fn [tehtava] (= (:tpk_id tehtava) (:id rivi)))
                                                                                                       @yks-hint-tehtavien-summat)))
                                                                                            nil)))
                                            rivit))
                 lisaa-toteutuneet-kustannukset (fn [rivit]
                                                  (map
                                                    (fn [rivi]
                                                      (assoc rivi :hoitokauden-toteutuneet-kustannukset (* (:yksikkohinta rivi) (:hoitokauden-toteutunut-maara rivi))))
                                                    rivit))
                 lisaa-erotus (fn [rivit] (map
                                            (fn [rivi]
                                              (assoc rivi :kustannuserotus (- (:hoit3okauden-suunnitellut-kustannukset rivi) (:hoitokauden-toteutuneet-kustannukset rivi))))
                                            rivit))
                 tehtavarivit (reaction
                                (let [urakan-4-tason-tehtavat (map
                                                                (fn [tasot]
                                                                  (let [kolmostaso (nth tasot 2)
                                                                        nelostaso (nth tasot 3)]
                                                                    (assoc nelostaso :t3_koodi (:koodi kolmostaso))))
                                                                @u/urakan-toimenpiteet-ja-tehtavat)
                                      tehtavien-summat @yks-hint-tehtavien-summat]

                                  (when tehtavien-summat
                                    (-> (lisaa-yksikkohinta urakan-4-tason-tehtavat)
                                        (lisa-suunniteltu-maara)
                                        (lisaa-suunnitellut-kustannukset)
                                        (lisaa-toteutunut-maara)
                                        (lisaa-toteutuneet-kustannukset)
                                        (lisaa-erotus)))))
                 valittu-tpi @u/valittu-toimenpideinstanssi]
             (filter
               (fn [rivi] (and (= (:t3_koodi rivi) (:t3_koodi valittu-tpi))
                               (or
                                 (> (:hoitokauden-toteutunut-maara rivi) 0)
                                 (> (:hoitokauden-suunniteltu-maara rivi) 0))))
               @tehtavarivit))))

(def yksikkohintainen-toteuma-kartalla-xf
  (map #(do
         (assoc %
           :type :yksikkohintainen-toteuma
           :alue {:type   :arrow-line
                  :points (mapv (comp :coordinates :sijainti)
                                (sort-by
                                  :aika
                                  pvm/ennen?
                                  (:reittipisteet %)))}))))


(defonce valittu-yksikkohintainen-toteuma (atom nil))

(defn hae-toteumareitit [urakka-id sopimus-id [alkupvm loppupvm] toimenpide ]
  (k/post! :urakan-yksikkohintaisten-toteumien-reitit
           {:urakka-id  urakka-id
            :sopimus-id sopimus-id
            :alkupvm    alkupvm
            :loppupvm   loppupvm
            :toimenpide toimenpide}))

(def haetut-reitit
  (reaction<!
    [urakka-id (:id @nav/valittu-urakka)
     sopimus-id (first @urakka/valittu-sopimusnumero)
     hoitokausi @urakka/valittu-hoitokausi
     toimenpide (first (first @urakka/valittu-kokonaishintainen-toimenpide))
     nakymassa? @yksikkohintaiset-tyot-nakymassa?]
    (when nakymassa?
      (hae-toteumareitit urakka-id sopimus-id hoitokausi toimenpide))))

(def karttataso-yksikkohintainen-toteuma (atom false))

(defonce yksikkohintainen-toteuma-kartalla
         (reaction
           @valittu-yksikkohintainen-toteuma
           @haetut-reitit
           (when @karttataso-yksikkohintainen-toteuma
             (if @valittu-yksikkohintainen-toteuma
               (into [] yksikkohintainen-toteuma-kartalla-xf [@valittu-yksikkohintainen-toteuma]) ; FIXME Ei tarvita enää
               (kartalla-esitettavaan-muotoon
                 (map
                   #(assoc % :tyyppi-kartalla :toteuma)
                   @haetut-reitit))))))
