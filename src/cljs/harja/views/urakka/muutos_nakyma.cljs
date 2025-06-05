(ns harja.views.urakka.muutos-nakyma
  "MHU-urakoiden muutosten välilehti. Hallinnoi ja näyttää tarjouksen pohjatietoihin ja tavoitehintaan tehtäviä muutoksia."
  (:require [cljs.core.async :refer [<!]]
            [harja.fmt :as fmt]
            [harja.ui.ikonit :as ui-ikonit]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.domain.muutos-domain :as muutos-domain]
            [harja.ui.debug :refer [debug]]
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.yleiset :as yleiset]
            [harja.loki :refer [log logt]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.muutos-tiedot :as muutos-tiedot])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defn liite-kentta
  "Lomakkeen liitekenttä, joka näyttää liitteiden listauksen ja mahdollistaa uusien liitteiden lisäämisen."
  [e! {:keys [uusi-liite muokattava-muutos] :as app}]
  [{:otsikko "Liite" :nimi :liitteet :kaariva-luokka "muutosliite"
    :tyyppi :komponentti ::lomake/col-luokka "col-xs-12"
    :uusi-rivi? true
    :komponentti (fn [_]
                   [liitteet/liitteet-ja-lisays
                    @nav/valittu-urakka-id
                    (:liitteet muokattava-muutos)
                    {:uusi-liite-atom (r/wrap uusi-liite
                                        #(e! (muutos-tiedot/->LisaaLiite %)))
                     :uusi-liite-teksti "Lisää liite"
                     :salli-poistaa-lisatty-liite? true
                     :poista-lisatty-liite-fn #(e! (muutos-tiedot/->PoistaLisattyLiite))
                     :salli-poistaa-tallennettu-liite? true
                     :nayta-lisatyt-liitteet? false
                     :poista-tallennettu-liite-fn #(e! (muutos-tiedot/->PoistaTallennettuLiite %))}])}])

(defn- muutoslomakkeen-kentat-yhteiset
  "Eri muutostyypeille yhteiset kentät. Voi silti sisältää pienen määrän haaroitusta."
  [e! {:keys [muokattava-muutos] :as app}]
  (vec
    (keep identity
      (concat
        [{:otsikko "Tyyppi"
          :nimi :tyyppi
          :tyyppi :valinta
          :vayla-tyyli? true
          :valinnat muutos-domain/+muutostyypit+
          :valinta-arvo identity
          :valinta-nayta muutos-domain/tyyppi-fmt
          :uusi-rivi? true}
         (when (= "pysyva" (:tyyppi muokattava-muutos))
           {:tyyppi :komponentti
            :uusi-rivi? true
            :komponentti (fn [rivi]
                           [yleiset/info-laatikko :neutraali
                            "Pysyvä muutos vaikuttaa kaikkiin tuleviin hoitovuosiin."])})
         (lomake/ryhma {:otsikko "Perustiedot"}
           {:nimi :nimi
            :otsikko "Nimi"
            :tyyppi :string
            :uusi-rivi? true
            :pakollinen? true}
           {:nimi :syy
            :otsikko "Muutoksen syy"
            :tyyppi :text
            :koko [90 6]
            :aputeksti "Kuvaile muutos mahdollisimman tarkasti."
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

(defn muutoslomake [e! {:keys [muokattava-muutos] :as app}]
  [:span.muutoslomake

   ;; todo: eri tyyppisten muutosten lomakkeiden toteutus tähän
   ;; oletettavasti kannattaa toteuttaa lomakkeen harja.ui.lomake avulla
   ;; siten että niiden sisälle sijoitetaan tarvittaessa taulukkoja :muokkaus-grid, ks. esim views/urakka/toteumat/muut_materiaalit.cljs#L127
   [lomake/lomake
    {:otsikko (if (:id muokattava-muutos)
                "Muokkaa muutosta"
                "Lisää uusi muutos")
     :muokkaa! #(e! (muutos-tiedot/->PaivitaLomake (lomake/ilman-lomaketietoja %)))
     :footer-fn (fn [muutos]
                  [:span.tallenna-ja-peruuta
                   [napit/tallenna
                    #(e! (muutos-tiedot/->TallennaMuutos muutos))]
                   [napit/peruuta
                    #(e! (muutos-tiedot/->MuokkaaMuutosta nil))]])}
    ;; Tähän lomakkeiden muutostyyppikohtaiset skeemat
    (into []
      (concat
        (muutoslomakkeen-kentat-yhteiset e! app)

        (case (:tyyppi muokattava-muutos)
          "pysyva" (muutoslomakkeen-kentat-pysyva e! app)

          ;; tässä kohti default, että jokaiselle aukeaa jotain... poistunee lopulta kun kaikki toteutettu
          (muutoslomakkeen-kentat-pysyva e! app))))
    muokattava-muutos]])

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
      (when-not (= summa :ei-summaa) [:div.summa (fmt/euro-opt summa)])]
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
        :voi-lisata? false :voi-kumota? false
        :voi-poistaa? (constantly false) :voi-muokata? true}

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
        :voi-lisata? false :voi-kumota? false
        :voi-poistaa? (constantly false) :voi-muokata? true}

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

(defn- rahavarausten-muutokset [e! {:keys [rahavarausten-muutokset] :as app}]
  [kehystetty-avattava-grid e! app
   {:taulukon-avain :rahavarausten-muutokset
    :taulukon-nakyvyys-event #(e! (muutos-tiedot/->ToggleTaulukonNakyvyys :rahavarausten-muutokset))
    :otsikko "Rahavarausten muutokset"
    :summa (reduce + 0 (map :rahavarausten-muutokset rahavarausten-muutokset)) ;; todo
    :toiminnot (fn [e! app]
                 [::span
                  [:p rahavarausten-muutokset-aputeksti]
                  ;; Tämä muokkaus mahdollistaa vain syyn lisäämisen
                  [napit/uusi "Muokkaa" #(e! (muutos-tiedot/->MuokkaaRahavaraustenMuutoksienSyita))]])
    :taulukko
    (fn [e! app]
      [grid/grid
       {:tunniste :id
        :luokat ["rahavarausten-muutokset-grid"]
        :tyhja "Ei rahavarausten muutoksia."
        :voi-lisata? false :voi-kumota? false
        :voi-poistaa? (constantly false) :voi-muokata? true}

       ;; taulukon kentät
       [{:otsikko "Rahavaraus" :nimi :rahavaraus :tyyppi :string :leveys 15}
        {:otsikko "Muutoksen syy" :nimi :syy :tyyppi :string :leveys 35}
        {:otsikko "Suunniteltu määrä" :nimi :suunniteltu-maara :tyyppi :numero :leveys 15}
        {:otsikko "Toteutunut määrä" :nimi :toteutunut-maara :tyyppi :numero :leveys 15}
        {:otsikko "Tavoitehinnan muutos (€)" :nimi :tavoitehinnan-muutos :tyyppi :numero
         :fmt fmt/euro-opt :tasaa :oikea :leveys 15}]
       rahavarausten-muutokset])}])

(def lasketut-muutokset-aputeksti
  "Tavoitehintamuutosten laskennassa käytetään Harjan suunniteltuja ja toteutuneita määriä sekä palvelusopimuksen mukaisia kaavoja.")

(defn- lasketut-muutokset [e! {:keys [lasketut-muutokset] :as app}]
  [kehystetty-avattava-grid e! app
   {:taulukon-avain :lasketut-muutokset
    :taulukon-nakyvyys-event #(e! (muutos-tiedot/->ToggleTaulukonNakyvyys :lasketut-muutokset))
    :otsikko "Tehtävä- ja määräluetteloon perustuvat tavoitehintamuutokset"
    :summa (reduce + 0 (map :tavoitehinnan-muutos lasketut-muutokset)) ;; todo
    :toiminnot (fn [e! app]
                 ;; Tämä muokkaus mahdollistaa vain syyn lisäämisen
                 [:span
                  [:p lasketut-muutokset-aputeksti]
                  [napit/uusi "Muokkaa" #(e! (muutos-tiedot/->MuokkaaLaskettujenMuutoksienSyita))]])
    :taulukko
    (fn [e! app]
      [grid/grid
       {:tunniste :id
        :luokat ["lasketut-muutokset-grid"]
        :tyhja "Ei laskettuja muutoksia."
        :voi-lisata? false :voi-kumota? false
        :voi-poistaa? (constantly false) :voi-muokata? true}

       ;; taulukon kentät
       [{:otsikko "Tehtävä" :nimi :tehtava :tyyppi :string :leveys 15}
        {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :leveys 15}
        {:otsikko "Muutoksen syy / lisätieto" :nimi :syy :tyyppi :string :leveys 35}
        {:otsikko "Suunniteltu määrä" :nimi :suunniteltu_maara :tyyppi :numero :leveys 15}
        {:otsikko "Kirjattu määrä" :nimi :suunniteltu_maara :tyyppi :numero :leveys 15}
        {:otsikko "Määrämuutos (+/-)" :nimi :suunniteltu_maara :tyyppi :numero :leveys 15}
        {:otsikko "Kirjatut kulut (€)" :nimi :suunniteltu_maara :tyyppi :numero :leveys 15}
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
        :voi-lisata? false :voi-kumota? false
        :voi-poistaa? (constantly false) :voi-muokata? false}

       ;; taulukon kentät
       [{:otsikko "Tyyppi" :nimi :tyyppi :tyyppi :string :leveys 15
         :fmt muutos-domain/tyyppi-fmt}
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
