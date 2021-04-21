(ns harja.views.urakka.toteumat.maarien-toteumat
  "Urakan 'Toteumat' välilehden Määrien toteumat osio"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko +korostuksen-kesto+]]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.toteumat :as toteumat]
            [harja.tiedot.urakka.toteumat.maarien-toteumat :as maarien-toteumat]
            [harja.ui.kentat :as kentat]
            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [cljs.core.async :refer [<! >! chan]]
            [cljs.core.async :refer [<! timeout]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]

            [tuck.core :as tuck]
            [harja.ui.debug :as debug]
            [harja.views.urakka.toteumat.maarien-toteuma-lomake :as toteuma-lomake]
            [harja.views.kartta :as kartta]
            [harja.tyokalut.big :as big])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defn- laske-prosentti
  "Olettaa saavansa molemmat parametrit big arvoina."
  [toteuma suunniteltu]
  (if (or (nil? toteuma)
          (nil? suunniteltu)
          (big/eq (big/->big 0) toteuma)
          (big/eq (big/->big 0) suunniteltu))
    0
    (big/fmt (big/mul (big/->big 100) (big/div toteuma suunniteltu)) 2)))

(defn- muokkaa-toteumaa-linkki [e! db-aika toteuma-id]
  [:a {:href "#"
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
    [:a {:href "#"
         :on-click (fn [event]
                     (do
                       (.preventDefault event)
                       (e! (maarien-toteumat/->ToteumanSyotto true tehtava toimenpide))))}
     (str "+ Lisää toteuma")]))

(defn maarita-yksikko [rivi]
  (cond
    (= (:tyyppi rivi) "kokonaishintainen")
    (:yk rivi)

    (or (= (:tyyppi rivi) "lisatyo")
        (= (:tyyppi rivi) "akillinen-hoitotyo")
        (= (:tyyppi rivi) "vahinkojen-korjaukset")
        (= (:tyyppi rivi) "muut-rahavaraukset"))
    "kpl"

    :else
    (if (:yk rivi)
      (:yk rivi)
      "")))

; spekseistä laskettu
(def leveydet {:tehtava "55%"
               :caret "4%"
               :toteuma "15%"
               :suunniteltu "15%"
               :prosentti "11%"})

(defn- ryhmitellyt-taulukko [e! app r toteumat]
  (let [
        row-index-atom (r/atom 0)
        ll
        (mapcat
          (fn [[tehtavaryhma rivit]]
            (let [_ (reset! row-index-atom (inc @row-index-atom))
                  muodostetut (mapcat
                                (fn [rivi]
                                  (let [_ (reset! row-index-atom (inc @row-index-atom))
                                        kasin-lisattava? (:kasin_lisattava_maara (first (second rivi)))
                                        toteutunut-maara (reduce big/plus (big/->big 0)
                                                                 (keep #(big/->big (or (:materiaalimaara %) (:maara %) 0)) (second rivi)))
                                        suunniteltu-maara (big/->big (or (:suunniteltu_maara (first (second rivi))) 0))
                                        fontin-vari (if (big/gt toteutunut-maara suunniteltu-maara)
                                                      "#DD0000" ;red
                                                      "#191919") ;gray25
                                        ;; Näytetään varoituskolmio käyttäjälle prosentin sijasta, mikäli tehtävällä on toteumia, mutta suunnitelma on nolla
                                        nayta-varoituskolmio (or (and (big/gt toteutunut-maara suunniteltu-maara)
                                                                      (big/eq (big/->big 0) suunniteltu-maara))
                                                               false)
                                        {:keys [tyyppi]} (first (second rivi))]
                                    (concat
                                      [^{:key (hash rivi)}
                                       [:tr (merge
                                              (when kasin-lisattava?
                                                {:on-click #(e! (maarien-toteumat/->HaeTehtavanToteumat (first (second rivi))))})
                                              {:class (str "table-default-" (if (odd? @row-index-atom) "even" "odd") " " (when kasin-lisattava? "klikattava"))})
                                        [:td.strong {:style {:width (:tehtava leveydet)}} (first rivi) (when (and (:haetut-toteumat-lataa app)
                                                                                                                  (= (:avattu-tehtava app) (first rivi)))
                                                                                                         [:span {:style {:padding-left "10px"}} [yleiset/ajax-loader-pieni]])]
                                        [:td {:style {:width (:caret leveydet)}} (if
                                                                                   (= (:avattu-tehtava app) (first rivi))
                                                                                   (when kasin-lisattava?
                                                                                     [ikonit/livicon-chevron-up])
                                                                                   (when kasin-lisattava?
                                                                                     [ikonit/livicon-chevron-down]))]
                                        [:td {:style {:width (:toteuma leveydet)}} (str (big/fmt toteutunut-maara 1) " " (maarita-yksikko (first (second rivi))))]
                                        [:td {:style {:width (:suunniteltu leveydet)
                                                      :color fontin-vari}} (if (big/eq (big/->big -1) suunniteltu-maara)
                                                                                         (case tyyppi
                                                                                           "kokonaishintainen" [:span.tila-virhe "---"]
                                                                                           "---")
                                                                                         (str (if (big/gt suunniteltu-maara (big/->big -1))
                                                                                                (big/fmt suunniteltu-maara 1)
                                                                                                0) " " (:yk (first (second rivi)))))]
                                        [:td {:style {:width (:prosentti leveydet)
                                                      :color fontin-vari}} (if nayta-varoituskolmio
                                                                                       (case tyyppi
                                                                                         "kokonaishintainen" [:span.tila-virhe (ikonit/exclamation-sign)]
                                                                                         "---")
                                                                                       (str (laske-prosentti toteutunut-maara suunniteltu-maara) " %"))]]]

                                      ;; "+ Lisää toteuma" rivi - jos rivi on auki ja jos tehtävämäärän/toimenpiteen tehtävälle on tietokantaan sallittu käsin lisäys
                                      (when (and
                                              (= (:tehtava (first (second rivi))) (:avattu-tehtava app))
                                              (= true (:kasin_lisattava_maara (first (second rivi)))))
                                        [^{:key (str "lisää-toteuma-" (hash rivi))}
                                         [:tr {:class (str "table-default-" (if (odd? @row-index-atom) "even" "odd"))}
                                          [:td {:style {:width (:tehtava leveydet)}} [lisaa-toteuma-linkki e! app (first rivi) (:toimenpide (first (second rivi)))]]
                                          [:td {:style {:width (:caret leveydet)}} ""]
                                          [:td {:style {:width (:toteuma leveydet)}} ""]
                                          [:td {:style {:width (:suunniteltu leveydet)}} ""]
                                          [:td {:style {:width (:prosentti leveydet)}} ""]]])

                                      (when (and toteumat
                                                 (= (:tehtava (first (second rivi))) (:avattu-tehtava app)))
                                        (mapcat
                                          (fn [toteuma]
                                            (let [toteuma-linkki (if (= "muu" (:tyyppi toteuma))
                                                                   [muokkaa-toteumaa-linkki e! (:alkanut toteuma) (:id toteuma)]
                                                                   (if (or (> (:materiaalimaara toteuma) 0) (> (:maara toteuma) 0))
                                                                     "Järjestelmästä lisätty"
                                                                     nil))]
                                              (when toteuma-linkki
                                                [^{:key (str "toteuma-" (hash toteuma))}
                                                 (do
                                                   (reset! row-index-atom (inc @row-index-atom))
                                                   [:tr {:key (str "tr-toteuma" - (hash toteuma))
                                                         :class (str "table-default-" (if (odd? @row-index-atom) "even" "odd"))}
                                                    [:td {:style {:width (:tehtava leveydet)}} toteuma-linkki]
                                                    [:td {:style {:width (:caret leveydet)}}]
                                                    [:td {:style {:width (:toteuma leveydet)}} (str (big/fmt (big/->big (or (:materiaalimaara toteuma) (:maara toteuma) 0)) 2) " " (:yk (first (second rivi))))]
                                                    [:td {:style {:width (:suunniteltu leveydet)}}]
                                                    [:td {:style {:width (:prosentti leveydet)}}]])])))

                                          toteumat)))))
                                rivit)]
              (concat [^{:key (str "otsikko-" (hash tehtavaryhma))}
                       [:tr.header
                        [:td {:colSpan "5"} tehtavaryhma]]]
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
      (if (:toimenpiteet-lataa app)
        [:tbody
         [:tr
          [:td {:colSpan "5"} [yleiset/ajax-loader "Haetaan..."]]]]

        [:tbody
         (doall
           (for [l ll]
             ^{:key (hash l)}
             l))])]]))

(def filtterit-keyword->string
  {:maaramitattavat "Määrämitattavat"
   :rahavaraukset "Rahavaraukset"
   :lisatyot "Lisätyöt"})

(defn maarien-toteumalistaus
  "Määrien toteumat listattuna taulukkoon"
  [e! app]
  (let [{:keys [alkupvm]} (-> @tila/tila :yleiset :urakka)  ;; Ota urakan alkamis päivä
        vuosi (pvm/vuosi alkupvm)
        hoitokaudet (into [] (range vuosi (+ 5 vuosi)))
        ryhmitellyt-maarat (get-in app [:toteutuneet-maarat-grouped])
        toteumat (get-in app [:haetut-toteumat])
        toimenpiteet (get-in app [:toimenpiteet])
        valittu-toimenpide (if (nil? (:valittu-toimenpide app))
                             {:otsikko "Kaikki" :id 0}
                             (:valittu-toimenpide app))
        valittu-hoitokausi (if (nil? (get-in app [:hoitokauden-alkuvuosi]))
                             vuosi
                             (get-in app [:hoitokauden-alkuvuosi]))
        syottomoodi (get-in app [:syottomoodi])
        filtterit (:hakufiltteri app)]
    [:div.maarien-toteumat

     [debug/debug app]
     [:div {:style {:padding-top "1rem"}} [:p "Taulukossa toimenpiteittäin ne määrämitattavat tehtävät, joiden toteumaa urakassa seurataan." [:br]
                                           "Määrät, äkilliset hoitotyöt, yms. varaukset sekä lisätyöt."]]
     [:div.row
      [:div.col-xs-12.col-md-6 {:style {:margin-left "-15px"}}
       [:label.alasvedon-otsikko-vayla "Toimenpide"]
       [yleiset/livi-pudotusvalikko {:valinta valittu-toimenpide
                                     :vayla-tyyli? true
                                     :valitse-fn #(e! (maarien-toteumat/->ValitseToimenpide (:id @nav/valittu-urakka) %))
                                     :format-fn #(:otsikko %)}
        (merge toimenpiteet {:otsikko "Kaikki" :id 0})]]
      [:div.col-xs-6.col-md-3
       [:label.alasvedon-otsikko-vayla "Hoitokausi"]
       [yleiset/livi-pudotusvalikko {:valinta valittu-hoitokausi
                                     :vayla-tyyli? true
                                     :valitse-fn #(e! (maarien-toteumat/->ValitseHoitokausi (:id @nav/valittu-urakka) %))
                                     :format-fn #(str "1.10." % "-30.9." (inc %))}
        hoitokaudet]]
      [:div.col-xs-6.col-md-3 {:style {:padding-top "21px"}}
       [napit/uusi
        "Lisää toteuma"
        (r/partial #(e! (maarien-toteumat/->ToteumanSyotto (not syottomoodi) nil (:valittu-toimenpide app))))
        {:vayla-tyyli? true
         :luokka "suuri"}]]]
     [:div.flex-row
      [kentat/tee-kentta {:tyyppi :checkbox-group
                          :nayta-rivina? true
                          :vayla-tyyli? true
                          :rivi-solun-tyyli {:padding-right "3rem"}
                          :vaihtoehto-nayta filtterit-keyword->string
                          :vaihtoehdot [:maaramitattavat :rahavaraukset :lisatyot]
                          :valitse-fn (fn [tila polku arvo]
                                        (e! (maarien-toteumat/->AsetaFiltteri polku arvo)))
                          :valittu-fn (fn [tila polku]
                                        (when polku
                                          (polku tila)))}
       (r/wrap filtterit
               (constantly true))]]
     [ryhmitellyt-taulukko e! app ryhmitellyt-maarat toteumat]]))

(defn maarien-toteumat* [e! app]
  (komp/luo
    (komp/lippu toteumat/maarien-toteumat-nakymassa?)
    (komp/piirretty (fn [this]
                      (do
                        (e! (maarien-toteumat/->HaeKaikkiTehtavat))
                        (e! (maarien-toteumat/->HaeToimenpiteet))
                        (e! (maarien-toteumat/->HaeToimenpiteenTehtavaYhteenveto {:otsikko "Kaikki"})))))
    (fn [e! app]
      (let [syottomoodi (get-in app [:syottomoodi])]
        [:div {:id "vayla"}
         (if syottomoodi
           [:div
            [kartta/kartan-paikka]
            [toteuma-lomake/maarien-toteuman-syottolomake* e! app]]
           [:div
            [maarien-toteumalistaus e! app]])]))))

(defn maarien-toteumat []
  (tuck/tuck tila/maarien-toteumat maarien-toteumat*))