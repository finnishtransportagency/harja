(ns harja.views.urakka
  "Urakan näkymät: sisältää urakan perustiedot ja tabirakenteen"
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.ui.bootstrap :as bs]
            [harja.asiakas.tapahtumat :as t]
            [harja.views.urakka.yleiset :as urakka-yleiset]
            [harja.views.urakka.suunnittelu :as suunnittelu]
            [harja.views.urakka.toteumat :as toteumat]
            [harja.views.urakka.toteutus :as toteutus]
            [harja.views.urakka.laskutus :as laskutus]
            [harja.views.vesivaylat.urakka.laskutus :as laskutus-vesivaylat]
            [harja.views.urakka.yllapitokohteet.paallystyksen-kohdeluettelo :as paallystyksen-kohdeluettelo]
            [harja.views.urakka.aikataulu :as aikataulu]
            [harja.views.urakka.valitavoitteet :as valitavoitteet]
            [harja.views.urakka.tiemerkinnan-kustannukset :as tiemerkinnan-kustannukset]
            [harja.views.urakka.yllapitokohteet.paikkaukset-kohdeluettelo :as paikkaukset]
            [harja.tiedot.urakka.suunnittelu.kokonaishintaiset-tyot :as kok-hint-tyot]
            [harja.tiedot.urakka.suunnittelu.yksikkohintaiset-tyot :as yks-hint-tyot]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.tiemerkinnan-kustannukset :as tiemerkinnan-kustannukset-tiedot]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.views.urakka.laadunseuranta :as laadunseuranta]
            [harja.views.urakka.turvallisuuspoikkeamat :as turvallisuuspoikkeamat]
            [harja.views.vesivaylat.urakka.toimenpiteet :as toimenpiteet]
            [harja.views.vesivaylat.urakka.turvalaitteet :as turvalaitteet]
            [harja.views.vesivaylat.urakka.materiaalit :as vv-materiaalit]
            [harja.views.kanavat.urakka.liikenne :as liikenne]
            [harja.views.kanavat.urakka.laskutus :as laskutus-kanavat]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.urakka :as u-domain]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.urakka :as urakka])

  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(defn valilehti-mahdollinen? [valilehti {:keys [tyyppi sopimustyyppi id] :as urakka}]
  (case valilehti
    :yleiset true
    :suunnittelu (and (oikeudet/urakat-suunnittelu id)
                      (not= sopimustyyppi :kokonaisurakka)
                      (not= tyyppi :tiemerkinta))
    :toteumat (and (oikeudet/urakat-toteumat id)
                   (not= sopimustyyppi :kokonaisurakka)
                   (not (u-domain/vesivaylaurakkatyyppi? tyyppi))
                   (not= tyyppi :tiemerkinta))
    :toimenpiteet (and (oikeudet/urakat-vesivaylatoimenpiteet id)
                       (u-domain/vesivaylaurakkatyyppi? tyyppi)
                       (istunto/ominaisuus-kaytossa? :vesivayla))
    :vv-materiaalit (and
                      (oikeudet/urakat-vesivayla-materiaalit id)
                      (u-domain/vesivaylaurakkatyyppi? tyyppi))
    :liikenne (and (oikeudet/urakat-kanavat-liikenne id)
                   (u-domain/kanavaurakka? urakka))
    :toteutus (and (oikeudet/urakat-toteutus id)
                   (not= sopimustyyppi :kokonaisurakka)
                   (= tyyppi :tiemerkinta))
    :turvalaitteet (and (oikeudet/urakat-vesivayla-turvalaitteet id)
                        (u-domain/vesivaylaurakkatyyppi? tyyppi)
                        (istunto/ominaisuus-kaytossa? :vesivayla))
    :paikkaukset (and (oikeudet/urakat-paikkaukset id)
                      (#{:hoito :teiden-hoito :paallystys} tyyppi))
    :aikataulu (and (oikeudet/urakat-aikataulu id) (or (= tyyppi :paallystys)
                                                       (= tyyppi :tiemerkinta)))
    :kohdeluettelo-paallystys (and (or (oikeudet/urakat-kohdeluettelo-paallystyskohteet id)
                                       (oikeudet/urakat-kohdeluettelo-paallystysilmoitukset id))
                                   (= tyyppi :paallystys))
    :laadunseuranta (or
                      (and (oikeudet/urakat-laadunseuranta id)
                           (not (u-domain/vesivaylaurakkatyyppi? tyyppi)))
                      (and (oikeudet/urakat-laadunseuranta id)
                           (u-domain/vesivaylaurakkatyyppi? tyyppi)
                           (istunto/ominaisuus-kaytossa? :vesivayla)))
    :valitavoitteet (and (oikeudet/urakat-valitavoitteet id)
                         (not (urakka/kanavaurakka? urakka)))
    :turvallisuuspoikkeamat (oikeudet/urakat-turvallisuus id)
    :laskutus (and (oikeudet/urakat-laskutus id)
                   (not= tyyppi :paallystys)
                   (not= tyyppi :tiemerkinta)
                   (not (u-domain/vesivaylaurakkatyyppi? tyyppi)))
    :laskutus-vesivaylat (and (oikeudet/urakat-laskutus-vesivaylalaskutusyhteenveto id)
                              (u-domain/vesivaylaurakkatyyppi? tyyppi)
                              (not (u-domain/kanavaurakka? urakka))
                              (istunto/ominaisuus-kaytossa? :vesivayla))
    :laskutus-kanavat (and (oikeudet/urakat-laskutus-kanavalaskutusyhteenveto id)
                           (u-domain/kanavaurakka? urakka)
                           (istunto/ominaisuus-kaytossa? :vesivayla))
    :tiemerkinnan-kustannukset (and (oikeudet/urakat-kustannukset id)
                                    (= tyyppi :tiemerkinta))

    false))

(defn urakka
  "Urakkanäkymä"
  []
  (let [ur @nav/valittu-urakka
        _ (when-not (valilehti-mahdollinen? (nav/valittu-valilehti :urakat) ur)
            (nav/aseta-valittu-valilehti! :urakat :yleiset))
        mhu-urakka? (= :teiden-hoito
                       (:tyyppi ur))
        hae-urakan-tyot (fn [ur]
                          (when (oikeudet/urakat-suunnittelu-kokonaishintaisettyot (:id ur))
                            (go (reset! u/urakan-kok-hint-tyot (<! (kok-hint-tyot/hae-urakan-kokonaishintaiset-tyot ur)))))
                          (when (or (oikeudet/urakat-suunnittelu-yksikkohintaisettyot (:id ur))
                                    (oikeudet/urakat-toteumat-yksikkohintaisettyot (:id ur)))
                            (go (reset! u/urakan-yks-hint-tyot
                                        (s/prosessoi-tyorivit ur
                                                              (<! (yks-hint-tyot/hae-urakan-yksikkohintaiset-tyot (:id ur))))))))]

    ;; Luetaan toimenpideinstanssi, jotta se ei menetä arvoaan kun vaihdetaan välilehtiä
    @u/valittu-toimenpideinstanssi

    (hae-urakan-tyot ur)
    (if @u/urakan-tiedot-ladattu?
      [bs/tabs {:style :tabs :classes "tabs-taso1"
                :active (nav/valittu-valilehti-atom :urakat)}
       "Yleiset"
       :yleiset
       (when (oikeudet/urakat-yleiset (:id ur))
         ^{:key "yleiset"}
         [urakka-yleiset/yleiset ur])

       "Suunnittelu"
       :suunnittelu
       (when (valilehti-mahdollinen? :suunnittelu ur)
         ^{:key "suunnittelu"}
         [suunnittelu/suunnittelu ur])

       (if mhu-urakka? "Kulut" "Laskutus")
       :laskutus
       (when (valilehti-mahdollinen? :laskutus ur)
         ^{:key "laskutus"}
         [laskutus/laskutus])

       "Toteumat"
       :toteumat
       (when (valilehti-mahdollinen? :toteumat ur)
         ^{:key "toteumat"}
         [toteumat/toteumat ur])

       "Toimenpiteet"
       :toimenpiteet
       (when (valilehti-mahdollinen? :toimenpiteet ur)
         ^{:key "toimenpiteet"}
         [toimenpiteet/toimenpiteet ur])

       "Liikenne"
       :liikenne
       (when (valilehti-mahdollinen? :liikenne ur)
         ^{:key "liikenne"}
         [liikenne/liikenne])

       "Materiaalit"
       :vv-materiaalit
       (when (valilehti-mahdollinen? :vv-materiaalit ur)
         ^{:key "vv-materiaalit"}
         [vv-materiaalit/materiaalit ur])

       ;; TODO Enabloi vasta kun tehty kokonaan, ei keskeneräistä kamaa tuotantoon
       ;;"Turvalaitteet"
       ;;:turvalaitteet
       ;;(when (valilehti-mahdollinen? :turvalaitteet ur)
       ;;  ^{:key "turvalaitteet"}
       ;;  [turvalaitteet/turvalaitteet ur])

       "Toteutus"
       :toteutus
       (when (valilehti-mahdollinen? :toteutus ur)
         ^{:key "toteutus"}
         [toteutus/toteutus ur])

       "Aikataulu"
       :aikataulu
       (when (valilehti-mahdollinen? :aikataulu ur)
         ^{:key "aikataulu"}
         [aikataulu/aikataulu ur {:nakyma (:tyyppi ur)}])

       "Kohdeluettelo"
       :kohdeluettelo-paallystys
       (when (valilehti-mahdollinen? :kohdeluettelo-paallystys ur)
         ^{:key "kohdeluettelo"}
         [paallystyksen-kohdeluettelo/kohdeluettelo ur])

       "Laadunseuranta"
       :laadunseuranta
       (when (valilehti-mahdollinen? :laadunseuranta ur)
         ^{:key "laadunseuranta"}
         [laadunseuranta/laadunseuranta ur])

       "Välitavoitteet"
       :valitavoitteet
       (when (valilehti-mahdollinen? :valitavoitteet ur)
         ^{:key "valitavoitteet"}
         [valitavoitteet/valitavoitteet ur])

       "Turvallisuus"
       :turvallisuuspoikkeamat
       (when (valilehti-mahdollinen? :turvallisuuspoikkeamat ur)
         ^{:key "turvallisuuspoikkeamat"}
         [turvallisuuspoikkeamat/turvallisuuspoikkeamat ur])

       "Paikkaukset"
       :paikkaukset
       (when (valilehti-mahdollinen? :paikkaukset ur)
         ^{:key "paikkaukset"}
         [paikkaukset/paikkaukset ur])

       "Laskutus"
       :laskutus-vesivaylat
       (when (valilehti-mahdollinen? :laskutus-vesivaylat ur)
         ^{:key "laskutus"}
         [laskutus-vesivaylat/laskutus])

       "Laskutus"
       :laskutus-kanavat
       (when (valilehti-mahdollinen? :laskutus-kanavat ur)
         ^{:key "laskutus"}
         [laskutus-kanavat/laskutus])

       "Kustannukset"
       :tiemerkinnan-kustannukset
       (when (valilehti-mahdollinen? :tiemerkinnan-kustannukset ur)
         ^{:key "tiemerkinnan-kustannukset"}
         [tiemerkinnan-kustannukset/kustannukset
          ur
          tiemerkinnan-kustannukset-tiedot/raportin-parametrit
          tiemerkinnan-kustannukset-tiedot/raportin-tiedot])]

      [ajax-loader "Ladataan urakan tietoja..."])))
