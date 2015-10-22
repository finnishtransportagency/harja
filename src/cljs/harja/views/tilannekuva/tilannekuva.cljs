(ns harja.views.tilannekuva.tilannekuva
  (:require [reagent.core :refer [atom]]
            [harja.ui.bootstrap :as bs]
            [harja.ui.komponentti :as komp]

            [harja.tiedot.tilannekuva.tilannekuva :as tilannekuva]
            [harja.views.tilannekuva.historiakuva :as historiakuva]
            [harja.views.tilannekuva.nykytilanne :as nykytilanne]
            [harja.views.kartta :as kartta]))

(defn tilannekuva []
  (komp/luo
    (komp/lippu tilannekuva/tilannekuvassa?)
    (komp/sisaan-ulos #(reset! kartta/pida-geometriat-nakyvilla? false) #(reset! kartta/pida-geometriat-nakyvilla? true))
    (fn []
      [:span.tilannekuva
       [bs/tabs
        {:style :tabs :classes "tabs-taso1" :active tilannekuva/valittu-valilehti}

        "Nykytilanne" :nykytilanne
        [nykytilanne/nykytilanne]

        "Historia" :historiakuva
        [historiakuva/historiakuva]]
       [kartta/kartan-paikka]])))