(ns harja.views.urakka.suunnittelu.tehtavat-maarat-nakyma
  (:require [harja.pvm :as pvm]
            [tuck.core :as tuck]
            [harja.fmt :as fmt]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.tiedot.urakka :as u]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
            [harja.ui.kentat :as kentat]
            [harja.ui.napit :as napit]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.suunnittelu.tehtavat-maarat-tiedot :as tiedot]
            [harja.views.urakka.valinnat :as urakka-valinnat]))

(defn- tallennus-painikkeet [e! tallennus-kesken? tallennustila? viimeisin-muokkaus viimeisin-muokkaaja tehtavat-ja-maarat]
  [:div.painikkeet.text-right
   [:div.grid-status-viestit
    (cond
      (and (tiedot/onko-muutoksia?) viimeisin-muokkaus)
      [:<>
       [:div.status-viesti.tallennettu
        (str "Viimeksi tallennettu: " (pvm/pvm-aika-klo viimeisin-muokkaus) " (" viimeisin-muokkaaja ")")]
       [:div.status-viesti.tallentamatta
        "Tallentamattomia muutoksia"]]

      (tiedot/onko-muutoksia?)
      [:div.status-viesti.tallentamatta
       "Tallentamattomia muutoksia"]

      viimeisin-muokkaus
      [:div.status-viesti.tallennettu
       (str "Viimeksi tallennettu: " (pvm/pvm-aika-klo viimeisin-muokkaus) " (" viimeisin-muokkaaja ")")]

      :else
      [:div.status-viesti.ei-muutoksia
       "Ei tallennettuja muutoksia"])]

   (if tallennustila?
     [:div
      [:span {:style {:margin-left "1rem"}}
       [napit/yleinen-ensisijainen "Tallenna"
        #(e! (tiedot/->TallennaTehtavat tehtavat-ja-maarat))
        {:disabled (or tallennus-kesken? false)}]]
      [:span {:style {:margin-left "1rem"}}
       [napit/yleinen-toissijainen "Peruuta"
        #(e! (tiedot/->PeruutaTallennus))
        {:disabled (or tallennus-kesken? false)}]]]

     [:span {:style {:margin-left "1rem"}}
      [napit/yleinen-toissijainen "Muokkaa sopimuksen määriä"  #(e! (tiedot/->ToggleTallennus))
       {:vayla-tyyli? true}]])])

(defn tehtava-taulukko [e! haku-kaynnissa? tallennustila? tehtavat-ja-maarat]
  (let [_ (js/console.log "Tehtavataulukko :: tallennustila?" (pr-str tallennustila?) tallennustila? (boolean tallennustila?))
        sarakkeet [{:otsikko "nro" :leveys "30%" :nimi :jarjestys :tyyppi :numero :muokattava? (constantly false)}
                   {:otsikko "Tehtävä" :leveys "30%" :nimi :nimi :tyyppi :teksti :muokattava? (constantly false)}
                   {:otsikko "Sopimuksen määrä" :leveys "15%" :nimi :tarjous_maara :tyyppi :euro :tasaa :vasen :fmt (partial fmt/euro-opt false) :muokattava? (constantly (or tallennustila? false))}
                   {:otsikko "Muutokset" :leveys "15%" :nimi :muutokset :tyyppi :euro :tasaa :vasen :muokattava? (constantly false)}
                   {:otsikko "Muuttunut määrä" :leveys "20%" :nimi :muuttunut_maara :tyyppi :euro :tasaa :vasen :muokattava? (constantly false)}
                   {:otsikko "Yksikkö" :leveys "20%" :nimi :yksikko :tyyppi :teksti :tasaa :vasen :muokattava? (constantly false)}]]
    (if haku-kaynnissa?
      [ajax-loader-pieni]
      [grid/grid
       {:otsikko ""
        :tyhja "Ei tietoja."
        :luokat ["matala-panel"]
        :data-cy "tehtavat-ja-maarat-grid"
        :muokkaa-aina (or tallennustila? false)
        :voi-muokata? (or tallennustila? false)
        :muokattava? (constantly (or tallennustila? false))
        :voi-poistaa? (constantly false)
        :voi-lisata? false
        :voi-kumota? false
        :piilota-toiminnot? false
        :tunniste :tehtava_id
        :jarjesta :jarjestys
        :muutos #(do
                   (e! (tiedot/->PaivitaTehtavatGrid (vals (grid/hae-muokkaustila %)))))}
       sarakkeet
       tehtavat-ja-maarat])))

(defn nakyma [e! {:keys [haku-kaynnissa? tallennus-kesken? tallennustila? viimeisin-muokkaus viimeisin-muokkaaja tehtavat-ja-maarat] :as app}]
  (js/console.log "nakyma")
  [:div#vayla
   [:div.row
    [:div.col-xs-12
     [:h1 "Tehtävät ja määrät"]]]

   [:div.flex-row {:style {:justify-content "flex-start"}}
    [:div.filtteri {:style {:width "200px"}}
     [urakka-valinnat/paivittava-urakkavuosi-tuck
      @u/valittu-aikavali
      #(e! (tiedot/->HaeTehtavatJaMaarat nil)) haku-kaynnissa? false]]]

   [tallennus-painikkeet e! tallennus-kesken? tallennustila? viimeisin-muokkaus viimeisin-muokkaaja tehtavat-ja-maarat]
   [tehtava-taulukko e! haku-kaynnissa? tallennustila? tehtavat-ja-maarat]
   [debug/debug app]])

(defn tehtavat-maarat*
  [e! _]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan #(e! (tiedot/->HaeTehtavatJaMaarat nil)))
    (fn [e! app]
      [:div
       [nakyma e! app]])))

(defn tehtavat-maarat []
  (tuck/tuck tila/suunnittelu-tehtavat-maarat tehtavat-maarat*))
