(ns harja.tiedot.urakka.varusteet-kartalla
  (:require [harja.ui.kartta.ikonit :as kartta-ikonit]
            [harja.ui.kartta.varit.puhtaat :as puhtaat])
  (:require-macros [reagent.ratom :refer [reaction]]))


(def karttataso-varusteet (atom nil))
(defonce karttataso-nakyvissa? (atom true))
;; Tehdään set, jossa on määriteltynä mitä kohteita kartalla näytetään
;; Mikäli mitään ei ole valittu, näytetään kaikki
(defonce valitut-kohteet-atom (atom #{}))

(defonce varusteet-kartalla
         (reaction
           (let [kohteet @karttataso-varusteet]
             (when (and (not-empty kohteet) @karttataso-nakyvissa?)
               (with-meta (mapv (fn [kohde]
                                  (when (:sijainti kohde)
                                    {:alue (merge {:stroke {:width 8
                                                            :color "red"}}
                                                  (:sijainti kohde))
                                     :selite {:teksti "Ömmööömöö"
                                              :img (kartta-ikonit/pinni-ikoni "sininen")}
                                     :ikonit [{:tyyppi :merkki
                                               :paikka [:loppu]
                                               :zindex 21
                                               :img (kartta-ikonit/pinni-ikoni "sininen")}]}))
                                kohteet)
                          {:selitteet [{:vari (map :color [{:color puhtaat/musta-raja
                                                            :width 8}
                                                           {:color puhtaat/vihrea
                                                            :width 6}])
                                        :teksti "Öööt"}]})))))

