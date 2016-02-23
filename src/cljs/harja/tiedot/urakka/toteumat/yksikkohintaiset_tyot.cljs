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

(defonce yks-hint-tehtavien-summat
         (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
                      [valittu-sopimus-id _] @u/valittu-sopimusnumero
                      nakymassa? @yksikkohintaiset-tyot-nakymassa?
                      valittu-hoitokausi @u/valittu-hoitokausi
                      valittu-toimenpide-id (:tpi_id @urakka/valittu-toimenpideinstanssi)
                      valittu-tehtava-id (:id @u/valittu-yksikkohintainen-tehtava)]
                     {:nil-kun-haku-kaynnissa? true}
                     (when (and valittu-urakka-id valittu-sopimus-id valittu-hoitokausi nakymassa?)
                       (log "Haetaan urakan toteumat: " (pr-str valittu-urakka-id)
                            (pr-str valittu-sopimus-id) (pr-str valittu-hoitokausi))
                       (toteumat/hae-urakan-toteumien-tehtavien-summat
                         valittu-urakka-id valittu-sopimus-id valittu-hoitokausi :yksikkohintainen
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
     (assoc rivi :hoitokauden-toteutuneet-kustannukset (* (:yksikkohinta rivi) (:hoitokauden-toteutunut-maara rivi))))
   rivit))

(defn- laske-erotus [rivit]
  (map
   (fn [rivi]
     (assoc rivi :kustannuserotus
            (- (:hoitokauden-suunnitellut-kustannukset rivi)
               (:hoitokauden-toteutuneet-kustannukset rivi))))
   rivit))

(def yks-hint-tyot-tehtavittain
  (reaction
   (let [assosioi (fn [rivit rivin-avain haettava-avain]
                    (assosioi rivit rivin-avain haettava-avain @u/valittu-sopimusnumero @u/valittu-hoitokausi @u/urakan-yks-hint-tyot))]
     (-> @yks-hint-tehtavien-summat
         (assosioi :yksikkohinta :yksikkohinta)
         (assosioi :hoitokauden-suunniteltu-maara :maara)
         (assosioi :hoitokauden-suunnitellut-kustannukset :yhteensa)
         (assosioi :yksikko :yksikko)
         (laske-toteutuneet-kustannukset)
         (laske-erotus)))))

(defonce valittu-yksikkohintainen-toteuma (atom nil))

(defn hae-toteumareitit [urakka-id sopimus-id [alkupvm loppupvm] toimenpide tehtava]
  (k/post! :urakan-yksikkohintaisten-toteumien-reitit
           {:urakka-id  urakka-id
            :sopimus-id sopimus-id
            :alkupvm    alkupvm
            :loppupvm   loppupvm
            :toimenpide toimenpide
            :tehtava    tehtava}))

(def haetut-reitit
  (reaction<! [urakka-id (:id @nav/valittu-urakka)
               sopimus-id (first @urakka/valittu-sopimusnumero)
               hoitokausi @urakka/valittu-hoitokausi
               toimenpide (:tpi_id @u/valittu-toimenpideinstanssi)
               tehtava (:id @u/valittu-yksikkohintainen-tehtava)
               nakymassa? @yksikkohintaiset-tyot-nakymassa?
               kartta-nakyvissa? @nav/kartta-nakyvissa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and nakymassa? kartta-nakyvissa?)
                (hae-toteumareitit urakka-id sopimus-id hoitokausi toimenpide tehtava))))

(def karttataso-yksikkohintainen-toteuma (atom false))

(def yksikkohintainen-toteuma-kartalla
  (reaction
   (when @karttataso-yksikkohintainen-toteuma
     (kartalla-esitettavaan-muotoon
      @haetut-reitit
      @valittu-yksikkohintainen-toteuma
      nil
      (map #(assoc % :tyyppi-kartalla :toteuma))))))

;; REPL TUNKKAUSTA, toteumien piirtotyylejä varten
(def monesko? (atom 0))

(def komennot
  [
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "auraus ja sohjonpoisto"}]) @haetut-reitit)))
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "SUOLAUS"}]) @haetut-reitit)))
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "LIUOSSUOLAUS"}]) @haetut-reitit)))
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "PISTEHIEKOITUS"}]) @haetut-reitit)))
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "LINJAHIEKOITUS"}]) @haetut-reitit)))
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "PINNAN TASAUS"}]) @haetut-reitit)))
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "LUMIVALLIEN MADALTAMINEN"}]) @haetut-reitit)))
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "SULAMISVEDEN HAITTOJEN TORJUNTA"}]) @haetut-reitit)))
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "AURAUSVIITOITUS JA KINOSTIMET"}]) @haetut-reitit)))
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "LUMENSIIRTO"}]) @haetut-reitit)))
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "PAANNEJAAN POISTO"}]) @haetut-reitit)))
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "MUU"}]) @haetut-reitit)))
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "SORATEIDEN POLYNSIDONTA"}]) @haetut-reitit)))
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "SORASTUS"}]) @haetut-reitit)))
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "SORATEIDEN MUOKKAUSHOYLAYS"}]) @haetut-reitit)))
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "PAALLYSTEIDEN PAIKKAUS"}]) @haetut-reitit)))
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "PAALLYSTEIDEN JUOTOSTYOT"}]) @haetut-reitit)))
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "KONEELLINEN NIITTO"}]) @haetut-reitit)))
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "KONEELLINEN VESAKONRAIVAUS"}]) @haetut-reitit)))
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "HARJAUS"}]) @haetut-reitit)))
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "LIIKENNEMERKKIEN PUHDISTUS"}]) @haetut-reitit)))
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "L- JA P-ALUEIDEN PUHDISTUS"}]) @haetut-reitit)))
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "SILTOJEN PUHDISTUS"}]) @haetut-reitit)))
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "SORATEIDEN TASAUS"}]) @haetut-reitit)))

   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "auraus ja sohjonpoisto"} {:toimenpide "SUOLAUS"}]) @haetut-reitit)))
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "auraus ja sohjonpoisto"} {:toimenpide "SUOLAUS"} {:toimenpide "PINNAN TASAUS"}]) @haetut-reitit)))
   (fn [] (reset! haetut-reitit (map #(assoc % :tehtavat [{:toimenpide "auraus ja sohjonpoisto"} {:toimenpide "PISTEHIEKOITUS"}]) @haetut-reitit)))])

(defn vaihda! []
  ((nth komennot @monesko?))
  (swap! monesko? inc)
  (log "Toimenpide on nyt: " (get-in (first @haetut-reitit) [:tehtavat 0 :toimenpide])))
