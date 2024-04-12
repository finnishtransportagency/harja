(ns harja.views.urakka.yllapitokohteet.mpu-kustannukset
  "MPU sopimustyyppisten urakoiden kustannukset"
  (:require [tuck.core :refer [tuck]]
            [harja.tiedot.urakka.mpu-kustannukset :as tiedot]
            [reagent.core :as r]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.ui.liitteet :as liitteet]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.kentat :as kentat]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.napit :as napit]
            [harja.views.kartta :as kartta]
            [harja.tiedot.istunto :as istunto])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))


(defn kustannukset-listaus [e! app]
  [:div "listaus"])


(defn mpu-kustannukset* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan #(e! (tiedot/->HaeTiedot)))

    (fn [e! app]
      [:div
       [kustannukset-listaus e! app]])))


(defn mpu-kustannukset []
  [tuck tiedot/tila mpu-kustannukset*])
 