(ns harja.views.urakka.yllapitokohteet.reikapaikkaukset
  "Reikäpaikkaukset päänäkymä"
  (:require [tuck.core :refer [tuck]]
            [harja.asiakas.kommunikaatio :as komm]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-reikapaikkaukset :as tiedot]
            [harja.views.urakka.yllapitokohteet.paikkaukset.reikapaikkaukset-apurit :as apurit]
            [harja.ui.varmista-kayttajalta :refer [varmista-kayttajalta]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.ui.liitteet :as liitteet]
            [harja.fmt :as fmt]
            [harja.ui.valinnat :as valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.transit :as transit]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.views.kartta :as kartta]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.tierekisteri :as tr-domain]))


(defn reikapaikkaus-listaus [e! {:keys [rivit valinnat
                                        muokataan nayta-virhe-modal 
                                        excel-virheet valittu-rivi 
                                        tyomenetelmat haku-kaynnissa? rivi-maara kustannukset] :as app}]
  (let [alkuaika (:alkuaika valittu-rivi)
        alasveto-valinnat (mapv :id tyomenetelmat)
        alasveto-kuvaukset (into {} (map (fn [{:keys [id nimi]}] [id nimi]) tyomenetelmat))
        voi-kirjoittaa? (oikeudet/voi-kirjoittaa? oikeudet/urakat-paikkaukset-paikkauskohteet @nav/valittu-urakka-id @istunto/kayttaja)
        voi-tallentaa? (and
                         voi-kirjoittaa?
                         (tiedot/voi-tallentaa? valittu-rivi app))]

    [:div.reikapaikkaukset
     ;; Muokkauspaneeli
     (when muokataan
       (apurit/reikapaikkaus-muokkauspaneeli e! voi-kirjoittaa? voi-tallentaa? valittu-rivi alasveto-kuvaukset alkuaika alasveto-valinnat))

     [:div.reikapaikkaus-listaus
      ;; Suodattimet
      (apurit/reikapaikkaus-suodattimet e! valinnat valinnat/aikavali)
      
      ;; Kartta
      [:div.reikapaikkaukset-kartta [kartta/kartan-paikka]]

      ;; Näytä Excel virheet jos virheitä tapahtui
      (apurit/excel-virhe-modal e! nayta-virhe-modal excel-virheet)

      ;; Taulukon ylhäällä olevat tekstit
      [:div.taulukko-header.header-yhteiset
       ;; Formatoi rivimäärä välilyönnillä, esim 1000 = 1 000, fmt/desimaaliluku tekee tämän, eurot myös
       [:h3 (str
              (fmt/formatoi-numero-tuhansittain (or rivi-maara 0))
              (if (= rivi-maara 1)
                " rivi, "
                " riviä, ")
              (fmt/euro false (or kustannukset 0)) " EUR")]

       ;; Oikealla puolella olevat lataus / tuontinapit
       [:div.flex-oikealla
        [:div.lataus-nappi
         [:form {:style {:margin-left "auto"}
                 :target "_blank" :method "POST"
                 :action (komm/excel-url :reikapaikkaukset-urakalle-excel)}
          [:input {:type "hidden" :name "parametrit"
                   :value (transit/clj->transit {:tr (:tr valinnat)
                                                 :aikavali (:aikavali valinnat)
                                                 :urakka-id @nav/valittu-urakka-id})}]
          [:button {:type "submit"
                    :class #{"nappi-reunaton"}}
           [ikonit/ikoni-ja-teksti (ikonit/livicon-upload) "Vie Exceliin"]]]]
        ;; Excel tuonti
        [:div.lataus-nappi
         [liitteet/lataa-tiedosto
          {:urakka-id (-> @tila/tila :yleiset :urakka :id)}
          {:nappi-teksti "Tuo tiedot excelistä"
           :url "lue-reikapaikkauskohteet-excelista"
           :lataus-epaonnistui #(e! (tiedot/->TiedostoLadattu %))
           :tiedosto-ladattu (fn []
                               (varmista-kayttajalta
                                 {:otsikko "Tietojen tuonti ylikirjoittaa aiemmin tuodut toteumat samalla tunnisteella"
                                  :hyvaksy "Tuo tiedot"
                                  :toiminto-fn #(e! (tiedot/->TiedostoLadattu %))}))}]]

        ;; Excel-Pohjan lataus
        [:div.lataus-nappi
         [yleiset/tiedoston-lataus-linkki
          "Lataa Excel-pohja"
          "/excel/harja_reikapaikkausten_pohja.xlsx"]]]]

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
         :leveys 0.2}

        {:otsikko "Sijainti"
         :tyyppi :komponentti
         :komponentti (fn [arvo _]
                        (tr-domain/tierekisteriosoite-tekstina arvo))
         :luokka "text-nowrap"
         :leveys 0.55}

        {:otsikko "Menetelmä"
         :tyyppi :string
         :nimi :tyomenetelma-nimi
         :luokka "text-nowrap"
         :leveys 1}

        {:otsikko "Määrä"
         :tasaa :oikea
         :tyyppi :string
         :hae #(str (:maara %) " " (:reikapaikkaus-yksikko %))
         :luokka "text-nowrap"
         :leveys 0.3}

        {:otsikko "Kustannus (EUR)"
         :tasaa :oikea
         :tyyppi :euro
         :nimi :kustannus
         :luokka "text-nowrap"
         :leveys 0.3}]
       rivit]]]))


(defn reikapaikkaukset* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa? tiedot/karttataso-reikapaikkaukset)
    (komp/sisaan
      ;; Sisään
      #(do
         ;; Kartta
         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
         (nav/vaihda-kartan-koko! :M)
         ;; Tiedot
         (e! (tiedot/->HaeTyomenetelmat))
         (e! (tiedot/->HaeTiedot))))

    ;; Näytä listaus
    (fn [e! app]
      [:div
       [reikapaikkaus-listaus e! app]])))


(defn reikapaikkaukset []
  [tuck tiedot/tila reikapaikkaukset*])
