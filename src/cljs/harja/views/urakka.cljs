(ns harja.views.urakka
  "Urakan näkymät: sisältää urakan perustiedot ja tabirakenteen"
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.ui.bootstrap :as bs]
            [harja.asiakas.tapahtumat :as t]

            [harja.views.urakka.yleiset :as urakka-yleiset]
            [harja.views.urakka.suunnittelu :as suunnittelu]
            [harja.views.urakka.toteumat :as toteumat]
            [harja.views.urakka.laskutus :as laskutus]
            [harja.views.urakka.paallystyksen-kohdeluettelo :as paallystyksen-kohdeluettelo]
            [harja.views.urakka.paikkauksen-kohdeluettelo :as paikkauksen-kohdeluettelo]
            [harja.views.urakka.aikataulu :as aikataulu]
            [harja.views.urakka.valitavoitteet :as valitavoitteet]
            [harja.tiedot.urakka.suunnittelu.kokonaishintaiset-tyot :as kok-hint-tyot]
            [harja.tiedot.urakka.suunnittelu.yksikkohintaiset-tyot :as yks-hint-tyot]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.views.urakka.laadunseuranta :as laadunseuranta]
            [harja.views.urakka.turvallisuuspoikkeamat :as turvallisuuspoikkeamat]
            [harja.tiedot.navigaatio :as nav])

  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(defn valilehti-mahdollinen? [valilehti urakkatyyppi sopimustyyppi]
  ;; FIXME: siirrä navigaatioon
  (case valilehti
    :yleiset true
    :suunnittelu (not= sopimustyyppi :kokonaisurakka)
    :toteumat (not= sopimustyyppi :kokonaisurakka)
    :aikataulu (= urakkatyyppi :paallystys)
    :kohdeluettelo-paallystys (= urakkatyyppi :paallystys)
    :kohdeluettelo-paikkaus (= urakkatyyppi :paikkaus)
    :laadunseuranta true
    :valitavoitteet true
    :turvallisuuspoikkeamat (= urakkatyyppi :hoito)
    :laskutus))

(defn urakka
  "Urakkanäkymä"
  []
  (let [ur @nav/valittu-urakka
        _ (when-not (valilehti-mahdollinen? (nav/valittu-valilehti :urakat) (:tyyppi ur) (:sopimustyyppi ur))
            (nav/aseta-valittu-valilehti! :urakat :yleiset))
        hae-urakan-tyot (fn [ur]
                          (go (reset! u/urakan-kok-hint-tyot (<! (kok-hint-tyot/hae-urakan-kokonaishintaiset-tyot ur))))
                          (go (reset! u/urakan-yks-hint-tyot
                                      (s/prosessoi-tyorivit ur
                                                            (<! (yks-hint-tyot/hae-urakan-yksikkohintaiset-tyot (:id ur)))))))]

    ;; Luetaan toimenpideinstanssi, jotta se ei menetä arvoaan kun vaihdetaan välilehtiä
    @u/valittu-toimenpideinstanssi

    (hae-urakan-tyot ur)
    [bs/tabs {:style :tabs :classes "tabs-taso1"
              :active (nav/valittu-valilehti-atom :urakat)}
     "Yleiset"
     :yleiset
     ^{:key "yleiset"}
     [urakka-yleiset/yleiset ur]

     "Suunnittelu"
     :suunnittelu
     (when (valilehti-mahdollinen? :suunnittelu (:tyyppi ur) (:sopimustyyppi ur))
       ^{:key "suunnittelu"}
       [suunnittelu/suunnittelu ur])

     "Toteumat"
     :toteumat
     (when (valilehti-mahdollinen? :toteumat (:tyyppi ur) (:sopimustyyppi ur))
       ^{:key "toteumat"}
       [toteumat/toteumat])


     "Aikataulu"
     :aikataulu
     (when (valilehti-mahdollinen? :aikataulu (:tyyppi ur) (:sopimustyyppi ur))
       ^{:key "aikataulu"}
       [aikataulu/aikataulu])

     "Kohdeluettelo"
     :kohdeluettelo-paallystys
     (when (valilehti-mahdollinen? :kohdeluettelo-paallystys (:tyyppi ur) (:sopimustyyppi ur))
       ^{:key "kohdeluettelo"}
       [paallystyksen-kohdeluettelo/kohdeluettelo])

     "Kohdeluettelo"
     :kohdeluettelo-paikkaus
     (when (valilehti-mahdollinen? :kohdeluettelo-paikkaus (:tyyppi ur) (:sopimustyyppi ur))
       ^{:key "kohdeluettelo"}
       [paikkauksen-kohdeluettelo/kohdeluettelo])

     "Laadunseuranta"
     :laadunseuranta
     ^{:key "laadunseuranta"}
     [laadunseuranta/laadunseuranta]

     "Välitavoitteet"
     :valitavoitteet
     (when (valilehti-mahdollinen? :valitavoitteet (:tyyppi ur) (:sopimustyyppi ur))
       ^{:key "valitavoitteet"}
       [valitavoitteet/valitavoitteet ur])

     "Turvallisuus"
     :turvallisuuspoikkeamat
     (when (valilehti-mahdollinen? :turvallisuuspoikkeamat (:tyyppi ur) (:sopimustyyppi ur))
       ^{:key "turvallisuuspoikkeamat"}
       [turvallisuuspoikkeamat/turvallisuuspoikkeamat])

     "Laskutus"
     :laskutus
     ^{:key "laskutus"}
     [laskutus/laskutus]]))
