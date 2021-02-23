(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohdelomake
  (:require [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [clojure.string :as str]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.fmt :as fmt]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.debug :as debug]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as t-paikkauskohteet]))

(defn- lukutila-rivi [otsikko arvo]
  [:div {:style {:padding-top "16px" :padding-bottom "8px"}}
   [:div.row
    [:span {:style {:font-weight 400 :font-size "12px" :color "#5C5C5C"}} otsikko]]
   [:div.row
    [:span {:style {:font-weight 400 :font-size "14px" :line-height "20px" :color "black"}} arvo]]])

(defn suunnitelman-kentat []

  [(lomake/ryhma
     {:otsikko "Alustava suunnitelma"
      :ryhman-luokka "lomakeryhman-otsikko-tausta"}
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
      ::lomake/col-luokka "col-sm-4"
      :rivi-luokka "lomakeryhman-rivi-tausta"}
     {:otsikko "Yksikkö"
      :tyyppi :valinta
      :valinnat ["m²" "t" "kpl" "jm"]
      :nimi :yksikko
      :pakollinen? true
      ::lomake/col-luokka "col-sm-2"}
     {:otsikko "Suunniteltu hinta"
      :tyyppi :positiivinen-numero
      :nimi :suunniteltu-hinta
      ::lomake/col-luokka "col-sm-6"
      :pakollinen? true
      :yksikko "€"})
   (lomake/rivi
     {:otsikko "Lisatiedot"
      :tyyppi :text
      :nimi :lisatiedot
      :pakollinen? false
      ::lomake/col-luokka "col-sm-12"
      :rivi-luokka "lomakeryhman-rivi-tausta"}
     )])

(defn sijainnin-kentat []
  [(lomake/ryhma
     {:otsikko "Sijainti"
      :rivi? true
      :ryhman-luokka "lomakeryhman-otsikko-tausta"}

     {:otsikko "Tie"
      :tyyppi :numero
      :nimi :tie
      :pakollinen? true
      :rivi-luokka "lomakeryhman-rivi-tausta"}
     {:otsikko "Ajorata"
      :tyyppi :valinta
      :valinnat [0 1 2 3] ; TODO: Hae jostain?
      :nimi :ajorata
      :pakollinen? false})
   (lomake/rivi
     {:otsikko "A-osa"
      :tyyppi :numero
      :pakollinen? true
      :nimi :aosa
      :rivi-luokka "lomakeryhman-rivi-tausta"}
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
      :nimi :let})])

(defn nimi-numero-ja-tp-kentat [ muu-menetelma?]
  [{:otsikko "Nimi"
    :tyyppi :string
    :nimi :nimi
    :pakollinen? true
    ::lomake/col-luokka "col-sm-6"}
   {:otsikko "Lask.nro"
    :tyyppi :string
    :nimi :nro
    ::lomake/col-luokka "col-sm-3"}
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
      ::lomake/col-luokka "col-sm-6"})])

(defn paikkauskohde-skeema [e! muu-menetelma? voi-muokata? lomake]
  (let [nimi-nro-ja-tp (when voi-muokata?
                         (nimi-numero-ja-tp-kentat muu-menetelma?))
        sijainti (when voi-muokata?
                   (sijainnin-kentat))
        suunnitelma (when voi-muokata?
                      (suunnitelman-kentat))]
    (vec (concat nimi-nro-ja-tp
                 sijainti
                 suunnitelma))))

(defn paikkauskohde-lomake [e! lomake]
  (let [voi-muokata? (or
                       (= :paikkauskohteen-muokkaus (:tyyppi lomake))
                       (= :uusi-paikkauskohde (:tyyppi lomake)))
        muu-menetelma? (= "Muu" (:tyomenetelma lomake))
        kayttajarooli (roolit/osapuoli @istunto/kayttaja)
        ;; Paikkauskohde on tilattivissa, kun sen tila on "ehdotettu" ja käyttäjä on tilaaja
        voi-tilata? (or (and
                          (= "ehdotettu" (:paikkauskohteen-tila lomake))
                          (= :tilaaja kayttajarooli))
                        false)]
    ;; TODO: Korjaa paikkauskohteesta toiseen siirtyminen (avaa paikkauskohde listalta, klikkaa toista paikkauskohdetta)
    [:div.overlay-oikealla {:style {:width "600px"}}
     (when (not voi-muokata?)
       [:div
        [:div {:style {:padding-left "16px" :padding-top "32px"}}
         [:div.pieni-teksti (:nro lomake)]
         [:div {:style {:font-size 16 :font-weight "bold"}} (:nimi lomake)]]

        (if (:paikkauskohteen-tila lomake)
          [:div.row
           [:div.col-xs-12
            [:div {:class (str (:paikkauskohteen-tila lomake) "-bg")
                   :style {:display "inline-block"}}
             [:div
              [:div {:class (str "circle "
                                 (cond
                                   (= "tilattu" (:paikkauskohteen-tila lomake)) "tila-tilattu"
                                   (= "ehdotettu" (:paikkauskohteen-tila lomake)) "tila-ehdotettu"
                                   (= "valmis" (:paikkauskohteen-tila lomake)) "tila-valmis"
                                   (= "hylatty" (:paikkauskohteen-tila lomake)) "tila-hylatty"
                                   :default "tila-ehdotettu"))}]
              [:span (str/capitalize (:paikkauskohteen-tila lomake))]]]
            (when (:tilattupvm lomake)
              [:span.pieni-teksti {:style {:padding-left "24px"
                                           :display "inline-block"}}
               (str "Tilauspvm " (harja.fmt/pvm (:tilattupvm lomake)))])
            [:span.pieni-teksti {:style {:padding-left "24px"
                                         :display "inline-block"}}
             (if (:muokattu lomake)
               (str "Päivitetty " (harja.fmt/pvm (:muokattu lomake)))
               "Ei päivitystietoa")]]]
          [:span "Tila ei tiedossa"])
        ;; Jos kohde on hylätty, urakoitsija ei voi muokata sitä enää.
        (when (or (= :tilaaja (roolit/osapuoli @istunto/kayttaja)) ;; Tilaaja voi muokata missä tahansa tilassa olevaa paikkauskohdetta
                  (and (= :urakoitsija (roolit/osapuoli @istunto/kayttaja))
                       (not= "hylatty" (:paikkauskohteen-tila lomake)))
                  false ;; Defaulttina piilossa
                  )
          [:div.col-xs-12 {:style {:padding-top "24px"}}
           [napit/muokkaa "Muokkaa kohdetta" #(e! (t-paikkauskohteet/->AvaaLomake (assoc lomake :tyyppi :paikkauskohteen-muokkaus)))]])

        [:hr]

        ;; Lukutilassa on kaksi erilaista vaihtoehtoa - tilattu tai valmis  ja muut
        (if (or (= "tilattu" (:paikkauskohteen-tila lomake))
                (= "valmis" (:paikkauskohteen-tila lomake)))
          ;; Tilattu
          [:div.col-xs-12
           [lukutila-rivi "Työmenetelmä" (:tyomenetelma lomake)]
           ;; Sijainti
           [lukutila-rivi "Sijainti" (t-paikkauskohteet/fmt-sijainti (:tie lomake) (:aosa lomake) (:losa lomake)
                                                                     (:aet lomake) (:let lomake))]
           ;; Lisätiedot
           [lukutila-rivi "Lisätiedot" (:lisatiedot lomake)]
           ;; Harmaisiin laatikoihin
           [:div.row {:style {:background-color "#F0F0F0" :margin-bottom "4px"}}
            [:div.col-xs-12
             [:h4 "AIKATAULU"]]
            [:div.row
             [:div.col-xs-6
              [lukutila-rivi
               "Arvioitu aikataulu"
               (if (and (:alkupvm lomake) (:loppupvm lomake))
                 (harja.fmt/pvm-vali [(:alkupvm lomake) (:loppupvm lomake)])
                 "Ei arviota")]]
             [:div.col-xs-6
              [lukutila-rivi
               "Toteutunut aikataulu"
               nil]]]]
           [:div.row {:style {:background-color "#F0F0F0" :margin-bottom "4px"}}
            [:div.col-xs-12
             [:h4 "MÄÄRÄ"]]
            [:div.row
             [:div.col-xs-6
              [lukutila-rivi "Suunniteltu määrä" (str (:suunniteltu-maara lomake) " " (:yksikko lomake))]];; :koostettu-maara
             [:div.col-xs-6
              [lukutila-rivi "Toteutunut määrä" (str " - " )]]]]
           (when (oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (-> @tila/tila :yleiset :urakka :id))
             [:div.row {:style {:background-color "#F0F0F0" :margin-bottom "4px"}}
              [:div.col-xs-12
               [:h4 "KUSTANNUKSET"]]
              [:div.row
               [:div.col-xs-6
                [lukutila-rivi "Suunniteltu hinta" (fmt/euro-opt (:suunniteltu-hinta lomake))]];; :koostettu-maara
               [:div.col-xs-6
                [lukutila-rivi "Toteutunut hinta" (fmt/euro-opt (:toteutunut-hinta lomake))]]]])]
          ;; Ja muut
          [:div.col-xs-12
           [lukutila-rivi "Työmenetelmä" (:tyomenetelma lomake)]
           ;; Sijainti
           [lukutila-rivi "Sijainti" (t-paikkauskohteet/fmt-sijainti (:tie lomake) (:aosa lomake) (:losa lomake)
                                                                     (:aet lomake) (:let lomake))]
           [lukutila-rivi
            "Suunniteltu aikataulu"
            (if (and (:alkupvm lomake) (:loppupvm lomake))
              (harja.fmt/pvm-vali [(:alkupvm lomake) (:loppupvm lomake)])
              "Suunniteltua aikataulua ei löytynyt")]
           [lukutila-rivi "Suunniteltu määrä" (str (:suunniteltu-maara lomake) " " (:yksikko lomake))] ;; :koostettu-maara
           (when (oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (-> @tila/tila :yleiset :urakka :id))
             [lukutila-rivi "Suunniteltu hinta" (fmt/euro-opt (:suunniteltu-hinta lomake))])
           [lukutila-rivi "Lisätiedot" (:lisatiedot lomake)]])])

     [lomake/lomake
      {:ei-borderia? true
       :voi-muokata? voi-muokata?
       :otsikko (when voi-muokata?
                  (if (:id lomake) "Muokkaa paikkauskohdetta" "Ehdota paikkauskohdetta"))
       :muokkaa! #(e! (t-paikkauskohteet/->PaivitaLomake (lomake/ilman-lomaketietoja %)))
       :footer-fn (fn [lomake]
                    (let [lomake-ilman-lomaketietoja (lomake/ilman-lomaketietoja lomake)
                          urakoitsija? (= (roolit/osapuoli @istunto/kayttaja) :urakoitsija)]
                      [:div.row
                       [:hr]
                       (when (and (not urakoitsija?) voi-tilata? (not voi-muokata?))
                         [:div.col-xs-9 {:style {:padding "8px 0 8px 0"}} "Urakoitsija saa sähköpostiin ilmoituksen, kuin tilaat tai hylkäät paikkauskohde-ehdotuksen."])
                       (when voi-muokata?
                         [:div.row
                          [:div.col-xs-6 {:style {:padding-left "0"}}
                           [napit/tallenna
                            "Tallenna"
                            #(e! (t-paikkauskohteet/->TallennaPaikkauskohde lomake-ilman-lomaketietoja))]
                           [napit/yleinen-toissijainen
                            "Peruuta"
                            #(e! (t-paikkauskohteet/->SuljeLomake))]]
                          [:div.col-xs-6 {:style {:text-align "end"}}
                           [napit/yleinen-toissijainen
                            "Poista"
                            #(e! (t-paikkauskohteet/->PoistaPaikkauskohde lomake-ilman-lomaketietoja))]]])
                       (when (and voi-tilata? (not voi-muokata?))
                         [:div.row
                          [:div.col-xs-6 {:style {:padding-left "0"}}
                           [napit/tallenna
                            "Tilaa"
                            #(e! (t-paikkauskohteet/->TilaaPaikkauskohde lomake-ilman-lomaketietoja))]
                           [napit/yleinen-toissijainen
                            "Hylkää"
                            #(e! (t-paikkauskohteet/->HylkaaPaikkauskohde lomake-ilman-lomaketietoja))]]
                          [:div.col-xs-6 {:style {:text-align "end"}}
                           [napit/yleinen-toissijainen
                            "Sulje"
                            #(e! (t-paikkauskohteet/->SuljeLomake))]]])
                       ;; Tämä on ainut tilanne, missä tulee vain yksi nappi
                       (when (and (not voi-muokata?) (not voi-tilata?))
                         [:div.row
                          [:div.col-xs-12 {:style {:text-align "end"}}
                           [napit/yleinen-toissijainen
                            "Sulje"
                            #(e! (t-paikkauskohteet/->SuljeLomake))]]])
                       ]))}
      (paikkauskohde-skeema e! muu-menetelma? voi-muokata? lomake) ;;TODO: korjaa päivitys
      lomake]]))

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
