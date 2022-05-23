(ns harja.tiedot.urakka.varusteet-kartalla
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.kartta.ikonit :as kartta-ikonit]
            [harja.ui.kartta.varit.puhtaat :as puhtaat]
            [harja.ui.kartta.varit.alpha :as alpha])
  (:require-macros [reagent.ratom :refer [reaction]]))

(def toteuma-vari {"lisatty" alpha/fig-default
                   "paivitetty" alpha/lemon-default
                   "poistettu" alpha/eggplant-default
                   "tarkastus" alpha/pitaya-default
                   "korjaus" alpha/pea-default
                   "puhdistus" alpha/black-light})

(def karttataso-varusteet (atom []))

(defonce karttataso-nakyvissa? (atom true))

(defonce varusteet-kartalla
         (reaction
           (let [kohteet @karttataso-varusteet]
             (when (and (not-empty kohteet) @karttataso-nakyvissa?)
               (with-meta (mapv (fn [{:keys [toteuma] :as kohde}]
                                  (when (:sijainti kohde)
                                    (let [vari (get toteuma-vari toteuma "white")]
                                      {:alue (merge {:stroke {:width 8
                                                              :color vari}}
                                                    (:sijainti kohde))
                                       :selite {:teksti toteuma
                                                :img (kartta-ikonit/pinni-ikoni "sininen")}
                                       :ikonit [{:tyyppi :merkki
                                                 :paikka [:loppu]
                                                 :zindex 21
                                                 :img (kartta-ikonit/pinni-ikoni "sininen")}]})))
                                kohteet)
                          {:selitteet [{:teksti "Lisätty" :vari puhtaat/fig-default}
                                       {:teksti "Poistettu" :vari puhtaat/eggplant-default}
                                       {:teksti "Tarkistettu" :vari puhtaat/pitaya-default}
                                       {:teksti "Puhdistettu" :vari puhtaat/black-light}
                                       {:teksti "Korjattu" :vari puhtaat/pea-default}
                                       {:teksti "Päivitetty" :vari puhtaat/lemon-default}]})))))

