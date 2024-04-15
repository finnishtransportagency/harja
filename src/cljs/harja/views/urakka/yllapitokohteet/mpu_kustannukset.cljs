(ns harja.views.urakka.yllapitokohteet.mpu-kustannukset
  "MPU sopimustyyppisten urakoiden kustannukset"
  (:require [tuck.core :refer [tuck]]
            [harja.tiedot.urakka.mpu-kustannukset :as tiedot]
            [reagent.core :as r]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka :as urakka]
            [harja.ui.liitteet :as liitteet]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.kentat :as kentat]
            [cljs-time.core :as t]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.napit :as napit]
            [harja.views.kartta :as kartta]
            [harja.tiedot.istunto :as istunto])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))


(defn kustannukset-listaus [e! {:keys [valittu-vuosi]} urakka]
  
  [:div.mpu-kustannukset
   
   ;; Vuosi valinta
   [valinnat/vuosi
    {:disabled false
     :otsikko-teksti "Kalenterivuosi"}
    (t/year (:alkupvm urakka))
    (t/year (:loppupvm urakka))
    urakka/valittu-urakan-vuosi
    #(do
       (println "valittu vuosi" %)
       (urakka/valitse-urakan-vuosi! %))]])


(defn mpu-kustannukset* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan #(e! (tiedot/->HaeTiedot)))

    (fn [e! app]
      [:div
       [kustannukset-listaus e! app @nav/valittu-urakka]])))


(defn mpu-kustannukset []
  [tuck tiedot/tila mpu-kustannukset*])
 