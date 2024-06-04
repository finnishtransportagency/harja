(ns harja.views.hallinta
  "Päätason sivu Hallinta, josta kaikkeen ylläpitötyöhön pääsee käsiksi."
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.ui.bootstrap :as bs]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.navigaatio :as nav]
            [harja.views.toimenpidekoodit :as tp]
            [harja.views.hallinta.indeksit :as i]
            [harja.views.hallinta.yhteydenpito :as yhteydenpito]
            [harja.views.hallinta.lampotilat :as lampotilat]
            [harja.views.hallinta.urakoiden-lyhytnimet :as lyhytnimet]
            [harja.views.hallinta.integraatiotilanne :as integraatiotilanne]
            [harja.views.hallinta.hairiot :as hairiot]
            [harja.views.hallinta.valtakunnalliset-valitavoitteet :as valitavoitteet]
            [harja.views.hallinta.api-jarjestelmatunnukset :as api-jarjestelmatunnukset]
            [harja.views.vesivaylat.hallinta :as vu]
            [harja.views.hallinta.raporttien-suoritustieto :as raporttien-suoritustieto]
            [harja.views.hallinta.jarjestelma-asetukset :as jarjestelma-asetukset]
            [harja.views.hallinta.toteumatyokalu-nakyma :as toteumatyokalu-nakyma]
            [harja.views.hallinta.tyomaapaivakirjatyokalu-nakyma :as paivakirjatyokalu-nakyma]
            [harja.views.hallinta.koulutusvideot :as koulutusvideot]
            [harja.views.hallinta.palauteluokitukset :as pl]
            [harja.views.hallinta.viestitestaus-nakyma :as viestinakyma]
            [harja.views.hallinta.urakkatiedot.tehtava-nakyma :as tehtava-nakyma]
            [harja.views.hallinta.tarjoushinnat :as tarjoushinnat]
            [harja.views.hallinta.rahavaraukset :as rahavaraukset]
            [harja.views.hallinta.rahavarausten-tehtavat :as rahavarausten-tehtavat]
            [harja.views.hallinta.urakkahenkilot :as urakkahenkilot]
            [harja.tiedot.istunto :as istunto]))

(defn hallinta []
  [bs/tabs {:style :tabs :classes "tabs-taso1"
            :active (nav/valittu-valilehti-atom :hallinta)}

   "Urakkatiedot"
   :hallinta-urakat
   ^{:key "urakoiden-hallinta"}
   [bs/tabs {:style :tabs :classes "tabs-taso2"
             :active (nav/valittu-valilehti-atom :hallinta-urakat)}

    "Indeksit"
    :indeksit
    (when (oikeudet/hallinta-indeksit)
      ^{:key "indeksit"}
      [i/indeksit-elementti])


    "Tehtävät"
    :tehtavat
    (when (oikeudet/hallinta-tehtavat)
      ^{:key "tehtävät"}
      [tp/toimenpidekoodit])


    "Välitavoitteet"
    :valtakunnalliset-valitavoitteet
    (when (oikeudet/hallinta-valitavoitteet)
      ^{:key "valtakunnalliset-valitavoitteet"}
      [valitavoitteet/valitavoitteet])

    "Urakkanimet"
    :lyhytnimet
    (when (oikeudet/hallinta-indeksit) ; ei uutta roolia roolit exceliin
      ^{:key "lyhytnimet"}
      [lyhytnimet/urakoiden-lyhytnimet])

    "Lämpötilat"
    :lampotilat
    (when (oikeudet/hallinta-lampotilat)
      ^{:key "lämpötilat"}
      [lampotilat/lampotilat])

    "Vesiväyläurakat"
    :vesivayla-hallinta
    (when (and (istunto/ominaisuus-kaytossa? :vesivayla)
            (oikeudet/hallinta-vesivaylat))
      ^{:key "vesivaylaurakat"}
      [vu/vesivayla-hallinta])

    "MHU Tehtäväryhmät ja tehtävät"
    :tehtavatjatehtavaryhmat
    (when true
      ^{:key "tehtavaryhmatjatehtavat"}
      [tehtava-nakyma/tehtavat])

    "MHU tarjoushinnat"
    :mhu-tarjoushinnat
    (when (oikeudet/hallinta-tarjoushinnat)
      ^{:key "mhu-tarjoushinnat"}
      [tarjoushinnat/tarjoushinnat])

    "Rahavaraukset"
    :rahavaraukset
    (when (oikeudet/hallinta-rahavaraukset)
      ^{:key "rahavaraukset"}
      [rahavaraukset/rahavaraukset])

    "Rahavarausten tehtävät"
    :rahavarausten-tehtavat
    (when (oikeudet/hallinta-rahavaraukset)
      ^{:key "rahavarausten-tehtavat"}
      [rahavarausten-tehtavat/rahavarausten-tehtavat])

    "Urakoiden henkilöt"
    :urakkahenkilot
    (when (oikeudet/hallinta-urakkahenkilot)
      ^{:key "urakkahenkilot"}
      [urakkahenkilot/urakkahenkilot])]

   "Seuranta"
   :hallinta-seuranta
   ^{:key "seuranta"}
   [bs/tabs {:style :tabs :classes "tabs-taso2"
             :active (nav/valittu-valilehti-atom :hallinta-seuranta)}

    "Integraatiotilanne"
    :integraatiotilanne
    (when (oikeudet/hallinta-integraatiotilanne)
      ^{:key "integraatiotilanne"}
      [integraatiotilanne/integraatiotilanne])


    "Raporttitiedot"
    :raporttitiedot
    (when (oikeudet/hallinta-indeksit)
      ^{:key "raporttien-suoritustieto"}
      [raporttien-suoritustieto/raporttien-suoritustieto])]

   "Järjestelmän hallinta"
   :hallinta-jarjestelma
   ^{:key "jarjestelma"}
   [bs/tabs {:style :tabs :classes "tabs-taso2"
             :active (nav/valittu-valilehti-atom :hallinta-jarjestelma)}

    "API-järjestelmätunnukset"
    :api-jarjestelmatunnukset
    (when (oikeudet/hallinta-api-jarjestelmatunnukset)
      ^{:key "jarjestelmatunnukset"}
      [api-jarjestelmatunnukset/api-jarjestelmatunnukset-paakomponentti])

    "Järjestelmäasetukset"
    :jarjestelma-asetukset
    (when true
      ^{:key "jarjestelma-asetukset"}
      [jarjestelma-asetukset/jarjestelma-asetukset])

    "Koulutusvideot"
    :koulutusvideot
    (when (oikeudet/voi-kirjoittaa? oikeudet/hallinta-koulutusvideot)
      ^{:key "koulutusvideot"}
      [koulutusvideot/nakyma])

    "Palauteluokitukset"
    :palauteluokitukset
    (when (oikeudet/hallinta-palautevayla)
      ^{:key "palauteluokitukset"}
      [pl/palauteluokitukset])]

   "Työkalut"
   :hallinta-tyokalut
   ^{:key "tyokalut"}
   [bs/tabs {:style :tabs :classes "tabs-taso2"
             :active (nav/valittu-valilehti-atom :hallinta-tyokalut)}

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

    "Toteumatyökalu"
    :toteumatyokalu
    (when (and (istunto/ominaisuus-kaytossa? :toteumatyokalu)
            (oikeudet/voi-kirjoittaa? oikeudet/hallinta-toteumatyokalu))
      ^{:key "toteumatyokalu"}
      [toteumatyokalu-nakyma/simuloi-toteuma])

    "Työmaapäiväkirjatyökalu"
    :tyomaapaivakirjatyokalu
    (when (and (istunto/ominaisuus-kaytossa? :toteumatyokalu)
            (oikeudet/voi-kirjoittaa? oikeudet/hallinta-toteumatyokalu))
      ^{:key "tyomaapaivakirjatyokalu"}
      [paivakirjatyokalu-nakyma/simuloi-tyomaapaivakirja])

    "Viestitestaus"
    :viestitestaus
    (when true
      ^{:key "viestitestaus"}
      [viestinakyma/viestitestaus])]])

