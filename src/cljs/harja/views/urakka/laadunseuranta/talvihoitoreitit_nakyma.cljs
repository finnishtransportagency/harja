(ns harja.views.urakka.laadunseuranta.talvihoitoreitit-nakyma
  "Talvihoitoreittien näkymä. Kartta ja listaus."
  (:require [harja.fmt :as fmt]
            [harja.ui.liitteet :as liitteet]
            [tuck.core :as tuck]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.views.kartta :as kartta]
            [harja.views.kartta.tasot :as kartta-tasot]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.laadunseuranta.talvihoitoreitit-tiedot :as tiedot]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]))

(defn talvihoitoreitit-sivu [e! app]
  (let [talvihoitoreitit (:talvihoitoreitit app)
        talvihoitoreittien-tilat (:talvihoitoreittien-tilat app)
        valitut-kohteet @tiedot/valitut-kohteet-atom]
    [:div
     [kartta/kartan-paikka]
     (if (:haku-kaynnissa? app)
       [ajax-loader "Ladataan talvihoitoreittejä..."]
       [:div
        [:div.flex-row
         [:h2 "Talvihoitoreititys"]
         [:div
          [liitteet/lataa-tiedosto
           {:urakka-id (-> @tila/tila :yleiset :urakka :id)}
           {:nappi-teksti "Tuo kohteet excelistä"
            :url "lue-talvihoitoreitit-excelista"
            :lataus-epaonnistui #(e! (tiedot/->TiedostoLadattu %))
            :tiedosto-ladattu #(e! (tiedot/->TiedostoLadattu %))}]
          [yleiset/tiedoston-lataus-linkki
           "Lataa Excel-pohja"
           "/excel/harja_talvihoitoreitit_pohja.xlsx"]]]
        (if (empty? talvihoitoreitit)
          [:div "Ei talvihoitoreittejä. Aloita tuomalla reitit käyttäen excel-tiedostoa."]
          (doall
            (for [rivi talvihoitoreitit
                  :let [reittien-maara (count (:reitit rivi))
                        auki? (contains? (:talvihoitoreittien-tilat app) (:id rivi))]]
              ^{:key (:id rivi)}
              [:<>
               [:div.flex-row.venyta.otsikkokomponentti {:class (str "" (when (> reittien-maara 0) " klikattava"))
                                                         :on-click #(when (> reittien-maara 0) (e! (tiedot/->AvaaTalvihoitoreitti (:id rivi))))
                                                         :data-cy (str "avaa-reitti-" (:nimi rivi))}
                ;; Nuoli
                [:div.basis48.nogrow
                 (when (> reittien-maara 0)
                   (if auki?
                     [ikonit/navigation-ympyrassa :down]
                     [ikonit/navigation-ympyrassa :right]))]

                ;; Nimi
                [:div.basis256.nogrow.shrink3.rajaus
                 [:div {:style {:display "flex"}}
                  [:span.talvihoitoreitti-nimi {:style {:background-color (:varikoodi rivi)}}]
                  [:div.body-text.semibold.musta {:style {:font-size "1rem"}} (str (:nimi rivi))]]
                 [:div.body-text.musta.semibold (fmt/desimaaliluku-opt (:laskettu_pituus rivi) 2) " km"]]

                [:div.basis384.grow2.shrink3.rajaus
                 [:div.body-text.semibold.musta {:style {:font-size "1rem"}} "Hoitoluokkien osuudet reitillä (km)"]
                 [:div {:style {:display "flex"}}
                  (doall (for [h (:hoitoluokat rivi)]
                           ^{:key (hash (str "hoitoluokka-" h))}
                           [:div
                            [:div.body-text.musta.semibold {:style {:padding-right "30px"}} (:hoitoluokka h)]
                            [:div.small-text.musta {:style {:padding-right "30px"}} (fmt/desimaaliluku-opt (:pituus h) 2)]]))]]

                [:div.basis384.grow2.shrink3.rajaus
                 [:div.body-text.semibold.musta {:style {:font-size "1rem"}} "Kalusto (kpl)"]
                 [:div {:style {:display "flex"}}
                  (doall (for [kalusto (:kalustot rivi)]
                           ^{:key (hash kalusto)}
                           [:div
                            [:div.body-text.musta.semibold {:style {:padding-right "30px"}} (:kalustotyyppi kalusto)]
                            [:div.small-text.musta {:style {:padding-right "30px"}} (:kalustomaara kalusto)]]))]]

                [:div.basis256.grow2.shrink2
                 [:div.body-text.strong.musta ""]
                 ;; Näytä valittu rivi kartalla tai piilota se
                 [:<>
                  (if (contains? valitut-kohteet (:id rivi))
                    (napit/avaa "Piilota kartalta" #(e! (tiedot/->PoistaValittuKohdeKartalta (:id rivi))) {:luokka "talvihoitoreitti-kartan-naytto"})
                    (napit/avaa "Näytä kartalla" #(e! (tiedot/->LisaaValittuKohdeKartalle (:id rivi))) {:luokka "talvihoitoreitti-kartan-naytto"}))]]]

               ;; Otsikkokoponentin voi avata ja avaamisen jälkeen näytetään lista (grid) reiteistä
               (when (get talvihoitoreittien-tilat (:id rivi))
                 (when (> reittien-maara 0)
                   [:div {:style {:max-width "900px"}}
                    [:h2 {:style {:margin-bottom "-10px"}} "Reitti"]
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
                     (:reitit rivi)]]))])))])]))

(defn *talvihoitoreitit [e! app]
  (komp/luo
    (komp/sisaan-ulos
      #(do
         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
         (nav/vaihda-kartan-koko! :L)
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
  [tuck/tuck tila/talvihoitoreitit *talvihoitoreitit])
