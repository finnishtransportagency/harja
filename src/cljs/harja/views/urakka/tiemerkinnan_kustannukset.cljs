(ns harja.views.urakka.tiemerkinnan-kustannukset
  "Tiemerkintäurakan Kustannukset-välilehti"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan]]

            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]

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
            [harja.ui.yleiset :as yleiset])

  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defn kustannukset [urakka raportin-parametrit-atom raportin-tiedot-atom]
  (komp/luo
    (komp/lippu tiedot/kustannukset-valilehti-nakyvissa?)
    (fn [urakka raportin-parametrit-atom raportin-tiedot-atom]
      (let []
        [:div
         [valinnat/urakan-hoitokausi urakka]
         [valinnat/hoitokauden-kuukausi]

         (when-let [parametrit @raportin-parametrit-atom]
           [upotettu-raportti/raportin-vientimuodot parametrit])

         (if-let [raportti @raportin-tiedot-atom]
           [muodosta-html (assoc-in raportti [1 :tunniste] :tiemerkinnan-kustannusyhteenveto)]
           [yleiset/ajax-loader "Raporttia suoritetaan..."])]))))
