(ns harja.views.hallinta
  "Päätason sivu Hallinta, josta kaikkeen ylläpitötyöhön pääsee käsiksi."
  (:require [reagent.core :refer [atom] :as reagent]
            [bootstrap :as bs]
            
            [harja.tiedot.istunto :as istunto]
            [harja.views.toimenpidekoodit :as tp]
            [harja.views.indeksit :as i]
            [harja.views.hallinta.kayttajat :as kayttajat]
            [harja.ui.grid :as g]
            ))


(defn hallinta []
  ;; FIXME: miten hallinta valitaa, "linkkejä" vai tabeja vai jotain muuta?

   [bs/tabs {}

    "Käyttäjät"
    ^{:key "kayttajat"}
    (if (istunto/saa-hallita-kayttajia?)
                            [kayttajat/kayttajat]
                        "Ei käyttöoikeutta tähän osioon.")

    "Indeksit"
    (istunto/jos-rooli istunto/rooli-jarjestelmavastuuhenkilo
                       ^{:key "indeksit"}
                       [i/indeksit-elementti]
    "Tämä osio on vain järjestelmän vastuuhenkilön käytössä.")
    
    "Tehtävät"
    (istunto/jos-rooli istunto/rooli-jarjestelmavastuuhenkilo
    ^{:key "tehtävät"}
    [tp/toimenpidekoodit]
    "Tämä osio on vain järjestelmän vastuuhenkilön käytössä.")])

