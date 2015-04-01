(ns harja.views.urakka.materiaalit
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :refer [kuuntelija raksiboksi] :refer-macros [deftk]]
            [harja.tiedot.urakka.materiaalit :as t]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.suunnittelu :as suunnittelu]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            
            [harja.views.kartta.tasot :as tasot]
            [harja.views.kartta.pohjavesialueet :refer [hallintayksikon-pohjavesialueet-haku]]
            [harja.tiedot.navigaatio :as nav]
            [cljs-time.coerce :as tc]
            
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn pohjavesialueiden-materiaalit-grid
  "Listaa pohjavesialueiden materiaalit ja mahdollistaa kartalta valinnan."
  [opts materiaalikoodit materiaalit]
  
  (let [karttavalinta? (atom false)
        valitse-kartalta (fn [e]
                           (.preventDefault e)
                           (if @karttavalinta?
                             (do (tasot/taso-pois! :pohjavesialueet)
                                 (reset! karttavalinta? false)
                                 (reset! nav/kartan-koko :S))
                             (do (tasot/taso-paalle! :pohjavesialueet)
                                 (reset! karttavalinta? true)
                                 (reset! nav/kartan-koko :M))))
        g (grid/grid-ohjaus)
        ]
    (kuuntelija
     {}
     (fn [opts materiaalikoodit materiaalit]
        (log "MATSKUJA: " (pr-str materiaalit))
        [:div.pohjavesialueet
         [grid/muokkaus-grid
          {:otsikko "Pohjavesialueiden materiaalit"
           :tyhja "Ei pohjavesialueille kirjattuja materiaaleja."
           :ohjaus g
           :muokkaa-footer (fn [g]
                             [:button.btn.btn-default {:on-click valitse-kartalta}
                              (if @karttavalinta?
                                "Piilota kartta"
                                "Valitse kartalta")])
           
           :muutos (when-let [virheet (:virheet opts)]
                     #(reset! virheet (grid/hae-virheet %)))
           }
          [{:otsikko "Pohjavesialue"
            :tyyppi :haku :lahde hallintayksikon-pohjavesialueet-haku :nayta :nimi
            :nimi :pohjavesialue :fmt :nimi :leveys "40%"
            :validoi [[:ei-tyhja "Valitse pohjavesialue"]]}
           {:otsikko "Materiaali"
            :tyyppi :valinta :valinnat materiaalikoodit :valinta-nayta #(or (:nimi %) "- materiaali -")
            :nimi :materiaali :fmt :nimi :leveys "35%"
            :validoi [[:ei-tyhja "Valitse materiaali"]]}
           {:otsikko "Määrä" :nimi :maara :leveys "15%" :tyyppi :numero
            :validoi [[:ei-tyhja "Kirjoita määrä"]]}
           {:otsikko "Yks." :nimi :yksikko :hae (comp :yksikko :materiaali)  :leveys "5%"
            :tyyppi :string :muokattava? (constantly false)}]
          
          materiaalit
          ]])
     :pohjavesialue-klikattu (fn [this pohjavesialue]
                               (grid/lisaa-rivi! g {:pohjavesialue (dissoc pohjavesialue :type :aihe)})
                               (log "hei klikkasit pohjavesialuetta: " (dissoc pohjavesialue :alue))))))
      
     
(defn yleiset-materiaalit-grid [opts materiaalikoodit yleiset-materiaalit-muokattu]
  [grid/muokkaus-grid
   {:otsikko "Materiaalit"
    :tyhja "Ei kirjattuja materiaaleja."
    :muutos (when-let [virheet (:virheet opts)]
              #(reset! virheet (grid/hae-virheet %)))
    }
   
   [{:otsikko "Materiaali" :nimi :materiaali :fmt :nimi :leveys "60%"
     :tyyppi :valinta :valinnat materiaalikoodit :valinta-nayta #(or (:nimi %) "- materiaali -")
     :validoi [[:ei-tyhja "Valitse materiaali"]]
     }
    {:otsikko "Määrä" :nimi :maara :leveys "30%"
     :tyyppi :numero}
    {:otsikko "Yks." :nimi :yksikko :hae (comp :yksikko :materiaali) :leveys "5%"
     :tyyppi :string :muokattava? (constantly false)}]
   
   yleiset-materiaalit-muokattu])

  
(deftk materiaalit [ur]
  [;; haetaan kaikki materiaalit urakalle
   urakan-materiaalit (<! (t/hae-urakan-materiaalit (:id ur)))

   ;; ryhmitellään valitun sopimusnumeron materiaalit hoitokausittain
   ;; HUOM: goog DateTime hashcodet ei toimi, muunnetaan ne longeiksi
   sopimuksen-materiaalit-hoitokausittain
   :reaction (let [[sopimus-id _] @suunnittelu/valittu-sopimusnumero]
               (log "URAKAN MATSKUI::: " @urakan-materiaalit)
               (log "SOPIMUS: " sopimus-id " MATSKUI:: " (filter #(= sopimus-id (:sopimus %))
                                 @urakan-materiaalit))
               (group-by (juxt :alkupvm :loppupvm)
                         (filter #(= sopimus-id (:sopimus %))
                                 @urakan-materiaalit)))
                   
   
   ;; valitaan materiaaleista vain valitun hoitokauden
   materiaalit :reaction (let [{:keys [alkupvm loppupvm] :as hk} @suunnittelu/valittu-hoitokausi]
                           (log "valittu hk: " [alkupvm loppupvm])
                           (log " siinä matskui  ==> " (get @sopimuksen-materiaalit-hoitokausittain [alkupvm loppupvm]))
                           (log "hoitokausittaisia avaimia on " (pr-str (keys @sopimuksen-materiaalit-hoitokausittain)))
                           (doseq [k (keys @sopimuksen-materiaalit-hoitokausittain)]
                             (log " TESTAA " (pr-str k) " = " (pr-str [alkupvm loppupvm]) "? " (= [alkupvm loppupvm] k)))
                           (get @sopimuksen-materiaalit-hoitokausittain [alkupvm loppupvm]))
   
   uusi-id (atom 0)
   
   ;; luokitellaan yleiset materiaalit ja pohjavesialueiden materiaalit
   yleiset-materiaalit :reaction (into {}
                                       (comp (filter #(not (contains? % :pohjavesialue)))
                                             (map (juxt :id identity)))
                                       @materiaalit)
   pohjavesialue-materiaalit :reaction (into {}
                                             (comp (filter #(contains? % :pohjavesialue))
                                                   (map (juxt :id identity)))
                                             @materiaalit)

   yleiset-materiaalit-virheet nil
   yleiset-materiaalit-muokattu :reaction @yleiset-materiaalit

   pohjavesialue-materiaalit-virheet nil
   pohjavesialue-materiaalit-muokattu :reaction @pohjavesialue-materiaalit

   ;; kopioidaanko myös tuleville kausille
   tuleville? true

   ;; jos tulevaisuudessa on dataa, joka poikkeaa tämän hoitokauden materiaaleista, varoita ylikirjoituksesta
   varoita-ylikirjoituksesta? 
   :reaction (let [kopioi? @tuleville?
                   hoitokausi @suunnittelu/valittu-hoitokausi
                   hoitokausi-alku (tc/to-long (:alkupvm hoitokausi))
                   vertailumuoto (fn [materiaalit]
                                   ;; vertailtaessa "samuutta" eri hoitokausien välillä poistetaan pvm:t ja id:t
                                   (into #{}
                                         (map #(dissoc % :alkupvm :loppupvm :id))
                                         materiaalit))

                   [tama-kausi & tulevat-kaudet] (into []
                                                       (comp (drop-while #(> hoitokausi-alku (ffirst %)))
                                                             (map second)
                                                             (map vertailumuoto))
                                                       (sort-by ffirst @sopimuksen-materiaalit-hoitokausittain))]
               
               ;;(doseq [tk tulevat-kaudet]
               ;;  (log "ONKO tämä kausi " tama-kausi " SAMA kuin tuleva " tk "? " (= tama-kausi tk)))
               (if-not kopioi?
                 false
                 (some #(not= tama-kausi %) tulevat-kaudet)))
       
   ]
  
  (let [materiaalikoodit @(t/hae-materiaalikoodit)
        muokattu? (or (not= @yleiset-materiaalit @yleiset-materiaalit-muokattu)
                      (not= @pohjavesialue-materiaalit @pohjavesialue-materiaalit-muokattu))
        virheita? (or (not (empty? @yleiset-materiaalit-virheet))
                      (not (empty? @pohjavesialue-materiaalit-virheet))) 
        voi-tallentaa? (and muokattu? (not virheita?))]
    (log "matskui " @materiaalit)
    [:div.materiaalit
     [yleiset-materiaalit-grid {:virheet yleiset-materiaalit-virheet}
      materiaalikoodit yleiset-materiaalit-muokattu]
     
     (when (= (:tyyppi ur) :hoito)
       [pohjavesialueiden-materiaalit-grid {:virheet pohjavesialue-materiaalit-virheet}
        materiaalikoodit pohjavesialue-materiaalit-muokattu])

     [raksiboksi "Tallenna tulevillekin hoitokausille" @tuleville?
      #(swap! tuleville? not)
      [:div.raksiboksin-info (ikonit/warning-sign) "Tulevilla hoitokausilla eri tietoa, jonka tallennus ylikirjoittaa."]
      (and @tuleville? @varoita-ylikirjoituksesta?)
      ]

     [:div.toiminnot
      [:button.btn.btn-primary {:disabled (not voi-tallentaa?)
                                :on-click #(do (.preventDefault %)
                                               (log "tallennellaan..."))}
       "Tallenna materiaalit"]]
     ])) 
