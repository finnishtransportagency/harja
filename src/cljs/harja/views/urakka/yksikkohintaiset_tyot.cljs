(ns harja.views.urakka.yksikkohintaiset-tyot
  "Urakan 'Yksikkohintaiset työt' välilehti:"
  (:require [reagent.core :refer [atom] :as reagent]
            [bootstrap :as bs]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      alasveto-ei-loydoksia alasvetovalinta radiovalinta]]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.yksikkohintaiset-tyot :as yks-hint-tyot]
            [harja.tiedot.urakka.urakan-toimenpiteet :as urakan-toimenpiteet]
            [harja.tiedot.istunto :as istunto]

            [harja.loki :refer [log]]
            [harja.pvm :as pvm]

            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [cljs-time.core :as t]

            )
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.ui.yleiset :refer [deftk]]))


(def tallenna-tuleville-hoitokausille? (atom false))

(defn muuta-tallenna-tuleville-hoitokausille []
  (reset! tallenna-tuleville-hoitokausille? (not @tallenna-tuleville-hoitokausille?)))


(defn tallenna-tyot [ur sopimusnumero valittu-hoitokausi tyot uudet-tyot]
  (go (let [tallennettavat-hoitokaudet (if @tallenna-tuleville-hoitokausille?
                                         (s/tulevat-hoitokaudet ur valittu-hoitokausi)
                                         valittu-hoitokausi)
            muuttuneet
            (into []
                  ;; FIXME: jossain pitää vielä suodattaa pois ne joihin ei käyttöliittymässä koskettu
                  (if @tallenna-tuleville-hoitokausille?
                    (s/rivit-tulevillekin-kausille ur uudet-tyot valittu-hoitokausi)
                    uudet-tyot
                    ))
            res (<! (yks-hint-tyot/tallenna-urakan-yksikkohintaiset-tyot (:id ur) sopimusnumero muuttuneet))]
        (reset! tyot res)
        true)))

(defn luo-tyhja-tyo [tp ur hk]
  {:tehtava (:id tp), :tehtavan_nimi (:nimi tp) :yksikko (:yksikko tp) :urakka (:id ur)
   :alkupvm (first hk) :loppupvm (second hk)})

(defn ryhmittele-tehtavat
  "Ryhmittelee 4. tason tehtävät. Lisää väliotsikot eri tehtävien väliin"
  [toimenpiteet-tasoittain tyorivit]
  (let [otsikko (fn [{:keys [tehtava]}]
                  (some (fn [[t1 t2 t3 t4]]
                          (when (= (:id t4) tehtava)
                            (str (:nimi t2) " / " (:nimi t3))))
                        toimenpiteet-tasoittain))
        otsikon-mukaan (group-by otsikko tyorivit)]
    (mapcat (fn [[otsikko rivit]]
              (concat [(grid/otsikko otsikko)] rivit))
            (seq otsikon-mukaan))))


(deftk yksikkohintaiset-tyot [ur]

  [tyot (<! (yks-hint-tyot/hae-urakan-yksikkohintaiset-tyot (:id ur)))
   toimenpiteet-ja-tehtavat (<! (urakan-toimenpiteet/hae-urakan-toimenpiteet-ja-tehtavat (:id ur)))
   tyorivit-samat-alkutilanteessa? true
   tehtavien-rivit-kaikki-hoitokaudet
   :reaction (into []
                   (group-by :tehtava
                             (filter (fn [t]
                                       (= (:sopimus t) (first @s/valittu-sopimusnumero))) @tyot)))

   tyorivit-kaikki-hoitokaudet-alkutilanne
   :reaction (mapv (fn [[_ tehtavan-rivit]]
                     (map #(yks-hint-tyot/kannan-rivit->tyorivi %) (partition 2 tehtavan-rivit))
                     ) @tehtavien-rivit-kaikki-hoitokaudet)
   tyorivit
   :reaction (let [valittu-hoitokausi @s/valittu-hoitokausi
                   alkupvm (first valittu-hoitokausi)
                   loppupvm (second valittu-hoitokausi)
                   tehtavien-rivit (group-by :tehtava
                                             (filter (fn [t]
                                                       (and (= (:sopimus t) (first @s/valittu-sopimusnumero))
                                                            (or (pvm/sama-pvm? (:alkupvm t) alkupvm)
                                                                (pvm/sama-pvm? (:loppupvm t) loppupvm)))) @tyot))
                   nelostason-tpt (map #(nth % 3) @toimenpiteet-ja-tehtavat)
                   kirjatut-tehtavat (into #{} (keys tehtavien-rivit))
                   tyhjat-tyot (map #(luo-tyhja-tyo % ur valittu-hoitokausi)
                                    (filter (fn [tp]
                                              (not (kirjatut-tehtavat (:id tp)))) nelostason-tpt))]
                             

               (when (not (empty? @tyorivit-kaikki-hoitokaudet-alkutilanne))
                 (reset! tyorivit-samat-alkutilanteessa?
                         (s/hoitokausien-sisalto-sama? 
                          (s/jaljella-olevien-hoitokausien-rivit @tyorivit-kaikki-hoitokaudet-alkutilanne
                                                                 (s/tulevat-hoitokaudet ur @s/valittu-hoitokausi)))))
                             
               (ryhmittele-tehtavat
                @toimenpiteet-ja-tehtavat
                (vec (concat (mapv (fn [[_ tehtavan-rivit]]
                                     (yks-hint-tyot/kannan-rivit->tyorivi tehtavan-rivit)
                                     ) tehtavien-rivit)
                             tyhjat-tyot))))
   kaikki-tyorivit-flattina :reaction (flatten @tyorivit-kaikki-hoitokaudet-alkutilanne)
   kaikkien-hoitokausien-kustannukset :reaction (s/toiden-kustannusten-summa @kaikki-tyorivit-flattina)
   valitun-hoitokauden-kustannukset
   :reaction (s/toiden-kustannusten-summa (filter
                                           #(pvm/sama-pvm?
                                             (:alkupvm %) (first @s/valittu-hoitokausi))
                                           @kaikki-tyorivit-flattina))
   ]

  (do
    [:div.yksikkohintaiset-tyot
     [grid/grid
      {:otsikko        "Yksikköhintaiset työt"
       :tyhja          (if (nil? @toimenpiteet-ja-tehtavat) [ajax-loader "Yksikköhintaisia töitä haetaan..."] "Ei yksikköhintaisia töitä")
       :tallenna       (istunto/jos-rooli-urakassa istunto/rooli-urakanvalvoja
                                                   (:id ur)
                                                   #(tallenna-tyot ur @s/valittu-sopimusnumero @s/valittu-hoitokausi
                                                                   tyot %)
                                                   :ei-mahdollinen)
       :tunniste       :tehtava
       :voi-lisata?    false
       :voi-poistaa?   (constantly false)
       :muokkaa-footer (fn [g]
                         [raksiboksi "Tallenna tulevillekin hoitokausille"
                          @tallenna-tuleville-hoitokausille?
                          muuta-tallenna-tuleville-hoitokausille
                          [:div.raksiboksin-info (ikonit/warning-sign) "Tulevilla hoitokausilla eri tietoa, jonka tallennus ylikirjoittaa."]
                          (and @tallenna-tuleville-hoitokausille?
                               (not @tyorivit-samat-alkutilanteessa?))])
       }

      ;; sarakkeet
      [{:otsikko "Tehtävä" :nimi :tehtavan_nimi :tyyppi :string :muokattava? (constantly false) :leveys "25%"}
       {:otsikko (str "Määrä 10-12/" (.getYear (first @s/valittu-hoitokausi))) :nimi :maara-kkt-10-12 :tyyppi :numero :leveys "15%"}
       {:otsikko (str "Määrä 1-9/" (.getYear (second @s/valittu-hoitokausi))) :nimi :maara-kkt-1-9 :tyyppi :numero :leveys "15%"}
       {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :muokattava? (constantly false) :leveys "15%"}
       {:otsikko (str "Yksikköhinta") :nimi :yksikkohinta :tasaa :oikea :tyyppi :numero :fmt #(if % (str (.toFixed % 2) " \u20AC")) :leveys "15%"}
       {:otsikko "Yhteensä" :nimi :yhteensa :tasaa :oikea :tyyppi :string :muokattava? (constantly false) :leveys "15%" :fmt #(if % (str (.toFixed % 2) " \u20AC"))}
       ]
      @tyorivit
      ]

     [:div.hoitokauden-kustannukset
      [:div "Yksikkohintaisten töiden hoitokausi yhteensä "
       [:span (str (.toFixed @valitun-hoitokauden-kustannukset 2) "\u20AC")]]
      [:div "Yksikköhintaisten töiden kaikki hoitokaudet yhteensä "
       [:span (str (.toFixed @kaikkien-hoitokausien-kustannukset 2) "\u20AC")]]]]))


