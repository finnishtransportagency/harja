(ns harja.views.urakka.materiaalit
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :refer [kuuntelija raksiboksi] :refer-macros [deftk]]
            [harja.tiedot.urakka.materiaalit :as t]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.ui.viesti :as viesti]
            [harja.views.kartta.tasot :as tasot]
            [harja.views.kartta.pohjavesialueet :refer [hallintayksikon-pohjavesialueet-haku]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto]
            [cljs-time.coerce :as tc]
            
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [run! reaction]]))

(defn aseta-hoitokausi [rivi]
  (let [[alkupvm loppupvm] @u/valittu-hoitokausi]
    ;; lisätään kaikkiin riveihin valittu hoitokausi
    (assoc rivi :alkupvm alkupvm :loppupvm loppupvm)))

  
(defn pohjavesialueiden-materiaalit-grid
  "Listaa pohjavesialueiden materiaalit ja mahdollistaa kartalta valinnan."
  [opts ur valittu-hk valittu-sop materiaalikoodit materiaalit]
  
  (let [karttavalinta? (atom false)
        valitse-kartalta (fn [e]
                           (.preventDefault e)
                           (if @karttavalinta?
                             (do (reset! karttavalinta? false)
                                 (reset! nav/kartan-koko :S))
                             (do (reset! karttavalinta? true)
                                 (reset! nav/kartan-koko :M))))
        g (grid/grid-ohjaus)
        ]
    
    (komp/luo 
     (komp/kuuntelija
      :pohjavesialue-klikattu (fn [this pohjavesialue]
                                (grid/lisaa-rivi! g {:pohjavesialue (dissoc pohjavesialue :type :aihe)})
                                (log "hei klikkasit pohjavesialuetta: " (dissoc pohjavesialue :alue))))
     {:component-will-receive-props (fn [& _]
                                      (grid/nollaa-historia! g))}

     
     (fn [{:keys [virheet voi-muokata?]} ur valittu-hk valittu-sop materiaalikoodit materiaalit]
       (log "MATSKUJA: " (pr-str materiaalit))
       [:div.pohjavesialueet
        [grid/muokkaus-grid
         {:otsikko "Pohjavesialueiden materiaalit"
          :tyhja "Ei pohjavesialueille kirjattuja materiaaleja."
          :voi-muokata? voi-muokata?
          :voi-poistaa? (constantly voi-muokata?)
          :ohjaus g
          :uusi-rivi aseta-hoitokausi                                
          :muokkaa-footer (fn [g]
                            [:button.nappi-toissijainen {:on-click valitse-kartalta}
                             (if @karttavalinta?
                               "Piilota kartta"
                               "Valitse kartalta")])
           
          :muutos (when virheet
                    #(reset! virheet (grid/hae-virheet %)))
          }
         [{:otsikko "Pohjavesialue"
           :tyyppi :haku :lahde hallintayksikon-pohjavesialueet-haku :nayta #(str (:tunnus %) " " (:nimi %))
           :muokattava? (constantly voi-muokata?)
           :nimi :pohjavesialue :fmt #(str (:tunnus %) " " (:nimi %)) :leveys "40%"
           :validoi [[:ei-tyhja "Valitse pohjavesialue"]]}
          {:otsikko "Materiaali"
           :tyyppi :valinta :valinnat materiaalikoodit :valinta-nayta #(or (:nimi %) "- materiaali -")
           :muokattava? (constantly voi-muokata?)
           :nimi :materiaali :fmt :nimi :leveys "35%"
           :validoi [[:ei-tyhja "Valitse materiaali"]]}
          {:otsikko "Määrä" :nimi :maara :leveys "15%" :tyyppi :numero
           :muokattava? (constantly voi-muokata?)
           :validoi [[:ei-tyhja "Kirjoita määrä"]]}
          {:otsikko "Yks." :nimi :yksikko :hae (comp :yksikko :materiaali)  :leveys "5%"
           :tyyppi :string :muokattava? (constantly false)}]
          
         materiaalit
         ]])
     )))
      
     
(defn yleiset-materiaalit-grid [{:keys [virheet voi-muokata?]}
                                ur valittu-hk valittu-sop
                                materiaalikoodit yleiset-materiaalit-muokattu]
  (let [g (grid/grid-ohjaus)]
    (komp/luo
     {:component-will-receive-props (fn [& _]
                                      (grid/nollaa-historia! g))}
     (fn [{:keys [virheet voi-muokata?]}
          ur valittu-hk valittu-sop
          materiaalikoodit yleiset-materiaalit-muokattu]
       [grid/muokkaus-grid
        {:otsikko "Materiaalit"
         :voi-muokata? voi-muokata?
         :voi-poistaa? (constantly false)
         :voi-lisata? false
         :ohjaus g
         :tyhja "Ei kirjattuja materiaaleja."
         :uusi-rivi aseta-hoitokausi
         :muutos (when virheet
                   #(reset! virheet (grid/hae-virheet %)))
         :jarjesta (comp :nimi :materiaali)
         }
   
        [{:otsikko "Materiaali" :nimi :materiaali :fmt :nimi :leveys "60%"
          :muokattava? (constantly false)
          :tyyppi :valinta :valinnat materiaalikoodit :valinta-nayta #(or (:nimi %) "- materiaali -")
          :validoi [[:ei-tyhja "Valitse materiaali"]]
          }
         {:otsikko "Määrä" :nimi :maara :leveys "30%"
          :muokattava? (constantly voi-muokata?)
          :tyyppi :numero}
         {:otsikko "Yks." :nimi :yksikko :hae (comp :yksikko :materiaali) :leveys "10%"
          :tyyppi :string :muokattava? (constantly false)}]
   
        yleiset-materiaalit-muokattu]))))


  
(defn materiaalit [ur]
  (let [;; haetaan kaikki materiaalit urakalle
        urakan-materiaalit (atom nil)

        hae-urakan-materiaalit (fn [ur]
                                 (go (reset! urakan-materiaalit (<! (t/hae-urakan-materiaalit (:id ur))))))
        
        ;; ryhmitellään valitun sopimusnumeron materiaalit hoitokausittain
        sopimuksen-materiaalit-hoitokausittain
        (reaction (let [[sopimus-id _] @u/valittu-sopimusnumero]
                    (u/ryhmittele-hoitokausittain (filter #(= sopimus-id (:sopimus %))
                                                          @urakan-materiaalit)
                                                  (u/hoitokaudet ur))))
        
        
        ;; valitaan materiaaleista vain valitun hoitokauden
        materiaalit (reaction (let [hk @u/valittu-hoitokausi]
                                (get @sopimuksen-materiaalit-hoitokausittain hk)))
        
        uusi-id (atom 0)
        
        ;; Haetaan kaikki materiaalikoodit ja valitaan tälle urakalle sopivat 
        materiaalikoodit (reaction (filter #(= (:tyyppi ur) (:urakkatyyppi %)) @(t/hae-materiaalikoodit)))
        
        ;; Jaetaan materiaalikoodit yleisiin ja kohdistettaviin
        yleiset-materiaalikoodit (reaction (filter #(not (:kohdistettava %)) @materiaalikoodit))
        kohdistettavat-materiaalikoodit (reaction (filter :kohdistettava @materiaalikoodit))
        
        
        ;; luokitellaan yleiset materiaalit ja pohjavesialueiden materiaalit
        yleiset-materiaalit (reaction (let [materiaalit (into {}
                                                              (comp (filter #(not (contains? % :pohjavesialue)))
                                                                    (map (juxt :id identity)))
                                                              @materiaalit)
                                            kaytetyt-materiaali-idt (into #{} (map (comp :id :materiaali) (vals materiaalit)))]
                                        (loop [materiaalit materiaalit
                                               [mk & materiaalikoodit] @yleiset-materiaalikoodit]
                                          (if-not mk
                                            materiaalit
                                            (if (kaytetyt-materiaali-idt (:id mk))
                                              (recur materiaalit materiaalikoodit)
                                              (let [id (- (:id mk))
                                                    [alku loppu] @u/valittu-hoitokausi]
                                                (recur (assoc materiaalit id {:id id :materiaali mk :alkupvm alku :loppupvm loppu})
                                                       materiaalikoodit)))))))
        
        pohjavesialue-materiaalit (reaction (into {}
                                                  (comp (filter #(contains? % :pohjavesialue))
                                                        (map (juxt :id identity)))
                                                  @materiaalit))
        
        yleiset-materiaalit-virheet (atom nil)
        yleiset-materiaalit-muokattu (reaction @yleiset-materiaalit)
        
        pohjavesialue-materiaalit-virheet (atom nil)
        pohjavesialue-materiaalit-muokattu (reaction @pohjavesialue-materiaalit)
        
        ;; kopioidaanko myös tuleville kausille (oletuksena false, vaarallinen)
        tuleville? (atom false)
        
        ;; jos tulevaisuudessa on dataa, joka poikkeaa tämän hoitokauden materiaaleista, varoita ylikirjoituksesta
        varoita-ylikirjoituksesta?
        (reaction (let [kopioi? @tuleville?                 
                        varoita? (s/varoita-ylikirjoituksesta? @sopimuksen-materiaalit-hoitokausittain
                                                               @u/valittu-hoitokausi)]
                    (if-not kopioi?
                      false
                      varoita?)))
        
        
        ]

    (hae-urakan-materiaalit ur)

    (komp/luo
     (komp/sisaan-ulos #(tasot/taso-paalle! :pohjavesialueet)
                       #(tasot/taso-pois! :pohjavesialueet))
     {:component-will-receive-props (fn [this & [_ ur]]
                                      (hae-urakan-materiaalit ur))}
  
     (fn [ur]
       (let [muokattu? (or (not= @yleiset-materiaalit @yleiset-materiaalit-muokattu)
                           (not= @pohjavesialue-materiaalit @pohjavesialue-materiaalit-muokattu))
             virheita? (or (not (empty? @yleiset-materiaalit-virheet))
                           (not (empty? @pohjavesialue-materiaalit-virheet))) 
             voi-tallentaa? (and (or muokattu? @tuleville?) (not virheita?))
             voi-muokata? (istunto/rooli-urakassa? istunto/rooli-urakanvalvoja (:id ur))
             ]
    
         [:div.materiaalit
          [yleiset-materiaalit-grid {:voi-muokata? voi-muokata?
                                     :virheet yleiset-materiaalit-virheet}
           ur @u/valittu-hoitokausi @u/valittu-sopimusnumero
           @yleiset-materiaalikoodit yleiset-materiaalit-muokattu]
     
          (when (= (:tyyppi ur) :hoito)
            [pohjavesialueiden-materiaalit-grid {:voi-muokata? voi-muokata?
                                                 :virheet pohjavesialue-materiaalit-virheet}
             ur @u/valittu-hoitokausi @u/valittu-sopimusnumero
             @kohdistettavat-materiaalikoodit pohjavesialue-materiaalit-muokattu])

          (when voi-muokata?
            [raksiboksi "Tallenna tulevillekin hoitokausille" @tuleville?
             #(swap! tuleville? not) 
             [:div.raksiboksin-info (ikonit/warning-sign) "Tulevilla hoitokausilla eri tietoa, jonka tallennus ylikirjoittaa."]
             (and @tuleville? @varoita-ylikirjoituksesta?)
             ])

          (when voi-muokata?
            [:div.toiminnot
             [:button.btn.btn-primary
              {:disabled (not voi-tallentaa?)
               :on-click #(do (.preventDefault %)
                              (go 
                                (let [rivit (concat (vals @yleiset-materiaalit-muokattu)
                                                    (vals @pohjavesialue-materiaalit-muokattu))
                                      rivit (if @tuleville?
                                              (u/rivit-tulevillekin-kausille ur rivit @u/valittu-hoitokausi)
                                              rivit)
                                      uudet-materiaalit (<! (t/tallenna (:id ur)
                                                                        (first @u/valittu-sopimusnumero)
                                                                        rivit))]
                                  (when uudet-materiaalit
                                    (viesti/nayta! "Materiaalit tallennettu." :success)
                                    (reset! urakan-materiaalit uudet-materiaalit)))))}
              "Tallenna materiaalit"]])
          ]))))) 
