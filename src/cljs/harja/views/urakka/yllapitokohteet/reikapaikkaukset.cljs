(ns harja.views.urakka.yllapitokohteet.reikapaikkaukset
  "Reikäpaikkaukset päänäkymä"
  (:require [tuck.core :refer [tuck]]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-reikapaikkaukset :as tiedot]
            [harja.ui.debug :refer [debug]]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.kentat :as kentat]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.napit :as napit]
            [harja.pvm :as pvm]
            [clojure.string :as str])
   (:require-macros [harja.tyokalut.ui :refer [for*]]))


(defn reikapaikkaus-listaus [e! {:keys [valinnat rivit] :as app}]
  (let [
    ;;tänne varmaankin jotain
  ]
    ;; wrappaa reikapaikkausluokkaan niin ei yliajeta mitään 
    [:div.reikapaikkaukset
     [:div.reikapaikkaus-listaus
      [:h1.header-yhteiset "Reikäpaikkaukset"]
      [:div.row.filtterit
       [valinnat/aikavali
        tiedot/aikavali-atom
        {:otsikko "Aikaväli"
         :for-teksti "filtteri-aikavali"
         :luokka #{"label-ja-aikavali " "ei-tiukkaa-leveytta "}
         :ikoni-sisaan? true
         :vayla-tyyli? true
         :aikavalin-rajoitus [6 :kuukausi]}]]

      [grid/grid {:tyhja "Valitulle aikavälille ei löytynyt mitään."
                  :tunniste :tunniste
                  :sivuta grid/vakiosivutus
                  :voi-kumota? false
                  :piilota-toiminnot? true
                  :jarjesta :pvm
                  :mahdollista-rivin-valinta? true
                  ;; TODO tähän tulee varmaankin muokkaus :rivi-klikattu #(funktio..)
                  }

       [{:otsikko "Pvm"
         :tyyppi :komponentti
         :komponentti (fn [arvo _]  "test")
         :luokka "text-nowrap"
         :leveys 0.4}

        {:otsikko "Sijainti"
         :tyyppi :komponentti
         :komponentti (fn [arvo _] "test")
         :luokka "text-nowrap"
         :leveys 0.5}

        {:otsikko "Menetelmä"
         :tyyppi :komponentti
         :komponentti (fn [arvo _] "test")
         :luokka "text-nowrap"
         :leveys 1}

        {:otsikko "Määrä"
         :tyyppi :komponentti
         :komponentti (fn [arvo _] "test")
         :luokka "text-nowrap"
         :leveys 0.3}

        {:otsikko "Kustannus (EUR)"
         :tyyppi :komponentti
         :komponentti (fn [arvo _] "test")
         :luokka "text-nowrap"
         :leveys 0.3}]
       rivit]]]))


(defn reikapaikkaukset* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan-ulos
      ;; sisään, tee aikavälivahti
      #(do
         (add-watch tiedot/aikavali-atom :aikavali-haku
           (fn [_ _ vanha uusi]
             (when-not
               (and
                 (pvm/sama-pvm? (first vanha) (first uusi))
                 (pvm/sama-pvm? (second vanha) (second uusi)))
               (e! (tiedot/->PaivitaAikavali {:aikavali uusi})))))
         (e! (tiedot/->HaeTiedot)))
      ;; ulos
      #(do
         (println "komp ulos en nyt muista mitä nämä tekee ")
         ;; (remove-watch tiedot/aikavali-atom :aikavali-haku)
         ))
    
    ;; näytä listaus
    (fn [e! app]
      [:div
       [reikapaikkaus-listaus e! app]])))


(defn reikapaikkaukset []
  [tuck tiedot/tila reikapaikkaukset*])
