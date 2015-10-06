(ns harja.views.urakka
  "Urakan näkymät: sisältää urakan perustiedot ja tabirakenteen"
  (:require [reagent.core :refer [atom] :as reagent]
            [bootstrap :as bs]

            [harja.views.urakka.yleiset :as urakka-yleiset]
            [harja.views.urakka.suunnittelu :as suunnittelu]
            [harja.views.urakka.toteumat :as toteumat]
            [harja.views.urakka.laskutus :as laskutus]
            [harja.views.urakka.paallystyksen-kohdeluettelo :as paallystyksen-kohdeluettelo]
            [harja.views.urakka.paikkauksen-kohdeluettelo :as paikkauksen-kohdeluettelo]
            [harja.views.urakka.valitavoitteet :as valitavoitteet]
            [harja.tiedot.urakka.kokonaishintaiset-tyot :as kok-hint-tyot]
            [harja.tiedot.urakka.yksikkohintaiset-tyot :as yks-hint-tyot]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.views.urakka.laadunseuranta :as laadunseuranta]
            [harja.views.urakka.turvallisuus.turvallisuuspoikkeamat :as turvallisuuspoikkeamat]
            [harja.tiedot.navigaatio :as nav])

  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(defn urakka
  "Urakkanäkymä"
  []
  (let [ur @nav/valittu-urakka
        hae-urakan-tyot (fn [ur]
                          (go (reset! u/urakan-kok-hint-tyot (<! (kok-hint-tyot/hae-urakan-kokonaishintaiset-tyot ur))))
                          (go (reset! u/urakan-yks-hint-tyot
                                      (s/prosessoi-tyorivit ur
                                                            (<! (yks-hint-tyot/hae-urakan-yksikkohintaiset-tyot (:id ur)))))))]

    ;; Luetaan toimenpideinstanssi, jotta se ei menetä arvoaan kun vaihdetaan välilehtiä
    @u/valittu-toimenpideinstanssi
    
    (hae-urakan-tyot ur)
    [bs/tabs {:style :tabs :classes "tabs-taso1" :active u/urakan-valittu-valilehti}
     "Yleiset"
     :yleiset
     ^{:key "yleiset"}
     [urakka-yleiset/yleiset ur]

     "Suunnittelu"
     :suunnittelu
     (when-not (= :kokonaisurakka (:sopimustyyppi ur))
       ^{:key "suunnittelu"}
       [suunnittelu/suunnittelu ur])

     "Toteumat"
     :toteumat
     (when-not (= :kokonaisurakka (:sopimustyyppi ur))
       ^{:key "toteumat"}
       [toteumat/toteumat ur])

     "Kohdeluettelo"
     :kohdeluettelo
     (when (= :paallystys (:tyyppi ur))
       ^{:key "kohdeluettelo"}
       [paallystyksen-kohdeluettelo/kohdeluettelo ur])

     "Kohdeluettelo"
     :kohdeluettelo
     (when (= :paikkaus (:tyyppi ur))
       ^{:key "kohdeluettelo"}
       [paikkauksen-kohdeluettelo/kohdeluettelo ur])

     "Laadunseuranta"
     :laadunseuranta
     ^{:key "laadunseuranta"}
     [laadunseuranta/laadunseuranta]

     "Välitavoitteet"
     :valitavoitteet
     (when-not (= :hoito (:tyyppi ur))
       ^{:key "valitavoitteet"}
       [valitavoitteet/valitavoitteet ur])

     "Turvallisuus"
     :turvallisuuspoikkeamat
     (when (= :hoito (:tyyppi ur))
       ^{:key "turvallisuuspoikkeamat"}
       [turvallisuuspoikkeamat/turvallisuuspoikkeamat])

     "Laskutus"
     :laskutus
     ^{:key "laskutus"}
     [laskutus/laskutus]]))
