(ns harja.views.urakka.suunnittelu
  "Päätason sivu Hallinta, josta kaikkeen ylläpitötyöhön pääsee käsiksi."
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.bootstrap :as bs]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.views.urakka.suunnittelu.tehtavat :as tehtavat]
            [harja.views.urakka.suunnittelu.yksikkohintaiset-tyot :as yksikkohintaiset-tyot]
            [harja.views.urakka.suunnittelu.kokonaishintaiset-tyot :as kokonaishintaiset-tyot]
            [harja.views.urakka.suunnittelu.muut-tyot :as muut-tyot]
            [harja.views.urakka.suunnittelu.suola :as suola]
            [harja.views.urakka.suunnittelu.materiaalit :as mat]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma :as kustannussuunnitelma]
            [harja.views.vesivaylat.urakka.suunnittelu.kiintiot :as kiintiot]
            [harja.loki :refer [log]]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.komponentti :as komp]
            [harja.domain.urakka :as ur]
            [tuck.core :as tuck])

  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(defn valilehti-mahdollinen? [valilehti {:keys [tyyppi sopimustyyppi id] :as urakka}]
  (case valilehti
    :materiaalit (and (not (#{:teiden-hoito :paallystys :tiemerkinta} tyyppi))
                      (not (ur/vesivaylaurakkatyyppi? tyyppi)))
    :tehtavat (= tyyppi :teiden-hoito)
    :suola (#{:hoito :teiden-hoito} tyyppi)
    :muut (not (ur/vesivaylaurakkatyyppi? tyyppi))
    :kiintiot (= tyyppi :vesivayla-hoito)
    :kokonaishintaiset (not= tyyppi :teiden-hoito)
    :yksikkohintaiset (not= tyyppi :teiden-hoito)
    :kustannussuunnitelma (= tyyppi :teiden-hoito)))

(defn suunnittelu [ur]
  (let [valitun-hoitokauden-yks-hint-kustannukset (s/valitun-hoitokauden-yks-hint-kustannukset ur)]
    (komp/luo
      (fn [{:keys [id] :as ur}]

        [:span.suunnittelu
         [bs/tabs {:style :tabs :classes "tabs-taso2"
                   :active (nav/valittu-valilehti-atom :suunnittelu)}

          "Kokonaishintaiset työt"
          :kokonaishintaiset
          (when (and (oikeudet/urakat-suunnittelu-kokonaishintaisettyot id)
                     (valilehti-mahdollinen? :kokonaishintaiset ur))
            ^{:key "kokonaishintaiset-tyot"}
            [kokonaishintaiset-tyot/kokonaishintaiset-tyot ur valitun-hoitokauden-yks-hint-kustannukset])

          "Yksikköhintaiset työt"
          :yksikkohintaiset
          (when (and (oikeudet/urakat-suunnittelu-yksikkohintaisettyot id)
                     (valilehti-mahdollinen? :yksikkohintaiset ur))
            ^{:key "yksikkohintaiset-tyot"}
            [yksikkohintaiset-tyot/yksikkohintaiset-tyot-view ur valitun-hoitokauden-yks-hint-kustannukset])

          "Muutos- ja lisätyöt"
          :muut
          (when (and (oikeudet/urakat-suunnittelu-muutos-ja-lisatyot id)
                     (valilehti-mahdollinen? :muut ur))
            ^{:key "muut-tyot"}
            [muut-tyot/muut-tyot ur])

          "Suola" :suola
          (when (and (oikeudet/urakat-suunnittelu-suola id)
                     (valilehti-mahdollinen? :suola ur))
            [suola/suola])

          "Materiaalit"
          :materiaalit
          (when (and (oikeudet/urakat-suunnittelu-materiaalit id)
                     (valilehti-mahdollinen? :materiaalit ur))
            ^{:key "materiaalit"}
            [mat/materiaalit ur])

          "Kiintiöt"
          :kiintiot
          (when (and (oikeudet/urakat-vesivaylasuunnittelu-kiintiot id)
                  (valilehti-mahdollinen? :kiintiot ur))
            ^{:key "kiintiöt"}
            [kiintiot/kiintiot])

          "Kustannussuunnitelma"
          :kustannussuunnitelma
          (when (and (oikeudet/urakat-suunnittelu-kustannussuunnittelu id)
                     (valilehti-mahdollinen? :kustannussuunnitelma ur))
            ^{:key "kustannussuunnitelma"}
            [kustannussuunnitelma/kustannussuunnitelma])

          "Tehtävät"
          :tehtavat
          (when (and (oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo id)
                     (valilehti-mahdollinen? :tehtavat ur))
            ^{:key "tehtavat"}
            [tehtavat/tehtavat])]]))))
