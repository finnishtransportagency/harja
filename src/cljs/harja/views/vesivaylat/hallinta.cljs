(ns harja.views.vesivaylat.hallinta
  (:require [harja.ui.bootstrap :as bs]
            [harja.tiedot.navigaatio :as nav]
            [harja.views.vesivaylat.urakoiden-luonti :as vu]
            [harja.views.vesivaylat.hankkeiden-luonti :as vhu]
            [harja.views.vesivaylat.urakoitsijoiden-luonti :as vuu]
            [harja.views.vesivaylat.sopimuksien-luonti :as vsu]
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
   
   ;; Hankkeet luodaan nykyään urakan kanssa automaattisesti.
   ;; Otetaan näkymä käyttöön jos sille vielä tarve tulee.
   ; "Hankkeiden luonti"
   ; :vesivaylahankkeiden-luonti
   ; (when (and (istunto/ominaisuus-kaytossa? :vesivayla)
   ;            (oikeudet/hallinta-vesivaylat))
   ;   ^{:key "vesivaylahankkeet"}
   ;   [vhu/vesivaylahankkeiden-luonti])

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
     [vu/vesivaylaurakoiden-luonti])])
