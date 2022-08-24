(ns harja.tiedot.urakka.toteumat.yksikkohintaiset-tyot
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.navigaatio :as nav]
            [harja.asiakas.kommunikaatio :as k]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.toteumat :as toteumat]
            [harja.ui.openlayers :as openlayers]
            [harja.ui.kartta.esitettavat-asiat :as esitettavat-asiat])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defonce yksikkohintaiset-tyot-nakymassa? (atom false))

(defonce yks-hint-tehtavien-summat
         (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
                      [valittu-sopimus-id _] @u/valittu-sopimusnumero
                      nakymassa? @yksikkohintaiset-tyot-nakymassa?
                      valittu-hoitokausi @u/valittu-hoitokausi
                      valittu-aikavali @u/yksikkohintaiset-aikavali
                      valittu-toimenpide-id (:tpi_id @u/valittu-toimenpideinstanssi)
                      valittu-tehtava-id (:id @u/valittu-yksikkohintainen-tehtava)]
                     {:nil-kun-haku-kaynnissa? true}
                     (when (and valittu-urakka-id valittu-sopimus-id valittu-hoitokausi nakymassa?)
                       (log "Haetaan urakan toteumat: " (pr-str valittu-urakka-id)
                            (pr-str valittu-sopimus-id) (pr-str valittu-hoitokausi))
                       (toteumat/hae-urakan-toteumien-tehtavien-summat
                         valittu-urakka-id valittu-sopimus-id valittu-aikavali :yksikkohintainen
                         valittu-toimenpide-id valittu-tehtava-id))))

(defn assosioi [rivit rivin-avain hakuehto valittu-sopimusnumero valittu-hoitokausi urakan-yks-hint-tyot]
  (map
    (fn [rivi]
      (assoc rivi rivin-avain
                  (hakuehto (first (filter
                                     (fn [tyo]
                                       (and (= (:sopimus tyo)
                                               (first valittu-sopimusnumero))
                                            (= (:tehtava tyo) (:tpk_id rivi))
                                            (pvm/sama-pvm?
                                              (:alkupvm tyo)
                                              (first valittu-hoitokausi))))
                                     urakan-yks-hint-tyot)))))
    rivit))

(defn- laske-toteutuneet-kustannukset [rivit]
  (map
    (fn [rivi]
      (assoc rivi :hoitokauden-toteutuneet-kustannukset (* (:yksikkohinta rivi) (:maara rivi))))
    rivit))

(defn- laske-kustannuserotus [rivit]
  (map
    (fn [rivi]
      (assoc rivi :kustannuserotus
                  (- (:hoitokauden-suunnitellut-kustannukset rivi)
                     (:hoitokauden-toteutuneet-kustannukset rivi))))
    rivit))

(def yks-hint-tyot-tehtavittain
  (reaction
    (let [assosioi (fn [rivit rivin-avain haettava-avain]
                     (assosioi rivit rivin-avain haettava-avain @u/valittu-sopimusnumero
                               @u/valittu-hoitokausi @u/urakan-yks-hint-tyot))]
      (when @yks-hint-tehtavien-summat
        (sort-by :nimi
                 (-> @yks-hint-tehtavien-summat
                     (assosioi :yksikkohinta :yksikkohinta)
                     (assosioi :hoitokauden-suunniteltu-maara :maara)
                     (assosioi :hoitokauden-suunnitellut-kustannukset :yhteensa)
                     (assosioi :yksikko :yksikko)
                     (laske-toteutuneet-kustannukset)
                     (laske-kustannuserotus)))))))

(defonce valittu-yksikkohintainen-toteuma (atom nil))

(defn uusi-yksikkohintainen-toteuma []
  {:alkanut (pvm/nyt)
   :paattynyt (pvm/nyt)
   :suorittajan-nimi (:nimi @u/urakan-organisaatio)
   :suorittajan-ytunnus (:ytunnus @u/urakan-organisaatio)})

(defn luo-yksikkohintaisten-toteumien-kuvataso
  [urakka-id sopimus-id taso-paalla? [alkupvm loppupvm]
   toimenpide toimenpidenimet tehtava toteuma-id]
  (when taso-paalla?
    (openlayers/luo-kuvataso
     :yksikkohintaiset-toteumat (mapv esitettavat-asiat/toimenpiteen-selite toimenpidenimet)
     "yht" (k/url-parametri
            {:urakka-id urakka-id
             :sopimus-id sopimus-id
             :alkupvm alkupvm
             :loppupvm loppupvm
             :toimenpide toimenpide
             :tehtava tehtava
             :toteuma-id toteuma-id}))))

(def karttataso-yksikkohintainen-toteuma (atom false))

(defonce yksikkohintainen-toteuma-kartalla
  (reaction
   (let [urakka-id (:id @nav/valittu-urakka)
         sopimus-id (first @u/valittu-sopimusnumero)
         hoitokausi @u/valittu-hoitokausi
         aikavali @u/yksikkohintaiset-aikavali
         toimenpide (:tpi_id @u/valittu-toimenpideinstanssi)
         tehtava (:id @u/valittu-yksikkohintainen-tehtava)
         taso-paalla? @karttataso-yksikkohintainen-toteuma
         summat @yks-hint-tehtavien-summat
         toteuma @valittu-yksikkohintainen-toteuma]
     (luo-yksikkohintaisten-toteumien-kuvataso
      urakka-id sopimus-id taso-paalla? (pvm/tiukin-aikavali aikavali hoitokausi) toimenpide
      (if toteuma
        (let [tehtavat (into #{}
                             (map (comp :id :tehtava))
                             (vals (:tehtavat toteuma)))]
          (map :nimi (filter (comp tehtavat :tpk_id) summat)))
        (map :nimi summat))
      tehtava
      (:toteuma-id toteuma)))))
