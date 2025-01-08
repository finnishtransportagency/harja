(ns harja.views.urakka.laadunseuranta.talvihoitoreitit-nakyma
  "Talvihoitoreittien näkymä. Kartta ja listaus."
  (:require [harja.fmt :as fmt]
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
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]))

(defn- talvihoitoreitti-rivi [{:keys [talvihoitoreittien-tilat] :as app} e!
                              {:keys [laskettu_pituus nimi id varikoodi hoitoluokat ulkoinen_id reitit urakka_id
                                      tr_maara ka_maara kup_maara]}]
  
  (let [_ (js/console.log "talvihoitoreitti-rivi :: hoitoluokat" (pr-str hoitoluokat))
        valitut-kohteet @tiedot/valitut-kohteet-atom
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
                   [:div.body-text.musta.semibold (:hoitoluokka h)
                    [:span.small-text.musta.talvihoitoreitti-valistys (fmt/desimaaliluku-opt (:pituus h) 2)]]]))]]]

      ;; Huoltoaukot
      [:div.basis256.grow2.shrink3.rajaus
       [:div.body-text.musta "HUOLTOAUKOT JA PYSÄKÖINTIALUEET"]
       [:div
        [:div
         [:div.body-text.musta.semibold "Talvihoito:"
          [:span.small-text.musta.talvihoitoreitti-valistys (or talvihoito 0)]]]
        [:div
         [:div.body-text.musta.semibold "Kuoma-autot:"
          [:span.small-text.musta.talvihoitoreitti-valistys (or talvihoito-osin 0)]]]
        [:div
         [:div.body-text.musta.semibold "Kuppi-kuomaajat:"
          [:span.small-text.musta.talvihoitoreitti-valistys (or ei-talvihoitoa 0)]]]]]

      ;; Kalusto
      [:div.basis128.grow2.shrink3.rajaus
       [:div.body-text.musta "KALUSTO"]
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
          (napit/avaa "Piilota kartalta" #(e! (tiedot/->PoistaValittuKohdeKartalta id)) {:luokka "talvihoitoreitti-kartan-naytto"})
          (napit/avaa "Näytä kartalla" #(e! (tiedot/->LisaaValittuKohdeKartalle id)) {:luokka "talvihoitoreitti-kartan-naytto"}))]
       ;; Keskitä yhteen yksittäiseen reittiin
       [:div (napit/yleinen "Keskitä"
               :toissijainen
               #(e! (tiedot/->KeskitaTalvihoitoreitti id reitit))
               {:ikoni    (ikonit/zoom-in)
                :luokka "talvihoitoreitti-poisto"})]
       [:div (napit/yleinen "Poista"
               :toissijainen


               #(varmista-kayttajalta/varmista-kayttajalta
                  {:otsikko "Poista talvihoitoreitti"
                   :sisalto [:div "Oletko varma, että haluat poistaa talvihoitoreitin?"]
                   :hyvaksy "Poista"
                   :toiminto-fn (fn [] (e! (tiedot/->PoistaTalvihoitoreitti ulkoinen_id)))})
               {:ikoni    (ikonit/livicon-trash)
                :luokka "talvihoitoreitti-poisto"})]]]

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

(defn talvihoitoreitit-sivu [e! {:keys [talvihoitoreitit] :as app}]
  [:<>
   [kartta/kartan-paikka]

   (if (:haku-kaynnissa? app)
     [:div.ajax-loader-valistys
      [ajax-loader "Ladataan talvihoitoreittejä..."]]

     [:div.talvihoitoreititys
      [:div.flex-row {:style {:justify-content "space-between"}}
       [:h2 "Talvihoitoreititys"]
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
        [:div "Ei talvihoitoreittejä. Aloita tuomalla reitit käyttäen excel-tiedostoa."]

        (doall
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
           talvihoitoreitit]))])])

(defn talvihoitoreitit* [e! app]
  (komp/luo
    (komp/sisaan-ulos
      #(do
         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
         (nav/vaihda-kartan-koko! :M)

         (reset! tiedot/valitut-kohteet-atom #{})
         (e! (tiedot/->HaeTalvihoitoreitit))

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
