(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [harja.ui.grid :as grid]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as t-paikkauskohteet]
            [harja.ui.debug :as debug]
            [harja.loki :refer [log]]
            [harja.ui.lomake :as lomake]
            [harja.ui.komponentti :as komp]))

(defn- paikkauskohteet-taulukko [e! app]
  (let [skeema [{:otsikko "NRO"
                 :leveys 1
                 :nimi :testinro}
                {:otsikko "Nimi"
                 :leveys 1
                 :nimi :testinimi}
                {:otsikko "Tila"
                 :leveys 1
                 :nimi :testitila}
                {:otsikko "Menetelmä"
                 :leveys 1
                 :nimi :testimenetelma}
                {:otsikko "Sijainti"
                 :leveys 1
                 :nimi :testisijainti}
                {:otsikko "Aikataulu"
                 :leveys 1
                 :nimi :testiaikataulu}]
        paikkauskohteet (:paikkauskohteet app)]
    [grid/grid
     {:otsikko "Paikkauskohteet"
      :tyhja "Ei tietoja"
      :rivi-klikattu #(e! (if (:lomake app) (t-paikkauskohteet/->SuljeLomake) (t-paikkauskohteet/->AvaaLomake %)))
      :tunniste :testinro}
     skeema
     paikkauskohteet]))

(defn kohteet [e! app]
  [:div
   [:div "Tänne paikkauskohteet"]
   [:div "Tähän kartta"]
   [paikkauskohteet-taulukko e! app]]
  )

(defn paikkauslomake [e!]
  [lomake/lomake-overlay {}
   (fn []
     [:div "Tänne lomake"])])

(defn paikkauskohteet* [e! app]
  (komp/luo
    (komp/piirretty (fn [this]
                      (do
                        ;; TODO: Hae kamat bäkkäristä
                        )))
    (fn [e! app]
      (let [_ (js/console.log " paikkauskohteet*  ")]
        [:div {:id ""}
         [:div
          [debug/debug app]
          (when (:lomake app)
            [paikkauslomake e!])
          [kohteet e! app]]]))))

(defn paikkauskohteet [ur]
  [tuck/tuck t-paikkauskohteet/app paikkauskohteet*])
