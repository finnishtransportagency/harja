(ns harja.views.urakka.toteutus
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.bootstrap :as bs]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.views.urakka.suunnittelu.kokonaishintaiset-tyot :as kokonaishintaiset-tyot]
            [harja.views.urakka.suunnittelu.muut-tyot :as muut-tyot]
            [harja.views.urakka.suunnittelu.suola :as suola]
            [harja.views.urakka.suunnittelu.materiaalit :as mat]
            [harja.loki :refer [log]]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.komponentti :as komp]))


(defn toteutus [ur]
  (let [valitun-hoitokauden-yks-hint-kustannukset (s/valitun-hoitokauden-yks-hint-kustannukset ur)]
    (komp/luo
      (fn [{:keys [id] :as ur}]

        [:span.suunnittelu
         [bs/tabs {:style :tabs :classes "tabs-taso2"
                   :active (nav/valittu-valilehti-atom :suunnittelu)}

          "Kokonaishintaiset työt"
          :kokonaishintaiset
          (when (oikeudet/urakat-suunnittelu-kokonaishintaisettyot id)
            ^{:key "kokonaishintaiset-tyot"}
            [kokonaishintaiset-tyot/kokonaishintaiset-tyot ur valitun-hoitokauden-yks-hint-kustannukset])

          ;; TODO Korvataan tiemerkinnän omalla suunnittelunäkymällä
          ;"Yksikköhintaiset työt"
          ;:yksikkohintaiset
          ;(when (oikeudet/urakat-suunnittelu-yksikkohintaisettyot id)
          ;  ^{:key "yksikkohintaiset-tyot"}
          ;  [yksikkohintaiset-tyot/yksikkohintaiset-tyot-view ur valitun-hoitokauden-yks-hint-kustannukset])

          "Muut työt"
          :muut
          (when (oikeudet/urakat-suunnittelu-muutos-ja-lisatyot id)
            ^{:key "muut-tyot"}
            [muut-tyot/muut-tyot ur])]]))))
