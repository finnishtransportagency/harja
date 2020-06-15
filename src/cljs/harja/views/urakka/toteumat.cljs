(ns harja.views.urakka.toteumat
  "Urakan 'Toteumat' välilehti:"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.bootstrap :as bs]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.tiedot.urakka :as u]
            [harja.views.urakka.toteumat.yksikkohintaiset-tyot :as yks-hint-tyot]
            [harja.views.urakka.toteumat.kokonaishintaiset-tyot :as kokonaishintaiset-tyot]
            [harja.views.urakka.toteumat.muut-tyot :as muut-tyot]
            [harja.views.urakka.toteumat.akilliset-hoitotyot :as akilliset-htyot]
            [harja.views.urakka.toteumat.erilliskustannukset :as erilliskustannukset]
            [harja.views.urakka.toteumat.materiaalit :refer [materiaalit-nakyma]]
            [harja.views.urakka.toteumat.varusteet :as varusteet]
            [harja.views.urakka.toteumat.suola :refer [suolatoteumat pohjavesialueen-suola]]

            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt]]
            [cljs.core.async :refer [<! >! chan]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.istunto :as istunto])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))


(defn toteumat
  "Toteumien pääkomponentti"
  [ur]
  (let [mhu-urakka? (= :teiden-hoito (:tyyppi ur))]
    (komp/luo
      (komp/sisaan-ulos #(do
                           (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                           (nav/vaihda-kartan-koko! :S))
                        #(nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko))
      (fn [{:keys [id] :as ur}]
        [bs/tabs {:style :tabs :classes "tabs-taso2"
                  :active (nav/valittu-valilehti-atom :toteumat)}

         "Äkilliset hoitotyöt ja vaurioiden korjaukset" :akilliset-hoitotyot-ja-vaurioiden-korjaukset
         (when (or
                 true
                 (oikeudet/urakat-toteumat-akilliset-hoitotyot id))
           [akilliset-htyot/akilliset-hoitotyot])

         "Kokonaishintaiset työt" :kokonaishintaiset-tyot
         (when (and (oikeudet/urakat-toteumat-kokonaishintaisettyot id)
                    (not mhu-urakka?))
           [kokonaishintaiset-tyot/kokonaishintaiset-toteumat])

         "Yksikköhintaiset työt" :yksikkohintaiset-tyot
         (when (and (oikeudet/urakat-toteumat-yksikkohintaisettyot id)
                    (not mhu-urakka?))
           [yks-hint-tyot/yksikkohintaisten-toteumat])

         "Muutos- ja lisätyöt" :muut-tyot
         (when (oikeudet/urakat-toteumat-muutos-ja-lisatyot id)
           [muut-tyot/muut-tyot-toteumat ur])

         "Suola" :suola
         (when (and (oikeudet/urakat-toteumat-suola id)
                    (#{:hoito :teiden-hoito} (:tyyppi ur)))
           [suolatoteumat])

         "Pohjavesialueet" :pohjavesialueet
         (when (and (oikeudet/urakat-toteumat-suola id)
                    (#{:hoito :teiden-hoito} (:tyyppi ur)))
           [pohjavesialueen-suola])

         "Materiaalit" :materiaalit
         (when (oikeudet/urakat-toteumat-materiaalit id)
           [materiaalit-nakyma ur])

         "Erilliskustannukset" :erilliskustannukset
         (when (oikeudet/urakat-toteumat-erilliskustannukset id)
           [erilliskustannukset/erilliskustannusten-toteumat ur])

         "Varusteet" :varusteet
         (when (and (istunto/ominaisuus-kaytossa? :tierekisterin-varusteet)
                    (oikeudet/urakat-toteumat-varusteet id)
                    (#{:hoito :teiden-hoito} (:tyyppi ur)))
           [varusteet/varusteet])]))))
