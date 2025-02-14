(ns harja.views.urakka.kustannusten-kirjaus
  "Tiemerkintäurakan Kustannukset-välilehti"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan]]

            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.ui.bootstrap :as bs]
            [harja.ui.komponentti :as komp]
            [harja.views.urakka.kustannusten-kirjaus.tiemerkintojen-korjaus :as tiemerkintojen-korjaus]
            [harja.views.urakka.kustannusten-kirjaus.uusien-paallysteiden-merkinnat :as paallysteiden-merkinnat]
            [harja.views.urakka.kustannusten-kirjaus.sakot-ja-bonukset :as sakot-ja-bonukset]
            [harja.views.urakka.kustannusten-kirjaus.muut-kustannukset :as muut-kustannukset]
            [harja.tiedot.urakka.tiemerkkinnan-kustannusten-kirjaus :as tm-korjaus-tiedot]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.tiemerkinnan-kustannukset :as tiedot]
            [harja.ui.upotettu-raportti :as upotettu-raportti]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.ui.raportti :refer [muodosta-html]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.transit :as t]
            [harja.ui.yleiset :as yleiset]))

(defn kustannusten-kirjaus [ur]
      (komp/lippu tiedot/kustannukset-valilehti-nakyvissa?)
      (komp/luo
        ;;TODO Käykö sama nakyvyys tarkistus kuin kustannukset-valilehti-nakyvissa?
        ;;(komp/lippu tiedot/kustannukset-valilehti-nakyvissa?)


        (fn [{:keys [id] :as ur}]
          [:span.kustannusten-kirjaus
           [bs/tabs {:style :tabs :classes "tabs-tabs2"
                     :active (nav/valittu-valilehti-atom :kustannusten-kirjaus)}

            "Tiemerkintöjen korjaus"
            :tiemerkintojen-korjaus
            [tiemerkintojen-korjaus/tiemerkintojen-korjaus
             ur
             @tm-korjaus-tiedot/tiemerkintaurakan-kustannukset]

            "Uusien päällysteiden merkinnät"
            :uusien-pallysteiden-merkinnat
            [paallysteiden-merkinnat/paallysteiden-merkinnat]

            "Sakot ja bonukset"
            :sakot-ja-bonukset
            [sakot-ja-bonukset/sakot-ja-bonukset]

            "Muut kustannukset"
            :muut-kustannukset
            [muut-kustannukset/muut-kustannukset]]])
        ))
