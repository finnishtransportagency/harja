(ns harja.views.urakka.suunnittelu.tehtavat
  (:require [reagent.core :as r]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.debug :as debug]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.suunnittelu.mhu-tehtavat :as t]
            [harja.ui.taulukko.taulukko :as taulukko]
            [harja.ui.taulukko.jana :as jana]
            [harja.ui.taulukko.osa :as osa]
            [harja.ui.taulukko.tyokalut :as tyokalu]
            [harja.ui.taulukko.protokollat :as p]
            [harja.loki :refer [log]]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.pvm :as pvm]
            [harja.loki :as loki])
  (:require-macros [harja.ui.taulukko.tyokalut :refer [muodosta-taulukko]]))

(defn sarakkeiden-leveys [sarake]
  (case sarake
    :tehtava "col-xs-12 col-sm-8 col-md-8 col-lg-8"
    :maara "col-xs-12 col-sm-8 col-md-8 col-lg-8"
    :maara-input "col-xs-12 col-sm-2 col-md-2 col-lg-2"
    :maara-yksikko "col-xs-12 col-sm-2 col-md-2 col-lg-2"))


(defn osien-paivitys-fn [tehtava maara yksikko]
  (fn [osat]
    (mapv
      (fn [osa]
        (case (p/osan-id osa)
          "Tehtävä" (tehtava osa)
          "Määrä" (maara osa)
          "Yksikkö" (yksikko osa)))
      osat)))

;; [{:id "1" :nimi "1.0 TALVIHOITO" :tehtavaryhmatyyppi "otsikko" :piillotettu? false}
;; {:id "2" :tehtava-id 4548 :nimi "Ise 2-ajorat." :tehtavaryhmatyyppi "tehtava" :maara 50 :vanhempi "1" :piillotettu? false}
;; {:id "3" :nimi "2.1 LIIKENNEYMPÄRISTÖN HOITO" :tehtavaryhmatyyppi "otsikko" :piillotettu? false}
;; {:id "4" :tehtava-id 4565 :nimi "Liikennemerkkien ja opasteiden kunnossapito (oikominen, pesu yms.)" :tehtavaryhmatyyppi "tehtava" :maara 50 :vanhempi "3" :piillotettu? false}
;; {:id "5" :tehtava-id 4621  :nimi "Opastustaulun/-viitan uusiminen" :tehtavaryhmatyyppi "tehtava" :maara 50 :vanhempi "3" :piillotettu? false}]

;; TODO: Muodosta palautettavat tiedot. Vrt. println tulostukset.

(defn- otsikkorivi
  [rivi]
  (-> rivi
      (p/aseta-arvo :id :tehtava
                    :class #{"table-default" "table-default-header"})
      (p/paivita-arvo :lapset
                      (osien-paivitys-fn #(p/aseta-arvo %
                                                        :id :tehtava-nimi
                                                        :arvo "Tehtävä"
                                                        :class #{(sarakkeiden-leveys :maara)})
                                         #(p/aseta-arvo %
                                                        :id :tehtava-maara
                                                        :arvo "Määrä"
                                                        :class #{(sarakkeiden-leveys :maara-input)})
                                         #(p/aseta-arvo %
                                                        :id :tehtava-yksikko
                                                        :arvo "Yksikkö"
                                                        :class #{(sarakkeiden-leveys :maara-yksikko)})))))


(defn luo-tehtava-taulukko
  [e! tehtavat-ja-maaraluettelo]
  (let [polku-taulukkoon [:tehtavat-taulukko]
        validi? (fn [arvo tyyppi]
                  (loki/log "Tsekkaan tilanteen" (str arvo) tyyppi)
                  (let [validius (case tyyppi
                                   :numero (re-matches #"\d+(?:\.?\d+)?" (str arvo)))]
                    (loki/log "Tilanne on " (not (nil? validius)))
                    (not (nil? validius))))
        taulukon-paivitys-fn! (fn [paivitetty-taulukko app]
                                (assoc-in app polku-taulukkoon paivitetty-taulukko))
        syottorivi (fn [rivi]
                     (mapv (fn [{:keys [nimi maara id piillotettu? tehtava-id tehtavaryhmatyyppi yksikko] :as tehtava}]
                             (-> rivi
                                 (p/aseta-arvo :id id
                                               :class #{(str "table-default-" (if (= 0 (rem id 2)) "even" "odd"))}
                                               :piillotettu? piillotettu?)
                                 (p/paivita-arvo :lapset
                                                 (osien-paivitys-fn #(p/aseta-arvo %
                                                                                   :id :tehtava-nimi
                                                                                   :arvo nimi
                                                                                   :class #{(sarakkeiden-leveys :maara)})
                                                                    #(p/aseta-arvo %
                                                                                   :id (keyword (str id "-maara"))
                                                                                   :arvo maara
                                                                                   :class #{(sarakkeiden-leveys :maara-input) "input-default" (if (validi? maara :numero) "" "ei-validi")}
                                                                                   :on-blur (fn [arvo]
                                                                                              (let [arvo (-> arvo (.. -target -value))]
                                                                                                (when (validi? arvo :numero)
                                                                                                 (e! (t/->TallennaTehtavamaara
                                                                                                       {:urakka-id  (-> @tila/tila :yleiset :urakka :id)
                                                                                                        :tehtava-id tehtava-id
                                                                                                        :maara      arvo})))))
                                                                                   :on-change (fn [arvo]
                                                                                                (e!
                                                                                                  (t/->PaivitaMaara osa/*this*
                                                                                                                    (-> arvo (.. -target -value))
                                                                                                                    #{(sarakkeiden-leveys :maara-input) "input-default" (if (validi? (-> arvo (.. -target -value)) :numero) "" "ei-validi")} ))))
                                                                    #(p/aseta-arvo %
                                                                                   :id :tehtava-yksikko
                                                                                   :arvo (or yksikko "")
                                                                                   :class #{(sarakkeiden-leveys :maara-yksikko)})))
                                 ))
                           (filter #(= "tehtava" (:tehtavaryhmatyyppi %)) tehtavat-ja-maaraluettelo)))]
    (muodosta-taulukko :tehtavat
                       {:teksti {:janan-tyyppi jana/Rivi
                                 :osat [osa/Teksti osa/Teksti osa/Teksti]}
                        :syotto {:janan-tyyppi jana/Rivi
                                 :osat [osa/Teksti osa/Syote osa/Teksti]}}
                       ["Tehtävä" "Määrä" "Yksikkö"]
                       [:teksti otsikkorivi
                        :syotto syottorivi]
                       {:class #{}
                        :taulukon-paivitys-fn! taulukon-paivitys-fn!})))

(defn noudetaan-taulukko
  []
  (let [datarivi (fn [rivi] (-> rivi
                                (p/aseta-arvo :id :dummy-rivi
                                              :class #{"table-default-odd"})
                                (p/paivita-arvo :lapset
                                                (osien-paivitys-fn #(p/aseta-arvo %
                                                                                  :id :tehtava-nimi
                                                                                  :class #{(sarakkeiden-leveys :maara)})
                                                                   #(-> % (p/aseta-arvo
                                                                            :id :tehtava-maara
                                                                            :class #{(sarakkeiden-leveys :maara)})
                                                                        (assoc :komponentti (fn [_ {:keys [teksti]} _] (yleiset/ajax-loader teksti {:luokka "col-xs-12 keskita"}))
                                                                               :komponentin-argumentit {:teksti "Haetaan tehtäviä"}))
                                                                   #(p/aseta-arvo %
                                                                                  :id :tehtava-yksikko
                                                                                  :class #{(sarakkeiden-leveys :maara)})))))]
    (muodosta-taulukko :noudetaan-tehtavat
                       {:teksti {:janan-tyyppi jana/Rivi
                                 :osat [osa/Teksti osa/Teksti osa/Teksti]}
                        :datarivit {:janan-tyyppi jana/Rivi
                                    :osat [osa/Teksti osa/Komponentti osa/Teksti]}}
                       ["Tehtävä" "Määrä" "Yksikkö"]
                       [:teksti otsikkorivi
                        :datarivit datarivi]
                       {:class #{}
                        :taulukon-paivitys-fn! identity})))

(defn valitaso-filtteri
  [_ app]
  (let [{:keys [alkupvm]} (-> @tila/tila :yleiset :urakka)]
    (fn [e! {:keys [tehtava-ja-maaraluettelo valinnat] :as app}]
      (let [vuosi (pvm/vuosi alkupvm)
            toimenpiteet (filter #(and
                                 #_(= (get-in valinnat [:toimenpide :id]) (:toimenpide %))
                                 (= "otsikko" (:tehtavaryhmatyyppi %))) tehtava-ja-maaraluettelo)
            hoitokaudet (into [] (range vuosi (+ 5 vuosi)))
            disabloitu-alasveto? (fn [koll] (or (:noudetaan valinnat)
                                                (= 0 (count koll))))]

        [:div
         [:div.label-ja-alasveto
          [:span.alasvedon-otsikko "Toimenpide"]
          [yleiset/livi-pudotusvalikko {:valinta (:valitaso valinnat)
                                        :valitse-fn #(e! (t/->ValitseTaso % :valitaso))
                                        :format-fn #(:nimi %)
                                        :disabled (disabloitu-alasveto? toimenpiteet)}
           toimenpiteet]]
         [:div.label-ja-alasveto
          [:span.alasvedon-otsikko "Hoitokausi"]
          [yleiset/livi-pudotusvalikko {:valinta (:hoitokausi valinnat)
                                        :valitse-fn #(e! (t/->HaeTehtavat {:hoitokausi %
                                                                           :prosessori (partial luo-tehtava-taulukko e!)
                                                                           :tilan-paivitys-fn (fn [tila] (assoc-in tila [:valinnat :hoitokausi] %))}))
                                        :format-fn #(str "1.10." % "-30.9." (inc %))
                                        :disabled (disabloitu-alasveto? hoitokaudet)}
           hoitokaudet]]
         #_[:label.kopioi-tuleville-vuosille
          [:input {:type "checkbox" :checked false
                   :on-change (r/partial #() :ei)
                   :disabled (:noudetaan valinnat)}]
          "Samat suunnitellut määrät kaikille hoitokausille"]]))))

(defn tehtavat*
  [e! app]
  (komp/luo
    (komp/piirretty (fn [this]
                      (e! (t/->HaeTehtavat {:hoitokausi (-> @tila/tila :yleiset :urakka :alkupvm pvm/vuosi)
                                            :prosessori (partial luo-tehtava-taulukko e!)}))))
    (fn [e! app]
      (let [{taulukon-tehtavat :tehtavat-taulukko} app
            {:keys [nimi]} (-> @tila/tila :yleiset :urakka)]
        [:div
         [debug/debug app]
         [:h1 "Tehtävät ja määrät" nimi]
         [:div "Tehtävät ja määrät suunnitellaan urakan alussa, ja tarkennetaan jokaisen hoitovuoden alussa. " [:a {:href "#"} "Toteuma"] "-puolelle kirjataan ja kirjautuu kalustosta toteutuneet määrät."]
         [valitaso-filtteri e! app]
         (if taulukon-tehtavat
           [p/piirra-taulukko taulukon-tehtavat]
           [p/piirra-taulukko (noudetaan-taulukko)])]))))

(defn tehtavat []
  (tuck/tuck tila/suunnittelu-tehtavat tehtavat*))