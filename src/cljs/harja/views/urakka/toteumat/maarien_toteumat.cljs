(ns harja.views.urakka.toteumat.maarien-toteumat
  "Urakan 'Toteumat' välilehden Määrien toteumat osio"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.grid :as grid]
            [harja.ui.taulukko.grid :as new-grid]
            [harja.ui.taulukko.impl.solu :as solu]
            [harja.ui.taulukko.grid-oletusarvoja :as konf]
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko +korostuksen-kesto+]]
            [harja.ui.napit :as napit]
            [harja.ui.viesti :as viesti]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.hallinta.indeksit :as i]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.toteumat :as toteumat]
            [harja.tiedot.urakka.toteumat.maarien-toteumat :as maarien-toteumat]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.ui.valinnat :as ui-valinnat]
            [harja.ui.kentat :as kentat]

            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [cljs.core.async :refer [<! >! chan]]
            [cljs.core.async :refer [<! timeout]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.urakka :as urakka-domain]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka :as urakka]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.urakka.urakka :as tila]
            [tuck.core :as tuck]
            [harja.ui.modal :as modal]
            [datafrisk.core :as df]
            [harja.ui.debug :as debug]
            [harja.views.urakka.toteumat.maarien-toteuma-lomake :as toteuma-lomake]
            [harja.loki :as loki])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))


(defonce debug-visible? (r/atom (not= -1 (.indexOf js/document.location.host "localhost"))))
(defonce debug-state-toggle-listener
         (do (.addEventListener
               js/window "keypress"
               (fn [e]
                 (when (or (and (.-ctrlKey e) (= "d" (.-key e)))
                           (and (.-ctrlKey e) (= "b" (.-key e))))
                   (swap! debug-visible? not))))
             true))

(defonce valittu-kustannus (atom nil))

(defn- laske-prosentti [toteuma suunniteltu]
  (if (or (= 0 toteuma)
          (nil? toteuma))
    0
    (fmt/desimaaliluku-opt (* 100 (/ suunniteltu toteuma)) 2))
  )

(defn- muokkaa-toteumaa-linkki [e! db-aika toteuma-id]
  [:a {:href     "#"
       :on-click (fn [event]
                   (do
                     (.preventDefault event)
                     (e! (maarien-toteumat/->MuokkaaToteumaa toteuma-id))))}
   (if db-aika
     (pvm/pvm db-aika)
       "Ei aikaa")])

(defn- lisaa-toteuma-linkki [e! app tehtavan-nimi tehtavaryhma-otsikko]
  (let [;; Hae tehtävä tehtävälistasta nimen perusteella
        tehtava (some (fn [t]
                        (when (= tehtavan-nimi (:tehtava t))
                          t))
                      (get-in app [:tehtavat]))
        toimenpide (some (fn [t]
                           (when (= tehtavaryhma-otsikko (:otsikko t))
                             t))
                         (get-in app [:toimenpiteet]))]
    [:a {:href     "#"
         :on-click (fn [event]
                     (do
                       (.preventDefault event)
                       (e! (maarien-toteumat/->ToteumanSyotto true tehtava toimenpide))))}
     (str "+ Lisää toteuma")]))

(defn maarita-yksikko [rivi]
  (cond
    (= (:tyyppi rivi) "kokonaishintainen")
    (:yksikko rivi)

    (or (= (:tyyppi rivi) "lisatyo")
        (= (:tyyppi rivi) "akillinen-hoitotyo")
        (= (:tyyppi rivi) "muut-rahavaraukset"))
    "kpl"

    :else
    ""))

; spekseistä laskettu
(def leveydet {:tehtava     "55%"
               :caret       "4%"
               :toteuma     "15%"
               :suunniteltu "17%"
               :prosentti   "9%"})

(defn- ryhmitellyt-taulukko [e! app r]
  (let [row-index-atom (r/atom 0)
        ll
        (mapcat
          (fn [[tehtavaryhma rivit]]
            (let [_ (reset! row-index-atom (inc @row-index-atom))
                  muodostetut (mapcat
                                (fn [rivi]
                                  (let [_ (reset! row-index-atom (inc @row-index-atom))
                                        avattava? true #_(if (or
                                                               (> (count (second rivi)) 1)
                                                               (:toteuma_aika (first (second rivi))))
                                                           true false)
                                        lapsi-rivit (if (and
                                                          (= (get-in app [:valittu-rivi]) (first rivi))
                                                          (not (nil? (:toteutunut (first (second rivi))))))
                                                      (second rivi)
                                                      nil)
                                        toteutunut-maara (or (apply + (map :toteutunut (second rivi))) 0)
                                        suunniteltu-maara (:suunniteltu_maara (first (second rivi)))
                                        {:keys [tyyppi]} (first (second rivi))]
                                    (concat
                                      [^{:key (hash rivi)}
                                      [:tr (merge
                                             (when avattava?
                                               {:on-click #(e! (maarien-toteumat/->AvaaRivi (first rivi)))})
                                             {:class (str "table-default-" (if (odd? @row-index-atom) "even" "odd") " " (when avattava? "klikattava"))})
                                       [:td.strong {:style {:width (:tehtava leveydet)}} (first rivi)]
                                       [:td {:style {:width (:caret leveydet)}} (if
                                                                                  (= (get-in app [:valittu-rivi]) (first rivi))
                                                                                  [ikonit/livicon-chevron-up]
                                                                                  [ikonit/livicon-chevron-down])]
                                       [:td {:style {:width (:toteuma leveydet)}} (str toteutunut-maara " " (maarita-yksikko (first (second rivi))))]
                                       [:td {:style {:width (:suunniteltu leveydet)}} (if (= -1 suunniteltu-maara)
                                                                                        (case tyyppi
                                                                                          "kokonaishintainen" [:span.tila-virhe "---"]
                                                                                          "---")
                                                                                        (str (or suunniteltu-maara 0) " " (:yksikko (first (second rivi)))))]
                                       [:td {:style {:width (:prosentti leveydet)}} (if (= -1 suunniteltu-maara)
                                                                                      (case tyyppi
                                                                                        "kokonaishintainen" [:span.tila-virhe (ikonit/exclamation-sign)]
                                                                                        "---")
                                                                                      (str (laske-prosentti (:suunniteltu_maara (first (second rivi))) toteutunut-maara) " %"))]]]

                                      ;; "+ Lisää toteuma" rivi - jos rivi on auki ja jos tehtävämäärän/toimenpiteen tehtävälle on tietokantaan sallittu käsin lisäys
                                      (when (and
                                              (= (get-in app [:valittu-rivi]) (first rivi))
                                              (= true (:kasin-lisattava? (first (second rivi)))))
                                        [^{:key (str "lisää-toteuma-" (hash rivi))}
                                        [:tr {:class (str "table-default-" (if (odd? @row-index-atom) "even" "odd"))}
                                         [:td {:style {:width (:tehtava leveydet)}} [lisaa-toteuma-linkki e! app (first rivi) (:tehtavaryhma (first (second rivi)))]]
                                         [:td {:style {:width (:caret leveydet)}} ""]
                                         [:td {:style {:width (:toteuma leveydet)}} ""]
                                         [:td {:style {:width (:suunniteltu leveydet)}} ""]
                                         [:td {:style {:width (:prosentti leveydet)}} ""]]])

                                      (if lapsi-rivit
                                        ;; Lisätään toteutuneet määrät lapsi-riveinä
                                        (mapcat
                                          (fn [lapsi]
                                            (let [_ (reset! row-index-atom (inc @row-index-atom))]
                                              [^{:key (hash lapsi)}
                                              [:tr {:class (str "table-default-" (if (odd? @row-index-atom) "even" "odd"))}
                                               [:td {:style {:width (:tehtava leveydet)}} [muokkaa-toteumaa-linkki e! (:toteuma_aika lapsi) (:toteuma_id lapsi)]]
                                               [:td {:style {:width (:caret leveydet)}} ""]
                                               [:td {:style {:width (:toteuma leveydet)}} (str (:toteutunut lapsi) " " (maarita-yksikko lapsi))]
                                               [:td {:style {:width (:suunniteltu leveydet)}} "---"]
                                               [:td {:style {:width (:prosentti leveydet)}} "---"]]]))
                                          lapsi-rivit)
                                        ;; Jos lapsi-rivejä ei ole, mutta toteuma löytyy, niin lisätään se
                                        (when (and
                                                (= (get-in app [:valittu-rivi]) (first rivi))
                                                (> toteutunut-maara 0))
                                          [^{:key (str "toteuma-" (hash rivi))}
                                          (do
                                            (reset! row-index-atom (inc @row-index-atom))
                                            [:tr {:class (str "table-default-" (if (odd? @row-index-atom) "even" "odd"))}
                                             [:td {:style {:width (:tehtava leveydet)}} [muokkaa-toteumaa-linkki e! (:toteuma_aika (first (second rivi))) (:toteuma_id (first (second rivi)))]]
                                             [:td {:style {:width (:caret leveydet)}} ""]
                                             [:td {:style {:width (:toteuma leveydet)}} (str (:toteutunut (first (second rivi))) " " (maarita-yksikko (first (second rivi))))]
                                             [:td {:style {:width (:suunniteltu leveydet)}} "---"]
                                             [:td {:style {:width (:prosentti leveydet)}} "---"]])])))))
                                rivit)]
              (concat [
                       ^{:key (str "otsikko-" (hash tehtavaryhma))}
                       [:tr.header
                        [:td tehtavaryhma]
                        [:td ""]
                        [:td ""]
                        [:td ""]
                        [:td ""]]]
                      muodostetut)))
          r)]
    [:div.table-default
     [:table
      [:thead.table-default-header
       [:tr
        [:th {:style {:width (:tehtava leveydet)}} "Tehtävä"]
        [:th {:style {:width (:caret leveydet)}} ""]
        [:th {:style {:width (:toteuma leveydet)}} "Toteuma nyt"]
        [:th {:style {:width (:suunniteltu leveydet)}} "Suunniteltu"]
        [:th {:style {:width (:prosentti leveydet)}} "%"]]]
      [:tbody
       (doall
         (for [l ll]
           ^{:key (hash l)}
           l))]]]))

(def filtterit-keyword->string
  {:maaramitattavat "Määrämitattavat"
   :rahavaraukset   "Rahavaraukset"
   :lisatyot        "Lisätyöt"})

(defn maarien-toteumalistaus
  "Määrien toteumat listattuna taulukkoon"
  [e! app]
  (let [{:keys [alkupvm]} (-> @tila/tila :yleiset :urakka)  ;; Ota urakan alkamis päivä
        vuosi (pvm/vuosi alkupvm)
        hoitokaudet (into [] (range vuosi (+ 5 vuosi)))
        ryhmitellyt-maarat (get-in app [:toteutuneet-maarat-grouped])
        toimenpiteet (get-in app [:toimenpiteet])
        valittu-toimenpide (if (nil? (:valittu-toimenpide app))
                             {:otsikko "Kaikki" :id 0}
                             (:valittu-toimenpide app))
        valittu-hoitokausi (if (nil? (get-in app [:hoitokauden-alkuvuosi]))
                             2019
                             (get-in app [:hoitokauden-alkuvuosi]))
        ;aikavali-alkupvm (get-in app [:aikavali-alkupvm])
        ;aikavali-loppupvm (get-in app [:aikavali-loppupvm])
        syottomoodi (get-in app [:syottomoodi])
        filtterit (:hakufiltteri app)]
    [:div.maarien-toteumat
     #_ [debug/debug app]
     [:div "Taulukossa toimenpiteittäin ne määrämitattavat tehtävät, joiden toteumaa urakassa seurataan."]
     [:div.flex-row {:style {:flex-wrap "wrap"}}
      [:div {:style {:flex-grow 2 :padding-right "1rem" :min-width "250px"}}
       [:span.alasvedon-otsikko "Toimenpide"]
       [yleiset/livi-pudotusvalikko {:valinta    valittu-toimenpide
                                     :vayla-tyyli? true
                                     :valitse-fn #(e! (maarien-toteumat/->ValitseToimenpide (:id @nav/valittu-urakka) %))
                                     :format-fn  #(:otsikko %)}
        (merge toimenpiteet {:otsikko "Kaikki" :id 0})]]

      [:div {:style {:flex 1 :padding-right "1rem" :min-width "250px"}}
       [:span.alasvedon-otsikko "Hoitokausi"]
       [yleiset/livi-pudotusvalikko {:valinta    valittu-hoitokausi
                                     :vayla-tyyli? true
                                     :valitse-fn #(e! (maarien-toteumat/->ValitseHoitokausi (:id @nav/valittu-urakka) %))
                                     :format-fn  #(str "1.10." % "-30.9." (inc %))}
        hoitokaudet]]
      #_ [:div.label-ja-alasveto
       [kentat/aikavali
        {:kaari-luokkaan           "sininen"
         :valinta-fn               #(e! (maarien-toteumat/->ValitseAikavali %1 %2))
         :pvm-alku                 aikavali-alkupvm
         :rajauksen-alkupvm        (-> @tila/yleiset :urakka :alkupvm)
         :rajauksen-loppupvm       (-> @tila/yleiset :urakka :loppupvm)
         :pvm-loppu                aikavali-loppupvm
         :ikoni                    ikonit/calendar
         :sumeutus-kun-molemmat-fn (fn [alkupvm loppupvm]
                                     (let [_ (js/console.log ":sumeutus-kun-molemmat-fn :: alkupvm-loppupvm" (pr-str alkupvm) (pr-str loppupvm))]
                                       (e! (maarien-toteumat/->HaeToteutuneetMaarat
                                             (:id @nav/valittu-urakka)
                                             (get-in app [:toteuma :toimenpide])
                                             nil
                                             alkupvm loppupvm))))}]]
      [:div {:style {:flex 1 :padding-top "19px"}}
       [napit/uusi
        "Lisaa toteuma"
        #(e! (maarien-toteumat/->ToteumanSyotto (not syottomoodi) nil (:valittu-toimenpide app)))
        {:vayla-tyyli? true
         :luokka "suuri"}]]]
     [:div.flex-row
      [kentat/tee-kentta {:tyyppi           :checkbox-group
                          :nayta-rivina?    true
                          :vayla-tyyli?     true
                          :rivi-solun-tyyli {:padding-right "3rem"}
                          :vaihtoehto-nayta filtterit-keyword->string
                          :vaihtoehdot      [:maaramitattavat :rahavaraukset :lisatyot]
                          :valitse-fn       (fn [tila polku arvo]
                                              (loki/log "valitse " tila polku arvo filtterit)
                                              (e! (maarien-toteumat/->AsetaFiltteri polku arvo)))
                          :valittu-fn       (fn [tila polku]
                                              (when polku
                                                (polku tila)))}
       (r/wrap filtterit
               (constantly true))]]

     [ryhmitellyt-taulukko e! app ryhmitellyt-maarat]
     ;[new-grid/piirra g]
     ]))

(defn- debug-state [app]
  [:span
   (when @debug-visible?
     [:div.row
      [df/DataFriskShell app]])])

(defn maarien-toteumat* [e! app]
  (komp/luo
    (komp/lippu toteumat/maarien-toteumat-nakymassa?)
    (komp/piirretty (fn [this]
                      (do
                        (e! (maarien-toteumat/->HaeKaikkiTehtavat))
                        (e! (maarien-toteumat/->HaeToimenpiteet))
                        (e! (maarien-toteumat/->HaeToteutuneetMaarat
                              (:id @nav/valittu-urakka)
                              (:valittu-toimenpide app)
                              (get-in app [:hoitokauden-alkuvuosi]) nil nil)))))
    (fn [e! app]
      (let [syottomoodi (get-in app [:syottomoodi])]
        [:div {:id "vayla"}
         (if syottomoodi
           [toteuma-lomake/maarien-toteuman-syottolomake* e! app]
           [maarien-toteumalistaus e! app])
         [debug-state app]]))))

(defn maarien-toteumat []
  (tuck/tuck tila/toteumat-maarat maarien-toteumat*))