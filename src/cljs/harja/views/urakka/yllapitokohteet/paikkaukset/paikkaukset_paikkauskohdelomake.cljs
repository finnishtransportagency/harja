(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohdelomake
  (:require [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [clojure.string :as str]
            [harja.domain.oikeudet :as oikeudet]
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
          ::lomake/col-luokka "col-sm-3"
          :rivi-luokka "lomakeryhman-rivi-tausta"}
         {:otsikko "Yksikkö"
          :tyyppi :valinta
          :valinnat ["m²" "t" "kpl" "jm"]
          :nimi :yksikko
          :pakollinen? true
          ::lomake/col-luokka "col-sm-3"}
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
     {:otsikko "Suunniteltu hinta"
      :nimi :suunniteltu-hinta
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
