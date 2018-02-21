(ns harja.tiedot.kanavat.kohteet-kartalla
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [harja.tiedot.kanavat.urakka.kanavaurakka :as kanavaurakka]
            [harja.tiedot.kanavat.urakka.toimenpiteet.kokonaishintaiset :as kokonaishintaiset]
            [harja.tiedot.kanavat.urakka.toimenpiteet.muutos-ja-lisatyot :as lisatyot]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavan-toimenpide]
            [harja.domain.kanavat.kanavan-huoltokohde :as kanavan-huoltokohde]
            [harja.domain.kanavat.kohteenosa :as kohteenosa]
            [harja.domain.kayttaja :as kayttaja]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [clojure.set :as set]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defonce karttataso-kohteet (atom false))

(defonce aktiivinen-nakyma
  (reaction
    (let [kokonaishintaiset-nakymassa? (:nakymassa? @kokonaishintaiset/tila)
          lisatyot-nakymassa? (:nakymassa? @lisatyot/tila)]
      (zipmap [:tila :nakyma]
              (cond
                kokonaishintaiset-nakymassa? [@kokonaishintaiset/tila :kokonaishintaiset]
                lisatyot-nakymassa? [@lisatyot/tila :lisatyot])))))

(defn- kohde-valittu? [kohde]
  (= (::kohde/id kohde) (-> @aktiivinen-nakyma :tila :avattu-toimenpide ::kanavan-toimenpide/kohde ::kohde/id)))

(defn- kohde-on-gridissa? [kohde toimenpiteet]
  (some #(= (::kohde/id kohde) (-> % ::kanavan-toimenpide/kohde ::kohde/id))
        toimenpiteet))

(defonce naytettavat-kanavakohteet
  (reaction
    (let [{:keys [toimenpiteet avattu-toimenpide nakymassa?]} (:tila @aktiivinen-nakyma)
          lomakkeella? (boolean avattu-toimenpide)
          kokonaishintaiset-nakymassa? nakymassa?
          ;; Yhdistetään kohteen ja kohteelle tehdyn toimenpiteen tiedot. Toimenpiteen tietoja näytetään kartan
          ;; infopaneelissa.
          kohteet (flatten (map (fn [kohde]
                                  (let [kohteen-toimenpiteet (keep #(when (= (-> % ::kanavan-toimenpide/kohde ::kohde/id) (::kohde/id kohde))
                                                                      {:huoltokohde (-> % ::kanavan-toimenpide/huoltokohde ::kanavan-huoltokohde/nimi)
                                                                       :kohteenosan-tyyppi (when-let [tyyppi (-> % ::kanavan-toimenpide/kohteenosa ::kohteenosa/tyyppi)]
                                                                                             (name tyyppi))
                                                                       :kuittaaja (str (-> % ::kanavan-toimenpide/kuittaaja ::kayttaja/etunimi) " "
                                                                                       (-> % ::kanavan-toimenpide/kuittaaja ::kayttaja/sukunimi))
                                                                       :lisatieto (::kanavan-toimenpide/lisatieto %)
                                                                       :muu-toimenpide (::kanavan-toimenpide/muu-toimenpide %)
                                                                       :pvm (pvm/pvm (::kanavan-toimenpide/pvm %))
                                                                       :suorittaja (::kanavan-toimenpide/suorittaja %)
                                                                       :toimenpide (-> % ::kanavan-toimenpide/toimenpidekoodi ::toimenpidekoodi/nimi)})
                                                                   toimenpiteet)]
                                    (map #(assoc kohde :toimenpiteet %)
                                         kohteen-toimenpiteet)))
                                @kanavaurakka/kanavakohteet))]
      (reduce (fn [kasitellyt kasiteltava]
                (cond
                  ;; Jos ollaan lomakkeella, näytetään kaikki kohteet
                  lomakkeella? (conj kasitellyt kasiteltava)
                  ;; Jos ollaan gridinäkymässä, niin näytetään vain ne kohteet, joille on tehty toimenpiteitä
                  (and kokonaishintaiset-nakymassa? (kohde-on-gridissa? kasiteltava toimenpiteet)) (conj kasitellyt kasiteltava)
                  :else kasitellyt))
              [] kohteet))))

(defonce kohteet-kartalla
  (reaction
    (when @karttataso-kohteet
      (kartalla-esitettavaan-muotoon
        (map #(-> %
                  (set/rename-keys {::kohde/sijainti :sijainti})
                  (assoc :tyyppi-kartalla :kohde)
                  (dissoc ::kohde/kohteenosat ::kohde/kohdekokonaisuus ::kohde/urakat))
             @naytettavat-kanavakohteet)
        kohde-valittu?))))
