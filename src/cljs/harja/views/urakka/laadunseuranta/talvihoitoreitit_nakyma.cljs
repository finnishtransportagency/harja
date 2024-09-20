(ns harja.views.urakka.laadunseuranta.talvihoitoreitit-nakyma
  "Sanktioiden ja bonusten välilehti"
  (:require [harja.fmt :as fmt]
            [harja.ui.liitteet :as liitteet]
            [reagent.core :as r]
            [tuck.core :as tuck]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.views.kartta :as kartta]
            [harja.views.kartta.tasot :as kartta-tasot]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.laadunseuranta.talvihoitoreitit-tiedot :as tiedot]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.debug :as debug]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.asiakas.kommunikaatio :as k]))

(defn talvihoitoreitit-sivu [e! app]
  (let [talvihoitoreitit (:talvihoitoreitit app)
        talvihoitoreittien-tilat (:talvihoitoreittien-tilat app)
        valitut-kohteet @tiedot/valitut-kohteet-atom]
    [:div
     [kartta/kartan-paikka]
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
                                                      :on-click #(when (> reittien-maara 0) (e! (tiedot/->AvaaTalvihoitoreitti (:id rivi))) #_(avaa-rivi-rn (:id rivi) e! rivi))}
             ;; Nuoli
             [:div.basis48.nogrow
              (when (> reittien-maara 0)
                (if auki?
                  [ikonit/navigation-ympyrassa :down]
                  [ikonit/navigation-ympyrassa :right]))]

             ;; Nimi
             [:div.basis256.nogrow.shrink3.rajaus
              [:div {:style {:display "flex"}}
               [:span {:style {:margin-top "auto" :margin-bottom "auto" :margin-right "5px" :width "12px" :height "12px" :background-color (:vari rivi)}}]
               [:div.semibold.musta.body-text (str (:nimi rivi))]]
              [:div.body-text.harmaa (:pituus rivi)]]

             [:div.basis384.grow2.shrink3.rajaus
              [:div.body-text.semibold.musta "Hoitoluokkien osuudet reitillä (km)"]
              [:div {:style {:display "flex"}}
               (doall (for [h (:hoitoluokat rivi)]
                        ^{:key (hash (str "hoitoluokka-" h))}
                        [:div
                         [:div.body-text.musta {:style {:padding-right "10px"}} (:hoitoluokka h)]
                         [:div.small-text.musta {:style {:padding-right "10px"}} (fmt/desimaaliluku-opt (:pituus h) 2) ]]))]]

             [:div.basis384.grow2.shrink3.rajaus
              [:div.body-text.semibold.musta "Kalusto (kpl)"]
              [:div {:style {:display "flex"}}
               (doall (for [kalusto (:kalustot rivi)]
                        ^{:key (hash kalusto)}
                        [:div
                         [:div.body-text.musta {:style {:padding-right "10px"}} (:kalustotyyppi kalusto)]
                         [:div.small-text.musta {:style {:padding-right "10px"}} (:kalustomaara kalusto) ]]))]]

             [:div.basis256.grow2.shrink2
              [:div.body-text.strong.musta ""]
              ;; Näytä valittu rivi kartalla tai piilota se
              [:<>
               (if (contains? valitut-kohteet (:id rivi))
                 (harja.ui.napit/avaa "Piilota kartalta" #(e! (tiedot/->PoistaValittuKohdeKartalta (:id rivi))))
                 (harja.ui.napit/avaa "Näytä kartalla" #(e! (tiedot/->LisaaValittuKohdeKartalle (:id rivi)))))]]]

            ;; Otsikkokoponentin voi avata ja avaamisen jälkeen näytetään lista (grid) reiteistä
            (when (get talvihoitoreittien-tilat (:id rivi))
              (when (> reittien-maara 0)
                [:div {:style {:max-width "900px"}}
                 [grid/grid
                  {:salli-valiotsikoiden-piilotus? true
                   :valiotsikoiden-alkutila :kaikki-kiinni
                   :tunniste :id
                   :reunaviiva? true}
                  [{:otsikko "Tie" :nimi :tie :tyyppi :string :tasaa :vasen :leveys 1}
                   {:otsikko "Tieosoite" :nimi :formatoitu-tr :tyyppi :string :tasaa :vasen :leveys 3}
                   {:otsikko "Hoitoluokka" :nimi :hoitoluokka :tyyppi :string :tasaa :vasen :leveys 2}
                   {:otsikko "Suunniteltu pituus (km)" :nimi :pituus :tyyppi :numero
                    :fmt #(fmt/desimaaliluku-opt % 2) :tasaa :oikea :leveys 2}
                   {:otsikko "Laskettu pituus (km)" :nimi :laskettu_pituus :tyyppi :numero
                    :fmt #(fmt/desimaaliluku-opt % 2) :tasaa :oikea :leveys 2}]
                  (:reitit rivi)]]))])))]))

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
         (reset! tiedot/karttataso-nakyvissa? true)
         (js/console.log "sisään"))

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
