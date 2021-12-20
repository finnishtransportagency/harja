(ns harja.views.urakka.suunnittelu.tehtavat
  (:require [reagent.core :as r]
            [tuck.core :as tuck]
            [cljs.core.async :refer [<! chan]]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.debug :as debug]
            [harja.ui.grid :as grid]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.suunnittelu.mhu-tehtavat :as t]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.pvm :as pvm]
            [harja.loki :as loki])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn sarakkeiden-leveys [sarake]
  (case sarake
    :tehtava "col-xs-12 col-sm-8 col-md-8 col-lg-8"
    :maara "leveys-70"
    :maara-input "leveys-15"
    :maara-yksikko "leveys-15"))

#_(defn- validi?
  [arvo tyyppi]
  (let [validius (case tyyppi
                   :numero (re-matches #"\d+(?:\.?,?\d+)?" (str arvo)))]
    (not (nil? validius))))

#_(defn- luo-syottorivit
  [e! rivi tnt]
  (let [luku (atom {})
        pura-rivit (map (fn [[_ rivi]] rivi))
        lajittele-jarjestyksen-mukaan #(fn [rf]
                                         (let [s (volatile! [])]
                                           (fn
                                             ([]
                                              (rf))
                                             ([kaikki]
                                              (let [ss @s]
                                                (vreset! s [])
                                                (reduce rf kaikki (sort (fn [{a :jarjestys} {b :jarjestys}]
                                                                          (compare a b)) ss))))
                                             ([kaikki syote]
                                              (let [syote syote]
                                                (vswap! s conj syote)
                                                kaikki)))))
        tee-rivit (map
                    (fn [{:keys [nimi maarat id vanhempi yksikko jarjestys]}]
                      (swap! luku update vanhempi inc)
                      (-> rivi
                          (p/aseta-arvo :id (keyword (str vanhempi "/" id))
                                        :class #{(str "table-default-" (if (= 0 (rem (get @luku vanhempi) 2)) "even" "odd"))}
                                        :piilotettu? false)
                          (p/paivita-arvo :lapset
                                          (osien-paivitys-fn #(p/aseta-arvo %
                                                                            :id :tehtava-nimi
                                                                            :arvo (str nimi)
                                                                            :class #{(sarakkeiden-leveys :maara)})
                                                             #(p/aseta-arvo %
                                                                            :id (keyword (str vanhempi "/" id "-maara"))
                                                                            :arvo (let [maara (->> @tila/tila :yleiset :urakka :alkupvm pvm/vuosi str keyword (get maarat))]
                                                                                    (if (nil? yksikko)
                                                                                      ""
                                                                                      maara))
                                                                            :class #{(sarakkeiden-leveys :maara-input) "input-default"}
                                                                            :disabled? (or (nil? yksikko)
                                                                                           (= "" yksikko)
                                                                                           (= "-" yksikko))
                                                                            :on-blur (fn [arvo]
                                                                                       (let [arvo (-> arvo (.. -target -value))]
                                                                                         (when (validi? arvo :numero)
                                                                                           (e! (t/->TallennaTehtavamaara
                                                                                                 {:urakka-id  (-> @tila/tila :yleiset :urakka :id)
                                                                                                  :tehtava-id id
                                                                                                  :maara      arvo})))))
                                                                            :on-change (fn [arvo]
                                                                                         (e!
                                                                                           (t/->PaivitaMaara osa/*this*
                                                                                                             (-> arvo (.. -target -value))
                                                                                                             #{(sarakkeiden-leveys :maara-input) (str "input" (if (validi? (-> arvo (.. -target -value)) :numero) "" "-error") "-default")}))))
                                                             #(p/aseta-arvo %
                                                                            :id :tehtava-yksikko
                                                                            :arvo (or yksikko "")
                                                                            :class #{(sarakkeiden-leveys :maara-yksikko)}))))))
        ota-vain-neljas-taso (filter (fn [[_ t]]
                                       (= 4 (:taso t))))
        xform-fn (comp ota-vain-neljas-taso
                       pura-rivit
                       (lajittele-jarjestyksen-mukaan)
                       tee-rivit)]
    (into [] xform-fn tnt)))


#_(defn luo-tehtava-taulukko
  [e! tehtavat-ja-toimenpiteet]
  (let [polku-taulukkoon [:tehtavat-taulukko]
        taulukon-paivitys-fn! (fn [paivitetty-taulukko app]
                                (assoc-in app polku-taulukkoon paivitetty-taulukko))
        syottorivi (fn [rivi]
                     (luo-syottorivit e! rivi tehtavat-ja-toimenpiteet))]
    (muodosta-taulukko :tehtavat
                       {:teksti {:janan-tyyppi jana/Rivi
                                 :osat         [osa/Teksti osa/Teksti osa/Teksti]}
                        :syotto {:janan-tyyppi jana/Rivi
                                 :osat         [osa/Teksti osa/Syote osa/Teksti]}}
                       ["Tehtävä" "Määrä" "Yksikkö"]
                       [:teksti otsikkorivi
                        :syotto syottorivi]
                       {:class                 #{}
                        :taulukon-paivitys-fn! taulukon-paivitys-fn!})))

(defn valitaso-filtteri
  [_ app]
  (let [{:keys [alkupvm]} (-> @tila/tila :yleiset :urakka)]
    (fn [e! {:keys [tehtavat-ja-toimenpiteet valinnat] :as app}]
      (let [vuosi (pvm/vuosi alkupvm)
            toimenpide-xform (comp #_(map
                                     (fn [[_ data]] data))
                                   (filter
                                     (fn [data]
                                       (= 3 (:taso data)))))
            toimenpiteet (sort-by :nimi (into [] toimenpide-xform tehtavat-ja-toimenpiteet))
            hoitokaudet (into [] (range vuosi (+ 5 vuosi)))
            disabloitu-alasveto? (fn [koll]
                                   (= 0 (count koll)))]

        [:div.flex-row
         {:style {:justify-content "flex-start"
                  :align-items     "flex-end"}}
         [:div
          {:style {:width        "840px"
                   :margin-right "15px"}}
          [:label.alasvedon-otsikko "Toimenpide"]
          [yleiset/livi-pudotusvalikko {:valinta      (:toimenpide valinnat)
                                        :valitse-fn   #(e! (t/->ValitseTaso % :toimenpide))
                                        :format-fn    #(:nimi %)
                                        :disabled     (disabloitu-alasveto? toimenpiteet)
                                        :vayla-tyyli? true}
           toimenpiteet]]
         [:div
          {:style {:width        "220px"
                   :margin-right "15px"}}
          [:label.alasvedon-otsikko "Hoitokausi"]
          [yleiset/livi-pudotusvalikko {:valinta      (:hoitokausi valinnat)
                                        :valitse-fn   #(e! (t/->HaeMaarat {:hoitokausi        %
                                                                           ;:prosessori        (partial luo-tehtava-taulukko e!)
                                                                           :tilan-paivitys-fn (fn [tila] (assoc-in tila [:valinnat :hoitokausi] %))}))
                                        :format-fn    #(str "1.10." % "-30.9." (inc %))
                                        :disabled     (disabloitu-alasveto? hoitokaudet)
                                        :vayla-tyyli? true}
           hoitokaudet]]
         [:div
          [:input#kopioi-tuleville-vuosille.vayla-checkbox
           {:type      "checkbox"
            :checked   (:samat-tuleville valinnat)
            :on-change #(e! (t/->SamatTulevilleMoodi (not (:samat-tuleville valinnat))))
            :disabled  (:noudetaan valinnat)}]
          [:label
           {:for "kopioi-tuleville-vuosille"}
           "Samat suunnitellut määrät tuleville hoitokausille"]]]))))

(defn- map->id-map-maaralla
  [maarat hoitokausi rivi]
  [(:id rivi) (assoc rivi 
                :hoitokausi hoitokausi
                :maara (get-in maarat [(:id rivi) hoitokausi]))])

(defn tehtava-maarat-taulukko
  [e! {:keys [tehtavat-ja-toimenpiteet maarat] {:keys [toimenpide urakan-alku? hoitokausi] :as valinnat} :valinnat}]
  (let [valitut (if-not (= :kaikki toimenpide) 
                  (filter (fn [{:keys [id]}] (= id (:id toimenpide))) tehtavat-ja-toimenpiteet)
                  tehtavat-ja-toimenpiteet)
        tulevat-ominaisuudet? true]
    [:<>
     [debug/debug valinnat]
     (for [toimenpide valitut] 
       [:<>
        [:div (str (pr-str (:tehtavat toimenpide)))]
        [grid/muokkaus-grid
         {:otsikko (:nimi toimenpide)
          :id (keyword (str "tehtavat-maarat-" (:nimi toimenpide)))
          :tyhja "Ladataan tietoja"
          :voi-poistaa? (constantly false)
          :voi-muokata? true
          :voi-lisata? false
          :voi-kumota? false
          :piilota-muokkaus? true
          :piilota-toiminnot? false
          :on-rivi-blur (fn [& params]                          
                          (println (str "blurraus tapahtui" params))
                          (e! (t/->TallennaTehtavamaara params)))}
         [{:otsikko "Tehtävä" :nimi :nimi :tyyppi :string :muokattava? (constantly false) :leveys 8}
          (when (and urakan-alku? tulevat-ominaisuudet?)
            {:otsikko "Sopimuksen määrä koko urakka yhteensä" :nimi :maara-sopimus :tyyppi :numero :muokattava? (constantly true) :leveys 3})
          (when (and (not urakan-alku?) tulevat-ominaisuudet?) 
            {:otsikko "Sovittu koko urakka yhteensä" :nimi :maara-sovittu-koko-urakka :tyyppi :numero :muokattava? (constantly false) :leveys 3})
          (when (and (not urakan-alku?) tulevat-ominaisuudet?) 
            {:otsikko "Sovittu koko urakka jäljellä" :nimi :maara-jaljella-koko-urakka :tyyppi :numero :muokattava? (constantly false) :leveys 3})
          (when-not urakan-alku? 
            {:otsikko "Suunniteltu määrä hoitokausi" :nimi :maara :tyyppi :numero :muokattava? (constantly true) :leveys 3})
          {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :muokattava? (constantly false) :leveys 2}]
         (r/wrap (into {} (map (r/partial map->id-map-maaralla maarat hoitokausi)) (:tehtavat toimenpide)) 
           (constantly nil))]])]))

(defn tehtavat*
  [e! app]
  (komp/luo
    (komp/piirretty (fn [this]
                      (e! (t/->HaeTehtavat
                            {:hoitokausi         :kaikki
                             #_:tehtavat->taulukko #_(partial luo-tehtava-taulukko e!)}))))
    (fn [e! app]
      [:div#vayla
       [debug/debug app]
       [:div "Tehtävät ja määrät suunnitellaan urakan alussa ja tarkennetaan urakan kuluessa. Osalle tehtävistä kertyy toteuneita määriä automaattisesti urakoitsijajärjestelmistä. Osa toteutuneista määristä täytyy kuitenkin kirjata manuaalisesti Toteuma-puolelle."]
       [:div "Yksiköttömiin tehtäviin ei tehdä kirjauksia."]
       [valitaso-filtteri e! app]
       [tehtava-maarat-taulukko e! app]])))

(defn tehtavat []
  (tuck/tuck tila/suunnittelu-tehtavat tehtavat*))
