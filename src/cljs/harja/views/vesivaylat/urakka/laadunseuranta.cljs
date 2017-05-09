(ns harja.views.vesivaylat.urakka.laadunseuranta
  (:require [reagent.core :refer [atom]]
            [harja.ui.komponentti :as komp]
            [harja.ui.bootstrap :as bs]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.oikeudet :as oikeudet]
            [harja.views.vesivaylat.urakka.laadunseuranta.viat :as viat]
            [harja.views.vesivaylat.urakka.laadunseuranta.sanktiot :as sanktiot])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn laadunseuranta []
  (komp/luo
    (fn [{:keys [id] :as ur}]
      [bs/tabs {:style :tabs :classes "tabs-taso2"
                :active (nav/valittu-valilehti-atom :toimenpiteet)}

       "Viat" :vesivayla-viat
       (when (and (istunto/ominaisuus-kaytossa? :vesivayla)
                  (oikeudet/urakat-vesivaylalaadunseuranta-viat id))
         [viat/viat])

       "Sanktiot" :vesivayla-sanktiot
       (when (and (istunto/ominaisuus-kaytossa? :vesivayla)
                  (oikeudet/urakat-vesivaylalaadunseuranta-sanktiot id))
         [sanktiot/sanktiot])])))