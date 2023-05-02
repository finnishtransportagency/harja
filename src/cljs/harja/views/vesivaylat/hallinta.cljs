(ns harja.views.vesivaylat.hallinta
  (:require [harja.ui.bootstrap :as bs]
            [harja.tiedot.navigaatio :as nav]
            [harja.views.vesivaylat.hallinta.urakoiden-luonti :as vu]
            [harja.views.vesivaylat.hallinta.hankkeiden-luonti :as vhu]
            [harja.views.vesivaylat.hallinta.urakoitsijoiden-luonti :as vuu]
            [harja.views.vesivaylat.hallinta.sopimuksien-luonti :as vsu]
            [harja.views.vesivaylat.hallinta.liikennetapahtumien-ketjutus :as vsl]
            [harja.views.kanavat.hallinta.kohteiden-luonti :as kohteiden-luonti]
            [harja.views.kanavat.hallinta.huoltokohteiden-hallinta :as huoltokohteiden-hallinta]

            [harja.tiedot.istunto :as istunto]
            [harja.domain.oikeudet :as oikeudet]))

(defn vesivayla-hallinta
  []
  [bs/tabs {:style :tabs :classes "tabs-taso2"
            :active (nav/valittu-valilehti-atom :vesivayla-hallinta)}
   "Sopimuksien luonti"
   :vesivaylasopimuksien-luonti
   (when (and (istunto/ominaisuus-kaytossa? :vesivayla)
           (oikeudet/hallinta-vesivaylat))
     ^{:key "vesivaylasopimukset"}
     [vsu/vesivaylasopimuksien-luonti])

   "Urakoitsijoiden luonti"
   :vesivaylaurakoitsijoiden-luonti
   (when (and (istunto/ominaisuus-kaytossa? :vesivayla)
           (oikeudet/hallinta-vesivaylat))
     ^{:key "vesivaylaurakoitsijat"}
     [vuu/vesivaylaurakoitsijoiden-luonti])

   "Urakoiden luonti"
   :vesivaylaurakoiden-luonti
   (when (and (istunto/ominaisuus-kaytossa? :vesivayla)
           (oikeudet/hallinta-vesivaylat))
     ^{:key "vesivaylaurakat"}
     [vu/vesivaylaurakoiden-luonti])

   "Kohteiden luonti"
   :kanavaurakoiden-kohteiden-luonti
   (when (and (istunto/ominaisuus-kaytossa? :vesivayla)
           (oikeudet/hallinta-kanavat))
     ^{:key "kohteiden-luonti"}
     [kohteiden-luonti/kohteiden-luonti])

   "Liikennetapahtumien ketjutus"
   :vesivaylasopimuksien-liikenne-ketjutus
   (when (and (istunto/ominaisuus-kaytossa? :vesivayla)
           (oikeudet/hallinta-vesivaylat))
     ^{:key "vesivaylaketjutus"}
     [vsl/liikennetapahtumien-ketjutus])

   ;"Huoltokohteiden hallinta"
   ;:kanavien-huoltokohteet
   #_(when (and (istunto/ominaisuus-kaytossa? :vesivayla)
             (oikeudet/hallinta-kanavat))
       ^{:key "huoltokohteiden-hallinta"}
       [huoltokohteiden-hallinta/hallinta])])
