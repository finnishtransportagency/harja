(ns harja.views.urakka.suunnittelu.tehtavat
  (:require [reagent.core :as r]
            [tuck.core :as t]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.ui.taulukko.taulukko :as taulukko]
            [harja.ui.taulukko.jana :as jana]
            [harja.ui.taulukko.osa :as osa]))

(defrecord tehtava-ja-maararivi [kokonaisuus taso hinta arvio id]
  jana/Jana
  (piirra-jana [this])
  (janan-id? [this id])
  (janan-osat [this]))

(defn tehtavat*
  [e! app]
  (let [{{tehtavat :tehtavat} :suunnittelu} app]
    [taulukko/taulukko tehtavat]))

(defn tehtavat []
  (do (swap! tila/tila assoc-in [:suunnittelu :tehtavat]
             [(jana/->Rivi "rivin-id" [(osa/->Teksti 1 "vasen" nil) (osa/->Teksti 2 "keski" nil) (osa/->Teksti 3 "oikea" nil)] nil)])
    (fn []
      (t/tuck tila/tila tehtavat*))))