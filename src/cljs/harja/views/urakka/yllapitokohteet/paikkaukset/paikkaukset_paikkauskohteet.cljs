(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [harja.geo :as geo]
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [clojure.string :as str]
            [harja.domain.oikeudet :as oikeudet]
            [harja.fmt :as fmt]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.komponentti :as komp]
            [harja.ui.debug :as debug]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as t-paikkauskohteet]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet-kartalle :as t-paikkauskohteet-kartalle]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.kartta :as kartta-tiedot]
            [harja.views.kartta.tasot :as kartta-tasot]
            [harja.views.urakka.yllapitokohteet.yhteyshenkilot :as yllapito-yhteyshenkilot]
            [harja.views.kartta :as kartta]
            ))

(defn- paikkauskohteet-taulukko [e! app]
  (let [skeema [{:otsikko "NRO"
                 :leveys 2
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
                 :nimi :formatoitu-aikataulu}
                ;; Jos ei ole oikeuksia nähdä hintatietoja, niin ei näytetä niitä
                (when (oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (-> @tila/tila :yleiset :urakka :id))
                  {:otsikko "Suun. hinta"
                   :leveys 2
                   :nimi :suunniteltu-hinta
                   :fmt fmt/euro-opt
                   :tasaa :oikea})
                ;; Jos ei ole oikeuksia nähdä hintatietoja, niin ei näytetä niitä
                (when (oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (-> @tila/tila :yleiset :urakka :id))
                  {:otsikko "Tot. hinta"
                   :leveys 2
                   :nimi :toteutunut-hinta
                   :fmt fmt/euro-opt
                   :tasaa :oikea})
                ;; Jos ei ole oikeuksia nähdä hintatietoja, niin näytetään yhteystiedot
                (when (false? (oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (-> @tila/tila :yleiset :urakka :id)))
                  {:otsikko "Yh\u00ADte\u00ADys\u00ADtie\u00ADdot"
                   :leveys 3
                   :nimi :yhteystiedot
                   :tasaa :keskita
                   :tyyppi :komponentti
                   :komponentti (fn [rivi]
                                  [napit/yleinen-toissijainen ""
                                   #(yllapito-yhteyshenkilot/nayta-paikkauskohteen-yhteyshenkilot-modal! (:urakka-id rivi))
                                   {:ikoni (ikonit/user)
                                    :luokka "btn-xs"}])})
                ]
        paikkauskohteet (:paikkauskohteet app)]
    ;; Riippuen vähän roolista, taulukossa on enemmän dataa tai vähemmän dataa.
    ;; Niinpä kavennetaan sitä hieman, jos siihen tulee vähemmän dataa, luettavuuden parantamiseksi
    [:div.col-xs-12.col-md-12.col-lg-9 #_{:style {:display "flex"
                                                  :justify-content "flex-start"}}
     [grid/grid
      {:tunniste :id
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
                           {:teksti ""}
                           {:teksti ""}
                           {:teksti ""}])}
      skeema
      paikkauskohteet]]))

(defn kohteet [e! app]
  [:div
   [:div.row #_{:style {:display "flex"}} ;TODO: tähän class, mistä ja mikä?
    [:div.col-xs-12.col-md-6.col-lg-6 [:h2 (str (count (:paikkauskohteet app)) " paikkauskohdetta")]]
    [:div.col-xs-12.col-md-6.col-lg-6.pull-right
     ;TODO: Tee parempi luokka taustattomille napeille, nykyisessä teksti liian ohut ja tausta on puhtaan valkoinen. vs #fafafa taustassa
     ;TODO: Napeista puuttuu myös kulmien pyöristys
     [napit/lataa "Lataa Excel-pohja" #(js/console.log "Ladataan excel-pohja") {:luokka "napiton-nappi"}] ;TODO: Implementoi
     [napit/laheta "Vie Exceliin" #(js/console.log "Viedään exceliin") {:luokka "napiton-nappi"}] ;TODO: Implementoi
     [napit/uusi "Tuo kohteet excelistä" #(js/console.log "Tuodaan Excelistä") {:luokka "napiton-nappi"}] ;TODO: Implementoi
     [napit/uusi "Lisää kohde" #(e! (t-paikkauskohteet/->AvaaLomake {:tyyppi :uusi-paikkauskohde}))]]]
   [:div.row [paikkauskohteet-taulukko e! app]]]
  )

(defn suunnitelman-kentat [voi-muokata?]
  (if voi-muokata?
    [(lomake/ryhma
       {:otsikko "Alustava suunnitelma"}
       (lomake/rivi
         {:otsikko "Arv. aloitus"
          :tyyppi :pvm
          :nimi :alkupvm
          :pakollinen? true
          ::lomake/col-luokka "col-sm-6"}
         {:otsikko "Arv. lopetus"
          :tyyppi :pvm
          :nimi :loppupvm
          :pakollinen? true
          ::lomake/col-luokka "col-sm-6"})
       (lomake/rivi
         {:otsikko "Suunniteltu määrä"
          :tyyppi :positiivinen-numero
          :nimi :suunniteltu-maara
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
        :pakollinen? true
        :yksikko "€"})]
    [{:otsikko "Suunniteltu aikataulu"
      :nimi :aikataulu
      :uusi-rivi? true
      :tyyppi :string
      :hae (fn [rivi]
             (if (and (:alkupvm rivi) (:loppupvm rivi))
               (harja.fmt/pvm-vali [(:alkupvm rivi) (:loppupvm rivi)])
               "Suunniteltua aikataulua ei löytynyt"))}
     {:otsikko "Suunniteltu määrä"
      :nimi :koostettu-maara
      :tyyppi :string
      :uusi-rivi? true
      :hae (fn [rivi]
             (str (:suunniteltu-maara rivi) " " (:yksikko rivi)))}
     {:otsikko "Suunniteltu hinta"
      :nimi :suunniteltu-hinta
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
          :tyyppi :numero
          :nimi :tie
          :pakollinen? true}
         {:otsikko "Ajorata"
          :tyyppi :valinta
          :valinnat [0 1 2 3] ; TODO: Hae jostain?
          :nimi :ajorata
          :pakollinen? false})
       (lomake/rivi
         {:otsikko "A-osa"
          :tyyppi :numero
          :pakollinen? true
          :nimi :aosa}
         {:otsikko "A-et."
          :tyyppi :numero
          :pakollinen? true
          :nimi :aet}
         {:otsikko "L-osa."
          :tyyppi :numero
          :pakollinen? true
          :nimi :losa}
         {:otsikko "L-et."
          :tyyppi :numero
          :pakollinen? true
          :nimi :let}))]
    [{:tyyppi :string
      :otsikko "Sijainti"
      :nimi :sijaintikooste
      :uusi-rivi? true
      :hae #(clojure.string/join "/" ((juxt :tie :aosa :aet :losa :let) %))}]))

(defn nimi-numero-ja-tp-kentat [e! muu-menetelma? voi-muokata?]
  (if voi-muokata?
    [{:otsikko "Nimi"
      :tyyppi :string
      :nimi :nimi
      :pakollinen? true
      ::lomake/col-luokka "col-sm-8"}
     {:otsikko "Lask.nro"
      :tyyppi :string
      :nimi :nro
      ::lomake/col-luokka "col-sm-2"}
     {:otsikko "Työmenetelmä"
      :tyyppi :valinta
      :nimi :tyomenetelma
      :valinnat ["MPA" "KTVA" "SIPA" "SIPU" "REPA" "UREM" "Muu"] ;; TODO: Tähän tulee väylävirastolta valmiit valinnat(?)
      :pakollinen? true
      ::lomake/col-luokka "col-sm-6"}
     (when muu-menetelma?
       {:otsikko "Menetelmän kuvaus"
        :nimi :menetelman-kuvaus
        :pakollinen? :true
        :tyyppi :string
        ::lomake/col-luokka "col-sm-6"})]
    [{:tyyppi :string
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
                       (if arvo
                         [:span
                          [:span {:class (str "circle "
                                              (cond
                                                (= "tilattu" arvo) "tila-tilattu"
                                                (= "ehdotettu" arvo) "tila-ehdotettu"
                                                (= "valmis" arvo) "tila-valmis"
                                                :default "tila-ehdotettu"
                                                ))}] (str/capitalize arvo)]
                         "Tila ei tiedossa")))}
     {:nimi :muokattu
      :tyyppi :string
      :hae (fn [rivi]
             (if (:muokattu rivi) (str "Päivitetty " (harja.fmt/pvm (:muokattu rivi))) "Ei päivitystietoa"))}
     {:nimi :muokkauspainike
      :tyyppi :komponentti
      ::lomake/col-luokka "col-md-12 reunus-alhaalla"
      :komponentti (fn [{:keys [data]}]
                     [napit/muokkaa "Muokkaa kohdetta" #(e! (t-paikkauskohteet/->AvaaLomake (assoc data :tyyppi :paikkauskohteen-muokkaus)))])}
     {:nimi :tyomenetelma
      :otsikko "Työmenetelmä"
      :uusi-rivi? true
      :tyyppi :string}]))

(defn paikkauskohde-skeema [e! muu-menetelma? voi-muokata?]
  (let [nimi-nro-ja-tp (nimi-numero-ja-tp-kentat e! muu-menetelma? voi-muokata?)
        sijainti (sijainnin-kentat voi-muokata?)
        suunnitelma (suunnitelman-kentat voi-muokata?)]
    (vec (concat nimi-nro-ja-tp
                 sijainti
                 suunnitelma))))

(defn paikkauskohde-lomake [e! lomake]
  (let [voi-muokata? (or
                       (= :paikkauskohteen-muokkaus (:tyyppi lomake))
                       (= :uusi-paikkauskohde (:tyyppi lomake)))
        muu-menetelma? (= "Muu" (:tyomenetelma lomake))]
    ;; TODO: Korjaa paikkauskohteesta toiseen siirtyminen (avaa paikkauskohde listalta, klikkaa toista paikkauskohdetta)
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
     (paikkauskohde-skeema e! muu-menetelma? voi-muokata?) ;;TODO: korjaa päivitys
     lomake]))

(defn testilomake
  [e! _lomake]
  [:div "Kuvittele tähän hieno lomake"
   [napit/yleinen-ensisijainen "Debug/Sulje nappi" #(e! (t-paikkauskohteet/->SuljeLomake))]])

(defn paikkauslomake [e! lomake] ;; TODO: Parempi nimeäminen
  (case (:tyyppi lomake)
    :uusi-paikkauskohde [paikkauskohde-lomake e! lomake]
    :paikkauskohteen-muokkaus [paikkauskohde-lomake e! lomake]
    :paikkauskohteen-katselu [paikkauskohde-lomake e! lomake]
    :testilomake [testilomake e! lomake]
    [:div "Lomaketta ei ole vielä tehty" [napit/yleinen-ensisijainen "Debug/Sulje nappi" #(e! (t-paikkauskohteet/->SuljeLomake))]]))

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
      [:div.row
       [paikkauskohteet-sivu e! app]])))

(defn paikkauskohteet [ur]
  (komp/luo
    (komp/sisaan #(do
                    (reset! t-paikkauskohteet-kartalle/valitut-kohteet-atom #{})
                    (kartta-tasot/taso-paalle! :paikkaukset-paikkauskohteet)
                    (kartta-tasot/taso-pois! :paikkaukset-toteumat)))
    (fn [_]
      [tuck/tuck tila/paikkauskohteet paikkauskohteet*])))
