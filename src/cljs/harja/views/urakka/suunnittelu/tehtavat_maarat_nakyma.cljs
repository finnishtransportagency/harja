(ns harja.views.urakka.suunnittelu.tehtavat-maarat-nakyma
  (:require [harja.pvm :as pvm]
            [harja.ui.dom :as dom]
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
      [napit/yleinen-toissijainen "Muokkaa sopimuksen määriä" #(e! (tiedot/->ToggleTallennus))
       {:vayla-tyyli? true}]])])

(defn- avaa-tai-sulje-haitari [event e! tehtavaryhmaotsikko]
  (when (dom/enter-nappain? event)
    (e! (tiedot/->AvaaRivi tehtavaryhmaotsikko))))

(defn- piirra-caret [e! tehtavaryhmaotsikko avatut-rivit]
  (if (contains? avatut-rivit tehtavaryhmaotsikko)
    [:img {:alt "Expander"
           :src "images/expander-down.svg"
           :tabIndex "0"
           :on-key-down #(avaa-tai-sulje-haitari % e! tehtavaryhmaotsikko)}]
    [:img {:alt "Expander"
           :src "images/expander.svg"
           :tabIndex "0"
           :on-key-down #(avaa-tai-sulje-haitari % e! tehtavaryhmaotsikko)}]))

(defn tehtava-taulukko [e! haku-kaynnissa? tallennustila? tehtavat-ja-maarat tehtavaryhman-tehtavat avatut-rivit]
  (let [_ (js/console.log "Tehtavataulukko :: tallennustila?" (pr-str tallennustila?) tallennustila? (boolean tallennustila?))
        solun-luokka-fn (fn [_arvo rivi]
                          (when (or
                                  haku-kaynnissa?
                                  (some? (:valiotsikko rivi))) "vaalen-tumma-tausta"))
        sarakkeet [{:otsikko "nro" :leveys "5%"
                    :tyyppi :komponentti
                    :komponentti (fn [rivi]
                                   (piirra-caret e! (:tehtavaryhmaotsikko rivi) avatut-rivit))
                    :solun-luokka solun-luokka-fn}
                   {:otsikko "Tehtävä"
                    :leveys "30%"
                    :nimi :tehtava
                    :solun-luokka solun-luokka-fn
                    :muokattava? (constantly true)
                    :tyyppi :komponentti
                    :komponentti (fn [{:keys [tehtava_id nimi valiotsikko]}]
                                   (if tehtava_id
                                     [:<> nimi]
                                     [:div.body-text.strong valiotsikko]))}
                   {:otsikko "Sopimuksen määrä" :leveys "15%" :nimi :tarjous_maara :tyyppi :euro :tasaa :vasen :fmt (partial fmt/euro-opt false)
                    :muokattava? #(and
                                    tallennustila?
                                    ;; Älä anna muokata väliotsikkorivejä
                                    (nil? (:valiotsikko %))) :solun-luokka solun-luokka-fn}
                   {:otsikko "Muutokset" :leveys "15%" :nimi :muutokset :tyyppi :euro :tasaa :vasen :muokattava? (constantly false) :solun-luokka solun-luokka-fn}
                   {:otsikko "Muuttunut määrä" :leveys "20%" :nimi :muuttunut_maara :tyyppi :euro :tasaa :vasen :muokattava? (constantly false) :solun-luokka solun-luokka-fn}
                   {:otsikko "Yksikkö" :leveys "20%" :nimi :yksikko :tyyppi :teksti :tasaa :vasen :muokattava? (constantly false) :solun-luokka solun-luokka-fn}]]
    (if haku-kaynnissa?
      [ajax-loader-pieni]
      [grid/grid
       {:otsikko ""
        :tyhja "Ei tietoja."
        :luokat ["matala-panel"]
        :data-cy "tehtavat-ja-maarat-grid"
        :muokkaa-aina true
        :voi-muokata? (or tallennustila? false)
        :voi-poistaa? (constantly false)
        :peruuta false
        :voi-lisata? false
        :voi-kumota? false
        :piilota-toiminnot? true
        :piilota-muokkaus? true
        :tunniste :nimi
        :jarjesta :jarjestys
        :muutos #(do
                   (e! (tiedot/->PaivitaTehtavatGrid (vals (grid/hae-muokkaustila %)))))
        :rivi-klikattu (fn [rivi]
                         (js/console.log "Rivi klikattu: " (pr-str rivi))
                         (e! (tiedot/->AvaaRivi (:valiotsikko rivi))))}
       sarakkeet
       tehtavat-ja-maarat])))

(defn nakyma [e! {:keys [haku-kaynnissa? tallennus-kesken? tallennustila? viimeisin-muokkaus viimeisin-muokkaaja
                         tehtavat-ja-maarat tehtavaryhman-tehtavat avatut-rivit] :as app}]
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
   [tehtava-taulukko e! haku-kaynnissa? tallennustila? tehtavat-ja-maarat tehtavaryhman-tehtavat avatut-rivit]
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
