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


(defn kustannukset-listaus [e! {:keys [haku-kaynnissa?]} urakka]

  [:div.mpu-kustannukset

   [:div.header-valinnat
    ;; Vuosi valinta
    [valinnat/vuosi
     {:disabled false
      :kaanteinen-jarjestys? true
      :otsikko-teksti "Kalenterivuosi"}
     (t/year (:alkupvm urakka))
     (t/year (:loppupvm urakka))
     urakka/valittu-urakan-vuosi
     #(do
        (println "valittu vuosi ->" %)
        (urakka/valitse-urakan-vuosi! %))]

    ;; Lisää kustannus
    [:span
     [napit/yleinen-ensisijainen
      "Lisää kustannus"
      #(do
         (println "Uusi Kustannus ->"))
      {:ikoni [ikonit/harja-icon-action-add] :vayla-tyyli? true}]]]

   ;; Taulukko
   [grid/grid {:tyhja (if haku-kaynnissa?
                        [ajax-loader "Haku käynnissä..."]
                        "Valitulle aikavälille ei löytynyt mitään.")
               :tunniste :id
               :sivuta grid/vakiosivutus
               :voi-kumota? false
               :piilota-toiminnot? true
               :piilota-otsikot? true
               ;; Yhteenveto 
               :rivi-jalkeen-fn (fn [rivit]
                                  ^{:luokka "kustannukset-yhteenveto"}
                                  [{:teksti "Yhteensä" :luokka "lihavoitu"}
                                   {:teksti "4500,00"
                                    :tasaa :oikea :luokka "lihavoitu"}])}

    ;; Työmenetelmä
    [{:tyyppi :string
      :nimi :tyomenetelma
      :luokka "text-nowrap"
      :leveys 1}

     ;; Kustannus
     {:tyyppi :positiivinen-numero
      :desimaalien-maara 2
      :nimi :kustannus
      :tasaa :oikea
      :luokka "text-nowrap"
      :leveys 1}]
    ;; Testidata
    '({:tyomenetelma "Test 1", :kustannus 1500 :id 1}
      {:tyomenetelma "Test 2", :kustannus 2500 :id 2})]])


(defn mpu-kustannukset* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan #(e! (tiedot/->HaeTiedot)))

    (fn [e! app]
      [:div
       [kustannukset-listaus e! app @nav/valittu-urakka]])))


(defn mpu-kustannukset []
  [tuck tiedot/tila mpu-kustannukset*])
 