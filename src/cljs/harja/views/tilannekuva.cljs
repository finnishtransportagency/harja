(ns harja.views.tilannekuva
  "Harjan tilannekuvan pääsivu."
  (:require [reagent.core :refer [atom]]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.tilannekuva :as tiedot]))

(defn tilannekuva []
  (komp/luo
    {:component-will-mount
     (fn [] (when (or (= @nav/kartan-koko :hidden) (= @nav/kartan-koko :S)) (nav/vaihda-kartan-koko! :M)))}
    (fn []
      [:div "Tänne tulee myöhemmin tilannekuva..."])))