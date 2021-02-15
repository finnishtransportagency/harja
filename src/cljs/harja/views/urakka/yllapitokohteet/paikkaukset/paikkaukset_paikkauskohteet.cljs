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
                         (e! (t-paikkauskohteet/->AvaaLomake (merge kohde {:tyyppi :paikkauskohteen-katselu})))))
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
     [:div {:style {:display "flex"}} ;TODO: tähän class, mistä ja mikä?
      ;TODO: Tee parempi luokka taustattomille napeille, nykyisessä teksti liian ohut ja tausta on puhtaan valkoinen. vs #fafafa taustassa
      ;TODO: Napeista puuttuu myös kulmien pyöristys
      [napit/lataa "Lataa Excel-pohja" #(js/console.log "Ladataan excel-pohja") {:luokka "napiton-nappi"}] ;TODO: Implementoi
      [napit/laheta "Vie Exceliin" #(js/console.log "Viedään exceliin") {:luokka "napiton-nappi"}] ;TODO: Implementoi
      [napit/uusi "Tuo kohteet excelistä" #(js/console.log "Tuodaan Excelistä") {:luokka "napiton-nappi"}] ;TODO: Implementoi
      [napit/uusi "Lisää kohde" #(e! (t-paikkauskohteet/->AvaaLomake {:tyyppi :uusi-paikkauskohde}))]]
     [paikkauskohteet-taulukko e! app]])
  )

(defn suunnitelman-kentat [voi-muokata?]
  (if voi-muokata?
    [(lomake/ryhma
       {:otsikko "Alustava suunnitelma"}
       (lomake/rivi
         {:otsikko "Arv. aloitus"
          :tyyppi :pvm
          :nimi :alkuaika
          :pakollinen? true
          ::lomake/col-luokka "col-sm-6"}
         {:otsikko "Arv. lopetus"
          :tyyppi :pvm
          :nimi :loppuaika
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
       {:otsikko "Suunniteltu hinta"
        :tyyppi :positiivinen-numero
        :nimi :suunniteltu-hinta
        ::lomake/col-luokka "col-sm-12"
        :yksikko "€"})]
    [{:otsikko "Suunniteltu aikataulu"
      :nimi :aikataulu
      :uusi-rivi? true
      :tyyppi :string
      :hae (fn [rivi]
             (if (and (:alkuaika rivi) (:loppuaika rivi))
               (harja.fmt/pvm-vali [(:alkuaika rivi) (:loppuaika rivi)])
               "Suunniteltua aikataulua ei löytynyt"))}
     {:otsikko "Suunniteltu määrä"
      :nimi :koostettu-maara
      :tyyppi :string
      :uusi-rivi? true
      :hae (fn [rivi]
             (str (:maara rivi) " " (:yksikko rivi)))}
     {:otsikko "Suunniteltu hinta"
      :nimi :hinta
      :uusi-rivi? true
      :tyyppi :string}]))

(defn sijainnin-kentat [voi-muokata?]
  (if voi-muokata?
    [(lomake/ryhma
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
          :nimi :let}))]
    [{:tyyppi :string
      :otsikko "Sijainti"
      :nimi :sijaintikooste
      :uusi-rivi? true
      :hae #(clojure.string/join "/" ((juxt :tie :aosa :aet :losa :let) %))}]))

(defn nimi-numero-ja-tp-kentat [e! voi-muokata?]
  (if voi-muokata?
    [{:otsikko "Numero"
      :tyyppi :numero
      :nimi :nro
      ::lomake/col-luokka "col-sm-4"}
     {:otsikko "Nimi"
      :tyyppi :string
      :nimi :nimi
      :pakollinen? true
      ::lomake/col-luokka "col-sm-8"}
     {:otsikko "Työmenetelmä"
      :tyyppi :valinta
      :nimi :tyomenetelma
      :valinnat ["MPA" "KTVA" "SIPA" "SIPU" "REPA" "UREM" "Muu"] ;; TODO: Tähän tulee väylävirastolta valmiit valinnat(?)
      :pakolinen? true}]
    [{:tyyppi :numero
      :kokonaisluku? true
      :uusi-rivi? true
      :nimi :nro}
     {:tyyppi :string
      :uusi-rivi? true
      :nimi :nimi}
     {:nimi :paikkauskohteen-tila
      :tyyppi :komponentti
      :uusi-rivi? true
      :komponentti (fn [{:keys [data]}]
                     (let [arvo (:paikkauskohteen-tila data)]
                       [:span
                        [:span {:class (str "circle "
                                            (cond
                                              (= "tilattu" arvo) "tila-tilattu"
                                              (= "ehdotettu" arvo) "tila-ehdotettu"
                                              (= "valmis" arvo) "tila-valmis"
                                              :default "tila-ehdotettu"
                                              ))}] (str/capitalize arvo)]))}
     {:nimi :paivitetty
      :tyyppi :string
      :hae (fn [rivi]
             (if (:paivitetty rivi) (str "Päivitetty " (:paivitetty rivi)) "Ei päivitystietoa"))}
     {:nimi :muokkauspainike
      :tyyppi :komponentti
      :komponentti (fn [{:keys [data]}]
                     (napit/muokkaa "Muokkaa kohdetta" (e! (t-paikkauskohteet/->AvaaLomake (assoc data :tyyppi :paikkauskohteen-muokkaus)))))}
     {:nimi :tyomenetelma
      :otsikko "Työmenetelmä"
      :uusi-rivi? true
      :tyyppi :string}]))

(defn paikkauskohde-skeema [e! voi-muokata?]
  (let [nimi-nro-ja-tp (nimi-numero-ja-tp-kentat e! voi-muokata?)
        sijainti (sijainnin-kentat voi-muokata?)
        suunnitelma (suunnitelman-kentat voi-muokata?)]
    (vec (concat nimi-nro-ja-tp
                 sijainti
                 suunnitelma))))

(defn paikkauskohde-lomake [e! lomake]
  (let [voi-muokata? (or
                       (= :paikkauskohteen-muokkaus (:tyyppi lomake))
                       (= :uusi-paikkauskohde (:tyyppi lomake)))]
    (fn [e! lomake]
      [:div
       ^{:key (hash lomake)}
       [lomake/lomake
        {:luokka " overlay-oikealla"
         :overlay {:leveys "600px"}
         :ei-borderia? true
         :voi-muokata? voi-muokata?
         :otsikko (if (:id lomake) "Muokkaa paikkauskohdetta" "Ehdota paikkauskohdetta")
         :muokkaa! #(e! (t-paikkauskohteet/->PaivitaLomake (lomake/ilman-lomaketietoja %)))
         :footer-fn (fn [lomake]
                      (let [lomake-ilman-lomaketietoja (lomake/ilman-lomaketietoja lomake)]
                        [:div
                         [napit/tallenna
                          "Tallenna"
                          #(e! (t-paikkauskohteet/->TallennaPaikkauskohde lomake-ilman-lomaketietoja))]
                         [napit/yleinen-toissijainen
                          "Peruuta"
                          #(e! (t-paikkauskohteet/->SuljeLomake))]]))}
        (paikkauskohde-skeema e! voi-muokata?) ;;TODO: korjaa päivitys
        lomake]])))

(defn testilomake
  [e! _lomake]
  [:div "Kuvittele tähän hieno lomake"
   [napit/yleinen-ensisijainen "Debug/Sulje nappi" #(e! (t-paikkauskohteet/->SuljeLomake))]])

(defn paikkauslomake [e! lomake] ;; TODO: Parempi nimeäminen
  (fn [e! lomake]
    (case (:tyyppi lomake)
      :uusi-paikkauskohde [paikkauskohde-lomake e! lomake]
      :paikkauskohteen-muokkaus [paikkauskohde-lomake e! lomake]
      :paikkauskohteen-katselu [paikkauskohde-lomake e! lomake]
      :testilomake [testilomake e! lomake]
      [:div "Lomaketta ei ole vielä tehty" [napit/yleinen-ensisijainen "Debug/Sulje nappi" #(e! (t-paikkauskohteet/->SuljeLomake))]])))

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
