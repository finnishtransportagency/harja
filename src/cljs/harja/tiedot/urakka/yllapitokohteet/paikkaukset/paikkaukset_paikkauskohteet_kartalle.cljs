(ns harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet-kartalle
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log]]
            [taoensso.timbre :as log]
            [harja.domain.paikkaus :as paikkaus]
            [harja.ui.kartta.esitettavat-asiat :refer [maarittele-feature kartalla-esitettavaan-muotoon]]
            [harja.ui.kartta.asioiden-ulkoasu :as asioiden-ulkoasu]
            [harja.ui.kartta.ikonit :refer [sijainti-ikoni pinni-ikoni nuoli-ikoni]]
            [harja.tiedot.urakka.urakka :as tila])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))


(def karttataso-paikkauskohteet (atom []))
(defonce karttataso-nakyvissa? (atom true))
;; Tehdään set, jossa on määriteltynä mitä kohteita kartalla näytetään
;; Mikäli mitään ei ole valittu, näytetään kaikki
(defonce valitut-kohteet-atom (atom #{}))

(defonce paikkauskohteet-kartalla
         (reaction
           (let [;; Näytä vain valittu kohde kartalla
                 valitut-kohteet (if (= (count @valitut-kohteet-atom) 0)
                                   nil
                                   @valitut-kohteet-atom)
                 kohteet (keep (fn [kohde]
                                 (when
                                   (and (or (nil? valitut-kohteet)
                                            (contains? valitut-kohteet (:id kohde)))
                                        (:sijainti kohde))
                                   kohde))
                               @karttataso-paikkauskohteet)
                 tyomenetelmat (:tyomenetelmat @tila/paikkauskohteet)]
             (when (and (not-empty kohteet) @karttataso-nakyvissa?)
               (with-meta (mapv (fn [kohde]
                                  (when (:sijainti kohde)
                                    {:alue (merge {:tyyppi-kartalla :paikkaukset-paikkauskohteet
                                                   :stroke {:width 8
                                                            :color (asioiden-ulkoasu/tilan-vari (:paikkauskohteen-tila kohde))}}
                                                  (:sijainti kohde))
                                     :tyyppi-kartalla :paikkaukset-paikkauskohteet
                                     :selite {:teksti "Paikkauskohde"
                                              :img (pinni-ikoni "sininen")}
                                     :infopaneelin-tiedot {:ulkoinen-id (:ulkoinen-id kohde)
                                                           :nimi (:nimi kohde)
                                                           :tila (:paikkauskohteen-tila kohde)
                                                           :menetelma (paikkaus/tyomenetelma-id->nimi (:tyomenetelma kohde) tyomenetelmat)
                                                           :aikataulu (:formatoitu-aikataulu kohde)
                                                           :alkupvm (:alkupvm kohde)}
                                     :ikonit [{:tyyppi :merkki
                                               :paikka [:loppu]
                                               :zindex 21
                                               :img (pinni-ikoni "sininen")}]}))
                                kohteet)
                          {:selitteet [{:vari (map :color asioiden-ulkoasu/paikkaukset)
                                        :teksti "Paikkauskohteet"}]})))))