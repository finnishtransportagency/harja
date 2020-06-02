(ns harja.views.hallinta
  "Päätason sivu Hallinta, josta kaikkeen ylläpitötyöhön pääsee käsiksi."
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.ui.bootstrap :as bs]

            [harja.domain.roolit :as roolit]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.views.toimenpidekoodit :as tp]
            [harja.views.hallinta.indeksit :as i]
            [harja.views.hallinta.yhteydenpito :as yhteydenpito]
            [harja.views.hallinta.lampotilat :as lampotilat]
            [harja.views.hallinta.integraatiotilanne :as integraatiotilanne]
            [harja.views.hallinta.hairiot :as hairiot]
            [harja.views.hallinta.valtakunnalliset-valitavoitteet :as valitavoitteet]
            [harja.views.hallinta.api-jarjestelmatunnukset :as api-jarjestelmatunnukset]
            [harja.views.vesivaylat.hallinta :as vu]
            [harja.views.hallinta.jarjestelma-asetukset :as jarjestelma-asetukset]
            [harja.ui.grid :as g]
            [harja.tiedot.istunto :as istunto]))

(defn hallinta []
  [bs/tabs {:style :tabs :classes "tabs-taso1"
            :active (nav/valittu-valilehti-atom :hallinta)}

   "Indeksit"
   :indeksit
   (when (oikeudet/hallinta-indeksit)
     ^{:key "indeksit"}
     [i/indeksit-elementti])

   "Tehtävät ja määrät"
   :tehtavat
   (when (oikeudet/hallinta-tehtavat)
     ^{:key "tehtävät"}
     [tp/toimenpidekoodit])

   "Välitavoitteet"
   :valtakunnalliset-valitavoitteet
   (when (oikeudet/hallinta-valitavoitteet)
     ^{:key "valtakunnalliset-valitavoitteet"}
     [valitavoitteet/valitavoitteet])

   "Lämpötilat"
   :lampotilat
   (when (oikeudet/hallinta-lampotilat)
     ^{:key "lämpötilat"}
     [lampotilat/lampotilat])

   "Integraatiotilanne"
   :integraatiotilanne
   (when (oikeudet/hallinta-integraatiotilanne)
     ^{:key "integraatiotilanne"}
     [integraatiotilanne/integraatiotilanne])

   "Yhteydenpito"
   :yhteydenpito
   (when (oikeudet/hallinta-yhteydenpito)
     ^{:key "yhteydenpito"}
     [yhteydenpito/yhteydenpito])

   "Häiriöilmoitukset"
   :hairioilmoitukset
   (when (oikeudet/hallinta-hairioilmoitukset)
     ^{:key "integraatioloki"}
     [hairiot/hairiot])

   "API-järjestelmätunnukset"
   :api-jarjestelmatunnukset
   (when (oikeudet/hallinta-api-jarjestelmatunnukset)
     ^{:key "jarjestelmatunnukset"}
     [api-jarjestelmatunnukset/api-jarjestelmatunnukset-paakomponentti])

   "Vesiväyläurakat"
   :vesivayla-hallinta
   (when (and (istunto/ominaisuus-kaytossa? :vesivayla)
              (oikeudet/hallinta-vesivaylat))
     ^{:key "vesivaylaurakat"}
     [vu/vesivayla-hallinta])

   "Järjestelmäasetukset"
   :jarjestelma-asetukset
   (when true
     ^{:key "jarjestelma-asetukset"}
     [jarjestelma-asetukset/jarjestelma-asetukset])])
