(ns harja.views.urakka.laadunseuranta.talvihoitoreitit-nakyma
  "Talvihoitoreittien näkymä. Kartta ja listaus."
  (:require [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.ui.liitteet :as liitteet]
            [tuck.core :as tuck]
            [harja.asiakas.kommunikaatio :as k]
            [harja.transit :as transit]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.views.kartta :as kartta]
            [harja.views.kartta.tasot :as kartta-tasot]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.laadunseuranta.talvihoitoreitit-tiedot :as tiedot]
            [harja.tiedot.kartta :as kartta-tiedot]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]))

(defn- talvihoitoreitti-rivi [{:keys [talvihoitoreittien-tilat] :as app} e!
                              {:keys [laskettu_pituus nimi id varikoodi hoitoluokat ulkoinen_id reitit urakka_id
                                      tr_maara ka_maara kup_maara]}]

  (let [valitut-kohteet @tiedot/valitut-kohteet-atom
        reittien-maara (count reitit)
        auki? (contains? talvihoitoreittien-tilat id)
        reitteja-olemassa? (> reittien-maara 0)
        talvihoito (some #(when (= "Talvihoito" (:hoitoluokka %))
                            (:hoitoluokka %))
                     (get hoitoluokat :huoltoaukot))
        talvihoito-osin (some #(when (= "Talvihoito osin" (:hoitoluokka %))
                                 (:hoitoluokka %))
                          (get hoitoluokat :huoltoaukot))
        ei-talvihoitoa (some #(when (= "Ei talvihoitoa" (:hoitoluokka %))
                                (:hoitoluokka %))
                         (get hoitoluokat :huoltoaukot))]
    [:<>
     [:div.flex-row.venyta.otsikkokomponentti {:class (str "" (when reitteja-olemassa? " klikattava"))
                                               ;  :style {:margin-top "0"}
                                               :on-click #(when reitteja-olemassa? (e! (tiedot/->AvaaTalvihoitoreitti id)))
                                               :data-cy (str "avaa-reitti-" nimi)}
      ;; Nuoli
      [:div.basis32.nogrow.slim
       (when reitteja-olemassa?
         (if auki?
           [ikonit/navigation-ympyrassa :down]
           [ikonit/navigation-ympyrassa :right]))]

      ;; Nimi
      [:div.basis192.nogrow.shrink3.rajaus.slim
       [:div.talvihoitoreitti-ryhma
        [:span.talvihoitoreitti-nimi {:style {:background-color varikoodi :min-width "12px" :margin-right "5px"}}]
        [:div
         [:div.body-text.semibold.musta (str nimi)]
         [:div.body-text.musta (str "(" (fmt/desimaaliluku-opt laskettu_pituus 2) " km )")]]]]

      ;; Hoitoluokat
      [:div.basis256.grow2.shrink3.rajaus
       [:div.body-text.musta "HOITOLUOKAT (KM)"]
       [:div.ryhma-pilari
        [:div {:style {:display "flex" :flex-wrap "wrap"}}
         (doall (for [h (get hoitoluokat :maantiet)]
                  ^{:key (hash (str "hoitoluokka-" (gensym)))}
                  [:div
                   [:div.body-text.musta.semibold (str (:hoitoluokka h) ":")
                    [:span.small-text.musta.talvihoitoreitti-valistys (fmt/desimaaliluku-opt (:pituus h) 2)]]]))]]]

      ;; Huoltoaukot
      [:div.basis256.grow2.shrink3.rajaus
       [:div.body-text.musta "HUOLTOAUKOT JA PYSÄKÖINTIALUEET (KM)"]
       [:div
        [:div
         [:div.body-text.musta.semibold "Talvihoito:"
          [:span.small-text.musta.talvihoitoreitti-valistys (or talvihoito "0,00")]]]
        [:div
         [:div.body-text.musta.semibold "Talvihoito-osin:"
          [:span.small-text.musta.talvihoitoreitti-valistys (or talvihoito-osin "0,00")]]]
        [:div
         [:div.body-text.musta.semibold "Ei talvihoitoa:"
          [:span.small-text.musta.talvihoitoreitti-valistys (or ei-talvihoitoa "0,00")]]]]]

      ;; Kalusto
      [:div.basis128.grow2.shrink3.rajaus
       [:div.body-text.musta "KALUSTO (KPL)"]
       [:div
        [:div
         [:div.body-text.musta.semibold "Traktorit:"
          [:span.small-text.musta.talvihoitoreitti-valistys (or tr_maara 0)]]]
        [:div
         [:div.body-text.musta.semibold "Kuoma-autot:"
          [:span.small-text.musta.talvihoitoreitti-valistys (or ka_maara 0)]]]
        [:div
         [:div.body-text.musta.semibold "Kuppi-kuomaajat:"
          [:span.small-text.musta.talvihoitoreitti-valistys (or kup_maara 0)]]]]]

      ;; Toiminnallisuudet
      [:div.basis128.grow2.shrink2
       [:div.body-text.strong.musta ""]
       ;; Näytä valittu rivi kartalla tai piilota se
       [:<>
        (if (contains? valitut-kohteet id)
          (napit/avaa "Piilota kartalta" #(e! (tiedot/->PoistaValittuKohdeKartalta id)) {:luokka "btn-xs talvihoitoreitti-kartan-naytto"})
          (napit/avaa "Näytä kartalla" #(e! (tiedot/->LisaaValittuKohdeKartalle id)) {:luokka "btn-xs talvihoitoreitti-kartan-naytto"}))]
       ;; Keskitä yhteen yksittäiseen reittiin
       [:div (napit/yleinen "Keskitä"
               :toissijainen
               #(e! (tiedot/->KeskitaTalvihoitoreitti id reitit))
               {:ikoni (ikonit/zoom-in)
                :luokka "btn-xs talvihoitoreitti-poisto"})]
       [:div (napit/yleinen "Poista"
               :toissijainen
               #(varmista-kayttajalta/varmista-kayttajalta
                  {:otsikko "Poista talvihoitoreitti"
                   :sisalto [:div "Oletko varma, että haluat poistaa talvihoitoreitin?"]
                   :hyvaksy "Poista"
                   :toiminto-fn (fn [] (e! (tiedot/->PoistaTalvihoitoreitti ulkoinen_id)))})
               {:ikoni (ikonit/livicon-trash)
                :luokka "btn-xs talvihoitoreitti-poisto"})]]]

     ;; Otsikkokoponentin voi avata ja avaamisen jälkeen näytetään lista (grid) reiteistä
     (when (and
             (get talvihoitoreittien-tilat id)
             reitteja-olemassa?)

       ;; Sisältö
       [:div.talvihoitoreitti-sisalto
        [:h2 "Reitti"]

        [grid/grid
         {:salli-valiotsikoiden-piilotus? true
          :valiotsikoiden-alkutila :kaikki-kiinni
          :tunniste :id
          :reunaviiva? true
          :rivi-jalkeen-fn #(let [yhteensa-suunniteltu (reduce + 0 (map :pituus %))
                                  yhteensa-laskettu (reduce + 0 (map :laskettu_pituus %))]

                              [{:teksti "" :luokka "otsikko-ei-taustaa"}
                               {:teksti "Yhteensä" :luokka "lihavoitu otsikko-ei-taustaa"}
                               {:teksti "" :luokka "otsikko-ei-taustaa"}
                               {:teksti (str (fmt/euro-opt false yhteensa-suunniteltu))
                                :tasaa :oikea :luokka "lihavoitu otsikko-ei-taustaa"}
                               {:teksti (str (fmt/euro-opt false yhteensa-laskettu))
                                :tasaa :oikea :luokka "lihavoitu otsikko-ei-taustaa"}])}

         [{:otsikko "Tie" :nimi :tie :tyyppi :string :tasaa :vasen :leveys 1 :luokka "nakyma-valkoinen-solu"}
          {:otsikko "Osoiteväli" :nimi :formatoitu-tr :tyyppi :string :tasaa :vasen :leveys 3 :luokka "nakyma-valkoinen-solu"}
          {:otsikko "Hoitoluokka" :nimi :hoitoluokka :tyyppi :string :tasaa :vasen :leveys 2 :luokka "nakyma-valkoinen-solu"}
          {:otsikko "Suunniteltu pituus (km)" :nimi :pituus :tyyppi :numero
           :fmt #(fmt/desimaaliluku-opt % 2) :tasaa :oikea :leveys 2 :luokka "nakyma-valkoinen-solu"}
          {:otsikko "Laskettu pituus (km)" :nimi :laskettu_pituus :tyyppi :numero
           :fmt #(fmt/desimaaliluku-opt % 2) :tasaa :oikea :leveys 2 :luokka "nakyma-valkoinen-solu"}]
         reitit]])]))

(def ^:private kalustoyhteenveto-tooltip-otsikko
  "Reiteille suunniteltu kalusto")

(def ^:private kalustoyhteenveto-tooltip-teksti
  (str "Reitille suunniteltu kalusto lasketaan automaattisesti reittisuunnitelman perusteella. "
    "Jos reitillä on useampaa tarjouksessa esiintyvää hoitoluokkaryhmää, kalusto kirjataan "
    "sille hoitoluokkaryhmälle, jota on reitillä eniten."))

(def ^:private kalustoyhteenveto-ei-reitteja-teksti
  "Ei talvihoitoreittejä. Aloita tuomalla reitit käyttäen excel-tiedostoa.")

(defn- mhu26-urakka?
  "Talvihoitoreittien kalustoyhteenveto näytetään vain MHU26-urakoille eli teiden hoidon
   urakoille, joiden alkupäivämäärän vuosi on vähintään 2026."
  [{:keys [tyyppi alkupvm]}]
  (and (= tyyppi :teiden-hoito)
    (>= (pvm/vuosi alkupvm) 2026)))

(defn- suunniteltu-kalusto-tooltip
  "Saavutettava info-tooltip reiteille suunnitellun kaluston laskennasta."
  []
  [yleiset/tooltip {:suunta :ylos :leveys :levea}
   [:span.kalustoyhteenveto-info-ikoni
    {:tab-index 0
     :role "button"
     :aria-label (str kalustoyhteenveto-tooltip-otsikko ". " kalustoyhteenveto-tooltip-teksti)}
    (ikonit/nelio-info 16)]
   [:div
    [:h2 kalustoyhteenveto-tooltip-otsikko]
    [:p kalustoyhteenveto-tooltip-teksti]]])

(defn- kalustoyhteenveto-osio
  "Sivun yläosan yhteenveto: tarjouksessa luvattu kalusto suhteessa reiteille suunniteltuun
   kalustoon hoitoluokkaryhmittäin. Näytetään vain MHU26-urakoille."
  [kalustoyhteenveto]
  [:div.kalustoyhteenveto
   [:h2 "Tarjouksessa luvatun kaluston käyttö reittisuunnitelmissa"]
   (cond
     (empty? kalustoyhteenveto)
     [:div.kalustoyhteenveto-ei-reitteja
      "Urakalle ei ole kirjattu hoitoluokkaryhmiä Suunnittelu / Kalustoresurssit -sivulla."]

     :else
     [:table.kalustoyhteenveto-taulukko
      [:thead
       [:tr
        [:th "Hoitoluokkaryhmä"]
        [:th.tasaa-oikealle "Tarjouksessa luvattu kalusto (kpl)"]
        [:th.tasaa-oikealle
         [:div.kalustoyhteenveto-otsikko-tooltip
          [:span "Reiteille suunniteltu kalusto (kpl)"]
          [suunniteltu-kalusto-tooltip]]]]]
       [:tbody
        (doall
          (for [{:keys [hoitoluokkaryhma nimi luvattu suunniteltu]} kalustoyhteenveto]
            ^{:key hoitoluokkaryhma}
            [:tr
             [:td nimi]
             [:td.tasaa-oikealle (or luvattu 0)]
             [:td.tasaa-oikealle suunniteltu]]))]])])

(defn talvihoitoreitit-sivu [e! {:keys [talvihoitoreitit kalustoyhteenveto] :as app}]
  [:<>
   [:h1 "Talvihoitoreititys"]
   [kartta/kartan-paikka]

   (if (:haku-kaynnissa? app)
     [ajax-loader "Ladataan talvihoitoreittejä..."]

     [:div.talvihoitoreititys
      (when (and (mhu26-urakka? @nav/valittu-urakka)
                 (or (seq talvihoitoreitit) (seq kalustoyhteenveto)))
        [kalustoyhteenveto-osio kalustoyhteenveto])
      [:div.flex-row {:style {:justify-content "space-between"}}
       [:div {:style {:display "flex"}}
        ;; Jos talvihoitoreittejä on olemassa, niin annetaan käyttäjän ladata ne Exceliin
        (when-not (empty? talvihoitoreitit)
          [:span [:form {:style {:margin-left "auto"}
                         :target "_blank" :method "POST"
                         :action (k/excel-url :lataa-talvihoitoreitit-exceliin)}
                  [:input {:type "hidden" :name "parametrit"
                           :value (transit/clj->transit {:urakka-id (-> @tila/tila :yleiset :urakka :id)})}]
                  [:button {:type "submit"
                            :class #{"nappi-toissijainen"}}
                   [ikonit/ikoni-ja-teksti (ikonit/livicon-download) "Lataa talvihoitoreitit-Excel"]]]])
        [liitteet/lataa-tiedosto
         {:urakka-id (-> @tila/tila :yleiset :urakka :id)}
         {:nappi-teksti "Tuo kohteet Excelistä"
          :url "lue-talvihoitoreitit-excelista"
          :lataus-epaonnistui #(e! (tiedot/->TiedostoLadattu %))
          :tiedosto-ladattu #(e! (tiedot/->TiedostoLadattu %))}]

        [yleiset/tiedoston-lataus-linkki
         "Lataa Excel-pohja"
         "/excel/harja_talvihoitoreitit_pohja.xlsx"]]]

      (if (empty? talvihoitoreitit)
        [:div kalustoyhteenveto-ei-reitteja-teksti]

        (doall
          [:div.margin-top-16
           [grid/grid {:tunniste :id
                       :sivuta 10
                       :voi-kumota? false
                       :piilota-otsikot? true
                       :piilota-border? true
                       :piilota-muokkaus? true
                       :piilota-toiminnot? true
                       :mahdollista-rivin-valinta? false}

            [{:tyyppi :komponentti
              :solun-luokka #(str "talvihoitoreitti-rivi")
              :tunniste :id
              :komponentti (fn [reitti]
                             ;; Väkänen / rivi
                             (talvihoitoreitti-rivi app e! reitti))
              :leveys 1}]
            talvihoitoreitit]]))])])

(defn talvihoitoreitit* [e! app]
  (komp/luo
    (komp/sisaan-ulos
      #(do
         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
         (nav/vaihda-kartan-koko! :M)
         (kartta-tiedot/piilota-infopaneeli!)

         (reset! tiedot/valitut-kohteet-atom #{})
         (e! (tiedot/->HaeTalvihoitoreitit))
         (when (mhu26-urakka? @nav/valittu-urakka)
           (e! (tiedot/->HaeKalustoyhteenveto)))

         (kartta-tasot/taso-paalle! :talvihoitoreitit)
         (kartta-tasot/taso-paalle! :organisaatio)
         (reset! tiedot/karttataso-nakyvissa? true))

      #(do
         (kartta-tasot/taso-pois! :talvihoitoreitit)
         (kartta-tasot/taso-pois! :organisaatio)
         (reset! tiedot/karttataso-nakyvissa? false)))
    (fn [e! app]
      [:div.row
       [talvihoitoreitit-sivu e! app]])))

(defn talvihoitoreitit-nakyma
  []
  [tuck/tuck tila/talvihoitoreitit talvihoitoreitit*])
