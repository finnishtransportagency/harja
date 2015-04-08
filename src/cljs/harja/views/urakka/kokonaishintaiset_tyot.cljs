(ns harja.views.urakka.kokonaishintaiset-tyot
  "Urakan 'Kokonaishintaiset työt' välilehti:"
  (:require [reagent.core :refer [atom] :as reagent]
            [bootstrap :as bs]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      alasveto-ei-loydoksia alasvetovalinta radiovalinta]]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.kokonaishintaiset-tyot :as kok-hint-tyot]
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

(defn tallenna-tyot [ur sopimusnumero valittu-hoitokausi tyot uudet-tyot tuleville?]
  (go (let [tallennettavat-hoitokaudet (if tuleville?
                                         (s/tulevat-hoitokaudet ur valittu-hoitokausi)
                                         valittu-hoitokausi)
            muuttuneet
            (into []
                  (if tuleville?
                    (s/rivit-tulevillekin-kausille ur uudet-tyot valittu-hoitokausi)
                    uudet-tyot
                    ))
            res (<! (kok-hint-tyot/tallenna-urakan-kokonaishintaiset-tyot (:id ur) sopimusnumero muuttuneet))]
        (reset! tyot res)
        true)))

(defn ryhmittele-tehtavat
  "Ryhmittelee 4. tason tehtävät. Lisää väliotsikot eri tehtävien väliin"
  [toimenpiteet-tasoittain tyorivit]
  (let [otsikko (fn [{:keys [tehtava]}]
                  (some (fn [[t1 t2 t3 t4]]
                          (when (= (:id t4) tehtava)
                            (str (:nimi t1) " / " (:nimi t2) " / " (:nimi t3))))
                        toimenpiteet-tasoittain))
        otsikon-mukaan (group-by otsikko tyorivit)]
    (mapcat (fn [[otsikko rivit]]
              (concat [(grid/otsikko otsikko)] rivit))
            (seq otsikon-mukaan))))


(deftk kokonaishintaiset-tyot [ur]

       [urakan-kok-hint-tyot (<! (kok-hint-tyot/hae-urakan-kokonaishintaiset-tyot (:id ur)))
        toimenpiteet (<! (urakan-toimenpiteet/hae-urakan-toimenpiteet (:id ur)))

        ;; ryhmitellään valitun sopimusnumeron materiaalit hoitokausittain

        sopimuksen-tyot-hoitokausittain
        :reaction (let [[sopimus-id _] @s/valittu-sopimusnumero
                        sopimuksen-tyot (filter #(= sopimus-id (:sopimus %))
                                                @urakan-kok-hint-tyot)]
                    (s/ryhmittele-hoitokausittain sopimuksen-tyot (s/hoitokaudet ur)))


        ;; valitaan materiaaleista vain valitun hoitokauden
        valitun-hoitokauden-tyot :reaction (let [hk @s/valittu-hoitokausi]
                                (get @sopimuksen-tyot-hoitokausittain hk))

        valittu-toimenpide :reaction (first @toimenpiteet)
        valitun-toimenpiteen-ja-hoitokauden-tyot :reaction (let [valittu-tp-id (:id @valittu-toimenpide)]
                                                             (filter #(= valittu-tp-id (:toimenpide %))
                                                                     @valitun-hoitokauden-tyot))
        ;; kopioidaanko myös tuleville kausille (oletuksena false, vaarallinen)
        tuleville? false

        ;; jos tulevaisuudessa on dataa, joka poikkeaa tämän hoitokauden materiaaleista, varoita ylikirjoituksesta
        varoita-ylikirjoituksesta?
        :reaction (let [kopioi? @tuleville?
                        varoita? (s/varoita-ylikirjoituksesta? @sopimuksen-tyot-hoitokausittain
                                                               @s/valittu-hoitokausi)]
                    (if-not kopioi?
                      false
                      varoita?))
        ]

       (do
         [:div.kokonaishintaiset-tyot
          [:div.alasvetovalikot
           [:div.label-ja-alasveto
            [:span.alasvedon-otsikko "Toimenpide"]
            [alasvetovalinta {:valinta @valittu-toimenpide
                              ;;\u2014 on väliviivan unikoodi
                              :format-fn #(if % (str (:tpi_nimi %)) "Valitse")
                              :valitse-fn #(reset! valittu-toimenpide %)
                              :class "alasveto"
                              }
             @toimenpiteet
             ]]]
          [grid/grid
           {:otsikko        (str "Kokonaishintaiset työt: " (:t2_nimi @valittu-toimenpide) " / " (:t3_nimi @valittu-toimenpide) " / " (:tpi_nimi @valittu-toimenpide))
            :tyhja          (if (nil? @toimenpiteet) [ajax-loader "Kokonaishintaisia töitä haetaan..."] "Ei kokonaishintaisia töitä")
            :tallenna       (istunto/jos-rooli-urakassa istunto/rooli-urakanvalvoja
                                                        (:id ur)
                                                        #(tallenna-tyot ur @s/valittu-sopimusnumero @s/valittu-hoitokausi
                                                                        valitun-toimenpiteen-ja-hoitokauden-tyot % @tuleville?)
                                                        :ei-mahdollinen)
            :tunniste       #((juxt :tpi_nimi :sopimus :vuosi :kuukausi) %)
            :voi-lisata?    false
            :voi-poistaa?   (constantly false)
            :muokkaa-footer (fn [g]
                              [raksiboksi "Tallenna tulevillekin hoitokausille"
                               @tuleville?
                               #(swap! tuleville? not)
                               [:div.raksiboksin-info (ikonit/warning-sign) "Tulevilla hoitokausilla eri tietoa, jonka tallennus ylikirjoittaa."]
                               (and @tuleville? @varoita-ylikirjoituksesta?)])
            }

           ;; sarakkeet
           [{:otsikko "Vuosi" :nimi :vuosi :muokattava? (constantly false) :tyyppi :numero :leveys "25%"}
            {:otsikko "Kuukausi" :nimi "kk" :hae #(pvm/kuukauden-nimi (:kuukausi %)) :muokattava? (constantly false) :tyyppi :numero :leveys "25%"}
            {:otsikko "Summa" :nimi :summa :tyyppi :numero :fmt #(if % (str (.toFixed % 2) " \u20AC")) :tasaa :oikea :leveys "25%"}
            {:otsikko "Maksupvm" :nimi :maksupvm :tyyppi :pvm :fmt pvm/pvm :leveys "25%"}
            ]
           @valitun-toimenpiteen-ja-hoitokauden-tyot
           ]]))


