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

(defn suunnitelman-kentat [voi-muokata?]
  (if voi-muokata?
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
       )]
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
     (when (oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (-> @tila/tila :yleiset :urakka :id))
       {:otsikko "Suunniteltu hinta"
        :nimi :suunniteltu-hinta
        :uusi-rivi? true
        :fmt fmt/euro-opt
        :tyyppi :string})
     {:otsikko "Lisatiedot"
      :nimi :lisatiedot
      :uusi-rivi? true
      :tyyppi :string}]))

(defn sijainnin-kentat [voi-muokata?]
  (if voi-muokata?
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
        :nimi :let})]
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
        ::lomake/col-luokka "col-sm-6"})]
    [
     {:nimi :paikkauskohteen-tila
      :tyyppi :komponentti
      ::lomake/col-luokka "col-xs-12"
      :uusi-rivi? true
      :komponentti (fn [{:keys [data]}]
                     (let [arvo (:paikkauskohteen-tila data)]
                       (if arvo
                         [:span
                          [:div {:class (str arvo "-bg")
                                 :style {:display "inline-block"}}
                           [:div
                            [:div {:class (str "circle "
                                               (cond
                                                 (= "tilattu" arvo) "tila-tilattu"
                                                 (= "ehdotettu" arvo) "tila-ehdotettu"
                                                 (= "valmis" arvo) "tila-valmis"
                                                 (= "hylatty" arvo) "tila-hylatty"
                                                 :default "tila-ehdotettu"))}]
                            [:span (str/capitalize arvo)]]]
                          [:span.pieni-teksti {:style {:padding-left "24px"
                                                       :display "inline-block"}}
                           (if (:muokattu data)
                             (str "Päivitetty " (harja.fmt/pvm (:muokattu data)))
                             "Ei päivitystietoa")]]
                         "Tila ei tiedossa")))}
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
        muu-menetelma? (= "Muu" (:tyomenetelma lomake))
        kayttajarooli (roolit/osapuoli @istunto/kayttaja)
        ;; Paikkauskohde on tilattivissa, kun sen tila on "ehdotettu" ja käyttäjä on tilaaja
        voi-tilata? (or (and
                          (= "ehdotettu" (:paikkauskohteen-tila lomake))
                          (= :tilaaja kayttajarooli))
                        false)
        _ (js/console.log "lomake " (pr-str (dissoc lomake :sijainti)))
        _ (js/console.log "voi-muokata? " (pr-str voi-muokata?))
        _ (js/console.log "voi-tilata? " (pr-str voi-tilata?))
        ]
    ;; TODO: Korjaa paikkauskohteesta toiseen siirtyminen (avaa paikkauskohde listalta, klikkaa toista paikkauskohdetta)
    [:div.overlay-oikealla {:style {:width "600px"}}
     (when-not voi-muokata?
       (do
         (js/console.log "whenistä läpi " (:nimi lomake))
         [:div {:style {:padding-left "16px" :padding-top "16px"}}
          [:div.pieni-teksti (:nro lomake)]
          [:div {:style {:font-size 16 :font-weight "bold"}} (:nimi lomake)]]))
     [lomake/lomake
      {;:luokka " overlay-oikealla"
       ;:overlay {:leveys "600px"}
       :ei-borderia? true
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
                            "Sulje"
                            #(e! (t-paikkauskohteet/->SuljeLomake))]]])
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
      (paikkauskohde-skeema e! muu-menetelma? voi-muokata?) ;;TODO: korjaa päivitys
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
