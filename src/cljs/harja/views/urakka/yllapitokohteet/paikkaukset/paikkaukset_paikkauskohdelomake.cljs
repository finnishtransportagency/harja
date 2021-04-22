(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohdelomake
  (:require [harja.loki :refer [log]]
            [clojure.string :as str]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]
            [harja.domain.paikkaus :as paikkaus]
            [harja.fmt :as fmt]
            [harja.ui.lomake :as lomake]
            [harja.ui.modal :as modal]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.oikea-sivupalkki :as oikea-sivupalkki]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as t-paikkauskohteet]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-toteumalomake :as t-toteumalomake]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-toteumalomake :as v-toteumalomake]))

(defn nayta-virhe? [polku lomake]
  (let [validi? (if (nil? (get-in lomake polku))
                  true ;; kokeillaan palauttaa true, jos se on vaan tyhjä. Eli ei näytetä virhettä tyhjälle kentälle
                  (get-in lomake [::tila/validius polku :validi?]))]
    ;; Koska me pohjimmiltaan tarkistetaan, validiutta, mutta palautetaan tieto, että näytetäänkö virhe, niin käännetään
    ;; boolean ympäri
    (not validi?)))

(defn- lukutila-rivi [otsikko arvo]
  [:div {:style {:padding-top "16px" :padding-bottom "8px"}}
   [:div.row
    [:span {:style {:font-weight 400 :font-size "12px" :color "#5C5C5C"}} otsikko]]
   [:div.row
    [:span {:style {:font-weight 400 :font-size "14px" :line-height "20px" :color "black"}} arvo]]])

(defn suunnitelman-kentat [lomake]
  [(lomake/ryhma
     {:otsikko "Alustava suunnitelma"
      :ryhman-luokka "lomakeryhman-otsikko-tausta"}
     {:otsikko "Arv. aloitus"
      :tyyppi :pvm
      :nimi :alkupvm
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (nayta-virhe? [:alkupvm] lomake)
      ::lomake/col-luokka "col-sm-6"}
     {:otsikko "Arv. lopetus"
      :tyyppi :pvm
      :nimi :loppupvm
      :pakollinen? true
      :vayla-tyyli? true
      :pvm-tyhjana #(:alkupvm %)
      :rivi lomake
      :virhe? (nayta-virhe? [:loppupvm] lomake)
      ::lomake/col-luokka "col-sm-6"})
   (lomake/rivi
     {:otsikko "Suunniteltu määrä"
      :tyyppi :positiivinen-numero
      :nimi :suunniteltu-maara
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (nayta-virhe? [:suunniteltu-maara] lomake)
      ::lomake/col-luokka "col-sm-4"
      :rivi-luokka "lomakeryhman-rivi-tausta"
      :disabled? (when (= "tilattu" (:paikkauskohteen-tila lomake))
                   true)}
     {:otsikko "Yksikkö"
      :tyyppi :valinta
      :valinnat (vec paikkaus/paikkauskohteiden-yksikot)
      :nimi :yksikko
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (nayta-virhe? [:yksikko] lomake)
      ::lomake/col-luokka "col-sm-2"
      :disabled? (when (= "tilattu" (:paikkauskohteen-tila lomake))
                   true)}
     {:otsikko "Suunniteltu hinta"
      :tyyppi :numero
      :desimaalien-maara 2
      :piilota-yksikko-otsikossa? true
      :nimi :suunniteltu-hinta
      ::lomake/col-luokka "col-sm-6"
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (nayta-virhe? [:suunniteltu-hinta] lomake)
      :yksikko "€"
      :disabled? (when (= "tilattu" (:paikkauskohteen-tila lomake))
                   true)})
   (lomake/rivi
     {:otsikko "Lisätiedot"
      :tyyppi :text
      :nimi :lisatiedot
      :pakollinen? false
      ::lomake/col-luokka "col-sm-12"
      :rivi-luokka "lomakeryhman-rivi-tausta"}
     )])

(defn sijainnin-kentat [lomake]
  [(lomake/ryhma
     {:otsikko "Sijainti"
      :rivi? true
      :ryhman-luokka "lomakeryhman-otsikko-tausta"}

     {:otsikko "Tie"
      :tyyppi :numero
      :nimi :tie
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (nayta-virhe? [:tie] lomake)
      :rivi-luokka "lomakeryhman-rivi-tausta"}
     {:otsikko "Ajorata"
      :tyyppi :valinta
      :valinnat {nil "Ei ajorataa"
                 0 0
                 1 1
                 2 2}
      :valinta-arvo first
      :valinta-nayta second
      :nimi :ajorata
      :vayla-tyyli? true
      :pakollinen? false})
   (lomake/rivi
     {:otsikko "A-osa"
      :tyyppi :numero
      :pakollinen? true
      :nimi :aosa
      :vayla-tyyli? true
      :virhe? (nayta-virhe? [:aosa] lomake)
      :rivi-luokka "lomakeryhman-rivi-tausta"}
     {:otsikko "A-et."
      :tyyppi :numero
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (nayta-virhe? [:aet] lomake)
      :nimi :aet}
     {:otsikko "L-osa."
      :tyyppi :numero
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (nayta-virhe? [:losa] lomake)
      :nimi :losa}
     {:otsikko "L-et."
      :tyyppi :numero
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (nayta-virhe? [:let] lomake)
      :nimi :let}
     {:otsikko "Pituus (m)"
      :tyyppi :numero
      :vayla-tyyli? true
      :disabled? true
      :nimi :pituus
      :tarkkaile-ulkopuolisia-muutoksia? true
      :muokattava? (constantly false)})])

(defn nimi-numero-ja-tp-kentat [lomake]
  [{:otsikko "Nimi"
    :tyyppi :string
    :nimi :nimi
    :pakollinen? true
    :vayla-tyyli? true
    :virhe? (nayta-virhe? [:nimi] lomake)
    :validoi [[:ei-tyhja "Anna nimi"]]
    ::lomake/col-luokka "col-sm-6"
    :pituus-max 100}
   {:otsikko "Lask.nro"
    :tyyppi :string
    :nimi :nro
    :vayla-tyyli? true
    :pakollinen? true
    ::lomake/col-luokka "col-sm-3"}
   {:otsikko "Työmenetelmä"
    :tyyppi :valinta
    :nimi :tyomenetelma
    :valinnat paikkaus/paikkauskohteiden-tyomenetelmat
    :valinta-nayta paikkaus/kuvaile-tyomenetelma
    :vayla-tyyli? true
    :virhe? (nayta-virhe? [:tyomenetelma] lomake)
    :pakollinen? true
    ::lomake/col-luokka "col-sm-12"
    :disabled? (when (= "tilattu" (:paikkauskohteen-tila lomake))
                 true)}])

(defn paikkauskohde-skeema [voi-muokata? lomake]
  (let [nimi-nro-ja-tp (when voi-muokata?
                         (nimi-numero-ja-tp-kentat lomake))
        sijainti (when voi-muokata?
                   (sijainnin-kentat lomake))
        suunnitelma (when voi-muokata?
                      (suunnitelman-kentat lomake))]
    (vec (concat nimi-nro-ja-tp
                 sijainti
                 suunnitelma))))

(defn- nayta-pot-valinta?
  " Tilaajalle näytetään kolmen työmenetelmän kohdalla erillinen pot/toteuma radiobutton valinta.
  Mikäli tilaaja valitsee pot vaihtoehdon, toteumia ei kirjata normaaliprossin mukaan, vaan pot-lomakkeelta
  Kolme työmenetelmää ovat: AB-paikkaus levittäjällä, PAB-paikkaus levittäjällä, SMA-paikkaus levittäjällä"
  [lomake]
  (let [
        nayta? (and (t-paikkauskohteet/kayttaja-on-tilaaja? (roolit/osapuoli @istunto/kayttaja))
                    (= "ehdotettu" (:paikkauskohteen-tila lomake))
                    (or (= "AB-paikkaus levittäjällä" (:tyomenetelma lomake))
                        (= "PAB-paikkaus levittäjällä" (:tyomenetelma lomake))
                        (= "SMA-paikkaus levittäjällä" (:tyomenetelma lomake))))]
    nayta?))

(defn- lomake-lukutila [e! lomake nayta-muokkaus? toteumalomake toteumalomake-auki?]
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
         [:span (paikkaus/fmt-tila (:paikkauskohteen-tila lomake))]]]
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
   (when nayta-muokkaus?
     [:div.col-xs-12 {:style {:padding-top "24px"}}
      [napit/muokkaa "Muokkaa kohdetta" #(e! (t-paikkauskohteet/->AvaaLomake (assoc lomake :tyyppi :paikkauskohteen-muokkaus))) {:luokka "napiton-nappi" :paksu? true}]])

   [:hr]

   ;; Lukutilassa on kaksi erilaista vaihtoehtoa - tilattu tai valmis ja muut
   (if (or (= "tilattu" (:paikkauskohteen-tila lomake))
           (= "valmis" (:paikkauskohteen-tila lomake)))
     ;; Tilattu
     [:div.col-xs-12
      [lukutila-rivi "Työmenetelmä" (paikkaus/kuvaile-tyomenetelma (:tyomenetelma lomake))]
      ;; Sijainti
      [lukutila-rivi "Sijainti" (t-paikkauskohteet/fmt-sijainti (:tie lomake) (:aosa lomake) (:losa lomake)
                                                                (:aet lomake) (:let lomake))]
      ;; Pituus
      [lukutila-rivi "Kohteen pituus" (str (:pituus lomake) " m")]

      ;; Lisätiedot
      [lukutila-rivi "Lisätiedot" (:lisatiedot lomake)]
      [:div {:style {:padding-bottom "16px"}}]

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
      [:div {:style {:background-color "#F0F0F0" :margin-bottom "4px"}}
       [:div.row
        [:div.col-xs-6
         [:h4 "MÄÄRÄ"]]
        [:div.col-xs-6 {:style {:text-align "end"}}
         [napit/yleinen-toissijainen "Lisää toteuma"
          #(e! (t-toteumalomake/->AvaaToteumaLomake (assoc toteumalomake :tyyppi :uusi-toteuma)))
          {:paksu? true
           :ikoni (ikonit/livicon-plus)}]]]
       [:div.row
        [:div.col-xs-6
         [lukutila-rivi "Suunniteltu määrä" (str (:suunniteltu-maara lomake) " " (:yksikko lomake))]] ;; :koostettu-maara
        [:div.col-xs-6
         [lukutila-rivi "Toteutunut määrä" (str " ")]]]]
      (when (oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (-> @tila/tila :yleiset :urakka :id))
        [:div.row {:style {:background-color "#F0F0F0" :margin-bottom "4px"}}
         [:div.col-xs-12
          [:h4 "KUSTANNUKSET"]]
         [:div.row
          [:div.col-xs-6
           [lukutila-rivi "Suunniteltu hinta" (fmt/euro-opt (:suunniteltu-hinta lomake))]] ;; :koostettu-maara
          [:div.col-xs-6
           [lukutila-rivi "Toteutunut hinta" (fmt/euro-opt (:toteutunut-hinta lomake))]]]])]

     ;; Ja muut
     [:div.col-xs-12
      [lukutila-rivi "Työmenetelmä" (paikkaus/kuvaile-tyomenetelma (:tyomenetelma lomake))]
      ;; Sijainti
      [lukutila-rivi "Sijainti" (t-paikkauskohteet/fmt-sijainti (:tie lomake) (:aosa lomake) (:losa lomake)
                                                                (:aet lomake) (:let lomake))]
      ;; Pituus
      [lukutila-rivi "Kohteen pituus" (str (:pituus lomake) " m")]
      ;; Aikataulu
      [lukutila-rivi
       "Suunniteltu aikataulu"
       (if (and (:alkupvm lomake) (:loppupvm lomake))
         (harja.fmt/pvm-vali [(:alkupvm lomake) (:loppupvm lomake)])
         "Suunniteltua aikataulua ei löytynyt")]
      [lukutila-rivi "Suunniteltu määrä" (str (:suunniteltu-maara lomake) " " (:yksikko lomake))] ;; :koostettu-maara
      (when (oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (-> @tila/tila :yleiset :urakka :id))
        [lukutila-rivi "Suunniteltu hinta" (fmt/euro-opt (:suunniteltu-hinta lomake))])
      [lukutila-rivi "Lisätiedot" (:lisatiedot lomake)]
      [:div {:style {:padding-bottom "16px"}}]])])

(defn- footer-oikeat-napit [e! lomake muokkaustila? voi-tilata? voi-perua?]
  [:div {:style {:text-align "end"}}
   ;; Lomake on auki
   (when muokkaustila?
     [napit/yleinen-toissijainen
      "Peruuta"
      #(e! (t-paikkauskohteet/->SuljeLomake))
      {:paksu? true}])

   ;; Lukutila, tilaajan näkymä
   (when (and voi-tilata? (not muokkaustila?))
     [napit/yleinen-toissijainen
      "Sulje"
      #(e! (t-paikkauskohteet/->SuljeLomake))
      {:paksu? true}])

   ;; Lukutila, urakoitsijan näkymä
   (when (and (not muokkaustila?) (not voi-tilata?) voi-perua?)
     [napit/yleinen-toissijainen
      "Sulje"
      #(e! (t-paikkauskohteet/->SuljeLomake))
      {:paksu? true}]
     )
   ;; Lukutila - ei voi tilata, eikä voi perua
   (when (and (not muokkaustila?) (not voi-tilata?) (not voi-perua?))
     [napit/yleinen-toissijainen
      "Sulje"
      #(e! (t-paikkauskohteet/->SuljeLomake))
      {:paksu? true}])]
  )

(defn- footer-vasemmat-napit [e! lomake muokkaustila? voi-tilata? voi-perua?]
  (let [voi-tallentaa? (::tila/validi? lomake)]
    [:div
     ;; Lomake on auki
     (when muokkaustila?
       [:div
        [napit/tallenna
         "Tallenna muutokset"
         #(e! (t-paikkauskohteet/->TallennaPaikkauskohde (lomake/ilman-lomaketietoja lomake)))
         {:disabled (not voi-tallentaa?) :paksu? true}]
        ;; Paikkauskohde on pakko olla tietokannassa, ennenkuin sen voi poistaa
        ;; Ja sen täytyy olla ehdotettu tai hylatty tilassa. Tilattua tai valmista ei voida poistaa
        (when (and (:id lomake)
                   (or (= (:paikkauskohteen-tila lomake) "ehdotettu")
                       (= (:paikkauskohteen-tila lomake) "hylatty")))
          [napit/yleinen-toissijainen
           "Poista kohde"
           (t-paikkauskohteet/nayta-modal
             (str "Poistetaanko kohde \"" (:nimi lomake) "\"?")
             "Toimintoa ei voi perua."
             [napit/yleinen-toissijainen "Poista kohde" #(e! (t-paikkauskohteet/->PoistaPaikkauskohde
                                                               (lomake/ilman-lomaketietoja lomake))) {:paksu? true}]
             [napit/yleinen-toissijainen "Säilytä kohde" modal/piilota! {:paksu? true}])
           {:ikoni (ikonit/livicon-trash) :paksu? true}])])

     ;; Lukutila, tilaajan näkymä
     (when (and voi-tilata? (not muokkaustila?))
       [:div
        [napit/tallenna
         "Tilaa"
         (t-paikkauskohteet/nayta-modal
           (str "Tilataanko kohde \"" (:nimi lomake) "\"?")
           ;; TODO: Lisää teksti, kunhan sähköpostinlähetys on toteutettu
           "" ;"Urakoitsija saa sähköpostiin ilmoituksen kohteen tilauksesta."
           [napit/yleinen-toissijainen "Tilaa kohde" #(e! (t-paikkauskohteet/->TilaaPaikkauskohde
                                                            (lomake/ilman-lomaketietoja lomake))) {:paksu? true}]
           [napit/yleinen-toissijainen "Kumoa" modal/piilota! {:paksu? true}])
         {:paksu? true}]
        [napit/yleinen-toissijainen
         "Hylkää"
         (t-paikkauskohteet/nayta-modal
           (str "Hylätäänkö kohde " (:nimi lomake) "?")
           ;; TODO: Lisää teksti, kunhan sähköpostinlähetys on toteutettu
           "" ;"Urakoitsija saa sähköpostiin ilmoituksen kohteen hylkäyksestä."
           [napit/yleinen-toissijainen "Hylkää kohde" #(e! (t-paikkauskohteet/->HylkaaPaikkauskohde
                                                             (lomake/ilman-lomaketietoja lomake))) {:paksu? true}]
           [napit/yleinen-toissijainen "Kumoa" modal/piilota! {:paksu? true}])
         {:paksu? true}]])

     ;; Lukutila, tiljaa voi perua tilauksen tai hylätä peruutuksen
     (when (and (not muokkaustila?) (not voi-tilata?) voi-perua?)
       (if (= (:paikkauskohteen-tila lomake) "tilattu")
         [napit/nappi
          "Peru tilaus"
          (t-paikkauskohteet/nayta-modal
            (str "Perutaanko kohteen \"" (:nimi lomake) "\" tilaus ?")
            ""
            [napit/yleinen-toissijainen "Peru tilaus" #(e! (t-paikkauskohteet/->PeruPaikkauskohteenTilaus
                                                             (lomake/ilman-lomaketietoja lomake))) {:paksu? true}]
            [napit/yleinen-toissijainen "Kumoa" modal/piilota! {:paksu? true}])
          {:luokka "napiton-nappi punainen"
           :ikoni (ikonit/livicon-back-circle)}]
         [napit/nappi
          "Kumoa hylkäys"
          (t-paikkauskohteet/nayta-modal
            (str "Perutaanko kohteen " (:nimi lomake) " hylkäys ?")
            ;;TODO: Vaihda teksti, kun sähköpostinlähetys on toteutettu
            "Kohde palautetaan hylätty-tilasta takaisin ehdotettu-tilaan."
            ;"Kohde palautetaan hylätty-tilasta takaisin ehdotettu-tilaan. Urakoitsija saa sähköpostiin ilmoituksen kohteen tilan muutoksesta"
            [napit/yleinen-toissijainen "Peru hylkäys" #(e! (t-paikkauskohteet/->PeruPaikkauskohteenHylkays
                                                              (lomake/ilman-lomaketietoja lomake))) {:paksu? true}]
            [napit/yleinen-toissijainen "Kumoa" modal/piilota! {:paksu? true}])
          {:luokka "napiton-nappi punainen"
           :ikoni (ikonit/livicon-back-circle)}]))]))

(defn paikkauskohde-lomake [e! lomake toteumalomake]
  (let [muokkaustila? (or
                        (= :paikkauskohteen-muokkaus (:tyyppi lomake))
                        (= :uusi-paikkauskohde (:tyyppi lomake)))
        toteumalomake-auki? (or
                              (= :toteuman-muokkaus (:tyyppi toteumalomake))
                              (= :uusi-toteuma (:tyyppi toteumalomake)))
        toteumatyyppi-arvo (atom (:toteumatyyppi lomake))
        kayttajarooli (roolit/osapuoli @istunto/kayttaja)
        ;; Paikkauskohde on tilattivissa, kun sen tila on "ehdotettu" ja käyttäjä on tilaaja
        voi-tilata? (or (and
                          (= "ehdotettu" (:paikkauskohteen-tila lomake))
                          (= :tilaaja kayttajarooli))
                        false)
        voi-perua? (and
                     (= :tilaaja kayttajarooli)
                     (or
                       (= "tilattu" (:paikkauskohteen-tila lomake))
                       (= "hylatty" (:paikkauskohteen-tila lomake))))
        nayta-muokkaus? (or (= :tilaaja (roolit/osapuoli @istunto/kayttaja)) ;; Tilaaja voi muokata missä tahansa tilassa olevaa paikkauskohdetta
                            ;; Tarkista kirjoitusoikeudet
                            (oikeudet/voi-kirjoittaa? oikeudet/urakat-paikkaukset-paikkauskohteet
                                                      (-> @tila/tila :yleiset :urakka :id)
                                                      @istunto/kayttaja)
                            ;; Urakoitsija, jolla on periaatteessa kirjoitusoikeudet ei voi muuttaa enää hylättyä kohdetta
                            (and (= :urakoitsija (roolit/osapuoli @istunto/kayttaja))
                                 (oikeudet/voi-kirjoittaa? oikeudet/urakat-paikkaukset-paikkauskohteet
                                                           (-> @tila/tila :yleiset :urakka :id)
                                                           @istunto/kayttaja)
                                 (not= "hylatty" (:paikkauskohteen-tila lomake)))

                            false ;; Defaulttina estetään muokkaus
                            )
        ;; Pidetään kirjaa validoinnista
        voi-tallentaa? (::tila/validi? lomake)]
    ;; TODO: Korjaa paikkauskohteesta toiseen siirtyminen (avaa paikkauskohde listalta, klikkaa toista paikkauskohdetta)
    [:div.overlay-oikealla {:style {:width "600px" :overflow "auto"}}
     ;; Tarkistetaan muokkaustila
     (when (not muokkaustila?)
       [lomake-lukutila e! lomake nayta-muokkaus? toteumalomake toteumalomake-auki?])

     (when toteumalomake-auki?
       [oikea-sivupalkki/piirra "570px" 2
        ;; Liäsään yskikkö toteumalomakkeelle, jotta osataan näyttää kenttien otsikkotekstit oikein
        [v-toteumalomake/toteumalomake e!
         (-> toteumalomake
             (assoc :tyomenetelma (:tyomenetelma lomake))
             (assoc :kohteen-yksikko (:yksikko lomake))
             (assoc :paikkauskohde-id (:id lomake)))]])

     [lomake/lomake
      {:ei-borderia? true
       :voi-muokata? muokkaustila?
       :otsikko (when muokkaustila?
                  (if (:id lomake) "Muokkaa paikkauskohdetta" "Ehdota paikkauskohdetta"))
       :muokkaa! #(e! (t-paikkauskohteet/->PaivitaLomake (lomake/ilman-lomaketietoja %)))
       :footer-fn (fn [lomake]
                    (let [urakoitsija? (= (roolit/osapuoli @istunto/kayttaja) :urakoitsija)]
                      [:div.row
                       [:hr]

                       ;; Tilaajalle näytetään kolmen työmenetelmän kohdalla erillinen pot/toteuma radiobutton valinta.
                       ;; Mikäli tilaaja valitsee pot vaihtoehdon, toteumia ei kirjata normaaliprossin mukaan, vaan pot-lomakkeelta
                       (when (and (not muokkaustila?)
                               (nayta-pot-valinta? lomake))
                         [:div.row {:style {:background-color "#F0F0F0" :margin-bottom "24px" :padding-bottom "8px"}}
                          [:div.row
                           [:div.col-xs-12
                            [:h4 "RAPORTOINTITAPA"]]]
                          [:div.row {:style {:padding-left "16px"}}
                           [kentat/tee-kentta {:tyyppi :radio-group
                                               :nimi :toteumatyyppi
                                               :otsikko ""
                                               :vaihtoehdot [:normaali :pot]
                                               :nayta-rivina? true
                                               :vayla-tyyli? true
                                               :vaihtoehto-nayta {:pot "POT-lomake"
                                                                  :normaali "Toteumat"}
                                               :valitse-fn #(e! (t-paikkauskohteet/->AsetaToteumatyyppi %))}
                            toteumatyyppi-arvo]]])

                       ;;TODO: Enabloi tämä, kun sähköpostin lähetys on toteutettu
                       #_(when (and (not urakoitsija?) voi-tilata? (not muokkaustila?))
                           [:div.col-xs-9 {:style {:padding "8px 0 8px 0"}} "Urakoitsija saa sähköpostiin ilmoituksen, kuin tilaat tai hylkäät paikkauskohde-ehdotuksen."])

                       ;; UI on jaettu kahteen osioon. Oikeaan ja vasempaan.
                       ;; Tarkistetaan ensin, että mitkä näapit tulevat vasemmalle
                       [:div.row
                        [:div.col-xs-8 {:style {:padding-left "0"}}
                         [footer-vasemmat-napit e! lomake muokkaustila? voi-tilata? voi-perua?]]
                        [:div.col-xs-4
                         [footer-oikeat-napit e! lomake muokkaustila? voi-tilata? voi-perua?]]]]))}
      (paikkauskohde-skeema muokkaustila? lomake) ;;TODO: korjaa päivitys
      lomake]]))

(defn paikkauslomake [e! lomake toteumalomake] ;; TODO: Parempi nimeäminen
  (case (:tyyppi lomake)
    :uusi-paikkauskohde [paikkauskohde-lomake e! lomake toteumalomake]
    :paikkauskohteen-muokkaus [paikkauskohde-lomake e! lomake toteumalomake]
    :paikkauskohteen-katselu [paikkauskohde-lomake e! lomake toteumalomake]
    [:div "Lomaketta ei ole vielä tehty" [napit/yleinen-ensisijainen "Debug/Sulje nappi" #(e! (t-paikkauskohteet/->SuljeLomake))]]))
