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
            [harja.pvm :as pvm])
  (:require-macros [harja.ui.taulukko.tyokalut :refer [muodosta-taulukko]]))

(defn sarakkeiden-leveys [sarake]
  (case sarake
    :tehtava "col-xs-12 col-sm-8 col-md-8 col-lg-8"
    :maara "col-xs-12 col-sm-8 col-md-8 col-lg-8"
    :maara-input "col-xs-12 col-sm-2 col-md-2 col-lg-2"
    :maara-yksikko "col-xs-12 col-sm-2 col-md-2 col-lg-2"))

#_(defn luo-taulukon-tehtavat
  [e! tehtavat on-oikeus?]
  (let [tehtava-solu (fn [tehtavaryhmatyyppi id nimi]
                       (with-meta
                         (case tehtavaryhmatyyppi
                           "ylataso"
                           (osa/luo-tilallinen-laajenna (str id "-laajenna") nimi #(e! (t/->LaajennaSoluaKlikattu %1 %2)) {:class #{(sarakkeiden-leveys :tehtava)}})
                           "valitaso"
                           (osa/luo-tilallinen-laajenna (str id "-laajenna") nimi #(e! (t/->LaajennaSoluaKlikattu %1 %2)) {:class #{(sarakkeiden-leveys :tehtava) "solu-sisenna-1"}})
                           "alitaso"
                           (osa/->Teksti (str id "-tehtava") nimi {:class #{(sarakkeiden-leveys :tehtava)
                                                                            "solu-sisenna-2"}}))
                         {:sarake "Tehtävä"}))
        maara-solu (fn [id maara]
                     (with-meta
                       (if maara
                         (osa/->Syote (str id "-maara")
                                      {:on-change (fn [arvo]
                                                    ;; Arvo tulee :positiivinen? kaytos-wrapperiltä, joten jos se on nil, ei syötetty arvo ollut positiivinen.
                                                    (when arvo
                                                      (e! (t/->PaivitaMaara id (str id "-maara") arvo))))}
                                      {:on-change [:positiivinen-numero :eventin-arvo]}
                                      {:class #{(sarakkeiden-leveys :maara)}
                                       :type "text"
                                       :disabled (not on-oikeus?)
                                       :value maara})
                         (osa/->Teksti (str id "-maara")
                                       ""
                                       {:class #{(sarakkeiden-leveys :maara)}}))
                       {:sarake "Määrä"}))
        rivit (map (fn [{:keys [id tehtavaryhmatyyppi maara nimi piillotettu? vanhempi]}]
                     (with-meta (jana/->Rivi id
                                             [(tehtava-solu tehtavaryhmatyyppi id nimi)
                                              (maara-solu id maara)]
                                             (if piillotettu?
                                               #{"piillotettu"}
                                               #{}))
                                {:vanhempi vanhempi
                                 :tehtavaryhmatyyppi tehtavaryhmatyyppi}))
                   tehtavat)
        otsikot [(jana/->Rivi :tehtavataulukon-otsikko
                              [(osa/->Otsikko "tehtava otsikko" "Tehtävä" #(e! (t/->JarjestaTehtavienMukaan)) {:class #{(sarakkeiden-leveys :tehtava)}})
                               (osa/->Otsikko "maara otsikko" "Määrä" #(e! (t/->JarjestaMaaranMukaan)) {:class #{(sarakkeiden-leveys :maara)}})]
                              nil)]]

    (into [] (concat otsikot rivit))))

(defn osien-paivitys-fn [tehtava maara yksikko]
  (fn [osat]
    (log "Osat " osat)
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
      (tyokalu/aseta-arvo :id :tehtava
                          :class #{"table-default" "table-default-header"})
      (tyokalu/paivita-arvo :lapset
                            (osien-paivitys-fn #(tyokalu/aseta-arvo %
                                                                    :id :tehtava-nimi
                                                                    :arvo "Tehtävä"
                                                                    :class #{(sarakkeiden-leveys :maara)})
                                               #(tyokalu/aseta-arvo %
                                                                    :id :tehtava-maara
                                                                    :arvo "Määrä"
                                                                    :class #{(sarakkeiden-leveys :maara)})
                                               #(tyokalu/aseta-arvo %
                                                                    :id :tehtava-yksikko
                                                                    :arvo "Yksikkö"
                                                                    :class #{(sarakkeiden-leveys :maara)})))
      ))

(defn tehtava-mankeli [tehtava]
  tehtava)

(defn luo-tehtava-taulukko
  [e! tehtavat-ja-maaraluettelo]
  (let [polku-taulukkoon [:tehtavat-taulukko]
        taulukon-paivitys-fn! (fn [paivitetty-taulukko app]
                                (log "TAULUKKO" paivitetty-taulukko app polku-taulukkoon)
                                (assoc-in app polku-taulukkoon paivitetty-taulukko))
        rivit (mapv
                tehtava-mankeli
                tehtavat-ja-maaraluettelo)
        syottorivi (fn [rivi]
                       (mapv (fn [{:keys [nimi maara id piillotettu? tehtava-id tehtavaryhmatyyppi yksikko] :as tehtava}]
                               (log "My mission " tehtava)
                               (-> rivi
                                  (tyokalu/aseta-arvo :id id
                                                      :class #{(str "table-default-" (if (= 0 (rem id 2)) "even" "odd"))}
                                                      :piillotettu? piillotettu?)
                                  (tyokalu/paivita-arvo :lapset
                                                        (osien-paivitys-fn #(tyokalu/aseta-arvo %
                                                                                                :id :tehtava-nimi
                                                                                                :arvo nimi
                                                                                                :class #{(sarakkeiden-leveys :maara)})
                                                                           #(tyokalu/aseta-arvo %
                                                                                               :id (keyword (str id "-maara"))
                                                                                               :arvo maara
                                                                                               :class #{(sarakkeiden-leveys :maara-input) "input-default"}
                                                                                                :on-blur (fn [arvo]
                                                                                                           (e! (t/->TallennaTehtavamaara
                                                                                                                 {:hoitokausi
                                                                                                                  :urakka-id (-> @tila/tila :yleiset :urakka)
                                                                                                                  :tehtava-id tehtava-id
                                                                                                                  :maara (-> arvo (.. -target -value))})))
                                                                                               :on-change (fn [arvo]
                                                                                                            (e!
                                                                                                              (t/->PaivitaMaara osa/*this*
                                                                                                                                (-> arvo (.. -target -value))))))
                                                                           #(tyokalu/aseta-arvo %
                                                                                                :id :tehtava-yksikko
                                                                                                :arvo (or yksikko "")
                                                                                                :class #{(sarakkeiden-leveys :maara-yksikko)})))
                                  ))
                             (filter #(= "tehtava" (:tehtavaryhmatyyppi %)) rivit)))]
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
                                (tyokalu/aseta-arvo :id :dummy-rivi
                                                    :class #{"table-default-odd"})
                                (tyokalu/paivita-arvo :lapset
                                                      (osien-paivitys-fn #(tyokalu/aseta-arvo %
                                                                                              :id :tehtava-nimi
                                                                                              :class #{(sarakkeiden-leveys :maara)})
                                                                         #(-> % (tyokalu/aseta-arvo
                                                                                                  :id :tehtava-maara
                                                                                                  :class #{(sarakkeiden-leveys :maara)})
                                                                              (assoc :komponentti (fn [_ {:keys [teksti]} _] (yleiset/ajax-loader teksti))
                                                                                     :komponentin-argumentit {:teksti "Haetaan tehtäviä"}))
                                                                         #(tyokalu/aseta-arvo %
                                                                                              :id :tehtava-yksikko
                                                                                              :class #{(sarakkeiden-leveys :maara)})))))]
    (muodosta-taulukko :noudetaan-tehtavat
                      {:teksti    {:janan-tyyppi jana/Rivi
                                   :osat         [osa/Teksti osa/Teksti osa/Teksti]}
                       :datarivit {:janan-tyyppi jana/Rivi
                                   :osat         [osa/Teksti osa/Komponentti osa/Teksti]}}
                      ["Tehtävä" "Määrä" "Yksikkö"]
                      [:teksti otsikkorivi
                       :datarivit datarivi]
                      {:class #{}})))

(defn valitaso-filtteri
  [_ app]
  (let [{:keys [alkupvm]} (-> @tila/tila :yleiset :urakka )]
    (fn [e! {:keys [tehtava-ja-maaraluettelo valinnat] :as app}]
     (let [vuosi (pvm/vuosi alkupvm)
           valitasot (filter #(and
                               (= (get-in valinnat [:toimenpide :id]) (:toimenpide %))
                               (= "otsikko" (:tehtavaryhmatyyppi %))) tehtava-ja-maaraluettelo)
           ylatasot (filter #(= "toimenpide" (:tehtavaryhmatyyppi %)) tehtava-ja-maaraluettelo)
           hoitokaudet (into [] (range vuosi (+ 5 vuosi)))
           disabloitu-alasveto? (fn [koll] (or (:noudetaan valinnat)
                                               (= 0 (count koll))))]
       
       [:div
        [:div.label-ja-alasveto
         [:span.alasvedon-otsikko "Toimenpide"]
         [yleiset/livi-pudotusvalikko {:valinta    (:toimenpide valinnat)
                                       :valitse-fn #(e! (t/->ValitseTaso % :ylataso))
                                       :format-fn  #(:nimi %)
                                       :disabled   (disabloitu-alasveto? ylatasot)}
          ylatasot]]
        [:div.label-ja-alasveto
         [:span.alasvedon-otsikko "Välitaso"]
         [yleiset/livi-pudotusvalikko {:valinta    (:valitaso valinnat)
                                       :valitse-fn #(e! (t/->ValitseTaso % :valitaso))
                                       :format-fn #(:nimi %)
                                       :disabled (disabloitu-alasveto? valitasot)}
          valitasot]]
        [:div.label-ja-alasveto
         [:span.alasvedon-otsikko "Hoitokausi"]
         [yleiset/livi-pudotusvalikko {:valinta    (:hoitokausi valinnat)
                                       :valitse-fn #(e! (t/->ValitseTaso % :hoitokausi))
                                       :format-fn  #(str "1.10." % "-30.9." (inc %))
                                       :disabled (disabloitu-alasveto? hoitokaudet)}
          hoitokaudet]]
        [:label.kopioi-tuleville-vuosille
         [:input {:type      "checkbox" :checked false
                  :on-change (r/partial #() :ei)
                  :disabled (:noudetaan valinnat)}]
         "Samat suunnitellut määrät kaikille hoitokausille"]]))))

(defn tehtavat*
  [e! app]
  (komp/luo
    (komp/piirretty (fn [this]
                      (e! (t/->HaeTehtavat (partial luo-tehtava-taulukko e!)))))
    (fn [e! app]
      (let [{taulukon-tehtavat :tehtavat-taulukko} app
            {:keys [nimi]} (-> @tila/tila :yleiset :urakka )]
        [:div
         [debug/debug app]
         [:h1 "Tehtävät ja määrät" nimi]
         [:div "Tehtävät ja määrät suunnitellaan urakan alussa, ja tarkennetaan jokaisen hoitovuoden alussa. " [:a {:href "#"} "Toteuma"] "-puolelle kirjataan ja kirjautuu kalustosta toteutuneet määrät."]
         [valitaso-filtteri e! app]
         #_(if taulukon-tehtavat
           [p/piirra-taulukko taulukon-tehtavat]
           [p/piirra-taulukko (noudetaan-taulukko)])
         #_[yleiset/ajax-loader]]))))

(defn tehtavat []
  (tuck/tuck tila/suunnittelu-tehtavat tehtavat*))