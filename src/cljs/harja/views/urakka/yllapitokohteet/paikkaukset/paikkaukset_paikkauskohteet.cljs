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
                 :nimi :formatoitu-aikataulu}]
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
  (let [_ (js/console.log "View - kohteet:")]
    [:div
     [:div "Tänne paikkauskohteet"]
     [:div {:style {:display "flex"}} ;TODO: tähän class, mistä ja mikä?
      ;TODO: Tee parempi luokka taustattomille napeille, nykyisessä teksti liian ohut ja tausta on puhtaan valkoinen. vs #fafafa taustassa
      ;TODO: Napeista puuttuu myös kulmien pyöristys
      [napit/lataa "Lataa Excel-pohja" #(js/console.log "Ladataan excel-pohja") {:luokka "napiton-nappi"}] ;TODO: Implementoi
      [napit/laheta "Vie Exceliin" #(js/console.log "Viedään exceliin") {:luokka "napiton-nappi"}] ;TODO: Implementoi
      [napit/uusi "Tuo kohteet excelistä" #(js/console.log "Tuodaan Excelistä") {:luokka "napiton-nappi"}] ;TODO: Implementoi
      [napit/uusi "Lisää kohde" #(e! (t-paikkauskohteet/->AvaaLomake {:tyyppi :uusi-paikkauskohde}))]]
     [paikkauskohteet-taulukko e! app]])
  )

(defn uusi-paikkauskohde-lomake [e! lomake]
  (fn [e! lomake]
    [:div
     [lomake/lomake
      {:luokka " overlay-oikealla"
       :overlay {:leveys "600px"}
       :ei-borderia? true
       :otsikko "Ehdota paikkauskohdetta"
       :muokkaa! #(e! (t-paikkauskohteet/->PaivitaLomake (lomake/ilman-lomaketietoja %)))
       :footer-fn (fn [lomake]
                    [:div
                     [napit/tallenna
                      "Tallenna"
                      #(e! (t-paikkauskohteet/->TallennaUusiPaikkauskohde lomake))]
                     [napit/yleinen-toissijainen
                      "Peruuta"
                      #(e! (t-paikkauskohteet/->SuljeLomake))]])}
      [
       {:otsikko "Numero"
        :tyyppi :numero
        :nimi :numero
        ::lomake/col-luokka "col-sm-4"}
       {:otsikko "Nimi"
        :tyyppi :string
        :nimi :nimi
        :pakollinen? true
        ::lomake/col-luokka "col-sm-8"}
       {:otsikko "Työmenetelmä"
        :tyyppi :valinta
        :nimi :tyomenetelma
        :valinnat ["MPA" "KTVA" "SIPA" "SIPU" "REPA" "UREM" "Muu"]
        :pakolinen? true}
       (lomake/ryhma
         {:otsikko "Sijainti"
          :uusi-rivi? true
          :ryhman-luokka "lomakeryhman-border"}
         (lomake/rivi
           {:otsikko "Tie"
            :tyyppi :string
            :nimi :tie
            :pakolinen? true}
           {:otsikko "Ajorata"
            :tyyppi :valinta
            :valinnat [0 1 2 3] ; TODO: Hae jostain?
            :nimi :ajorata
            :pakolinen? false})
         (lomake/rivi
           {:otsikko "A-osa"
            :tyyppi :string
            :pakollinen? true
            :nimi :aosa}
           {:otsikko "A-et."
            :tyyppi :string
            :pakollinen? true
            :nimi :aet}
           {:otsikko "L-osa."
            :tyyppi :string
            :pakollinen? true
            :nimi :losa}
           {:otsikko "L-et."
            :tyyppi :string
            :pakollinen? true
            :nimi :let}))
       (lomake/ryhma
         {:otsikko "Alustava suunnitelma"}
         (lomake/rivi
           {:otsikko "Arv. aloitus"
            :tyyppi :pvm
            :nimi :aloitus
            :pakollinen? true
            ::lomake/col-luokka "col-sm-6"}
           {:otsikko "Arv. lopetus"
            :tyyppi :pvm
            :nimi :lopetus
            :pakollinen? true
            ::lomake/col-luokka "col-sm-6"})
         (lomake/rivi
           {:otsikko "Suunniteltu määrä"
            :tyyppi :positiivinen-numero
            :nimi :maara
            :pakollinen? true
            ::lomake/col-luokka "col-sm-6"}
           {:otsikko "Yksikkö"
            :tyyppi :valinta
            :valinnat ["m²" "t" "kpl" "jm"]
            :nimi :yksikko
            :pakollinen? true
            ::lomake/col-luokka "col-sm-6"})
         )
       {:otsikko "Suunniteltu hinta"
        :tyyppi :positiivinen-numero
        :nimi :suunniteltu-hinta
        ::lomake/col-luokka "col-sm-12"
        :yksikko "€"}]
      lomake]]))

(defn testilomake
  [e! _lomake]
  [:div "Kuvittele tähän hieno lomake"
   [napit/yleinen-ensisijainen "Debug/Sulje nappi" #(e! (t-paikkauskohteet/->SuljeLomake))]])

(defn paikkauslomake [e! lomake]
  (fn [e! lomake]
    (case (:tyyppi lomake)
      :uusi-paikkauskohde [uusi-paikkauskohde-lomake e! lomake]
      :testilomake [testilomake e! lomake]
      [:div "Lomaketta ei ole vielä tehty" [napit/yleinen-ensisijainen "Debug/Sulje nappi" #(e! (t-paikkauskohteet/->SuljeLomake))]])))

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
