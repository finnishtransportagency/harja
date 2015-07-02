(ns harja.views.urakka.yksikkohintaiset-tyot
  "Urakan 'Yksikkohintaiset työt' välilehti:"
  (:require [reagent.core :refer [atom] :as reagent]
            [bootstrap :as bs]
            [harja.domain.roolit :as roolit]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      alasveto-ei-loydoksia livi-pudotusvalikko radiovalinta]]
            [harja.ui.visualisointi :as vis]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.yksikkohintaiset-tyot :as yks-hint-tyot]
            [harja.tiedot.urakka.urakan-toimenpiteet :as urakan-toimenpiteet]
            [harja.tiedot.istunto :as istunto]

            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [cljs-time.core :as t]

            )
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))


(def tuleville? (atom false))

(defn prosessoi-tyorivit [ur rivit]
  (if (= :hoito (:tyyppi ur))
    (s/yhdista-rivien-hoitokaudet rivit :tehtava
                                  (fn [eka toka]
                                    (assoc eka
                                      :maara-kkt-10-12 (:maara eka)
                                      :maara-kkt-1-9 (:maara toka)
                                      
                                      ;; Määrä on kausien yhteenlaskettu, jotta yhteensä tiedot näkyvät
                                      :maara (+ (or (:maara eka) 0)
                                                (or (:maara toka) 0))
                                      :yhteensa-kkt-10-12 (when-let [hinta (:yksikkohinta eka)]
                                                            (* (or (:maara eka) 0) hinta))
                                      :yhteensa-kkt-1-9 (when-let [hinta (:yksikkohinta eka)]
                                                            (* (or (:maara toka) 0) hinta))
                                      :yhteensa (when-let [hinta (:yksikkohinta eka)]
                                                  (* (+ (or (:maara eka) 0)
                                                        (or (:maara toka) 0))
                                                     hinta))
                                      )))
    (mapv #(assoc % :yhteensa (when-let [hinta (:yksikkohinta %)]
                                (* (or (:maara %) 0) hinta)))
          rivit)))
  
(defn tallenna-tyot [ur sopimusnumero valittu-hoitokausi tyot uudet-tyot]
  (go (let [tallennettavat-hoitokaudet (if @tuleville?
                                         (u/tulevat-hoitokaudet ur valittu-hoitokausi)
                                         valittu-hoitokausi)
            muuttuneet
            (into []
                  (if @tuleville?
                    (u/rivit-tulevillekin-kausille ur uudet-tyot valittu-hoitokausi)
                    uudet-tyot
                    ))
            res (<! (yks-hint-tyot/tallenna-urakan-yksikkohintaiset-tyot ur sopimusnumero muuttuneet))]
        (reset! tyot (prosessoi-tyorivit ur res))
        (reset! tuleville? false)
        true)))

(defn luo-tyhja-tyo [tp ur hk]
  {:tehtava (:id tp), :tehtavan_nimi (:nimi tp) :yksikko (:yksikko tp) :urakka (:id ur)
   :alkupvm (first hk) :loppupvm (second hk)})

(defn ryhmittele-tehtavat
  "Ryhmittelee 4. tason tehtävät. Lisää väliotsikot eri tehtävien väliin"
  [toimenpiteet-tasoittain tyorivit]
  (log "toimenpiteet" (pr-str toimenpiteet-tasoittain))
  (let [otsikko (fn [{:keys [tehtava]}]
                  (or
                    (some (fn [[t1 t2 t3 t4]]
                           (when (= (:id t4) tehtava)
                             (str (:nimi t2) " / " (:nimi t3))))
                         toimenpiteet-tasoittain)
                    "Muut tehtävät"))
        otsikon-mukaan (group-by otsikko tyorivit)]
    (mapcat (fn [[otsikko rivit]]
              (concat [(grid/otsikko otsikko)] rivit))
            (seq otsikon-mukaan))))

(defn hoidon-sarakkeet []
  [{:otsikko "Tehtävä" :nimi :tehtavan_nimi :tyyppi :string :muokattava? (constantly false) :leveys "30%"}
   {:otsikko (str "Määrä 10-12/" (.getYear (first @u/valittu-hoitokausi))) :nimi :maara-kkt-10-12 :tyyppi :numero :leveys "10%"}
   {:otsikko (str "Yhteensä " (.getYear (first @u/valittu-hoitokausi))) :nimi :yhteensa-kkt-10-12 :tasaa :oikea :tyyppi :string :muokattava? (constantly false) :leveys "10%" :fmt fmt/euro-opt}
   {:otsikko (str "Määrä 1-9/" (.getYear (second @u/valittu-hoitokausi))) :nimi :maara-kkt-1-9 :tyyppi :numero :leveys "10%"}
   {:otsikko (str "Yhteensä " (.getYear (second @u/valittu-hoitokausi))) :nimi :yhteensa-kkt-1-9 :tasaa :oikea :tyyppi :string :muokattava? (constantly false) :leveys "10%" :fmt fmt/euro-opt}
   {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :muokattava? (constantly false) :leveys "7%"}
   {:otsikko (str "Yksikköhinta") :nimi :yksikkohinta :tasaa :oikea :tyyppi :numero :fmt fmt/euro-opt :leveys "10%"}
   {:otsikko "Kausi yhteensä" :nimi :yhteensa :tasaa :oikea :tyyppi :string :muokattava? (constantly false) :leveys "13%" :fmt fmt/euro-opt}])

(defn yllapidon-sarakkeet []
  [{:otsikko "Tehtävä" :nimi :tehtavan_nimi :tyyppi :string :muokattava? (constantly false) :leveys "40%"}
   {:otsikko "Määrä" :nimi :maara :tyyppi :numero :leveys "15%"}
   {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :muokattava? (constantly false) :leveys "15%"}
   {:otsikko (str "Yksikköhinta") :nimi :yksikkohinta :tasaa :oikea :tyyppi :numero :fmt fmt/euro-opt :leveys "15%"}
   {:otsikko "Yhteensä" :nimi :yhteensa :tasaa :oikea :tyyppi :string :muokattava? (constantly false) :leveys "15%" :fmt fmt/euro-opt}])

(defn yksikkohintaiset-tyot-view [ur valitun-hoitokauden-yks-hint-kustannukset]
  (let [urakan-yks-hint-tyot u/urakan-yks-hint-tyot
        toimenpiteet-ja-tehtavat (atom nil)
        urakka (atom nil)
        hae-urakan-tiedot (fn [ur]
                            (reset! urakka ur)
                            ;; Tehdään hoitokauden osien (10-12 / 1-9) yhdistäminen  urakalle
                            (go (reset! toimenpiteet-ja-tehtavat (<! (urakan-toimenpiteet/hae-urakan-toimenpiteet-ja-tehtavat (:id ur))))))
        
        tyorivit-samat-alkutilanteessa? (atom true)

        sopimuksen-tyot 
        (reaction 
         (into []
               (filter (fn [t]
                         (= (:sopimus t) (first @u/valittu-sopimusnumero))))
               @urakan-yks-hint-tyot))
        

        sopimuksen-tyot-hoitokausittain
        (reaction (let [tyyppi (:tyyppi @urakka)
                        [sopimud-id _] @u/valittu-sopimusnumero]
                    (u/ryhmittele-hoitokausittain @sopimuksen-tyot
                                                  @u/valitun-urakan-hoitokaudet)))
        
        varoita-ylikirjoituksesta?
        (reaction (let [kopioi? @tuleville?
                        varoita? (s/varoita-ylikirjoituksesta? @sopimuksen-tyot-hoitokausittain @u/valittu-hoitokausi)]
                    (if-not kopioi?
                      false
                      varoita?)))
        
        tyorivit
        (reaction (let [valittu-hoitokausi @u/valittu-hoitokausi
                        alkupvm (first valittu-hoitokausi)
                        loppupvm (second valittu-hoitokausi)
                        tehtavien-rivit (group-by :tehtava
                                                  (get @sopimuksen-tyot-hoitokausittain [alkupvm loppupvm]))
                        nelostason-tpt (map #(nth % 3) @toimenpiteet-ja-tehtavat)
                        kirjatut-tehtavat (into #{} (keys tehtavien-rivit))
                        tyhjat-tyot (map #(luo-tyhja-tyo % ur valittu-hoitokausi)
                                         (filter (fn [tp]
                                                   (not (kirjatut-tehtavat (:id tp)))) nelostason-tpt))]

                    (ryhmittele-tehtavat
                     @toimenpiteet-ja-tehtavat
                     (vec (concat (mapcat second tehtavien-rivit)
                                  tyhjat-tyot)))))
        
        kaikkien-hoitokausien-kustannukset
        (reaction (transduce (comp (mapcat second)
                                   (map #(* (:maara %) (:yksikkohinta %))))
                             + 0
                             (seq @sopimuksen-tyot-hoitokausittain)))]

    (hae-urakan-tiedot ur)
    (komp/luo
     {:component-will-receive-props
      (fn [_ & [_ ur]]
        (log "UUSI URAKKA: " (pr-str (dissoc ur :alue)))
        (hae-urakan-tiedot ur))

      :component-will-unmount
      (fn [this]
        (reset! tuleville? false))}

     (fn [ur]
       [:div.yksikkohintaiset-tyot
        [:div.hoitokauden-kustannukset
         [:div.piirakka-hoitokauden-kustannukset-per-kaikki.row
          [:div.col-xs-6.piirakka
           (let [valittu-kust @valitun-hoitokauden-yks-hint-kustannukset
                 kaikki-kust @kaikkien-hoitokausien-kustannukset]
               (when (or (not= 0 valittu-kust) (not= 0 kaikki-kust))
                   [:span.piirakka-wrapper
                    [:h5.piirakka-label "Tämän hoitokauden osuus kaikista hoitokausista"]
                    [vis/pie
                     {:width 230 :height 150 :radius 60 :show-text :percent :show-legend true}
                     {"Valittu hoitokausi" valittu-kust "Muut hoitokaudet" (- kaikki-kust valittu-kust)}]]))]
          [:div.col-xs-6.piirakka
           (let [yks-hint-yhteensa @valitun-hoitokauden-yks-hint-kustannukset
                 kok-hint-yhteensa @s/valitun-hoitokauden-kok-hint-kustannukset]
               (when (or (not= 0 yks-hint-yhteensa) (not= 0 kok-hint-yhteensa))
                   [:span.piirakka-wrapper
                    [:h5.piirakka-label "Hoitokauden yksikköhintaisten töiden osuus kaikista töistä"]
                    [vis/pie
                     {:width 230 :height 150 :radius 60 :show-text :percent :show-legend true}
                     {"Yksikköhintaiset" yks-hint-yhteensa "Kokonaishintaiset" kok-hint-yhteensa}]]))]]

         [:div.summa "Yksikkohintaisten töiden hoitokausi yhteensä "
          [:span (fmt/euro @valitun-hoitokauden-yks-hint-kustannukset)]]
         [:div.summa "Yksikköhintaisten töiden kaikki hoitokaudet yhteensä "
          [:span (fmt/euro @kaikkien-hoitokausien-kustannukset)]]]
        
        [grid/grid
         {:otsikko        "Yksikköhintaiset työt"
          :tyhja          (if (nil? @toimenpiteet-ja-tehtavat) [ajax-loader "Yksikköhintaisia töitä haetaan..."] "Ei yksikköhintaisia töitä")
          :tallenna       (roolit/jos-rooli-urakassa roolit/rooli-urakanvalvoja
                                                     (:id ur)
                                                     #(tallenna-tyot ur @u/valittu-sopimusnumero @u/valittu-hoitokausi
                                                                     urakan-yks-hint-tyot %)
                                                     :ei-mahdollinen)
          :peruuta #(reset! tuleville? false)
          :tunniste       :tehtava
          :voi-lisata?    false
          :voi-poistaa?   (constantly false)
          :muokkaa-footer (fn [g]
                            [raksiboksi "Tallenna tulevillekin hoitokausille"
                             @tuleville?
                             #(swap! tuleville? not)
                             [:div.raksiboksin-info (ikonit/warning-sign) "Tulevilla hoitokausilla eri tietoa, jonka tallennus ylikirjoittaa."]
                             @varoita-ylikirjoituksesta?])
          }

         ;; sarakkeet
         (if (= :hoito (:tyyppi ur))
           (hoidon-sarakkeet)
           (yllapidon-sarakkeet))
      
      
         @tyorivit
         ]]))))


