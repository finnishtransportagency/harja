(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [harja.ui.grid :as grid]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as t-paikkauskohteet]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet-kartalle :as t-paikkauskohteet-kartalle]
            [harja.views.kartta :as kartta]
            [harja.views.kartta.tasot :as kartta-tasot]
            [harja.geo :as geo]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.kartta :as kartta-tiedot]
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
                 :leveys 4
                 :nimi :testinimi}
                {:otsikko "Tila"
                 :leveys 2
                 :nimi :testitila
                 :fmt (fn [arvo]
                        [:span
                         [:span {:class (str "circle "
                                             (cond
                                               (= "Tilattu" arvo) "tila-tilattu"
                                               (= "Ehdotettu" arvo) "tila-ehdotettu"
                                               (= "Valmis" arvo) "tila-valmis"
                                               :default "tila-ehdotettu"
                                               ))}] arvo])}
                {:otsikko "Menetelmä"
                 :leveys 2
                 :nimi :testimenetelma}
                {:otsikko "Sijainti"
                 :leveys 4
                 :nimi :testisijainti}
                {:otsikko "Aikataulu"
                 :leveys 4
                 :nimi :testiaikataulu}]
        paikkauskohteet (:paikkauskohteet app)]
    [grid/grid
     {:otsikko "Paikkauskohteet"
      :tyhja "Ei tietoja"
      :rivi-klikattu (fn [kohde]
                       (do
                         (js/console.log "rivi-klikattu :: kohde" (pr-str kohde))
                         ;; Näytä valittu rivi kartalla
                         (when (not (nil? (:sijainti kohde)))
                           (kartta-tiedot/keskita-kartta-alueeseen! (harja.geo/extent (:sijainti kohde)))
                           (reset! t-paikkauskohteet-kartalle/valitut-kohteet-atom #{(:testinro kohde)}))
                         ;; avaa lomake
                         (e! (t-paikkauskohteet/->AvaaLomake (merge kohde {:tyyppi :testilomake})))))
      :tunniste :testinro
      :rivi-jalkeen-fn (fn [rivit]
                         (let [_ (js/console.log "rivi-jalkeen-fn")]
                           ^{:luokka "yhteenveto"}
                           [{:teksti "Yht."}
                            {:teksti (str (count paikkauskohteet) " kohdetta")}
                            {:teksti ""}
                            {:teksti ""}
                            {:teksti ""}
                            {:teksti ""}]))}
     skeema
     paikkauskohteet]))

(defn kohteet [e! app]
  (let [_ (js/console.log "View - kohteet:" )]
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
  (let [_ (js/console.log "paikkauskohteet-sivu ::  Karttataso näkyvissä?" (pr-str @t-paikkauskohteet-kartalle/karttataso-nakyvissa?))]
    [:div
     [kartta/kartan-paikka]
     [debug/debug app]
     (when (:lomake app)
       [paikkauslomake e! (:lomake app)])
     [kohteet e! app]]))

(defn paikkauskohteet* [e! app]
  (komp/luo
    (komp/sisaan #(do
                    (reset! t-paikkauskohteet-kartalle/karttataso-nakyvissa? true)
                    (reset! t-paikkauskohteet-kartalle/karttataso-paikkauskohteet app)
                    ;(kartta-tasot/taso-pois! :paikkaukset-toteumat)
                    (kartta-tasot/taso-paalle! :paikkaukset-paikkauskohteet)
                    ;(kartta-tasot/taso-paalle! :organisaatio)
                    (kartta-tiedot/zoomaa-valittuun-hallintayksikkoon-tai-urakkaan)
                    ;; TODO: Hae kamat bäkkäristä
                    ))
    (komp/ulos #(do
                  ;(kartta-tasot/taso-pois! :paikkaukset-paikkauskohteet)
                  ;(reset! t-paikkauskohteet-kartalle/karttataso-nakyvissa? false)
                  ))
    (fn [e! app]
      (let [_ (js/console.log " paikkauskohteet* ")]
        [:div {:id ""}
         [paikkauskohteet-sivu e! app]]))))

(defn paikkauskohteet [ur]
  (let [_ (reset! tila/paikkauskohteet t-paikkauskohteet/dummy-app-state)]
    [tuck/tuck tila/paikkauskohteet paikkauskohteet*]))
