(ns harja.views.urakka.kulut.mhu-kustannusten-seuranta
  "Urakan 'Toteumat' välilehden Määrien toteumat osio"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko +korostuksen-kesto+]]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.kulut.mhu-kustannusten-seuranta :as kustannusten-seuranta-tiedot]
            [harja.ui.kentat :as kentat]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [cljs.core.async :refer [<! >! chan]]
            [cljs.core.async :refer [<! timeout]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]

            [tuck.core :as tuck]
            [harja.ui.debug :as debug]
            [harja.tyokalut.big :as big])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defn- laske-prosentti
  "Olettaa saavansa molemmat parametrit big arvoina."
  [toteuma suunniteltu]
  (if (or (big/eq (big/->big 0) toteuma)
          (nil? toteuma))
    0
    (big/fmt (big/mul (big/->big 100) (big/div suunniteltu toteuma)) 2)))

; spekseistä laskettu
(def leveydet {:tehtava "46%"
               :caret "4%"
               :budjetoitu "17%"
               :toteuma "15%"
               :erotus "9%"
               :prosentti "9%"})

(defn- ryhmitellyt-taulukko [e! app r]
  (let [row-index-atom (r/atom 0)
        avattava? true
        toimenpiteet
        (map
          (fn [toimenpide]
            (let [_ (js/console.log "toimenpide" (pr-str toimenpide))
                  _ (reset! row-index-atom (inc @row-index-atom))
                  rivit (:tehtavat toimenpide)
                  muodostetut (if-not (= (get-in app [:valittu-rivi]) toimenpide)
                                nil
                               (mapcat
                                 (fn [rivi]
                                   (let [_ (reset! row-index-atom (inc @row-index-atom))
                                         _ (js/console.log "muodostetut mapcat :: rivi" (pr-str rivi))
                                         toteutunut-summa (big/->big (:toteutunut_summa rivi))
                                         budjetoitu-summa (big/->big (:budjetoitu_summa rivi))
                                         _ (js/console.log "toteutunut-summa" (pr-str toteutunut-summa) "budjetoitu-summa" (pr-str budjetoitu-summa))
                                         erotus (big/->big (big/minus budjetoitu-summa toteutunut-summa))
                                         ;_ (js/console.log "erotus" erotus)
                                         {:keys [tyyppi]} (first (second rivi))]
                                     (concat
                                       [^{:key (hash rivi)}
                                        [:tr
                                         [:td.strong {:style {:width (:tehtava leveydet)}} (:toimenpidekoodi_nimi rivi)]
                                         [:td {:style {:width (:caret leveydet)}} ]
                                         [:td {:style {:width (:budjetoitu leveydet)}} (str (big/fmt budjetoitu-summa 2) " ")]
                                         [:td {:style {:width (:toteuma leveydet)}} (str (big/fmt toteutunut-summa 2) " ")]
                                         [:td {:style {:width (:erotus leveydet)}} (str (big/fmt erotus 2) " ")]
                                         [:td {:style {:width (:prosentti leveydet)}} (laske-prosentti budjetoitu-summa toteutunut-summa)]]]

                                       )))
                                 rivit))]
              (concat [
                       ^{:key (str "otsikko-" (hash toimenpide))}
                       [:tr.header
                        (merge
                          (when avattava?
                            {:on-click #(e! (kustannusten-seuranta-tiedot/->AvaaRivi toimenpide))})
                          {:class (str "table-default-" (if (odd? @row-index-atom) "even" "odd") " " (when avattava? "klikattava"))})
                        [:td {:style {:width (:tehtava leveydet)}} (:toimenpide toimenpide)]
                        [:td {:style {:width (:caret leveydet)}} (if
                                                                   (= (get-in app [:valittu-rivi]) toimenpide)
                                                                   [ikonit/livicon-chevron-up]
                                                                   [ikonit/livicon-chevron-down])]
                        [:td {:style {:width (:budjetoitu leveydet)}}  (:yht-budjetoitu-summa toimenpide)]
                        [:td {:style {:width (:toteuma leveydet)}}  (:yht-toteutunut-summa toimenpide)]
                        [:td {:style {:width (:erotus leveydet)}} (- (:yht-budjetoitu-summa toimenpide) (:yht-toteutunut-summa toimenpide)) ]
                        [:td {:style {:width (:prosentti leveydet)}}  (laske-prosentti (big/->big (:yht-budjetoitu-summa toimenpide)) (big/->big (:yht-toteutunut-summa toimenpide)))]
                        ]]
                      muodostetut)))
          r)]
    [:div.table-default
     [:table
      [:thead.table-default-header
       [:tr
        [:th {:style {:width (:tehtava leveydet)}} "Toimenpide"]
        [:th {:style {:width (:caret leveydet)}} ""]
        [:th {:style {:width (:toteuma leveydet)}} "Budjetti €"]
        [:th {:style {:width (:budjetoitu leveydet)}} "Toteuma €"]
        [:th {:style {:width (:erotus leveydet)}} "Erotus €"]
        [:th {:style {:width (:prosentti leveydet)}} "%"]]]
      [:tbody
       (doall
         (for [l toimenpiteet]
           ^{:key (hash l)}
           l))]]]))



(defn kustannukset
  "Kustannukset listattuna taulukkoon"
  [e! app]
  (let [{:keys [alkupvm]} (-> @tila/tila :yleiset :urakka)  ;; Ota urakan alkamis päivä
        vuosi (pvm/vuosi alkupvm)
        hoitokaudet (into [] (range vuosi (+ 5 vuosi)))
        ryhmitellyt-maarat (get-in app [:kustannukset-grouped2])
        _ (js/console.log  "ryhmitellyt-maarat" (pr-str ryhmitellyt-maarat))
        toimenpiteet (get-in app [:toimenpiteet])
        valittu-toimenpide (if (nil? (:valittu-toimenpide app))
                             {:otsikko "Kaikki" :id 0}
                             (:valittu-toimenpide app))
        valittu-hoitokausi (if (nil? (get-in app [:hoitokauden-alkuvuosi]))
                             2019
                             (get-in app [:hoitokauden-alkuvuosi]))]
    [:div.kustannusten-seuranta
     [debug/debug app]
     [:div {:style {:padding-top "1rem"}} [:p "Tavoite- ja kattohinnat sekä budjetit on suunniteltu Suunnittelu-puolella.
     Toteutumissa näkyy ne kustannukset, jotka ovat Laskutus-osiossa syötetty järjestelmään."]]
     [:div.row
      [:div.col-xs-12.col-md-6 {:style {:margin-left "-15px"}}
       [:span.alasvedon-otsikko "Toimenpide"]
       [yleiset/livi-pudotusvalikko {:valinta valittu-toimenpide
                                     :vayla-tyyli? true
                                     :valitse-fn #(e! (kustannusten-seuranta-tiedot/->ValitseToimenpide (:id @nav/valittu-urakka) %))
                                     :format-fn #(:nimi %)
                                     :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
        (merge toimenpiteet {:nimi "Kaikki" :id 0})]]
      [:div.col-xs-6.col-md-3
       [:span.alasvedon-otsikko "Hoitokausi"]
       [yleiset/livi-pudotusvalikko {:valinta valittu-hoitokausi
                                     :vayla-tyyli? true
                                     :valitse-fn #(e! (kustannusten-seuranta-tiedot/->ValitseHoitokausi (:id @nav/valittu-urakka) %))
                                     :format-fn #(str "1.10." % "-30.9." (inc %))
                                     :klikattu-ulkopuolelle-params {:tarkista-komponentti? true}}
        hoitokaudet]]
      ]

     [ryhmitellyt-taulukko e! app ryhmitellyt-maarat]]))

(defn kustannusten-seuranta* [e! app]
  (komp/luo
    (komp/lippu tila/kustannusten-seuranta-nakymassa?)
    (komp/piirretty (fn [this]
                      (do
                        (e! (kustannusten-seuranta-tiedot/->HaeToimenpiteet))
                        (e! (kustannusten-seuranta-tiedot/->HaeKustannukset
                              (:id @nav/valittu-urakka)
                              (:valittu-toimenpide app)
                              (get-in app [:hoitokauden-alkuvuosi]) nil nil)))))
    (fn [e! app]
      [:div {:id "vayla"}
       [:div "jee"
         [kustannukset e! app]]])))

(defn kustannusten-seuranta []
  (tuck/tuck tila/kustannusten-seuranta kustannusten-seuranta*))