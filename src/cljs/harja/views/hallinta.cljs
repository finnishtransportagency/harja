(ns harja.views.hallinta
  "Päätason sivu Hallinta, josta kaikkeen ylläpitötyöhön pääsee käsiksi."
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.ui.bootstrap :as bs]

            [harja.domain.roolit :as roolit]
            [harja.views.toimenpidekoodit :as tp]
            [harja.views.indeksit :as i]
            [harja.views.hallinta.kayttajat :as kayttajat]
            [harja.views.hallinta.integraatioloki :as integraatioloki]
            [harja.ui.grid :as g]
            ))

(def valittu-valilehti "Valittu välilehti" (atom :kayttajat))

(defn hallinta []
  ;; FIXME: miten hallinta valitaa, "linkkejä" vai tabeja vai jotain muuta?

  [bs/tabs {:style :tabs :classes "tabs-taso1" :active valittu-valilehti}

   "Käyttäjät"
   :kayttajat
   ^{:key "kayttajat"}
   (roolit/jos-rooli roolit/kayttajien-hallinta
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
                     "Tämä osio on vain järjestelmän vastuuhenkilön käytössä.")

   "Integraatioloki"
   :integraatioloki
   (roolit/jos-rooli roolit/jarjestelmavastuuhenkilo
                     ^{:key "integraatioloki"}
                     [integraatioloki/integraatioloki]
                     "Tämä osio on vain järjestelmän vastuuhenkilön käytössä.")
   ])

