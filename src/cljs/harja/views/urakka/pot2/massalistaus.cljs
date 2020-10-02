(ns harja.views.urakka.pot2.massalistaus
  "POT2 massalistaukset"
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [cljs.core.async :refer [<! chan]]
            [cljs.spec.alpha :as s]
            [cljs-time.core :as t]
            [goog.events.EventType :as event-type]

            [harja.ui.grid :as grid]
            [harja.ui.debug :refer [debug]]
            [harja.ui.dom :as dom]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.komponentti :as komp]
            [harja.ui.modal :as modal]
            [harja.ui.napit :as napit]
            [harja.ui.validointi :as v]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.viesti :as viesti]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko virheen-ohje] :as yleiset]

            [harja.domain.paallystysilmoitus :as paallystysilmoitus-domain]
            [harja.domain.pot2 :as pot2-domain]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]

            [harja.tiedot.urakka.paallystys :as paallystys]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.urakka.yhatuonti :as yha]
            [harja.tiedot.urakka.yllapito :as yllapito-tiedot]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.muokkauslukko :as lukko]
            [harja.tiedot.urakka.pot2.massat :as tiedot-massa]
            [harja.tiedot.urakka.urakka :as tila]

            [harja.fmt :as fmt]
            [harja.loki :refer [log logt tarkkaile!]]


            [harja.views.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.pvm :as pvm]
            [harja.tyokalut.vkm :as vkm]
            [harja.ui.lomake :as ui-lomake]
            [clojure.string :as str])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn listaa-lisa-aineet [lisa-aineet]
  (let [_ (js/console.log "lisa-aineet" (pr-str lisa-aineet))
        _ (js/console.log "lisa-aineet2" (pr-str (map :lisaaine/nimi lisa-aineet)))]

    (str/join "," (map :lisaaine/nimi lisa-aineet))))

(defn- listaa-massat [app]
  (let [row-index-atom (r/atom 0)
        massat (:massat app)]
    [:div.table-default
     [debug app {:otsikko "TUCK STATE"}]
     [:table
      [:thead.table-default-header
       [:tr
        [:th {:style {:width "20%"}} "Massatyyppi"]
        [:th {:style {:width "20%"}} "Runkoaineet"]
        [:th {:style {:width "20%"}} "Sideaineet"]
        [:th {:style {:width "20%"}} "Lisäaineet"]
        [:th {:style {:width "20%"}} ""]]]
      [:tbody
       (for [m massat
             :let [_ (reset! row-index-atom (inc @row-index-atom))]]
         ^{:key (hash m)}
         [:tr {:class (str "table-default-" (if (odd? @row-index-atom) "even" "odd"))}
          [:td {:style {:width "20%"}} (::pot2-domain/nimi m)]
          [:td {:style {:width "20%"}} ""]
          [:td {:style {:width "20%"}} (str/join "," (map :sideaine/tyyppi (::pot2-domain/sideaineet m)))]
          [:td {:style {:width "20%"}} (listaa-lisa-aineet (::pot2-domain/lisa-aineet m))]
          [:td {:style {:width "20%"}} "---"]])
       ]]]))

(defn- valitut-sideaineet [{:keys [e! app]} {{sideaineet :sideaineet :as lomake} :data}]
  (let [_ (js/console.log "valitut-sideaineet :: lomake" (pr-str lomake))
        _ (js/console.log "valitut-sideaineet :: sideaineet" (pr-str sideaineet))
        paivita! (fn [polku indeksi arvo]
                   (js/console.log "polku indeksi arvo" polku indeksi arvo))]
    [:div
     (doall
       (map-indexed
         (fn [indeksi sideaine]
           [:div "indeksi"]
           #_ [kentat/tee-kentta
            {:otsikko "Tyyppi"
             :nimi [:sideaineet indeksi]
             ::ui-lomake/col-luokka ""
             ;:virhe? (validi? [::t/toteumat indeksi ::t/tehtava])
             :tyyppi :valinta
             :valinta-nayta (fn [arvo] (str arvo))
             :vayla-tyyli? true
             :valinta-arvo identity
             :valinnat pot2-domain/paallystemassan-sideaineet
             :pakollinen? true}
            (r/wrap sideaine
                    (r/partial paivita! :sideaineet indeksi))])
         sideaineet))]
    ))

(defn massa-lomake [e! app]
  (let [massa (:massa app)
        lomake (:pot2-massa-lomake app)
        _ (js/console.log "massa-lomake :: lomake " (pr-str lomake))
        _ (js/console.log "massa-lomake :: app " (pr-str app))
        ;runkoaineen-materiaalit [{:id 1 :nimi "jotain on"}{:id 2 :nimi "Runkoaine 2"}]
        runkoaineen-materiaalit (keep ::pot2-domain/nimi (:runkoaineet app)) #_["jako" "apila" "uusikatu"]
        _ (js/console.log "massa-lomake :: runkoaineen-materiaalit " (pr-str runkoaineen-materiaalit))
        sideaine-index 0
        sideaineet-kentat {:otsikko "Tyyppi"
                           :nimi :sideaineet
                           ::ui-lomake/col-luokka "col-xs-12 col-md-3"
                           :tyyppi :komponentti
                           :komponentti (fn [_] [:div "jes"]) #_(r/partial valitut-sideaineet {:e! e!
                                                                                               :app app})}
        #_ [{:teksti "Lopputuotteen sideaine"
                            :tyyppi :valiotsikko
                            ::ui-lomake/col-luokka "col-xs-12"}
                           ;; TODO: Näitä pitää pystyä lisäämään useita
                           {:otsikko "Tyyppi"
                            :nimi :sideaineet
                            ::ui-lomake/col-luokka "col-xs-12 col-md-3"
                            :tyyppi :komponentti
                            :komponentti [:div "jes"] #_ (r/partial valitut-sideaineet {:e! e!
                                                                        :app app})}]]
    [:div
     [debug app {:otsikko "TUCK STATE"}]
     [ui-lomake/lomake
      {
       :muokkaa! #(e! (tiedot-massa/->PaivitaLomake (ui-lomake/ilman-lomaketietoja %)))
       #_(fn [data]
           (do
             ;;todo muokkausfunkkari
             (js/console.log "muokkaa!" (pr-str data))
             (e! (tiedot-massa/->PaivitaLomake data))
             ))
       ;:voi-muokata? true
       ;:tarkkaile-ulkopuolisia-muutoksia? true
       :header-fn (fn [data]
                    [:<>
                     [:div.flex-row {:style {:padding-left "15px"
                                             :padding-right "15px"}}
                      [:h2 (if (:pot2-massa/id massa)
                             "Muokkaa massaa"
                             "Luo uusi massa")]
                      ]])
       :footer-fn (fn [data]
                    [:div.flex-row.alkuun
                     [napit/tallenna
                      "Tallenna"
                      #(e! (tiedot-massa/->TallennaLomake data))
                      {:vayla-tyyli? true
                       :luokka "suuri"
                       ;:disabled (not koko-validi?)
                       }]
                     [napit/peruuta
                      "Peruuta"
                      #(e! (tiedot-massa/->TyhjennaLomake data))
                      {:vayla-tyyli? true
                       :luokka "suuri"}]])
       :vayla-tyyli? true
       }
      [{:otsikko "Massatyyppi"
        :nimi ::pot2-domain/massatyyppi
        ::ui-lomake/col-luokka "col-xs-12 col-md-3"
        ;:virhe? todo
        :tyyppi :valinta
        :valinta-nayta (fn [rivi]
                         (if (:koodi rivi)
                           (str (:lyhenne rivi) " - " (:nimi rivi))
                           (:nimi rivi)))
        :vayla-tyyli? true
        :valinta-arvo identity
        :valinnat paallystys-ja-paikkaus/+paallystetyypit-ja-nil+
        :pakollinen? true}
       {:otsikko "Max raekoko"
        :nimi ::pot2-domain/max-raekoko
        ::ui-lomake/col-luokka "col-xs-12 col-md-3"
        ;:virhe? todo
        :tyyppi :valinta
        :valinta-nayta (fn [rivi]
                         (str rivi))
        :vayla-tyyli? true
        :valinta-arvo identity
        :valinnat pot2-domain/massan-max-raekoko
        :pakollinen? true}
       {:otsikko "DoP nro"
        :nimi ::pot2-domain/dop_nro
        ::ui-lomake/col-luokka "col-xs-12 col-md-3"
        :tyyppi :positiivinen-numero
        :validoi [[:ei-tyhja "Anna DoP nro"]]
        :vayla-tyyli? true
        :pakollinen? true}
       {:otsikko "Massan nimi"
        :nimi ::pot2-domain/nimi
        ;::ui-lomake/col-luokka "col-xs-12 col-md-3"
        :tyyppi :string
        ;:pakollinen? true
        :vayla-tyyli? true
        }
       {:otsikko "Kuulamyllyluokka"
        :nimi ::pot2-domain/kuulamyllyluokka
        ::ui-lomake/col-luokka "col-xs-12 col-md-3"
        ;:virhe? todo
        :tyyppi :valinta
        :valinta-nayta (fn [rivi]
                         (str (:nimi rivi)))
        :vayla-tyyli? true
        :valinta-arvo identity
        :valinnat paallystysilmoitus-domain/+kyylamyllyt-ja-nil+
        :pakollinen? true}
       {:otsikko "Litteyslukuluokka"
        :nimi ::pot2-domain/litteyslukuluokka
        ::ui-lomake/col-luokka "col-xs-12 col-md-3"
        ;:virhe? todo
        :tyyppi :valinta
        :valinta-nayta (fn [rivi]
                         (str rivi))
        :vayla-tyyli? true
        :valinta-arvo identity
        :valinnat pot2-domain/litteyslukuluokat
        :pakollinen? true}
       {:nimi :runkoaineet
        :otsikko "Runkoaineen materiaali"
        :tyyppi :checkbox-group
        :vaihtoehdot runkoaineen-materiaalit
        :vaihtoehto-nayta (fn [rivi]
                            (str rivi))}
       sideaineet-kentat
       ;; TODO Lisätty sideaine pitää lisätä
       #_{}
       {:nimi :lisa-aineet
        :otsikko "Lisäaineet"
        :tyyppi :checkbox-group
        :vaihtoehdot pot2-domain/massan-lisaineet
        :vaihtoehto-nayta (fn [rivi]
                            (str rivi))}

       lomake]]]))


(defn- paallystysmassat-taulukko [e! {:keys [massat] :as app}]
  (log "jarno, päällystysmassat " (pr-str massat))
  [grid/grid
   {:otsikko "Massat"
    :tunniste :id
    :tyhja        (if (nil? massat)
                    [ajax-loader "Haetaan massatyyppejä..."]
                    "Urakalle ei ole vielä lisätty massoja")
    :voi-lisata?  false
    :voi-kumota?  false
    :voi-poistaa? (constantly false)
    :voi-muokata? true}
   [{:otsikko "Massatyyppi" :hae #(str (get % ::pot2-domain/massatyyppi) " - " ) :tyyppi :string :leveys 20}

    {:otsikko "Runkoaineet" :nimi ::pot2-domain/runkoaineet :fmt  #(or % "-") :tyyppi :komponentti :leveys 5
     :komponentti (fn [rivi] [:span "Runkoaineet TODO"])}
    {:otsikko "Sideaineet" :nimi ::pot2-domain/sideaineet :fmt  #(or % "-") :tyyppi :komponentti :leveys 5
     :komponentti (fn [rivi] [:span "Sideaineet TODO"])}
    {:otsikko "Lisäaineet" :nimi ::pot2-domain/lisa-aineet :fmt  #(or % "-") :tyyppi :komponentti :leveys 5
     :komponentti (fn [rivi] [:span "Lisäaineet TODO"])}
    {:otsikko "Toiminnot" :nimi ::pot2-domain/lisa-aineet :fmt #(or % "-") :tyyppi :komponentti :leveys 5
     :komponentti (fn [rivi] [:span
                              [napit/nappi ""
                               #(log "pen painettu")
                               {:ikoninappi? true :luokka "klikattava"
                                :ikoni (ikonit/livicon-pen)}]
                              [napit/nappi ""
                               #(log "duplicate painettu")
                               {:ikoninappi? true :luokka "klikattava"
                                :ikoni (ikonit/duplicate)}]])}]
   massat])

(defn- kantavan-kerroksen-materiaalit-taulukko [e! {:keys [murskeet] :as app}])

(defn- materiaalikirjasto [e! {:keys [runkoaineet massat murskeet] :as app}]
  (log "materiaalikirjasto, runkoaineet " (pr-str runkoaineet ))
  (log "materiaalikirjasto, massat " (pr-str massat ))
  (log "materiaalikirjasto, murskeet " (pr-str murskeet ))
  [:span
   [paallystysmassat-taulukko e! app]
   [kantavan-kerroksen-materiaalit-taulukko e! app]])

(defn massat* [e! app]
  (komp/luo
    (komp/lippu tiedot-massa/pot2-nakymassa?)
    (komp/piirretty (fn [this]
                      (e! (tiedot-massa/->HaePot2Massat))
                      (e! (tiedot-massa/->HaeKoodistot))))
    (fn [e! app]
      (let [
            avaa-massa-lomake? (:avaa-massa-lomake? app)
            _ (js/console.log "massat* :: app" (pr-str app))
            _ (js/console.log "massalistatus :: " "avaa-massa-lomake?" (pr-str avaa-massa-lomake?))]

        [:div
         [:div.row
          [napit/uusi
           "Lisaa massa"
           #(e! (tiedot-massa/->UusiMassa true))
           {:vayla-tyyli? true
            :luokka "suuri"}]

          (if avaa-massa-lomake?
            [massa-lomake e! app]
            [materiaalikirjasto e! app])]]))))



(defn materiaalikirjasto-modal [e! app]
  (log "materiaalikirjasto-modal " (pr-str app))
  [modal/modal
   {:otsikko (str "Urakan materiaalikirjasto - " (:nimi @nav/valittu-urakka))
    :luokka "materiaalikirjasto-modal"
    :nakyvissa? @tiedot-massa/nayta-materiaalikirjasto?
    :sulje-fn #(swap! tiedot-massa/nayta-materiaalikirjasto? not)}
   [:div
    [tuck/tuck tila/pot2 massat*]]])


