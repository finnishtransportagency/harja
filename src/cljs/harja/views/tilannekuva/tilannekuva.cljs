(ns harja.views.tilannekuva.tilannekuva
  (:require [reagent.core :refer [atom]]
            [bootstrap :as bs]
            [harja.ui.komponentti :as komp]

            [harja.tiedot.tilannekuva.tilannekuva :as tilannekuva]
            [harja.views.tilannekuva.historiakuva :as historiakuva]
            [harja.views.tilannekuva.nykytilanne :as nykytilanne]
            [harja.views.kartta :as kartta]))

(defn tilannekuva []
  (komp/luo
    (komp/lippu tilannekuva/tilannekuvassa?)
    (fn []
      [:span.tilannekuva
       [bs/tabs
        {:active tilannekuva/valittu-valilehti}

        "Nykytilanne" :nykytilanne
        [nykytilanne/nykytilanne]

        "Historia" :historiakuva
        [historiakuva/historiakuva]]
       [kartta/kartan-paikka]])))