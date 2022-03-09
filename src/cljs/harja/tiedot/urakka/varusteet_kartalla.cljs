(ns harja.tiedot.urakka.varusteet-kartalla
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.kartta.ikonit :as kartta-ikonit]
            [harja.ui.kartta.varit.puhtaat :as puhtaat])
  (:require-macros [reagent.ratom :refer [reaction]]))


(def karttataso-varusteet (atom nil))
(defonce karttataso-nakyvissa? (atom true))

(defonce varusteet-kartalla
         (reaction
           (let [kohteet @karttataso-varusteet]
             (println "petar kohteet: " kohteet)
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

