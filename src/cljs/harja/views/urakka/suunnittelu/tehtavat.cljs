(ns harja.views.urakka.suunnittelu.tehtavat
  (:require [reagent.core :as r]
            [tuck.core :as t]
            [harja.ui.debug :as debug]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.ui.taulukko.taulukko :as taulukko]
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.taulukko.jana :as jana]
            [harja.ui.taulukko.osa :as osa]))

#_(defrecord tehtava-ja-maararivi [kokonaisuus taso hinta arvio id]
  jana/Jana
  (piirra-jana [this])
  (janan-id? [this id])
  (janan-osat [this]))

(defn tehtavat*
  [e! app]
  (let [{{tehtavat :tehtavat} :suunnittelu} app]
    [:div
     [debug/debug app]
     [taulukko/taulukko tehtavat]]))

(defn tehtavat []
  (let [auki-rivit [(jana/->Rivi "rivin-id-3" [(osa/->Teksti 1 "vasen" nil) (osa/->Teksti 2 "keski" nil) (osa/->Teksti 3 "oikea" nil)] nil)]]
    (swap! tila/tila assoc-in [:suunnittelu :tehtavat]
           [(jana/->Rivi "rivin-id" [(osa/->Teksti 1 "vasen" nil) (osa/->Teksti 2 "keski" nil) (osa/->Teksti 3 "oikea" nil)] nil)
            (jana/->Rivi "rivin-id-2" [(osa/->Laajenna 3 "Laajenna" (fn [this auki?]
                                                                      (swap! tila/tila update-in [:suunnittelu :tehtavat]
                                                                             (fn [tila]
                                                                               (if auki?
                                                                                 (reduce (fn [uudet-rivit rivi]
                                                                                           (println (:janan-id rivi))
                                                                                           (if (= (p/osan-janan-id this)
                                                                                                  (:janan-id rivi))
                                                                                             (into []
                                                                                                   (concat (conj uudet-rivit
                                                                                                                 rivi)
                                                                                                           auki-rivit))
                                                                                             (conj uudet-rivit rivi)))
                                                                                         [] tila)
                                                                                 (reduce (fn [uudet-rivit rivi]
                                                                                           (if (some #(p/janan-id? % (:janan-id rivi)) auki-rivit)
                                                                                             uudet-rivit
                                                                                             (conj uudet-rivit rivi)))
                                                                                         [] tila))))))] nil)])
    (fn []
      (t/tuck tila/tila tehtavat*))))