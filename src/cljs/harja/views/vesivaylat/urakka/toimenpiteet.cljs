(ns harja.views.vesivaylat.urakka.toimenpiteet
  (:require [reagent.core :refer [atom]]
            [harja.ui.komponentti :as komp]
            [harja.ui.bootstrap :as bs]
            [harja.views.vesivaylat.urakka.toimenpiteet.yksikkohintaiset :as yks-hint]
            [harja.views.vesivaylat.urakka.toimenpiteet.kokonaishintaiset :as kok-hint]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn toimenpiteet []
  (komp/luo
    (fn [{:keys [id] :as ur}]
      [bs/tabs {:style :tabs :classes "tabs-taso2"
                :active (nav/valittu-valilehti-atom :toimenpiteet)}

       "Kokonaishintaiset" :kokonaishintaiset-toimenpiteet
       (when #_(oikeudet/urakat-vesivaylatoteumat-kokonaishintaisettyot id)
         true ;; TODO OIKEUSTARKISTUS!!!11
         [kok-hint/kokonaishintaiset-toimenpiteet])

       "Yksikköhintaiset" :yksikkohintaiset-toimenpiteet
       (when #_(oikeudet/urakat-vesivaylatoteumat-yksikkohintaisettyot id)
         true ;; TODO OIKEUSTARKISTUS!!!11
         [yks-hint/yksikkohintaiset-toimenpiteet])])))