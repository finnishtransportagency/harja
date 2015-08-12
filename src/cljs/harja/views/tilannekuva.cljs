(ns harja.views.tilannekuva
  "Harjan tilannekuvan pääsivu."
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.komponentti :as komp]))

(defn tilannekuva []
  (komp/luo
    {:component-will-mount
     (fn [] (when (or (= @nav/kartan-koko :hidden) (= @nav/kartan-koko :S)) (nav/vaihda-kartan-koko! :M)))}
    (fn []
      [:div "Tänne tulee myöhemmin tilannekuva..."])))