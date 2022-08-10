(ns harja.tiedot.urakka.varusteet-kartalla
  (:require [reagent.core :refer [atom]]
            [harja.tiedot.urakka.urakka :refer [velho-varusteet]]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]])
  (:require-macros [reagent.ratom :refer [reaction]]))

(def karttataso-varusteet (atom []))

(defonce karttataso-nakyvissa? (atom true))

(def varuste-klikattu-fn (atom (constantly nil)))

(def varuste-kartalle-xf
  (comp
    (map
      (fn [varuste] (assoc varuste :tyyppi-kartalla :varusteet-ulkoiset
                                   :on-item-click #(@varuste-klikattu-fn varuste)
                                   :avaa-paneeli? false)))))

(defonce varusteet-kartalla
  (reaction
    (let [kohteet @karttataso-varusteet]
      (when (and (not-empty kohteet) @karttataso-nakyvissa?)
        (kartalla-esitettavaan-muotoon
          kohteet
          #(= (:ulkoinen-oid %) (:ulkoinen-oid (:valittu-varuste @velho-varusteet)))
          varuste-kartalle-xf)))))

