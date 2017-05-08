(ns harja.views.vesivaylat.urakka.toimenpiteet
  (:require [reagent.core :refer [atom]]
            [harja.ui.komponentti :as komp]
            [harja.ui.bootstrap :as bs]
            [harja.views.vesivaylat.urakka.toimenpiteet.yksikkohintaiset :as yks-hint]
            [harja.views.vesivaylat.urakka.toimenpiteet.kokonaishintaiset :as kok-hint]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.oikeudet :as oikeudet])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn toimenpiteet []
  (komp/luo
    (fn [{:keys [id] :as ur}]
      [bs/tabs {:style :tabs :classes "tabs-taso2"
                :active (nav/valittu-valilehti-atom :toimenpiteet)}
       "Kokonaishintaiset" :kokonaishintaiset-toimenpiteet
       (when (and (istunto/ominaisuus-kaytossa? :vesivayla)
                  (oikeudet/urakat-vesivaylat-toimenpiteet-kokonaishintaiset id))
         [kok-hint/kokonaishintaiset-toimenpiteet])

       "Yksikköhintaiset" :yksikkohintaiset-toimenpiteet
       (when (and (istunto/ominaisuus-kaytossa? :vesivayla)
                  (oikeudet/urakat-vesivaylat-toimenpiteet-yksikkohintaiset id))
         [yks-hint/yksikkohintaiset-toimenpiteet])])))