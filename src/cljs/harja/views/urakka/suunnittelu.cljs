(ns harja.views.urakka.suunnittelu
  "Päätason sivu Hallinta, josta kaikkeen ylläpitötyöhön pääsee käsiksi."
  (:require [harja.ui.bootstrap :as bs]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.istunto :as istunto]
            [harja.views.urakka.suunnittelu.tehtavat :as tehtavat]
            [harja.views.urakka.suunnittelu.tehtavat-maarat-nakyma :as tehtavat-maarat-nakyma]
            [harja.views.urakka.suunnittelu.kalustoresurssit :as kalustoresurssit]
            [harja.views.urakka.suunnittelu.yksikkohintaiset-tyot :as yksikkohintaiset-tyot]
            [harja.views.urakka.suunnittelu.kokonaishintaiset-tyot :as kokonaishintaiset-tyot]
            [harja.views.urakka.suunnittelu.muut-tyot :as muut-tyot]
            [harja.views.urakka.suunnittelu.suola :as suola]
            [harja.views.urakka.suunnittelu.materiaalit :as mat]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.kustannussuunnitelma-view :as kustannussuunnitelma]
            [harja.views.urakka.suunnittelu.tarjous-kustannussuunnitelma.tarjous-nakyma :as tarjous-nakyma]
            [harja.views.urakka.suunnittelu.tarjous-kustannussuunnitelma.kustannussuunnitelma-nakyma :as kustannussuunitelma-nakyma]
            [harja.views.vesivaylat.urakka.suunnittelu.kiintiot :as kiintiot]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.komponentti :as komp]
            [harja.domain.urakka :as ur]))

(defn valilehti-mahdollinen? [valilehti {:keys [tyyppi alkupvm]}]
  (case valilehti
    :materiaalit (and (not (#{:teiden-hoito :paallystys :tiemerkinta} tyyppi))
                   (not (ur/vesivaylaurakkatyyppi? tyyppi)))
    :tehtavat (= tyyppi :teiden-hoito)
    :suola (#{:hoito :teiden-hoito} tyyppi)
    :muut (and (not (ur/vesivaylaurakkatyyppi? tyyppi))
            (not= tyyppi :teiden-hoito))
    :kiintiot (= tyyppi :vesivayla-hoito)
    :kokonaishintaiset (not= tyyppi :teiden-hoito)
    :yksikkohintaiset (not= tyyppi :teiden-hoito)
    :kustannussuunnitelma (and (= tyyppi :teiden-hoito) (< (pvm/vuosi alkupvm) 2025))
    :uusi-kustannussuunnitelma (and (= tyyppi :teiden-hoito) (>= (pvm/vuosi alkupvm) 2025))
    :kalustoresurssit (and (= tyyppi :teiden-hoito) (>= (pvm/vuosi alkupvm) 2026))
    :tarjous (and (= tyyppi :teiden-hoito) (>= (pvm/vuosi alkupvm) 2025))))

(defn suunnittelu [ur]
  (let [valitun-hoitokauden-yks-hint-kustannukset (s/valitun-hoitokauden-yks-hint-kustannukset ur)]
    (komp/luo
      (fn [{:keys [id alkupvm] :as ur}]
        [:span.suunnittelu
         [bs/tabs {:style :tabs :classes "tabs-taso2"
                   :active (nav/valittu-valilehti-atom :suunnittelu)
                   :on-change #(nav/aseta-valittu-valilehti! :suunnittelu %)}

          "Tarjouksen tiedot"
          :tarjous
          (when (and
                  (valilehti-mahdollinen? :tarjous ur)
                  (istunto/ominaisuus-kaytossa? :kustannussuunnitelma-tarjous)
                  (istunto/ominaisuus-kaytossa? :mhu-urakka))
            [tarjous-nakyma/tarjous])

          "Hoitovuoden alun tavoitehinta"
          :uusi-kustannussuunnitelma
          (when (and
                  (valilehti-mahdollinen? :uusi-kustannussuunnitelma ur)
                  (istunto/ominaisuus-kaytossa? :kustannussuunnitelma-tarjous)
                  (istunto/ominaisuus-kaytossa? :mhu-urakka))
            [kustannussuunitelma-nakyma/kustannussuunitelma])

          "Kustannussuunnitelma"
          :kustannussuunnitelma
          ^{:key "uusi-kustannussuunnitelma"}
          (when (and (oikeudet/urakat-suunnittelu-kustannussuunnittelu id)
                  (valilehti-mahdollinen? :kustannussuunnitelma ur)
                  (istunto/ominaisuus-kaytossa? :mhu-urakka))
            ^{:key "kustannussuunnitelma"}
            [kustannussuunnitelma/kustannussuunnitelma])

          "Tehtävät ja määrät"
          :tehtavat
          (when (and
                  (oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo id)
                  (valilehti-mahdollinen? :tehtavat ur)
                  (istunto/ominaisuus-kaytossa? :mhu-urakka)
                  (some-> alkupvm pvm/vuosi (< 2025)))
            ^{:key "tehtavat"}
            [tehtavat/tehtavat])

          "Tehtävä- ja määräluettelo"
          :tehtavat-maarat
          (when (and
                  (oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo id)
                  (istunto/ominaisuus-kaytossa? :mhu-urakka)
                  (valilehti-mahdollinen? :tehtavat ur)
                  (istunto/ominaisuus-kaytossa? :tehtavat-maarat)
                  (some-> alkupvm pvm/vuosi (>= 2025)))
            ^{:key "tehtavat-maarat"}
            [tehtavat-maarat-nakyma/tehtavat-maarat]) 

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

          "Suolarajoitukset" :suola
          (when (and (oikeudet/urakat-suunnittelu-suola id)
                  (valilehti-mahdollinen? :suola ur))
            [suola/urakan-suolarajoitukset])

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
          
          "Kalustoresurssit"
          :kalustoresurssit
          (when (and
                  (oikeudet/urakat-suunnittelu-tehtava-ja-maaraluettelo id)
                  (istunto/ominaisuus-kaytossa? :mhu-urakka)
                  (valilehti-mahdollinen? :kalustoresurssit ur))
            ^{:key "kalustoresurssit"}
            [kalustoresurssit/kalustoresurssit])]]))))
