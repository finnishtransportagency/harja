(ns harja.views.urakka.muutos-nakyma
  "MHU-urakoiden muutosten välilehti. Hallinnoi ja näyttää tarjouksen pohjatietoihin ja tavoitehintaan tehtäviä muutoksia."
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.ui.napit :as napit]
            [harja.ui.lomake :as lomake]
            [harja.ui.ikonit :as ui-ikonit]
            [harja.ui.grid :as grid]
            [harja.ui.debug :refer [debug]]
            [harja.ui.komponentti :as komp]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.muutos-tiedot :as muutos-tiedot]
            [harja.domain.muutos-domain :as muutos-domain]))

(defn liite-kentta
  "Lomakkeen liitekenttä, joka näyttää liitteiden listauksen ja mahdollistaa uusien liitteiden lisäämisen."
  [e! {:keys [uusi-liite muokattava-muutos] :as app}]
  [{:otsikko "Liite" :nimi :liitteet :kaariva-luokka "muutosliite"
    :tyyppi :komponentti ::lomake/col-luokka "col-xs-12"
    :uusi-rivi? true
    :komponentti (fn [_]
                   (when (every? :nimi (:liitteet muokattava-muutos))
                     [liitteet/liitteet-ja-lisays
                      @nav/valittu-urakka-id
                      (:liitteet muokattava-muutos)
                      {:uusi-liite-atom (r/wrap uusi-liite
                                          #(e! (muutos-tiedot/->LisaaLiite %)))
                       :uusi-liite-teksti "Lisää liite"
                       :nayta-lisatyt-liitteet? false
                       :lisaa-usea-liite? true
                       :salli-poistaa-lisatty-liite? true
                       :poista-lisatty-liite-fn #(e! (muutos-tiedot/->PoistaLisattyLiite))
                       :salli-poistaa-tallennettu-liite? true
                       :poista-tallennettu-liite-fn #(e! (muutos-tiedot/->PoistaTallennettuLiite %))}]))}])

(defn- muutoslomakkeen-kentat-yhteiset
  "Eri muutostyypeille yhteiset kentät. Voi silti sisältää pienen määrän haaroitusta."
  [e! {:keys [muokattava-muutos valittu-hoitokausi urakan-hoitokaudet] :as app}]
  (vec
    (keep identity
      (concat
        [{:otsikko "Tyyppi"
          :nimi :tyyppi
          :pakollinen? true
          ;; sallitaan muokkaus vain uudelle muutokselle
          :muokattava? (fn [rivi] (nil? (:id rivi)))
          :aseta (fn [rivi arvo]
                   (muutos-tiedot/alusta-tyyppikohtaisia-arvoja arvo valittu-hoitokausi)
                   (assoc rivi :tyyppi arvo))
          :kaariva-luokka "muutostyyppivalinta"
          :tyyppi :valinta
          :vayla-tyyli? true
          :valinnat muutos-domain/+muutostyypit-lomakkeella+
          :valinta-arvo identity
          :valinta-nayta (fn [arvo]
                           (muutos-domain/tyyppi-fmt arvo (:sopimustyyppi @nav/valittu-urakka)))
          :uusi-rivi? true}
         (when (= "pysyva" (:tyyppi muokattava-muutos))
           {:tyyppi :komponentti
            :uusi-rivi? true
            :komponentti (fn [rivi]
                           [yleiset/info-laatikko :neutraali
                            "Pysyvä muutos vaikuttaa kaikkiin tuleviin hoitovuosiin."])})
         (lomake/ryhma {:otsikko "Perustiedot"}
           (when (= "johto-ja-hallintokorvaus" (:tyyppi muokattava-muutos))
             {:nimi :hoitovuosi :tyyppi :string :otsikko "Hoitovuosi" :muokattava? (constantly false)
              :hae #(fmt/hoitokauden-jarjestysluku-ja-vuodet valittu-hoitokausi urakan-hoitokaudet "Hoitovuosi")})
           (when (= "pysyva" (:tyyppi muokattava-muutos))
             {:nimi :nimi
              :otsikko "Nimi"
              :tyyppi :string
              :uusi-rivi? true
              :pakollinen? true})
           {:nimi :syy
            :otsikko "Muutoksen syy"
            :tyyppi :text
            :palstoja 2
            :koko [90 4]
            :aputeksti "Kuvaile muutos mahdollisimman tarkasti. Ethän syötä kenttään henkilö- tai muuta arkaluontoista tietoa."
            :pituus-max 1000
            :uusi-rivi? true
            :pakollinen? true}
           {:nimi :voimassa_alkaen :otsikko "Voimassa alkaen"
            :tyyppi :pvm :uusi-rivi? true
            :pakollinen? true})]
        (liite-kentta e! app)))))

(defn- muutoslomakkeen-kentat-pysyva
  "Pysyvän muutoksen lomakekomponentti"
  [e! app]
  [])


(defn- muutoslomakkeen-kentat-johto-ja-hallintokorvaus
  "johto-ja-hallintokorvaus muutoksen lomakekomponentti"
  [e! {:keys [valittu-hoitokausi]}]
  (let [muutostapa (muutos-domain/jjh-korvaus-muutos-vai-vahennys? (:alkupvm @nav/valittu-urakka))
        summa (reduce + 0 (map :tavoitehinnan-muutos (vals @muutos-tiedot/johto-ja-hallintokorvausmuutokset-atom)))]
    [{:nimi :johto-ja-hallintokorvaus-muutokset
      :otsikko ""
      :palstoja 2
      :tyyppi :komponentti :uusi-rivi? true
      :komponentti
      (fn [e! {:keys [johto-ja-hallintokorvausten-muutokset valittu-hoitokausi]}]
        [:span
         [:hr]
         [:h3 "Muutokset tavoitehintaan ja kuluihin"]
         [grid/muokkaus-grid
          {:tunniste :pvm
           :luokat ["johto-ja-hallintokorvaus-muutokset-grid"]
           :piilota-toiminnot? true
           :voi-lisata? false
           :voi-kumota? false
           :voi-poistaa? (constantly false)
           :voi-muokata? true
           :rivi-jalkeen [{:teksti "Yhteensä" :sarakkeita 1 :luokka "yhteensa"}
                          {:teksti (fmt/euro-opt summa) :tasaa :oikea :luokka "yhteensa"}]}

          ;; taulukon kentät
          [{:otsikko "Kalenterikuukausi" :nimi :pvm :tyyppi :string :leveys 20
            :muokattava? (constantly false)
            :fmt #(when % (pvm/koko-kuukausi-ja-vuosi % true))}
           {:otsikko (if (= muutostapa :muutos)
                       "Muutos € (+/-)"
                       "Vähennys (€)")
            :nimi :tavoitehinnan-muutos :vaadi-negatiivinen? (when (= muutostapa :vahennys) true)
            :tyyppi :numero :fmt fmt/euro-opt :tasaa :oikea :leveys 8}]
          muutos-tiedot/johto-ja-hallintokorvausmuutokset-atom]
         [yleiset/info-laatikko :neutraali
          "Harja luo oikaisevat kulut automaattisesti tallentamisen jälkeen."
          nil nil {:luokka "johto-ja-hallintokorvaus-muutokset-info"}]])}]))


(defn muutoslomake [e! {:keys [muokattava-muutos tallennus-kesken?] :as app}]
  (komp/luo
    (komp/sisaan-ulos
      #(e! (muutos-tiedot/->HaeMuutoksenTiedot muokattava-muutos))
      #(e! (muutos-tiedot/->MuokkaaMuutosta nil)))
    (fn [e! {:keys [muokattava-muutos tallennus-kesken?] :as app}]
      [:span.muutoslomake
       [lomake/lomake
        {:otsikko (if (:id muokattava-muutos)
                    "Muokkaa muutosta"
                    "Lisää uusi muutos")
         :muokkaa! #(e! (muutos-tiedot/->PaivitaLomake (lomake/ilman-lomaketietoja %)))
         :footer-fn (fn [muutos]
                      [:span.tallenna-ja-peruuta
                       [:hr]
                       (when-not (empty? (:puuttuvat-pakolliset-kentat muokattava-muutos))
                         [yleiset/info-laatikko :varoitus
                          (str "Lomakkeelta puuttuu pakollisia kenttiä: "
                            (str/join ", " (:puuttuvat-pakolliset-kentat muokattava-muutos))
                            ". Korjaa ne ja yritä uudelleen.")])
                       [napit/tallenna "Tallenna"
                        #(tuck-apurit/e-kanavalla! e! muutos-tiedot/->TallennaMuutos
                           muutos)
                        {:disabled tallennus-kesken?}]
                       [napit/peruuta "Peruuta"
                        #(e! (muutos-tiedot/->MuokkaaMuutosta nil))
                        {:disabled tallennus-kesken?}]])}
        ;; Tähän lomakkeiden muutostyyppikohtaiset skeemat
        (into []
          (concat
            (muutoslomakkeen-kentat-yhteiset e! app)

            (case (:tyyppi muokattava-muutos)
              "pysyva" (muutoslomakkeen-kentat-pysyva e! app)

              "johto-ja-hallintokorvaus" (muutoslomakkeen-kentat-johto-ja-hallintokorvaus e! app)

              ;; tässä kohti default, että jokaiselle aukeaa jotain... poistunee lopulta kun kaikki toteutettu
              (muutoslomakkeen-kentat-pysyva e! app))))
        muokattava-muutos]])))

(defn- kehystetty-avattava-grid
  "Piirtää yhtenäisesti Muutoksien taulukot collapsoitaviksi."
  ;; summan saa piiloon antamalla sille arvon :ei-summaa
  [e! app {:keys [taulukon-avain taulukon-nakyvyys-event
                  otsikko summa toiminnot taulukko] :as tiedot}]
  (let [sisalto-nakyvissa? (get-in app [:taulukko-nakyvissa? taulukon-avain])]
    [:div.collapsoitava-osio
     [:div.otsikkorivi.klikattava {:on-click taulukon-nakyvyys-event}
      [:span
       [ui-ikonit/navigation-ympyrassa (if sisalto-nakyvissa?
                                         :down
                                         :right)]
       [:h2 otsikko]]
      (when-not (= summa :ei-summaa)
        [:div.summa {:aria-label (str otsikko " yhteensä " summa " euroa")}
         (fmt/euro-opt summa)])]
     (when sisalto-nakyvissa?
       [:span
        [:div.toiminnot
         [toiminnot e! app]]
        [:div.taulukko
         [taulukko e! app]]])]))

(defn- tavoitehinnan-muutokset [e! {:keys [tavoitehinnan-muutokset] :as app}]
  [kehystetty-avattava-grid e! app
   {:taulukon-avain :tavoitehinnan-muutokset
    :taulukon-nakyvyys-event #(e! (muutos-tiedot/->ToggleTaulukonNakyvyys :tavoitehinnan-muutokset))
    :otsikko "Tavoitehinnan muutokset"
    :summa (reduce + 0 (map :tavoitehinnan-muutos tavoitehinnan-muutokset)) ;; todo
    :toiminnot (fn [e! app]
                 [::span
                  [napit/uusi "Lisää muutos" #(e! (muutos-tiedot/->LisaaTavoitehintojenMuutos))]])
    :taulukko
    (fn [e! app]
      [grid/grid
       {:tunniste :id
        :luokat ["tavoitehinnan-muutokset-grid"]
        :tyhja "Ei tavoitehinnan muutoksia."
        :voi-lisata? false
        :voi-kumota? false
        :voi-poistaa? (constantly false)
        :voi-muokata? true}

       ;; taulukon kentät
       [{:otsikko "Muutos" :nimi :muutos :tyyppi :string :leveys 15}
        {:otsikko "Perustelu" :nimi :perustelu :tyyppi :string :leveys 35}
        {:otsikko "Vaikutus € (+/-)" :nimi :tavoitehinnan-muutos :tyyppi :numero
         :fmt fmt/euro-opt :tasaa :oikea :leveys 15}]
       tavoitehinnan-muutokset])}])

(defn- suunniteltujen-maarien-muutokset [e! {:keys [suunniteltujen-maarien-muutokset] :as app}]
  [kehystetty-avattava-grid e! app
   {:taulukon-avain :suunniteltujen-maarien-muutokset
    :taulukon-nakyvyys-event #(e! (muutos-tiedot/->ToggleTaulukonNakyvyys :suunniteltujen-maarien-muutokset))
    :otsikko "Suunniteltujen määrien muutokset"
    :summa :ei-summaa
    :toiminnot (fn [e! app]
                 [::span
                  [napit/uusi "Lisää muutos" #(e! (muutos-tiedot/->LisaaSuunniteltujenMaarienMuutos))]])
    :taulukko
    (fn [e! app]
      [grid/grid
       {:tunniste :id
        :luokat ["suunniteltujen-maarien-muutokset-grid"]
        :tyhja "Ei suunniteltujen määrien muutoksia."
        :voi-lisata? false
        :voi-kumota? false
        :voi-poistaa? (constantly false)
        :voi-muokata? true}

       ;; taulukon kentät
       [{:otsikko "Muutoksen syy" :nimi :syy :tyyppi :string :leveys 15}
        {:otsikko "Muutokset" :nimi :muutokset :tyyppi :string :leveys 35}
        {:otsikko "Lisätieto" :nimi :lisatieto :tyyppi :string :leveys 15}
        {:otsikko "" :nimi :toiminnot :tyyppi :komponentti :leveys 10 :tasaa :oikea
         :komponentti (fn [rivi]
                        [napit/muokkaa "Muokkaa"
                         #(e! (muutos-tiedot/->MuokkaaMuutosta rivi))])}]
       suunniteltujen-maarien-muutokset])}])


(def rahavarausten-muutokset-aputeksti
  "Harja laskee rahavarausten tavoitehintamuutokset automaattisesti kustannussuunnitelman ja kulukirjausten perusteella.")

(defn- rahavarausten-muutokset
  "Näyttää rahavarausten muutokset taulukossa sekä yhteenvedon. Taulukko on avattava ja suljettava. Sisältö automaattisesti laskettu muista tauluista."
  [e! {:keys [rahavarausten-muutokset] :as app}]
  (let [rivit (butlast rahavarausten-muutokset)
        yhteenveto (last rahavarausten-muutokset)
        suunnittelutiedot-puuttuvat (every? #(or
                                               (nil? (:summa-indeksikorjattu %))
                                               (zero? (:summa-indeksikorjattu %))) rivit)]
    [kehystetty-avattava-grid e! app
     {:taulukon-avain :rahavarausten-muutokset
      :taulukon-nakyvyys-event #(e! (muutos-tiedot/->ToggleTaulukonNakyvyys :rahavarausten-muutokset))
      :otsikko "Rahavarausten muutokset"
      :summa (:tavoitehinnan-muutos yhteenveto)
      :toiminnot (fn [e! app]
                   [::span
                    [yleiset/vihje rahavarausten-muutokset-aputeksti]
                    (when suunnittelutiedot-puuttuvat
                      [yleiset/toast-viesti "Suunnittelutiedot puuttuvat tarjouksen tiedoista."])])
      :taulukko
      (fn [e! app]
        [grid/grid
         {:tunniste :id
          :luokat ["rahavarausten-muutokset-grid"]
          :tyhja "Ei rahavarausten muutoksia."
          :voi-lisata? false
          :voi-kumota? false
          :voi-poistaa? (constantly false)
          :voi-muokata? true
          :tallenna #(tuck-apurit/e-kanavalla! e! muutos-tiedot/->TallennaRahavarausmuutostenSyyt %)
          :rivi-jalkeen-fn (fn []
                             [{:teksti "Tavoitehinnan muutokset yhteensä" :luokka "yhteensa" :yhteenveto-vayla true}
                              {:teksti "" :sarakkeita 1 :luokka "yhteensa"}
                              {:teksti (fmt/euro-opt (:summa-indeksikorjattu yhteenveto)) :tasaa :oikea :luokka "yhteensa"}
                              {:teksti (fmt/euro-opt (:toteumat yhteenveto)) :tasaa :oikea :luokka "yhteensa"}
                              {:teksti (fmt/euro-opt (:tavoitehinnan-muutos yhteenveto)) :tasaa :oikea :luokka "yhteensa"}])}

         ;; taulukon kentät
         [{:otsikko "Rahavaraus" :nimi :nimi :tyyppi :string :leveys 15 :muokattava? (constantly false)}
          {:otsikko "Muutoksen syy" :nimi :syy :tyyppi :text
           :pituus-max 1000 :koko [50 3] :leveys 25}
          {:otsikko "Suunniteltu määrä" :nimi :summa-indeksikorjattu :tyyppi :numero
           :tasaa :oikea :leveys 10 :muokattava? (constantly false)
           :fmt (fn [arvo]
                  (if arvo
                    (fmt/euro-opt arvo)
                    "Ei indeksikorjattua summaa"))}
          {:otsikko "Toteutunut määrä" :nimi :toteumat :tyyppi :numero
           :fmt     fmt/euro-opt :tasaa :oikea :leveys 10
           :muokattava? (constantly false)}
          {:otsikko "Tavoitehinnan muutos (€)" :nimi :tavoitehinnan-muutos :tyyppi :numero
           :fmt     fmt/euro-opt :tasaa :oikea :leveys 10
           :muokattava? (constantly false)}]
         rivit])}]))

(def lasketut-muutokset-aputeksti
  "Tavoitehintamuutosten laskennassa käytetään Harjan suunniteltuja ja toteutuneita määriä sekä palvelusopimuksen mukaisia kaavoja.")

(defn- lasketut-muutokset [e! {:keys [lasketut-muutokset] :as app}]
  [kehystetty-avattava-grid e! app
   {:taulukon-avain :lasketut-muutokset
    :taulukon-nakyvyys-event #(e! (muutos-tiedot/->ToggleTaulukonNakyvyys :lasketut-muutokset))
    :otsikko "Tehtävä- ja määrätoteumiin perustuvat tavoitehintamuutokset"
    :summa (reduce + 0 (map :tavoitehinnan-muutos lasketut-muutokset))
    :toiminnot (fn [e! app]
                 ;; Tämä muokkaus mahdollistaa vain syyn lisäämisen
                 [:span
                  [yleiset/vihje lasketut-muutokset-aputeksti]])
    :taulukko
    (fn [e! app]
      [grid/grid
       {:tunniste :id
        :luokat ["lasketut-muutokset-grid"]
        :tyhja "Ei laskettuja muutoksia."
        :voi-lisata? false
        :voi-kumota? false
        :voi-poistaa? (constantly false)
        :voi-muokata? true
        :tallenna #(e! (muutos-tiedot/->TallennaLaskettujenMuutostenSyyt %))}

       ;; taulukon kentät
       [{:otsikko "Tehtävä" :nimi :tehtava :tyyppi :string :leveys 15}
        {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :leveys 15}
        {:otsikko "Muutoksen syy / lisätieto" :nimi :syy :tyyppi :string :leveys 35}
        {:otsikko "Suunniteltu määrä" :nimi :suunniteltu_maara :tyyppi :numero :leveys 15}
        {:otsikko "Kirjattu määrä" :nimi :suunniteltu_maara :tyyppi :numero :leveys 15}
        {:otsikko "Määrämuutos (+/-)" :nimi :suunniteltu_maara :tyyppi :numero :leveys 15}
        {:otsikko "Kirjatut kulut (€)" :nimi :suunniteltu_maara :tyyppi :numero :leveys 15}
        {:otsikko "Tavoitehinnan muutos (€)" :nimi :tavoitehinnan-muutos :tyyppi :numero
         :fmt fmt/euro-opt :tasaa :oikea :leveys 15}]
       lasketut-muutokset])}])

(defn- kirjatut-muutokset [e! {:keys [kirjatut-muutokset] :as app}]
  [kehystetty-avattava-grid e! app
   {:taulukon-avain :kirjatut-muutokset
    :taulukon-nakyvyys-event #(e! (muutos-tiedot/->ToggleTaulukonNakyvyys :kirjatut-muutokset))
    :otsikko "Kirjatut muutokset"
    :summa (reduce + 0 (map :tavoitehinnan-muutos kirjatut-muutokset))
    :toiminnot (fn [e! app]
                 [napit/uusi "Lisää uusi" #(e! (muutos-tiedot/->MuokkaaMuutosta {}))])
    :taulukko
    (fn [e! app]
      [grid/grid
       {:tunniste :id
        :luokat ["kirjatut-muutokset-grid"]
        :tyhja "Ei kirjattuja muutoksia."
        :voi-lisata? false
        :voi-kumota? false
        :voi-poistaa? (constantly false)
        :voi-muokata? false}

       ;; taulukon kentät
       [{:otsikko "Tyyppi" :nimi :tyyppi :tyyppi :string :leveys 15
         :fmt (fn [arvo]
                (muutos-domain/tyyppi-fmt arvo (:sopimustyyppi @nav/valittu-urakka)))}
        {:otsikko "Muutoksen syy" :nimi :syy :tyyppi :string :leveys 35}
        {:otsikko "Voimassa alkaen" :nimi :voimassa_alkaen :tyyppi :pvm :leveys 15}
        {:otsikko "Tavoitehinnan muutos (€)" :nimi :tavoitehinnan-muutos :tyyppi :numero
         :fmt fmt/euro-opt :tasaa :oikea :leveys 15}
        {:otsikko "" :nimi :toiminnot :tyyppi :komponentti :leveys 10 :tasaa :oikea
         :komponentti (fn [rivi]
                        [napit/muokkaa "Muokkaa"
                         #(e! (muutos-tiedot/->MuokkaaMuutosta rivi))])}]
       kirjatut-muutokset])}])

(defn muutoslistaus [e! app]
  [:span.muutoslistaus
   (when (:valittu-hoitokausi app)
     (if (muutos-tiedot/ennen-muutoksien-kayttoonotto? (:valittu-hoitokausi app))
       ;; Tähän 1.10.2024 tai sitä aiemmiun alkaneiden hoitokausien "legacy" muutostoiminnot
       [:span.muutostiedot
        [tavoitehinnan-muutokset e! app]
        [suunniteltujen-maarien-muutokset e! app]]

       ;; Tähän 1.10.2025 tai sitä myöhemmin alkavien hoitokausien uudet muutostoiminnot
       [:span.uudet-muutostiedot
        [kirjatut-muutokset e! app]
        [lasketut-muutokset e! app]
        [rahavarausten-muutokset e! app]]))])

(def +indeksikorjausta-ei-vahvistettu-txt+ "Indeksikorjausta ei saatavilla")

(defn- muutosten-vaikutus
  "Yhteenveto muutosten vaikutuksista."
  [e! {:keys [budjettitavoitteet] :as app}]
  (let [indeksikorjaus-vahvistettu? (:indeksikorjaus-vahvistettu? budjettitavoitteet)]
    [:div.muutosten-vaikutus
    [:h2 "Muutosten vaikutus"]
    [yleiset/tietoja {:class "muutosten-vaikutus-container body-text"
                      :tietorivi-luokka "padding-8"}
     "Hoitovuoden alun indeksikorjattu tavoitehinta" (if-not indeksikorjaus-vahvistettu?
                                                       +indeksikorjausta-ei-vahvistettu-txt+
                                                       (fmt/euro-opt (:hoitovuoden-alun-indeksikorjattu-tavoitehinta budjettitavoitteet)))
     "Tavoitehinnan muutokset" (fmt/euro-opt (:muutosten-vaikutus-yhteensa budjettitavoitteet))
     "Hoitovuoden lopun tavoitehinta" (if-not indeksikorjaus-vahvistettu?
                                        +indeksikorjausta-ei-vahvistettu-txt+
                                        (fmt/euro-opt (:hoitovuoden-lopun-tavoitehinta budjettitavoitteet)))]
     (when-not indeksikorjaus-vahvistettu? [yleiset/vihje "Indeksikorjaus vahvistetaan kustannussuunnitelmassa."])]))


(defn muutokset-alempi-valilehti*
  [e! app]
  (let [urakka (:urakka @tila/yleiset)]
    (komp/luo
      (komp/sisaan-ulos
        #(do
           (when urakka
             (e! (muutos-tiedot/->ValitseUrakka urakka))
             (e! (muutos-tiedot/->HaeUrakanMuutostiedot urakka))))
        #(e! (muutos-tiedot/->NakymastaPoistuttiin)))
      (komp/watcher nav/valittu-urakka
        (fn [_ _ urakka]
          (when urakka
            (e! (muutos-tiedot/->ValitseUrakka urakka)))))
      (fn [e! app]
        [:span.muutokset-sivu
         (if (:muokattava-muutos app)
           [muutoslomake e! app]
           [:valinnat-ja-listaus
            [:h1 "Muutosten hallinta"]
            [:div.otsikko-ja-hoitokausi
             [valinnat/urakan-hoitokausi-tuck (:valittu-hoitokausi app)
              (:urakan-hoitokaudet app)
              #(e! (muutos-tiedot/->HoitokausiVaihdettu urakka %))]]
            [muutosten-vaikutus e! app]
            [muutoslistaus e! app]])
         [debug app]]))))

(defn muutokset-paatason-valilehti [ur]
  (fn [ur]
    [tuck/tuck tila/muutokset muutokset-alempi-valilehti*]))
