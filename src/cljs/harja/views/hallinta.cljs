(ns harja.views.hallinta
  "Päätason sivu Hallinta, josta kaikkeen ylläpitötyöhön pääsee käsiksi."
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.ui.bootstrap :as bs]

            [harja.domain.roolit :as roolit]
            [harja.tiedot.urakka :as u]
            [harja.views.toimenpidekoodit :as tp]
            [harja.views.indeksit :as i]
            [harja.views.hallinta.lampotilat :as lampotilat]
            [harja.views.hallinta.kayttajat :as kayttajat]
            [harja.views.hallinta.integraatioloki :as integraatioloki]
            [harja.ui.grid :as g]
            ))

(def +vain-jvhn-kaytossa+ "Tämä osio on vain järjestelmän vastuuhenkilön käytössä.")

(defn hallinta []
  [bs/tabs {:style :tabs :classes "tabs-taso1" :active u/hallinnan-valittu-valilehti}

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
                     +vain-jvhn-kaytossa+)

   "Tehtävät"
   :tehtavat
   (roolit/jos-rooli roolit/jarjestelmavastuuhenkilo
                     ^{:key "tehtävät"}
                     [tp/toimenpidekoodit]
                     +vain-jvhn-kaytossa+)

   "Lämpötilat"
   :lampotilat
   (roolit/jos-rooli roolit/jarjestelmavastuuhenkilo
                     ^{:key "lämpötilat"}
                     [lampotilat/lampotilat]
                     +vain-jvhn-kaytossa+)

   "Integraatioloki"
   :integraatioloki
   (roolit/jos-rooli roolit/jarjestelmavastuuhenkilo
                     ^{:key "integraatioloki"}
                     [integraatioloki/integraatioloki]
                     +vain-jvhn-kaytossa+)])

