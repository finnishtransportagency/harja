(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [harja.ui.grid :as grid]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as t-paikkauskohteet]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet-kartalle :as t-paikkauskohteet-kartalle]
            [harja.views.kartta :as kartta]
            [harja.views.kartta.tasot :as kartta-tasot]
            [harja.geo :as geo]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.kartta :as kartta-tiedot]
            [harja.ui.debug :as debug]
            [harja.loki :refer [log]]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.komponentti :as komp]
            [clojure.string :as str]))

(defn- paikkauskohteet-taulukko [e! app]
  (let [skeema [{:otsikko "NRO"
                 :leveys 1
                 :nimi :nro}
                {:otsikko "Nimi"
                 :leveys 4
                 :nimi :nimi}
                {:otsikko "Tila"
                 :leveys 2
                 :nimi :paikkauskohteen-tila
                 :fmt (fn [arvo]
                        [:span
                         [:span {:class (str "circle "
                                             (cond
                                               (= "tilattu" arvo) "tila-tilattu"
                                               (= "ehdotettu" arvo) "tila-ehdotettu"
                                               (= "valmis" arvo) "tila-valmis"
                                               :default "tila-ehdotettu"
                                               ))}] (str/capitalize arvo)])}
                {:otsikko "Menetelmä"
                 :leveys 2
                 :nimi :tyomenetelma}
                {:otsikko "Sijainti"
                 :leveys 4
                 :nimi :formatoitu-sijainti}
                {:otsikko "Aikataulu"
                 :leveys 4
                 :nimi :formatoitu-aikataulu}]
        paikkauskohteet (:paikkauskohteet app)]
    [grid/grid
     {:otsikko "Paikkauskohteet"
      :tunniste :id
      :tyhja "Ei tietoja"
      :rivi-klikattu (fn [kohde]
                       (do
                         ;(js/console.log "rivi-klikattu :: kohde" (pr-str kohde))
                         ;; Näytä valittu rivi kartalla
                         (when (not (nil? (:sijainti kohde)))
                           (reset! t-paikkauskohteet-kartalle/valitut-kohteet-atom #{(:id kohde)})
                           (kartta-tiedot/keskita-kartta-alueeseen! (harja.geo/extent (:sijainti kohde)))
                           )
                         ;; avaa lomake
                         (e! (t-paikkauskohteet/->AvaaLomake (merge kohde {:tyyppi :testilomake})))))
      :rivi-jalkeen-fn (fn [rivit]
                         ^{:luokka "yhteenveto"}
                         [{:teksti "Yht."}
                          {:teksti (str (count paikkauskohteet) " kohdetta")}
                          {:teksti ""}
                          {:teksti ""}
                          {:teksti ""}
                          {:teksti ""}])}
     skeema
     paikkauskohteet]))

(defn kohteet [e! app]
  (let [_ (js/console.log "View - kohteet:")]
    [:div
     [:div "Tänne paikkauskohteet"]
     [:div {:style {:display "flex"}}                       ;TODO: tähän class, mistä ja mikä?
      ;TODO: Tee parempi luokka taustattomille napeille, nykyisessä teksti liian ohut ja tausta on puhtaan valkoinen. vs #fafafa taustassa
      [napit/lataa "Lataa Excel-pohja" #(js/console.log "Ladataan excel-pohja") {:luokka "napiton-nappi"}] ;TODO: Implementoi
      [napit/laheta "Vie Exceliin" #(js/console.log "Viedään exceliin") {:luokka "napiton-nappi"}] ;TODO: Implementoi
      [napit/uusi "Tuo kohteet excelistä" #(js/console.log "Tuodaan Excelistä") {:luokka "napiton-nappi"}] ;TODO: Implementoi
      [napit/uusi "Lisää kohde" #(e! (t-paikkauskohteet/->AvaaLomake {:tyyppi :uusi-paikkauskohde}))]]
     [paikkauskohteet-taulukko e! app]])
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

(defn- paikkauskohteet-sivu [e! app]
  [:div
   [kartta/kartan-paikka]
   [debug/debug app]
   (when (:lomake app)
     [paikkauslomake e! (:lomake app)])
   [kohteet e! app]])

(defn paikkauskohteet* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(do
                         (kartta-tasot/taso-pois! :paikkaukset-toteumat)
                         (kartta-tasot/taso-paalle! :organisaatio)
                         (e! (t-paikkauskohteet/->HaePaikkauskohteet))
                         (reset! t-paikkauskohteet-kartalle/karttataso-nakyvissa? true))
                      #(do
                         (kartta-tasot/taso-pois! :paikkaukset-paikkauskohteet)
                         (reset! t-paikkauskohteet-kartalle/karttataso-nakyvissa? false)))
    (fn [e! app]
      [:div {:id ""}
       [paikkauskohteet-sivu e! app]])))

(defn paikkauskohteet [ur]
  (komp/luo
    (komp/sisaan #(do
                    (reset! t-paikkauskohteet-kartalle/valitut-kohteet-atom #{})
                    (kartta-tasot/taso-paalle! :paikkaukset-paikkauskohteet)
                    (kartta-tasot/taso-pois! :paikkaukset-toteumat)))
    (fn [_]
      [tuck/tuck tila/paikkauskohteet paikkauskohteet*])))
