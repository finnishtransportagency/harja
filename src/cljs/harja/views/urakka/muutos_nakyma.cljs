(ns harja.views.urakka.muutos-nakyma
  "MHU-urakoiden muutosten välilehti. Hallinnoi ja näyttää tarjouksen pohjatietoihin ja tavoitehintaan tehtäviä muutoksia."
  (:require
    [tuck.core :as tuck]
    [harja.tiedot.navigaatio :as nav]
    [harja.tiedot.urakka.siirtymat :as siirtymat]

    [harja.fmt :as fmt]
    [harja.pvm :as pvm]
    [harja.ui.grid :as grid]
    [harja.ui.napit :as napit]
    [harja.tiedot.urakka :as u]
    [harja.ui.komponentti :as komp]
    [harja.tiedot.urakka.urakka :as tila]
    [harja.views.urakka.valinnat :as urakka-valinnat]
    [harja.tiedot.urakka.muutokset.yhteiset-tiedot :as t-yhteiset]
    [harja.ui.yleiset :as yleiset]

    ;; Osiot / lomake
    [harja.views.urakka.muutokset.yhteiset :as yhteiset :refer [kehystetty-avattava-grid]]
    [harja.views.urakka.muutokset.kirjatut-muutokset :as kirjatut-muutokset]
    [harja.views.urakka.muutokset.lasketut-muutokset :as lasketut-muutokset]
    [harja.views.urakka.muutokset.rahavarausten-muutokset :as rahavarausten-muutokset]
    [harja.views.urakka.muutokset.lomake.muutoslomake :as muutoslomake]))


(defn- tavoitehinnan-muutokset [e! {:keys [tavoitehinnan-muutokset] :as app}]
  [kehystetty-avattava-grid e! app
   {:taulukon-avain :tavoitehinnan-muutokset
    :taulukon-nakyvyys-event #(e! (t-yhteiset/->ToggleTaulukonNakyvyys :tavoitehinnan-muutokset))
    :otsikko "Tavoitehinnan muutokset"
    :summa (reduce + 0 (map :tavoitehinnan-muutos tavoitehinnan-muutokset)) ;; todo
    :toiminnot (fn [e! app]
                 [::span
                  [napit/uusi "Lisää muutos" #(e! (t-yhteiset/->LisaaTavoitehintojenMuutos))]])
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

       ;; Taulukon kentät
       [{:otsikko "Muutos"
         :nimi :muutos
         :tyyppi :string
         :leveys 15}

        {:otsikko "Perustelu"
         :nimi :perustelu
         :tyyppi :string
         :leveys 35}

        {:otsikko "Vaikutus € (+/-)"
         :nimi :tavoitehinnan-muutos
         :tyyppi :numero
         :fmt fmt/euro-opt
         :tasaa :oikea
         :leveys 15}]
       tavoitehinnan-muutokset])}])


(defn- suunniteltujen-maarien-muutokset [e! {:keys [suunniteltujen-maarien-muutokset] :as app}]
  [kehystetty-avattava-grid e! app
   {:taulukon-avain :suunniteltujen-maarien-muutokset
    :taulukon-nakyvyys-event #(e! (t-yhteiset/->ToggleTaulukonNakyvyys :suunniteltujen-maarien-muutokset))
    :otsikko "Suunniteltujen määrien muutokset"
    :summa :ei-summaa
    :toiminnot (fn [e! app]
                 [::span
                  [napit/uusi "Lisää muutos" #(e! (t-yhteiset/->LisaaSuunniteltujenMaarienMuutos))]])
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

       ;; Taulukon kentät
       [{:otsikko "Muutoksen syy"
         :nimi :syy
         :tyyppi :string
         :leveys 15}

        {:otsikko "Muutokset"
         :nimi :muutokset
         :tyyppi :string
         :leveys 35}

        {:otsikko "Lisätieto"
         :nimi :lisatieto
         :tyyppi :string
         :leveys 15}

        {:otsikko "" :nimi
         :toiminnot :tyyppi
         :komponentti
         :leveys 10
         :tasaa :oikea
         :komponentti (fn [rivi]
                        [napit/muokkaa "Muokkaa"
                         #(e! (t-yhteiset/->MuokkaaMuutosta rivi))])}]
       suunniteltujen-maarien-muutokset])}])


(defn muutoslistaus [e! app]
  [:div.muutoslistaus
   (when (:valittu-hoitokausi app)
     (if (t-yhteiset/ennen-muutoksien-kayttoonotto? (:valittu-hoitokausi app))
       ;; Tähän 1.10.2024 tai sitä aiemmiun alkaneiden hoitokausien " legacy " muutostoiminnot
       [:span.muutostiedot
        [tavoitehinnan-muutokset e! app]
        [suunniteltujen-maarien-muutokset e! app]]

       ;; Tähän 1.10.2025 tai sitä myöhemmin alkavien hoitokausien uudet muutostoiminnot
       [:span.uudet-muutostiedot
        [kirjatut-muutokset/kirjatut-muutokset e! app]
        [lasketut-muutokset/lasketut-muutokset e! app]
        [rahavarausten-muutokset/rahavarausten-muutokset e! app]]))])


(defn muutosten-vaikutus-sisalto-rivit* [budjettitavoitteet valittu-hoitokausi indeksikorjaus-vahvistettu?]
  ["Hoitovuoden alun indeksikorjattu tavoitehinta"
   (if-not indeksikorjaus-vahvistettu?
     t-yhteiset/+indeksikorjausta-ei-vahvistettu-txt+
     (fmt/euro-opt (:hoitovuoden-alun-indeksikorjattu-tavoitehinta budjettitavoitteet)))

   (when (:aiemmat-pysyvat-muutokset-indeksikorjattu-yht budjettitavoitteet)
     [:ul
      [:li.harmaa-tumma-teksti "Edellisten hoitovuosien pysyvien muutosten osuus (indeksikorjattu)"]])
   (when (:aiemmat-pysyvat-muutokset-indeksikorjattu-yht budjettitavoitteet)
     (if-not indeksikorjaus-vahvistettu?
       t-yhteiset/+indeksikorjausta-ei-vahvistettu-txt+
       (fmt/euro-opt true true (:aiemmat-pysyvat-muutokset-indeksikorjattu-yht budjettitavoitteet))))

   "Kirjatut muutokset"
   (fmt/euro-opt true true (:kirjatut-muutokset-yht budjettitavoitteet))

   ^{:viiva-rivin-alle? true}
   [:div "Toteumiin perustuvat muutokset" [:br]
    "(vahvistetaan "
    [:a.klikattava.alleviivaa {:href "#"
                               :on-click
                               #(siirtymat/avaa-valikatselmus
                                  @nav/valittu-hallintayksikko-id (:id @nav/valittu-urakka)
                                  [(first valittu-hoitokausi)
                                   (second valittu-hoitokausi)])}
     "välikatselmuksessa."] ")"]
   (fmt/euro-opt true true (:toteumiin-perustuvat-muutokset-yht budjettitavoitteet))

   [:b "Yhteensä"]
   [:b (if (not indeksikorjaus-vahvistettu?)
         t-yhteiset/+muutosten-vaikutus-yhteensa-ei-saatavilla+
         (fmt/euro-opt (:muutosten-vaikutus-yht budjettitavoitteet)))]

   ^{:koko-rivin-leveys? true :tietorivi-luokka (str "keskita-rivin-sisalto"
                                                  (when indeksikorjaus-vahvistettu?
                                                    " piilota-rivin-sisalto"))}
   [yleiset/info-laatikko :neutraali
    [:span "Indeksikorjaus vahvistetaan "
     [:a.klikattava.alleviivaa {:href "#"
                                :on-click #(siirtymat/siirry-annettuun-valilehteen
                                             @nav/valittu-hallintayksikko-id (:id @nav/valittu-urakka)
                                             {:taso1 :urakat
                                              :taso2 :suunnittelu
                                              :taso3 :uusi-kustannussuunnitelma})}
      "kustannussuunnitelmassa."]]
    nil
    {:luokka "vihje-indeksikorjaus"}] ""])

(defn- muutosten-vaikutus
  "Yhteenveto muutosten vaikutuksista."
  [_e! {:keys [budjettitavoitteet valittu-hoitokausi haku-kaynnissa?] :as _app}]
  (let [indeksikorjaus-vahvistettu? (t-yhteiset/hoitovuoden-indeksikorjaus-vahvistettu?
                                      budjettitavoitteet valittu-hoitokausi)]
    [:div.muutosten-vaikutus
     (into []
       (concat
         [yleiset/tietoja {:class "muutosten-vaikutus-container body-text"
                           :tietorivi-luokka "padding-8"}
          [:h2 "Muutosten vaikutus"] ""]
         (if haku-kaynnissa?
           [[yleiset/ajax-loader "Ladataan yhteenvetoa..."] ""]
           (muutosten-vaikutus-sisalto-rivit* budjettitavoitteet valittu-hoitokausi indeksikorjaus-vahvistettu?))))]))


(defn muutosten-hallinta-sisalto [e! {:keys [haku-kaynnissa?] :as app}]
  [:div.valinnat-ja-listaus
   [:h1 "Muutosten hallinta"]
   [:div.otsikko-ja-hoitokausi

    ;; TODO:  
    ;; Jos vanhoille hoitovuosille toteutetaan osiot, kaikki nayta-muutokset-sivu? kutsut voi poistaa 
    [urakka-valinnat/paivittava-urakkavuosi-tuck
     @u/valittu-aikavali
     #(when (t-yhteiset/nayta-muutokset-sivu?)
        ;; Älä tee turhia kutsuja, jos sivua ei näytetä 
        (e! (t-yhteiset/->HaeUrakanMuutostiedot nil))) haku-kaynnissa? false]]

   (if (t-yhteiset/nayta-muutokset-sivu?)
     [:<>
      [muutosten-vaikutus e! app]
      [muutoslistaus e! app]]
     ;; Tämä näytetään, jos 2025 aikaisempi hoitovuosi valittuna
     [yleiset/varoitus-vihje
      "Muutokset ovat käytössä hoitovuodesta 2025 alkaen." nil :alert])])


(defn muutokset-alempi-valilehti*
  [e! _app]
  (komp/luo
    (komp/lippu t-yhteiset/nakymassa?)
    (komp/sisaan #(when (t-yhteiset/nayta-muutokset-sivu?)
                    (e! (t-yhteiset/->HaeUrakanMuutostiedot nil))))
    (fn [e!
         {:keys [muokattava-muutos] :as app}]
      [:span.muutokset-sivu
       (if muokattava-muutos
         ;; Jos valittuna rivi, näytä lomake 
         [muutoslomake/muutoslomake e! app]
         ;; Muuten näytä sivun sisältö 
         [muutosten-hallinta-sisalto e! app])])))


(defn muutokset-paatason-valilehti [_ur]
  (fn [_ur] [tuck/tuck tila/muutokset muutokset-alempi-valilehti*]))
