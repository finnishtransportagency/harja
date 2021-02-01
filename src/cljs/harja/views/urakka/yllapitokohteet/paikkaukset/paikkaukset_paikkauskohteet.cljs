(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [harja.ui.grid :as grid]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as t-paikkauskohteet]
            [harja.ui.debug :as debug]
            [harja.loki :refer [log]]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
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
      :rivi-klikattu #(e! (t-paikkauskohteet/->AvaaLomake (merge % {:tyyppi :testilomake})))
      :tunniste :testinro}
     skeema
     paikkauskohteet]))

(defn kohteet [e! app]
  [:div
   [:div "Tänne paikkauskohteet"]
   [:div "Tähän kartta"]
   [:div {:style {:display "flex"}} ;TODO: tähän class, mistä ja mikä?
    ;TODO: Tee parempi luokka taustattomille napeille, nykyisessä teksti liian ohut ja tausta on puhtaan valkoinen. vs #fafafa taustassa
    [napit/lataa "Lataa Excel-pohja" #(js/console.log "Ladataan excel-pohja") {:luokka "napiton-nappi"}] ;TODO: Implementoi
    [napit/laheta "Vie Exceliin" #(js/console.log "Viedään exceliin") {:luokka "napiton-nappi"}] ;TODO: Implementoi
    [napit/uusi "Tuo kohteet excelistä" #(js/console.log "Tuodaan Excelistä") {:luokka "napiton-nappi"}] ;TODO: Implementoi
    [napit/uusi "Lisää kohde" #(e! (t-paikkauskohteet/->AvaaLomake {:tyyppi :uusi-paikkauskohde}))]]
   [paikkauskohteet-taulukko e! app]]
  )

(defn uusi-paikkauskohde-lomake
  [e!]
  [:div "Tänne lomake uudelle kohteelle"
   [napit/yleinen-ensisijainen "Debug/Sulje nappi" #(e! (t-paikkauskohteet/->SuljeLomake))]])

(defn testilomake
  [e! _lomake]
  [:div "Kuvittele tähän hieno lomake"
   [napit/yleinen-ensisijainen "Debug/Sulje nappi" #(e! (t-paikkauskohteet/->SuljeLomake))]])

(defn paikkauslomake [e! lomake]
  [lomake/lomake-overlay {}
   (fn []
     (case (:tyyppi lomake)
       :uusi-paikkauskohde [uusi-paikkauskohde-lomake e!]
       :testilomake [testilomake e! lomake]
       [:div "Lomaketta ei ole vielä tehty" [napit/yleinen-ensisijainen "Debug/Sulje nappi" #(e! (t-paikkauskohteet/->SuljeLomake))]]))])

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
            [paikkauslomake e! (:lomake app)])
          [kohteet e! app]]]))))

(defn paikkauskohteet [ur]
  [tuck/tuck t-paikkauskohteet/app paikkauskohteet*])
