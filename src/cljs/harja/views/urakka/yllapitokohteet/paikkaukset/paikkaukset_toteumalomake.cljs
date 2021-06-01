(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-toteumalomake
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
            [harja.ui.validointi :as validointi]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-toteumalomake :as t-toteumalomake]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as t-paikkauskohteet]))

(defn- fmt-vector
  "Input -kentälle voidaan antaa joko numeerin arvo esim. 1, tai sitten vectorissa arvot [1 2].
  Tehdään yksi formatointifunktio, joka osaa näyttää näissä kaikissa tilanteissa luvut lukutilassa oikein.
  Ja lisäksi näytetään viiva - mikäli arvoa ei ole annettu ollenkaan."
  [v]
  (if (vector? v)
    (clojure.string/join ", " v)
    (if (or (nil? v) (and (vector? v) (empty? v)))
      "-"
      v)))

(defn- maarakentan-otsikko
  "Määrä -kentän otsikko riippuu valitusta yksiköstä"
  [yksikko]
  (cond
    (= "kpl" yksikko) "Kappalemäärä"
    (= "m2" yksikko) "Pinta-ala"
    (= "jm" yksikko) "Juoksumetriä"
    (= "t" yksikko) "Tonnia"
    :else "Tonnia"))

(defn paivamaara-kentat [toteumalomake tyomenetelmat]
  [(lomake/ryhma
     {:otsikko ""}
     {:otsikko "Työ alkoi"
      :tyyppi (if (= "UREM" (paikkaus/tyomenetelma-id->lyhenne (:tyomenetelma toteumalomake) tyomenetelmat))
                :pvm-aika
                :pvm)
      :nimi :alkuaika
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (validointi/nayta-virhe? [:alkuaika] toteumalomake)
      ::lomake/col-luokka "col-sm-3"}
     {:otsikko "Työ päättyi"
      :tyyppi (if (= "UREM" (paikkaus/tyomenetelma-id->lyhenne (:tyomenetelma toteumalomake) tyomenetelmat))
                :pvm-aika
                :pvm)
      :nimi :loppuaika
      :pakollinen? true
      :vayla-tyyli? true
      :pvm-tyhjana #(:alkuaika %)
      :rivi toteumalomake
      :virhe? (validointi/nayta-virhe? [:loppuaika] toteumalomake)
      ::lomake/col-luokka "col-sm-3"})])

(defn maara-kentat [toteumalomake tyomenetelmat]
  (let [urem? (= "UREM" (paikkaus/tyomenetelma-id->lyhenne (:tyomenetelma toteumalomake) tyomenetelmat))]
    (if (or (= "AB-paikkaus levittäjällä" (paikkaus/tyomenetelma-id->nimi (:tyomenetelma toteumalomake) tyomenetelmat))
            (= "PAB-paikkaus levittäjällä" (paikkaus/tyomenetelma-id->nimi (:tyomenetelma toteumalomake) tyomenetelmat))
            (= "SMA-paikkaus levittäjällä" (paikkaus/tyomenetelma-id->nimi (:tyomenetelma toteumalomake) tyomenetelmat))
            urem?)
      [(lomake/ryhma
         {:otsikko "Määrä"
          :ryhman-luokka "lomakeryhman-otsikko-tausta"
          :rivi? true}
         (when (not urem?)
           {:otsikko "Massatyyppi"
            :tyyppi :valinta
            :valinta-arvo first
            :valinta-nayta second
            :valinnat {nil "Valitse"
                       "AB, Asfalttibetoni" "AB, Asfalttibetoni",
                       "SMA, Kivimastiksiasfaltti" "SMA, Kivimastiksiasfaltti"
                       "PAB-B, Pehmeät asfalttibetonit" "PAB-B, Pehmeät asfalttibetonit"
                       "PAB-V, Pehmeät asfalttibetonit" "PAB-V, Pehmeät asfalttibetonit"
                       "PAB-O, Pehmeät asfalttibetonit" "PAB-O, Pehmeät asfalttibetonit"
                       "VA, valuasfaltti" "VA, valuasfaltti"
                       "SIP, Sirotepintaus" "SIP, Sirotepintaus"
                       "SOP, Soratien pintaus" "SOP, Soratien pintaus"}
            :nimi :massatyyppi
            :pakollinen? true
            :vayla-tyyli? true
            :virhe? (validointi/nayta-virhe? [:massatyyppi] toteumalomake)
            ::lomake/col-luokka "col-sm-6"
            :rivi-luokka "lomakeryhman-rivi-tausta"})
         (when (not urem?)
           {:otsikko "Max raekoko"
            :tyyppi :valinta
            :valinta-arvo first
            :valinta-nayta second
            :valinnat {nil "Valitse"
                       5 5
                       8 8
                       11 11
                       16 16
                       22 22
                       31 31}
            :nimi :raekoko
            :pakollinen? true
            :vayla-tyyli? true
            :virhe? (validointi/nayta-virhe? [:raekoko] toteumalomake)
            ::lomake/col-luokka "col-sm-3"
            :rivi-luokka "lomakeryhman-rivi-tausta"})
         (when (not urem?)
           {:otsikko "KM-arvo"
            :tyyppi :string
            :nimi :kuulamylly
            :pakollinen? true
            :vayla-tyyli? true
            :virhe? (validointi/nayta-virhe? [:kuulamylly] toteumalomake)
            ::lomake/col-luokka "col-sm-3"
            :rivi-luokka "lomakeryhman-rivi-tausta"})
         )
       (lomake/rivi
         {:otsikko "Kok. massam."
          :tyyppi :positiivinen-numero
          :nimi :massamenekki
          :yksikko "t"
          :piilota-yksikko-otsikossa? true
          :pakollinen? true
          :vayla-tyyli? true
          :virhe? (validointi/nayta-virhe? [:massamenekki] toteumalomake)
          ::lomake/col-luokka "col-sm-3"
          :rivi-luokka "lomakeryhman-rivi-tausta"}
         (when-not (not urem?)
           {:otsikko "Massamäärä"
            :tyyppi :positiivinen-numero
            :nimi :massamaara
            :yksikko "kg/m2"
            :piilota-yksikko-otsikossa? true
            :pakollinen? true
            :vayla-tyyli? true
            :virhe? (validointi/nayta-virhe? [:massamaara] toteumalomake)
            ::lomake/col-luokka "col-sm-3"
            :rivi-luokka "lomakeryhman-rivi-tausta"})
         (when-not (not urem?)
           {:otsikko "Leveys"
            :tyyppi :positiivinen-numero
            :nimi :leveys
            :yksikko "m"
            :piilota-yksikko-otsikossa? true
            :pakollinen? true
            :vayla-tyyli? true
            :virhe? (validointi/nayta-virhe? [:leveys] toteumalomake)
            ::lomake/col-luokka "col-sm-3"
            :rivi-luokka "lomakeryhman-rivi-tausta"})
         {:otsikko "Pinta-ala"
          :tyyppi :positiivinen-numero
          :nimi :pinta-ala
          :yksikko "m2"
          :piilota-yksikko-otsikossa? true
          :pakollinen? true
          :vayla-tyyli? true
          :virhe? (validointi/nayta-virhe? [:pinta-ala] toteumalomake)
          ::lomake/col-luokka "col-sm-3"
          :rivi-luokka "lomakeryhman-rivi-tausta"})]

      ;; Muille työmenetelmille lomake on hieman erilainen
      [(lomake/ryhma
         {:otsikko "Määrä"
          :ryhman-luokka "lomakeryhman-otsikko-tausta"}
         {:otsikko (maarakentan-otsikko (:kohteen-yksikko toteumalomake))
          :tyyppi :positiivinen-numero
          :nimi :maara
          :pakollinen? true
          :vayla-tyyli? true
          :virhe? (validointi/nayta-virhe? [:maara] toteumalomake)
          ::lomake/col-luokka "col-sm-6"})])))

(defn materiaali-kentat
  "Vain UREM työmenetelmän paikkaustoteumille eritellään asiat materiaalikenttiin"
  [toteumalomake]
  [(lomake/ryhma
     {:otsikko "Materiaali"
      :ryhman-luokka "lomakeryhman-otsikko-tausta"
      :rivi? true}
     {:otsikko "Tyyppi"
      :tyyppi :valinta
      :valinta-arvo first
      :valinta-nayta second
      :valinnat {nil "Valitse"
                 "AB, Asfalttibetoni" "AB, Asfalttibetoni",
                 "SMA, Kivimastiksiasfaltti" "SMA, Kivimastiksiasfaltti"
                 "PAB-B, Pehmeät asfalttibetonit" "PAB-B, Pehmeät asfalttibetonit"
                 "PAB-V, Pehmeät asfalttibetonit" "PAB-V, Pehmeät asfalttibetonit"
                 "PAB-O, Pehmeät asfalttibetonit" "PAB-O, Pehmeät asfalttibetonit"
                 "VA, valuasfaltti" "VA, valuasfaltti"
                 "SIP, Sirotepintaus" "SIP, Sirotepintaus"
                 "SOP, Soratien pintaus" "SOP, Soratien pintaus"}
      :nimi :massatyyppi
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (validointi/nayta-virhe? [:massatyyppi] toteumalomake)
      ::lomake/col-luokka "col-sm-4"
      :rivi-luokka "lomakeryhman-rivi-tausta"}
     {:otsikko "Raekoko"
      :tyyppi :valinta
      :valinta-arvo first
      :valinta-nayta second
      :valinnat {nil "Valitse"
                 1 1
                 5 5
                 8 8
                 11 11
                 16 16
                 22 22
                 31 31}
      :nimi :raekoko
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (validointi/nayta-virhe? [:raekoko] toteumalomake)
      ::lomake/col-luokka "col-sm-4"
      :rivi-luokka "lomakeryhman-rivi-tausta"}
     {:otsikko "KM-luokka"
      :tyyppi :string
      :nimi :kuulamylly
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (validointi/nayta-virhe? [:kuulamylly] toteumalomake)
      ::lomake/col-luokka "col-sm-4"
      :rivi-luokka "lomakeryhman-rivi-tausta"}
     )])

(defn sijainnin-kentat [toteumalomake tyomenetelmat]
  (if (not= "UREM" (paikkaus/tyomenetelma-id->lyhenne (:tyomenetelma toteumalomake) tyomenetelmat))
    ;; Muille kuin uremille
    [(lomake/ryhma
       {:otsikko "Sijainti"
        :rivi? true
        :ryhman-luokka "lomakeryhman-otsikko-tausta"}

       {:otsikko "Tie"
        :tyyppi :positiivinen-numero
        :nimi :tie
        :pakollinen? true
        :vayla-tyyli? true
        :virhe? (validointi/nayta-virhe? [:tie] toteumalomake)
        :rivi-luokka "lomakeryhman-rivi-tausta"}
       {:otsikko "Ajorata"
        :tyyppi :valinta
        :valinnat {nil "Ei ajorataa"
                   0 0
                   1 1
                   2 2} ; TODO: Hae tietokannasta
        :valinta-arvo first
        :valinta-nayta second
        :nimi :ajorata
        :vayla-tyyli? true
        :pakollinen? (if
                       (paikkaus/levittimella-tehty? toteumalomake tyomenetelmat)
                       true
                       false)}
       (when (paikkaus/levittimella-tehty? toteumalomake tyomenetelmat)
         {:otsikko "Kaista"
          :tyyppi :positiivinen-numero
          :nimi :kaista
          :pakollinen? true
          :vayla-tyyli? true
          :virhe? (validointi/nayta-virhe? [:kaista] toteumalomake)
          :rivi-luokka "lomakeryhman-rivi-tausta"}))
     (lomake/rivi
       {:otsikko "A-osa"
        :tyyppi :numero
        :pakollinen? true
        :nimi :aosa
        :vayla-tyyli? true
        :virhe? (validointi/nayta-virhe? [:aosa] toteumalomake)
        :rivi-luokka "lomakeryhman-rivi-tausta"}
       {:otsikko "A-et."
        :tyyppi :numero
        :pakollinen? true
        :vayla-tyyli? true
        :virhe? (validointi/nayta-virhe? [:aet] toteumalomake)
        :nimi :aet}
       {:otsikko "L-osa."
        :tyyppi :numero
        :pakollinen? true
        :vayla-tyyli? true
        :virhe? (validointi/nayta-virhe? [:losa] toteumalomake)
        :nimi :losa}
       {:otsikko "L-et."
        :tyyppi :numero
        :pakollinen? true
        :vayla-tyyli? true
        :virhe? (validointi/nayta-virhe? [:let] toteumalomake)
        :nimi :let}
       {:otsikko "Pituus (m)"
        :tyyppi :numero
        :vayla-tyyli? true
        :disabled? true
        :nimi :pituus
        :tarkkaile-ulkopuolisia-muutoksia? true
        :muokattava? (constantly false)})]

    ;; Uremin sijainnit ovat vähän erilaiset
    [(lomake/ryhma
       {:otsikko "Sijainti"
        :rivi? true
        :ryhman-luokka "lomakeryhman-otsikko-tausta"}

       {:otsikko "Tie"
        :tyyppi :string
        :nimi :formatoitu-sijainti
        :hae (fn [rivi]
               (str (str (:tie rivi) " - " (:aosa rivi) "/" (:aet rivi) " - " (:losa rivi) "/" (:let rivi))))
        :rivi-luokka "lomakeryhman-rivi-tausta"})

     (lomake/rivi
       {:otsikko "Leveys"
        :tyyppi :positiivinen-numero
        :nimi :leveys
        :yksikko "m"
        :piilota-yksikko-otsikossa? true
        :pakollinen? true
        :vayla-tyyli? true
        ;::lomake/col-luokka "col-sm-3"
        :rivi-luokka "lomakeryhman-rivi-tausta"}
       {:otsikko "Ajourat"
        :tyyppi :string
        :pakollinen? true
        :vayla-tyyli? true
        :nimi :ajourat
        :fmt fmt-vector}
       {:otsikko "Reunat"
        :tyyppi :string
        :pakollinen? true
        :vayla-tyyli? true
        :nimi :reunat
        :fmt fmt-vector}
       {:otsikko "Urien välit"
        :tyyppi :string
        :pakollinen? true
        :vayla-tyyli? true
        :nimi :ajouravalit
        :fmt fmt-vector}
       {:otsikko "Keskisauma"
        :tyyppi :string
        :pakollinen? true
        :vayla-tyyli? true
        :nimi :keskisaumat
        :fmt fmt-vector})]))

(defn toteuma-skeema
  "Määritellään toteumalomakkeen skeema. Skeema on jaoteltu osiin, jotta saadaan ulkonäöllisesti harmaat laatikot
  rakennettua eri input elementtien ympärille."
  [toteumalomake tyomenetelmat]
  (let [pvmkentat (paivamaara-kentat toteumalomake tyomenetelmat)
        sijainti (sijainnin-kentat toteumalomake tyomenetelmat)
        maara (maara-kentat toteumalomake tyomenetelmat)
        materiaali (when (= "UREM" (paikkaus/tyomenetelma-id->lyhenne (:tyomenetelma toteumalomake) tyomenetelmat))
                     (materiaali-kentat toteumalomake))]
    (vec (concat pvmkentat
                 sijainti
                 materiaali
                 maara))))

(defn- footer-vasemmat-napit [e! toteumalomake muokkaustila?]
  (let [voi-tallentaa? (::tila/validi? toteumalomake)]
    [:div
     ;; Lomake on auki
     (when muokkaustila?
       [:div
        [napit/tallenna
         "Tallenna muutokset"
         #(e! (t-toteumalomake/->TallennaToteuma (lomake/ilman-lomaketietoja toteumalomake)))
         {:disabled (not voi-tallentaa?) :paksu? true}]
        ;; Toteuman on pakko olla tietokannassa, ennenkuin sen voi poistaa
        (when (:id toteumalomake)
          [napit/yleinen-toissijainen
           "Poista toteuma"
           (t-paikkauskohteet/nayta-modal
             (str "Poistetaanko toteuma ?")
             "Toimintoa ei voi perua."
             [napit/yleinen-toissijainen "Poista toteuma" #(e! (t-toteumalomake/->PoistaToteuma
                                                                 (lomake/ilman-lomaketietoja toteumalomake))) {:paksu? true}]
             [napit/yleinen-toissijainen "Säilytä toteuma" modal/piilota! {:paksu? true}])
           {:ikoni (ikonit/livicon-trash) :paksu? true}])])]))

(defn- toteumalomake-header [toteumalomake tyomenetelmat]
  [:div.ei-borderia.lukutila
   [:div {:style {:padding "0 16px"}}
    [:h2 (cond
           (= "harja-api" (:lahde toteumalomake)) "Toteuman tiedot"
           (:id toteumalomake) "Muokkaa toteumaa"
           :else "Uusi toteuma")]
    [:h4 {:style {:margin-bottom 0}} (:paikkauskohde-nimi toteumalomake)]
    [:div.pieni-teksti (paikkaus/tyomenetelma-id->nimi (:tyomenetelma toteumalomake) tyomenetelmat)]
    [:hr]]])

(defn toteumalomake [e! app]
  (let [toteumalomake (:toteumalomake app)
        tyomenetelmat (:tyomenetelmat app)
        ;; Vain harja-ui:n kautta tulleita toteumia voi muokata - tarkistetaan negatiivisen kautta, koska tyhjällä lomakkeella ei ole
        ;; lähdettä vielä asetettuna
        muokkaustila? (and (not= "harja-api" (:lahde toteumalomake))
                           (or
                             (= :toteuman-muokkaus (:tyyppi toteumalomake))
                             (= :uusi-toteuma (:tyyppi toteumalomake))))]

    ;; Wrapataan lomake vain diviin. Koska tämä aukaistaan eri kokoisiin oikealta avattaviin diveihin eri näkymistä
    [:div {:style {:padding "16px"}}
     [lomake/lomake
      {:ei-borderia? true
       :voi-muokata? muokkaustila?
       :header-fn #(toteumalomake-header toteumalomake tyomenetelmat)
       :muokkaa! #(e! (t-toteumalomake/->PaivitaLomake (lomake/ilman-lomaketietoja %)))
       :footer-fn (fn [toteumalomake]
                    [:div.flex-row
                     ;; UI on jaettu kahteen osioon. Oikeaan ja vasempaan.
                     ;; Tarkistetaan ensin, että mitkä näapit tulevat vasemmalle
                     [footer-vasemmat-napit e! toteumalomake muokkaustila?]
                     [napit/yleinen-toissijainen
                      (if muokkaustila? "Peruuta" "Sulje")
                      #(e! (t-toteumalomake/->SuljeToteumaLomake))
                      {:paksu? true}]])}
      (toteuma-skeema toteumalomake tyomenetelmat)
      toteumalomake]]))
