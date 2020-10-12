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

(defn massa-lomake [e! {:keys [massa lomake materiaalikoodistot] :as app}]
  (let [{:keys [massatyypit runkoainetyypit sideainetyypit lisaainetyypit]} materiaalikoodistot
        _ (log "jarno massatyypit " (pr-str massatyypit))
        _ (log "jarno runkoainetyypit " (pr-str runkoainetyypit))
        _ (log "jarno sideainetyypit " (pr-str sideainetyypit))
        _ (log "jarno lisaainetyypit " (pr-str lisaainetyypit))
        massa (:massa app)
        lomake (:pot2-massa-lomake app)
        _ (js/console.log "massa-lomake :: lomake " (pr-str lomake))]
    [:div
     [ui-lomake/lomake
      {:muokkaa! #(e! (tiedot-massa/->PaivitaLomake (ui-lomake/ilman-lomaketietoja %)))
       :otsikko (if (:pot2-massa/id massa)
                  "Muokkaa massaa"
                  "Uusi massa")
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
       :vayla-tyyli? true}
      [{:otsikko "Massan nimi" :muokattava? (constantly false) :nimi ::pot2-domain/massan-nimi :tyyppi :string :palstoja 3
        :luokka "bold" :vayla-tyyli? true :kentan-arvon-luokka "placeholder"
        :hae (fn [rivi]
               (if-not (::pot2-domain/tyyppi rivi)
                 "Nimi muodostuu automaattisesti lomakkeeseen täytettyjen tietojen perusteella"
                 (pot2-domain/massatyypin-rikastettu-nimi massatyypit rivi)))}
       (ui-lomake/rivi
         {:otsikko "Massatyyppi"
          :nimi ::pot2-domain/tyyppi :tyyppi :valinta
          :valinta-nayta ::pot2-domain/nimi :valinta-arvo ::pot2-domain/koodi :valinnat massatyypit
          :pakollinen? true :vayla-tyyli? true}
         {:otsikko "Max raekoko" :nimi ::pot2-domain/max-raekoko
          :tyyppi :valinta
          :valinta-nayta (fn [rivi]
                           (str rivi))
          :vayla-tyyli? true
          :valinta-arvo identity
          :valinnat pot2-domain/massan-max-raekoko
          :pakollinen? true}
         {:otsikko "Nimen tarkenne" :nimi ::pot2-domain/nimen-tarkenne :tyyppi :string
          :vayla-tyyli? true})
       (ui-lomake/rivi
         {:otsikko "Kuulamyllyluokka"
          :nimi ::pot2-domain/kuulamyllyluokka
          :tyyppi :valinta :valinta-nayta (fn [rivi]
                                            (str (:nimi rivi)))
          :vayla-tyyli? true :valinta-arvo :nimi
          :valinnat paallystysilmoitus-domain/+kyylamyllyt-ja-nil+
          :pakollinen? true}
         {:otsikko "Litteyslukuluokka"
          :nimi ::pot2-domain/litteyslukuluokka :tyyppi :valinta
          :valinta-nayta (fn [rivi]
                           (str rivi))
          :vayla-tyyli? true
          :valinta-arvo identity
          :valinnat pot2-domain/litteyslukuluokat
          :pakollinen? true}
         {:otsikko "DoP nro" :nimi ::pot2-domain/dop-nro :tyyppi :string
          :validoi [[:ei-tyhja "Anna DoP nro"]]
          :vayla-tyyli? true :pakollinen? true})]

      #_{:nimi :runkoaineet
         :otsikko "Runkoaineen materiaali"
         :tyyppi :checkbox-group
         :vaihtoehdot (map ::pot2-domain/nimi runkoainetyypit)
         :vaihtoehto-nayta str}

      ;; TODO Lisätty sideaine pitää lisätä
      #_{}
      #_{:nimi :lisa-aineet
         :otsikko "Lisäaineet"
         :tyyppi :checkbox-group
         :vaihtoehdot pot2-domain/massan-lisaineet
         :vaihtoehto-nayta (fn [rivi]
                             (str rivi))}

      lomake]
     [debug app {:otsikko "TUCK STATE"}]]))

(defn- massan-runkoaineet
  [rivi ainetyypit]
[:span
 (for [aine (reverse
              (sort-by :runkoaine/massaprosentti
                       (:harja.domain.pot2/runkoaineet rivi)))]
   ^{:key (:runkoaine/id aine)}
   [:span
    [:div
     (str (pot2-domain/ainetyypin-koodi->nimi ainetyypit (:runkoaine/tyyppi aine))
          " ("
          (:runkoaine/esiintyma aine) ")")
   [:span.pull-right (str (:runkoaine/massaprosentti aine) "%")]]
    (when (:runkoaine/kuulamyllyarvo aine)
      [:div "-kuulamyllyarvo " (:runkoaine/kuulamyllyarvo aine)])
    (when (:runkoaine/litteysluku aine)
      [:div "-litteysluku " (:runkoaine/litteysluku aine)])])])

(defn- massan-sideaineet [rivi ainetyypit]
  [:span
   (for [aine (reverse
                (sort-by :sideaine/pitoisuus
                         (:harja.domain.pot2/sideaineet rivi)))]
     ^{:key (:sideaine/id aine)}
     [:span
      [:div
       (str (pot2-domain/ainetyypin-koodi->nimi ainetyypit (:sideaine/tyyppi aine)))
       [:span.pull-right (str (:sideaine/pitoisuus aine) "%")]]])])

(defn- massan-lisaaineet [rivi ainetyypit]
  [:span
   (for [aine (reverse
                (sort-by :lisaaine/pitoisuus
                         (:harja.domain.pot2/lisa-aineet rivi)))]
     ^{:key (:lisaaine/id aine)}
     [:span
      [:div
       (str (pot2-domain/ainetyypin-koodi->nimi ainetyypit (:lisaaine/tyyppi aine)))
       [:span.pull-right (str (:lisaaine/pitoisuus aine) "%")]]])])

(defn- massan-toiminnot [e! rivi]
  [:span.pull-right
   [napit/nappi ""
    #(log "pen painettu")
    {:ikoninappi? true :luokka "klikattava"
     :ikoni (ikonit/livicon-pen)}]
   [napit/nappi ""
    #(log "duplicate painettu")
    {:ikoninappi? true :luokka "klikattava"
     :ikoni (ikonit/livicon-duplicate)}]])

(defn- paallystysmassat-taulukko [e! {:keys [massat materiaalikoodistot] :as app}]
  [grid/grid
   {:otsikko "Massat"
    :tunniste :pot2-massa/id
    :tyhja (if (nil? massat)
             [ajax-loader "Haetaan massatyyppejä..."]
             "Urakalle ei ole vielä lisätty massoja")
    :voi-lisata? false
    :voi-kumota? false
    :voi-poistaa? (constantly false)
    :voi-muokata? true
    :custom-toiminto {:teksti "Luo uusi massa"
                      :toiminto #(e! (tiedot-massa/->UusiMassa true))
                      :opts {:ikoni (ikonit/livicon-plus)
                             :luokka "napiton-nappi"}}}
   [{:otsikko "Massatyyppi" :tyyppi :string
     :hae (fn [rivi]
            (pot2-domain/massatyypin-rikastettu-nimi (:massatyypit materiaalikoodistot) rivi))
     :solun-luokka (constantly "bold") :leveys 8}
    {:otsikko "Runkoaineet" :nimi ::pot2-domain/runkoaineet :fmt #(or % "-") :tyyppi :komponentti :leveys 6
     :komponentti (fn [rivi]
                    [massan-runkoaineet rivi (:runkoainetyypit materiaalikoodistot)])}
    {:otsikko "Sideaineet" :nimi ::pot2-domain/sideaineet :fmt  #(or % "-") :tyyppi :komponentti :leveys 5
     :komponentti (fn [rivi]
                    [massan-sideaineet rivi (:sideainetyypit materiaalikoodistot)])}
    {:otsikko "Lisäaineet" :nimi ::pot2-domain/lisa-aineet :fmt  #(or % "-") :tyyppi :komponentti :leveys 4
     :komponentti (fn [rivi]
                    [massan-lisaaineet rivi (:lisaainetyypit materiaalikoodistot)])}
    {:otsikko "Toiminnot" :nimi ::pot2-domain/lisa-aineet :fmt #(or % "-") :tyyppi :komponentti :leveys 3
     :komponentti (fn [rivi]
                    [massan-toiminnot e! rivi])}]
   massat])

(defn- kantavan-kerroksen-materiaalit-taulukko [e! {:keys [murskeet] :as app}])

(defn- materiaalikirjasto [e! app]
  [:span
   [paallystysmassat-taulukko e! app]
   [kantavan-kerroksen-materiaalit-taulukko e! app]
   [napit/sulje #(swap! tiedot-massa/nayta-materiaalikirjasto? not)]])

(defn massat* [e! app]
  (komp/luo
    (komp/lippu tiedot-massa/pot2-nakymassa?)
    (komp/piirretty (fn [this]
                      (e! (tiedot-massa/->AlustaTila))
                      (e! (tiedot-massa/->HaePot2Massat))
                      (e! (tiedot-massa/->HaeKoodistot))))
    (fn [e! app]
      [:div
       (if (:avaa-massa-lomake? app)
         [massa-lomake e! app]
         [materiaalikirjasto e! app])])))



(defn materiaalikirjasto-modal [_ _]
  [modal/modal
   {:otsikko (str "Urakan materiaalikirjasto - " (:nimi @nav/valittu-urakka))
    :luokka "materiaalikirjasto-modal"
    :nakyvissa? @tiedot-massa/nayta-materiaalikirjasto?
    :sulje-fn #(swap! tiedot-massa/nayta-materiaalikirjasto? not)}
   [:div
    [tuck/tuck tila/pot2 massat*]]])


