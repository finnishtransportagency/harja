(ns harja.views.urakka.yllapitokohteet.reikapaikkaukset
  "Reikäpaikkaukset päänäkymä"
  ;; TODO.. lisätty valmiiksi requireja, poista myöhemmin turhat 
  (:require [tuck.core :refer [tuck]]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-reikapaikkaukset :as tiedot]
            [harja.ui.debug :refer [debug]]
            [reagent.core :refer [atom] :as r]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.ui.lomake :as lomake]
            [harja.ui.liitteet :as liitteet]
            [harja.fmt :as fmt]
            [harja.ui.modal :as modal]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.kentat :as kentat]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.napit :as napit]
            [harja.pvm :as pvm]
            [clojure.string :as str]
            [harja.views.kartta :as kartta]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.tierekisteri :as tr-domain]
            [harja.domain.roolit :as roolit])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))


(defn reikapaikkaus-listaus [e! {:keys [valinnat rivit 
                                        muokataan nayta-virhe-modal 
                                        excel-virheet valittu-rivi 
                                        tyomenetelmat haku-kaynnissa? rivi-maara kustannukset] :as app}]
  (let [alkuaika (:alkuaika valittu-rivi)
        alasveto-valinnat (mapv :id tyomenetelmat)
        alasveto-kuvaukset (into {} (map (fn [{:keys [id nimi]}] [id nimi]) tyomenetelmat))
        ;; oikeus? (oikeudet/voi-kirjoittaa? oikeudet/ (get-in valinnat [:urakka :id])) ;; TODO 
        voi-tallentaa? (and
                         ;; (not oikeus?) ;; TODO 
                         (tiedot/voi-tallentaa? valittu-rivi app))
        ;; TODO, tarkista oikeus
        voi-poistaa? true]

    ;; Tr- osoite
    (add-watch tiedot/tr-atom :tierekisteri-haku
      (fn [_ _ _ uusi]
        (e! (tiedot/->PaivitaValinnat {:tr uusi}))))

    ;; Aikaväli tarkkailu
    (add-watch tiedot/aikavali-atom :aikavali-haku
      (fn [_ _ vanha uusi]
        (when-not
          (and
            (pvm/sama-pvm? (first vanha) (first uusi))
            (pvm/sama-pvm? (second vanha) (second uusi)))
          (e! (tiedot/->PaivitaValinnat {:aikavali uusi})))))

    ;; Wrappaa reikapaikkausluokkaan niin ei yliajeta mitään 
    [:div.reikapaikkaukset
     ;; Muokkauspaneeli
     (when muokataan
       [:div.overlay-oikealla
        ;; Lomake
        [lomake/lomake
         {:ei-borderia? true
          :tarkkaile-ulkopuolisia-muutoksia? true
          :muokkaa! #(e! (tiedot/->MuokkaaRivia %))
          ;; Header
          :header [:div.col-md-12
                   [:h2.header-yhteiset "Muokkaa toteumaa"]
                   [:hr]]
          ;; Footer, joka on vakiona col-md-12
          :footer [:<>
                   [:hr]
                   [:div.muokkaus-modal-napit
                    ;; Tallenna
                    [napit/tallenna "Tallenna muutokset" #(e! (tiedot/->TallennaReikapaikkaus valittu-rivi)) {:disabled (not voi-tallentaa?)}]
                    ;; Poista 
                    [napit/yleinen-toissijainen "Poista" #(e! (tiedot/->PoistaReikapaikkaus valittu-rivi)) {:ikoni (ikonit/livicon-trash)
                                                                                                            :paksu? true
                                                                                                            :luokka "lomake-poista"
                                                                                                            :disabled (not voi-poistaa?)}]
                    ;; Sulje 
                    [napit/yleinen-toissijainen "Sulje" #(e! (tiedot/->SuljeMuokkaus))]]]}

         [(lomake/rivi
            {:otsikko "Pvm"
             :pakollinen? true
             :tyyppi :komponentti
             :komponentti (fn []
                            [kentat/tee-kentta {:tyyppi :pvm
                                                :ikoni-sisaan? true
                                                :vayla-tyyli? true}
                             (r/wrap
                               alkuaika
                               #(e! (tiedot/->AsetaToteumanPvm %)))])})
          ;; Sijainti
          (lomake/ryhma
            {:otsikko "Sijainti"
             :ryhman-luokka "lomakeryhman-otsikko-tausta lomake-ryhma-otsikko"}
            ;; TR- valinnat
            (lomake/rivi
              {:nimi :tie
               :otsikko "Tie"
               :pakollinen? true
               :tyyppi :numero
               :input-luokka "lomake-tr-valinta"
               :rivi-luokka "lomakeryhman-rivi-tausta"
               :desimaalien-maara 0
               :validoi [[:ei-tyhja "Syötä tienumero"]]
               ::lomake/col-luokka "col-xs-2 tr-input"}
              {:nimi :aosa
               :otsikko "A-osa"
               :pakollinen? true
               :tyyppi :numero
               :input-luokka "lomake-tr-valinta"
               ::lomake/col-luokka "col-xs-2 tr-input"
               :validoi [[:ei-tyhja "Syötä alkuosa"]]
               :desimaalien-maara 0}
              {:nimi :aet
               :otsikko "A-et"
               :pakollinen? true
               :tyyppi :numero
               :input-luokka "lomake-tr-valinta"
               ::lomake/col-luokka "col-xs-2 tr-input"
               :validoi [[:ei-tyhja "Syötä alkuetäisyys"]]
               :desimaalien-maara 0}
              {:nimi :losa
               :otsikko "L-osa"
               :pakollinen? true
               :tyyppi :numero
               :input-luokka "lomake-tr-valinta"
               ::lomake/col-luokka "col-xs-2 tr-input"
               :validoi [[:ei-tyhja "Syötä loppuosa"]]
               :desimaalien-maara 0}
              {:nimi :let
               :otsikko "L-et"
               :pakollinen? true
               :tyyppi :numero
               :input-luokka "lomake-tr-valinta"
               ::lomake/col-luokka "col-xs-2 tr-input"
               :validoi [[:ei-tyhja "Syötä loppuetäisyys"]]
               :desimaalien-maara 0}))

          ;; Menetelmä
          (lomake/ryhma
            {:otsikko "Menetelmä"
             :ryhman-luokka "lomakeryhman-otsikko-tausta lomake-ryhma-otsikko"}
            ;; Alasveto
            (lomake/rivi
              {:otsikko "Menetelmä"
               :pakollinen? true
               :rivi-luokka "lomakeryhman-rivi-tausta"
               :validoi [[:ei-tyhja "Valitse menetelmä"]]
               :nimi :tyomenetelma
               :tyyppi :valinta
               :valinnat (into [nil] alasveto-valinnat)
               :valinta-nayta #(if %
                                 (alasveto-kuvaukset %)
                                 "- Valitse -")
               ::lomake/col-luokka "leveys-kokonainen"}))

          ;; Määrä 
          (lomake/ryhma
            {:otsikko "Määrä"
             :ryhman-luokka "lomakeryhman-otsikko-tausta lomake-ryhma-otsikko"}
            (lomake/rivi
              {:otsikko "Määrä"
               :pakollinen? true
               :rivi-luokka "lomakeryhman-rivi-tausta"
               :nimi :paikkaus_maara
               :tyyppi :numero
               :vayla-tyyli? true
               :validoi [[:ei-tyhja "Syötä määrä"]]
               ::lomake/col-luokka "maara-valinnat"}
              {:otsikko "Yksikkö"
               :tyyppi :valinta
               :valinnat (vec tiedot/reikapaikkausten-yksikot)
               :nimi :yksikko
               :pakollinen? true
               :vayla-tyyli? true
               ::lomake/col-luokka "maara-valinnat"}
              {:otsikko "Kustannus"
               :pakollinen? true
               :rivi-luokka "lomakeryhman-rivi-tausta"
               :nimi :kustannus
               :tyyppi :euro
               :teksti-oikealla "EUR"
               :vayla-tyyli? true
               :validoi [[:ei-tyhja "Syötä kustannusarvo"]]
               ::lomake/col-luokka "maara-valinnat"}))]
         valittu-rivi]])

     [:div.reikapaikkaus-listaus
      ;; Suodattimet
      [:div.row.filtterit
       ;; TR valinta
       [:div
        [:div.alasvedon-otsikko-vayla "Tieosoite"]
        [kentat/tee-kentta {:tyyppi :tierekisteriosoite
                            :alaotsikot? true
                            :vayla-tyyli? true} tiedot/tr-atom]]
       ;; Pvm valinta
       [:div
        [valinnat/aikavali
         tiedot/aikavali-atom
         {:otsikko "Päivämäärä"
          :for-teksti "filtteri-aikavali"
          :luokka #{"label-ja-aikavali " "ei-tiukkaa-leveytta reikapaikkaus-pvm "}
          :ikoni-sisaan? true
          :vayla-tyyli? true
          :aikavalin-rajoitus [6 :kuukausi]}]]

       ;; Haku
       [:div.haku-nappi
        [napit/yleinen-ensisijainen "Hae" #(e! (tiedot/->HaeTiedot true)) {:data-attributes {:data-cy "hae-reikapaikkauskohteita"}}]]]

      [:div.reikapaikkaukset-kartta [kartta/kartan-paikka]]

      ;; Virhe modal 
      [modal/modal
       {:otsikko "Virheitä reikäpaikkausten tuonnissa Excelillä"
        :nakyvissa? nayta-virhe-modal
        :sulje-fn #(e! (tiedot/->SuljeVirheModal))
        :footer [:div
                 [napit/sulje #(e! (tiedot/->SuljeVirheModal))]]}
       [:div
        [:<>
         [:p "Tuotua Exceliä ei voitu lukea. Varmista, että käytät HARJAsta ladattua pohjaa jonka sarakkeita A-K ei ole muokattu, ja paikkaukset alkavat riviltä 4."]
         [:<>
          [:br]
          [:<>
           [:p "Virheet:"]
           [:ul
            (for* [virhe excel-virheet]
              [:li virhe])]]]]
        [:<>
         [:br]
         [:p "Tarkista virheet ja yritä tuontia uudelleen."]]]]


      ;; Taulukon ylhäällä olevat tekstit
      [:div.taulukko-header.header-yhteiset
       ;; Formatoi rivimäärä välilyönnillä, esim 1000 = 1 000, fmt/desimaaliluku tekee tämän, eurot myös
       [:h3 (str (fmt/desimaaliluku (or rivi-maara 0) 0 true)
              (if (> rivi-maara 1) " riviä, " " rivi, ")
              (fmt/euro false (or kustannukset 0)) " EUR")]

       ;; Oikealla puolella olevat lataus / tuontinapit
       [:div.flex-oikealla
        ;; Excel tuonti
        [:div.lataus-nappi
         [liitteet/lataa-tiedosto
          {:urakka-id (-> @tila/tila :yleiset :urakka :id)}
          {:nappi-teksti "Tuo tiedot excelistä"
           :url "lue-reikapaikkauskohteet-excelista"
           :lataus-epaonnistui #(e! (tiedot/->TiedostoLadattu %))
           :tiedosto-ladattu #(e! (tiedot/->TiedostoLadattu %))}]]

        ;; Excel-Pohjan lataus
        [:div.lataus-nappi
         [yleiset/tiedoston-lataus-linkki
          "Lataa Excel-pohja"
          (str (when-not (k/kehitysymparistossa?)  "/harja") "/excel/harja_reikapaikkausten_pohja.xlsx")]]]]

      ;; Grid
      [grid/grid {:tyhja (if haku-kaynnissa?
                           [ajax-loader "Haku käynnissä..."]
                           "Valitulle aikavälille ei löytynyt mitään.")
                  :tunniste :id
                  :sivuta grid/vakiosivutus
                  :voi-kumota? false
                  :piilota-toiminnot? true
                  :mahdollista-rivin-valinta? true
                  :rivi-klikattu #(e! (tiedot/->AvaaMuokkausModal %))}

       [{:otsikko "Pvm"
         :tyyppi :pvm
         :nimi :alkuaika
         :luokka "text-nowrap"
         :leveys 0.4}

        {:otsikko "Sijainti"
         :tyyppi :komponentti
         :komponentti (fn [arvo _]
                        (tr-domain/tierekisteriosoite-tekstina arvo))
         :luokka "text-nowrap"
         :leveys 0.5}

        {:otsikko "Menetelmä"
         :tyyppi :string
         :nimi :tyomenetelma-nimi
         :luokka "text-nowrap"
         :leveys 1}

        {:otsikko "Määrä"
         :tyyppi :komponentti
         :komponentti (fn [{:keys [paikkaus_maara yksikko]} _arvo]
                        (str paikkaus_maara " " yksikko))
         :luokka "text-nowrap"
         :leveys 0.3}

        {:otsikko "Kustannus (EUR)"
         :tyyppi :euro
         :nimi :kustannus
         :luokka "text-nowrap"
         :leveys 0.3}]
       rivit]]]))


(defn reikapaikkaukset* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa? tiedot/karttataso-reikapaikkaukset)
    (komp/sisaan-ulos
      ;; Sisään
      #(do
         ;; Kartta
         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
         (nav/vaihda-kartan-koko! :M)
         ;; Tiedot
         (e! (tiedot/->HaeTyomenetelmat))
         (e! (tiedot/->HaeTiedot false)))
      ;; Ulos
      #(do
         (remove-watch tiedot/tr-atom :tierekisteri-haku)
         (remove-watch tiedot/aikavali-atom :aikavali-haku)))

    ;; Näytä listaus
    (fn [e! app]
      [:div
       [reikapaikkaus-listaus e! app]])))


(defn reikapaikkaukset []
  ;; Ei näytetä tuotannossa vielä
  (if (and 
        (k/kehitysymparistossa?)
        (roolit/roolissa? @istunto/kayttaja roolit/jarjestelmavastaava))
    [tuck tiedot/tila reikapaikkaukset*]
    [:div {:style {:padding "20px" :font-size "18px"}} "Maanteiden paikkausurakoiden reikäpaikkaukset tulevat tälle välilehdelle myöhemmin."]))
