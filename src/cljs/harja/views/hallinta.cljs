(ns harja.views.hallinta
  "Päätason sivu Hallinta, josta kaikkeen ylläpitötyöhön pääsee käsiksi."
  (:require [reagent.core :refer [atom] :as reagent]
            [bootstrap :as bs]

            [harja.domain.roolit :as roolit]
            [harja.tiedot.istunto :as istunto]
            [harja.views.toimenpidekoodit :as tp]
            [harja.views.indeksit :as i]
            [harja.views.hallinta.kayttajat :as kayttajat]
            [harja.ui.grid :as g]
            ))

(def valittu-valilehti "Valittu välilehti" (atom :kayttajat))

(defn hallinta []
  ;; FIXME: miten hallinta valitaa, "linkkejä" vai tabeja vai jotain muuta?

   [bs/tabs {:style :tabs :active valittu-valilehti}

    "Käyttäjät"
    :kayttajat
    ^{:key "kayttajat"}
    (if (istunto/saa-hallita-kayttajia?)
                            [kayttajat/kayttajat]
                        "Ei käyttöoikeutta tähän osioon.")

    "Indeksit"
    :indeksit
    (roolit/jos-rooli roolit/jarjestelmavastuuhenkilo
                       ^{:key "indeksit"}
                       [i/indeksit-elementti]
    "Tämä osio on vain järjestelmän vastuuhenkilön käytössä.")
    
    "Tehtävät"
    :tehtavat
    (roolit/jos-rooli roolit/jarjestelmavastuuhenkilo
    ^{:key "tehtävät"}
    [tp/toimenpidekoodit]
    "Tämä osio on vain järjestelmän vastuuhenkilön käytössä.")])

