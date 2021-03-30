(ns harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet-kartalle
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log]]
            [taoensso.timbre :as log]
            [harja.domain.paikkaus :as paikkaus]
            [harja.ui.kartta.esitettavat-asiat :refer [maarittele-feature kartalla-esitettavaan-muotoon]]
            [harja.ui.kartta.asioiden-ulkoasu :as asioiden-ulkoasu]
            [harja.ui.kartta.ikonit :refer [sijainti-ikoni pinni-ikoni nuoli-ikoni]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defn tila->vari [tila]
  (let [vari (case tila
               "ehdotettu" "#f1b371"
               "tilattu" "#274ac6"
               "valmis" "#58a006"
               "hylatty" "#B40A14"
               :default "#f1b371")]
    vari))
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
                               @karttataso-paikkauskohteet)]
             (when (and (not-empty kohteet) @karttataso-nakyvissa?)
               (with-meta (mapv (fn [kohde]
                                  (when (:sijainti kohde)
                                    {:alue (merge {:tyyppi-kartalla :paikkaukset-paikkauskohteet
                                                   :stroke {:width 8
                                                            :color (tila->vari (:paikkauskohteen-tila kohde))}}
                                                  (:sijainti kohde))
                                     :tyyppi-kartalla :paikkaukset-paikkauskohteet
                                     :selite {:teksti "Paikkauskohde"
                                              :img (pinni-ikoni "sininen")}
                                     :infopaneelin-tiedot {:nro (:nro kohde)
                                                           :nimi (:nimi kohde)
                                                           :tila (:paikkauskohteen-tila kohde)
                                                           :menetelma (paikkaus/paikkauskohteiden-tyomenetelmat (:tyomenetelma kohde))
                                                           :aikataulu (:formatoitu-aikataulu kohde)
                                                           :alkupvm (:alkupvm kohde)}
                                     :ikonit [{:tyyppi :merkki
                                               :paikka [:loppu]
                                               :zindex 21
                                               :img (pinni-ikoni "sininen")}]}))
                                kohteet)
                          {:selitteet [{:vari (map :color asioiden-ulkoasu/paikkaukset)
                                        :teksti "Paikkauskohteet"}]})))))