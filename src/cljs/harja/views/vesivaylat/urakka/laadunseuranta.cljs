(ns harja.views.vesivaylat.urakka.laadunseuranta
  (:require [reagent.core :refer [atom]]
            [harja.ui.komponentti :as komp]
            [harja.ui.bootstrap :as bs]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.oikeudet :as oikeudet]
            [harja.views.vesivaylat.urakka.laadunseuranta.tarkastukset :as tarkastukset]
            [harja.views.vesivaylat.urakka.laadunseuranta.laatupoikkeamat :as laatupoikkeamat]
            [harja.views.urakka.laadunseuranta.sanktiot :as sanktiot]
            [harja.views.vesivaylat.urakka.laadunseuranta.viat :as viat])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn laadunseuranta []
  (komp/luo
    (fn [{:keys [id tyyppi] :as ur}]
      [bs/tabs {:style :tabs :classes "tabs-taso2"
                :active (nav/valittu-valilehti-atom :toimenpiteet)}

       "Tarkastukset" :vesivayla-tarkastukset
       (when (and (istunto/ominaisuus-kaytossa? :vesivayla)
                  #_(oikeudet/urakat-vesivaylalaadunseuranta-tarkastukset id)) ; TODO OIKEUS!
         [tarkastukset/tarkastukset])

       "Laatupoikkeamat" :vesivayla-laatupoikkeamat
       (when (and (istunto/ominaisuus-kaytossa? :vesivayla)
                  #_(oikeudet/urakat-vesivaylalaadunseuranta-laatupoikkeamat id)) ; TODO OIKEUS
         [laatupoikkeamat/laatupoikkeamat])

       "Vikaseuranta" :vesivayla-viat
       (when (and (istunto/ominaisuus-kaytossa? :vesivayla)
                  (oikeudet/urakat-vesivaylalaadunseuranta-viat id))
         [viat/viat])

       "Sanktiot" :vesivayla-sanktiot
       (when (and (istunto/ominaisuus-kaytossa? :vesivayla)
                  (oikeudet/urakat-vesivaylalaadunseuranta-sanktiot id))
         [sanktiot/sanktiot {:nakyma tyyppi}])])))