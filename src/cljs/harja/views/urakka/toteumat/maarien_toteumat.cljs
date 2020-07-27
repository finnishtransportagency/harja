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
    (* 100 (/ suunniteltu toteuma)))
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
                                        suunniteltu-maara (:suunniteltu_maara (first (second rivi)))]
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
                                       [:td {:style {:width (:toteuma leveydet)}} (str toteutunut-maara " " (:yksikko (first (second rivi))))]
                                       [:td {:style {:width (:suunniteltu leveydet)}} (if (= -1 suunniteltu-maara)
                                                                                        "---"
                                                                                        (str (or suunniteltu-maara 0) " " (:yksikko (first (second rivi)))))]
                                       [:td {:style {:width (:prosentti leveydet)}} (if (= -1 suunniteltu-maara)
                                                                                      "---"
                                                                                      (str (laske-prosentti (:suunniteltu_maara (first (second rivi))) toteutunut-maara) " %"))]]]

                                      ;; "+ Lisää toteuma" rivi - jos rivi on auki
                                      (when (= (get-in app [:valittu-rivi]) (first rivi))
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
                                               [:td {:style {:width (:toteuma leveydet)}} (str (:toteutunut lapsi) " " (cond
                                                                                                                         (= (:tyyppi lapsi) "kokonaishintainen")
                                                                                                                         (:yksikko lapsi)

                                                                                                                         (or (= (:tyyppi lapsi) "lisatyo")
                                                                                                                             (= (:tyyppi lapsi) "akillinen-hoitotyo"))
                                                                                                                         "kpl"

                                                                                                                         :else
                                                                                                                         ""))]
                                               [:td {:style {:width (:suunniteltu leveydet)}} "---"]
                                               [:td {:style {:width (:prosentti leveydet)}} "---"]]]))
                                          lapsi-rivit)
                                        ;; Jos lapsi-rivejä ei ole, mutta toteuma löytyy, niin lisätään se
                                        (when (and
                                                (= (get-in app [:valittu-rivi]) (first rivi))
                                                #_(> toteutunut-maara 0))
                                          [^{:key (str "toteuma-" (hash rivi))}
                                          (do
                                            (reset! row-index-atom (inc @row-index-atom))
                                            [:tr {:class (str "table-default-" (if (odd? @row-index-atom) "even" "odd"))}
                                             [:td {:style {:width (:tehtava leveydet)}} [muokkaa-toteumaa-linkki e! (:toteuma_aika (first (second rivi))) (:toteuma_id (first (second rivi)))]]
                                             [:td {:style {:width (:caret leveydet)}} ""]
                                             [:td {:style {:width (:toteuma leveydet)}} (str (:toteutunut (first (second rivi))) " " (:yksikko (first (second rivi))))]
                                             [:td {:style {:width (:suunniteltu leveydet)}} "---"]
                                             [:td {:style {:width (:prosentti leveydet)}} "---"]])])))))
                                rivit)]
              (concat [[:tr.header
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
        toteutuneet-maarat (get-in app [:toteutuneet-maarat])
        ryhmitellyt-maarat (get-in app [:toteutuneet-maarat-grouped])
        toimenpiteet (get-in app [:toimenpiteet])
        tehtavat (get-in app [:tehtavat])
        valittu-toimenpide (if (nil? (get-in app [:toteuma :toimenpide]))
                             {:otsikko "Kaikki" :id 0}
                             (get-in app [:toteuma :toimenpide]))
        valittu-hoitokausi (if (nil? (get-in app [:hoitokauden-alkuvuosi]))
                             2019
                             (get-in app [:hoitokauden-alkuvuosi]))
        aikavali-alkupvm (get-in app [:aikavali-alkupvm])
        aikavali-loppupvm (get-in app [:aikavali-loppupvm])
        syottomoodi (get-in app [:syottomoodi])
        dom-id "maarien-toteumat-taulukko"
        ;g (maarien-toteumat/uusi-gridi dom-id)
        ;_ (new-grid/aseta-gridin-polut g)
        filtterit (:hakufiltteri app)]
    [:div.maarien-toteumat
     [debug/debug app]
     [:div "Taulukossa toimenpiteittäin ne määrämitattavat tehtävät, joiden toteumaa urakassa seurataan."]
     [:div
      [:div.label-ja-alasveto.iso-alasveto
       [:span.alasvedon-otsikko "Toimenpide"]
       [yleiset/livi-pudotusvalikko {:valinta    valittu-toimenpide
                                     :valitse-fn #(e! (maarien-toteumat/->ValitseToimenpide (:id @nav/valittu-urakka) %))
                                     :format-fn  #(:otsikko %)}
        (merge toimenpiteet {:otsikko "Kaikki" :id 0})]]

      [:div.label-ja-alasveto.iso-alasveto
       [:span.alasvedon-otsikko "Hoitokausi"]
       [yleiset/livi-pudotusvalikko {:class      "livi-alasveto-korkea"
                                     :valinta    valittu-hoitokausi
                                     :valitse-fn #(e! (maarien-toteumat/->ValitseHoitokausi (:id @nav/valittu-urakka) %))
                                     :format-fn  #(str "1.10." % "-30.9." (inc %))}
        hoitokaudet]]
      [:div.label-ja-alasveto
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
      [:div.label-ja-alasveto
       [napit/yleinen-ensisijainen
        " + Lisaa toteuma"
        #(e! (maarien-toteumat/->ToteumanSyotto (not syottomoodi) nil nil))
        {:vayla-tyyli? true
         :luokka       "suuri"}]]
      ]
     [:div.flex-row
      [kentat/tee-kentta {:tyyppi           :checkbox-group
                          :nayta-rivina?    true
                          :vayla-tyyli?     true
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

(defn- toteuman-poiston-varmistus-modaali
  [{:keys [varmistus-fn]}]
  [:div
   "Oletko varma?"

   [:div
    [napit/yleinen-toissijainen
     "Peruuta"
     (fn []
       (modal/piilota!))
     {:vayla-tyyli? true
      :luokka       "suuri"}]
    [napit/poista
     "Poista tiedot"
     varmistus-fn
     {:vayla-tyyli? true
      :luokka       "suuri"}]]])

(defonce maara-atom (r/atom 0))
(defonce toteuma-valmis-atom (r/atom (pvm/nyt)))
#_(defn toteuman-syotto [e! app]
    (let [syottomoodi (get-in app [:syottomoodi])
          toimenpiteet (get-in app [:toimenpiteet])
          valittu-toimenpide (get-in app [:toteuma :toimenpide])
          valittu-toimenpide (if (= 0 (:id valittu-toimenpide))
                               {:id 0 :otsikko " - valitse - "}
                               valittu-toimenpide)
          tehtavat (get-in app [:tehtavat])
          valittu-tehtava (cond
                            (empty? tehtavat)
                            {:tehtava "- ei käsin lisättäviä tehtäviä - " :id 0}
                            (and (not (empty? tehtavat))
                                 (nil? (get-in app [:toteuma :tehtava])))
                            {:tehtava "- valitse - " :id 0}
                            :default
                            (get-in app [:toteuma :tehtava]))
          maara (get-in app [:toteuma :maara])
          _ (reset! toteuma-valmis-atom (get-in app [:toteuma :loppupvm]))
          lisatieto (get-in app [:toteuma :lisatieto])
          lomake-validoitu? (get-in app [:lomake-validoitu?])]

      [:div.ajax-peitto-kontti.lomake
       [:div.palstat
        [:div.palsta
         [napit/takaisin
          "Takaisin"
          #(e! (maarien-toteumat/->ToteumanSyotto (not syottomoodi) nil nil))
          {:vayla-tyyli?  true
           :teksti-nappi? true
           :style         {:font-size "14px"}}]

         [:h2 (str (if-not (nil? (:id lomake))
                     "Muokkaa toteumaa"
                     "Uusi toteuma"))]]
        [:div.palsta.flex
         (when-not (nil? (:id lomake))
           [napit/poista "Poista toteuma"
            #(modal/nayta! {:otsikko "Haluatko varmasti poistaa toteuman?"}
                           [toteuman-poiston-varmistus-modaali
                            {:varmistus-fn (fn []
                                             (modal/piilota!)
                                             (e! (maarien-toteumat/->PoistaToteuma (:id lomake))))}])
            {:vayla-tyyli?  true
             :teksti-nappi? true
             :style         {:font-size   "14px"
                             :margin-left "auto"}}])]]
       [:div.palstat
        [:div.palsta
         [:h3 "Mihin toimenpiteeseen toteuma liittyy?"]

         [:div.label-ja-alasveto.iso-alasveto
          [:label "Toimenpide *"]
          [yleiset/livi-pudotusvalikko {:valinta    valittu-toimenpide
                                        :valitse-fn #(e! (maarien-toteumat/->ValitseToimenpide (:id @nav/valittu-urakka) %))
                                        :format-fn  #(:otsikko %)}
           toimenpiteet]]]]
       [:div.palstat
        [:div.palsta
         [:h3 "Tehtävän tiedot"]
         [:div.label-ja-alasveto.iso-alasveto.iso-input
          [:label.required "Työ valmis pvm *"]
          [kentat/tee-kentta {:otsikko              "Toteuma valmis"
                              :nimi                 :toteuma-valmis
                              :tyyppi               :pvm
                              :fmt                  pvm/pvm-aika-opt
                              :muokattava?          true
                              :leveys               5
                              :on-datepicker-select #(e! (maarien-toteumat/->AsetaLoppuPvm %))}
           toteuma-valmis-atom]]
         [:div.label-ja-alasveto.iso-alasveto
          [:label.required "Tehtavä *"]
          [yleiset/livi-pudotusvalikko {:valinta    valittu-tehtava
                                        :valitse-fn #(e! (maarien-toteumat/->ValitseTehtava %))
                                        :format-fn  #(:tehtava %)}
           tehtavat]]]]

       [:div.palstat
        [:div.palsta
         [:div.kulukentta
          [:label.required "Toteutunut määrä *"]
          [:div
           [:input.input-default.sisainen-kentta
            {:value     maara
             :on-change (fn [e]
                          (let [arvo (-> e .-target .-value)]
                            (e! (maarien-toteumat/->AsetaMaara arvo))))}]
           [:span.sisainen-label {:style {:margin-left (str "-" (+ 13 (* 5 (count (:yksikko valittu-tehtava)))) "px")}}
            (str (:yksikko valittu-tehtava))]]
          ]
         [kentat/vayla-lomakekentta
          "Lisätiedot"
          :on-change (fn [e]
                       (let [arvo (-> e .-target .-value)]
                         (e! (maarien-toteumat/->AsetaLisatieto arvo))))
          :arvo lisatieto]]]
       [:div.palstat
        [:div.palsta
         [:div {:style {:padding-top "40px"}}
          [napit/tallenna
           "Tallenna"
           #(e! (maarien-toteumat/->TallennaToteuma))
           {;:tallennus-kaynnissa? (:tallennus-kaynnissa? kuittaus)
            :ikoni       (ikonit/tallenna)
            :virheviesti "Toteuman tallennuksessa tapahtui virhe."
            :luokka      (str "nappi-ensisijainen button-primary-default" (when-not lomake-validoitu? " disabled"))}]
          [napit/peruuta
           "Peruuta"
           #(e! (maarien-toteumat/->ToteumanSyotto (not syottomoodi) nil nil))
           {:luokka "button-primary-default"
            :style  {:margin-left "40px"}}]]]]]))

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
                              (get-in app [:toteuma :toimenpide])
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