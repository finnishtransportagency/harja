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
            [harja.ui.yleiset :as yleiset])
  (:require-macros [harja.ui.taulukko.tyokalut :refer [muodosta-taulukko]]))

(defn sarakkeiden-leveys [sarake]
  (case sarake
    :tehtava "col-xs-12 col-sm-8 col-md-8 col-lg-8"
    :maara "col-xs-12 col-sm-4 col-md-4 col-lg-4"))

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

(defn tehtava-mankeli [tehtava]
  tehtava)

(defn luo-taul-teht
  [e! tehtavat-ja-maaraluettelo]
  (let [polku-taulukkoon [:tehtavat-taulukko]
        taulukon-paivitys-fn! (fn [paivitetty-taulukko app]
                                (log "TAULUKKO" paivitetty-taulukko app polku-taulukkoon)
                                (assoc-in app polku-taulukkoon paivitetty-taulukko))
        rivit (mapv
                tehtava-mankeli
                tehtavat-ja-maaraluettelo)
        otsikkorivi (fn [rivi]
                       (-> rivi
                           (tyokalu/aseta-arvo :id :tehtava)
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
        syottorivi (fn [rivi]
                       (mapv (fn [{:keys [nimi maara id piillotettu? tehtavaryhmatyyppi] :as tehtava}]
                               (log "Tehtttttt " tehtava)
                               (-> rivi
                                  (tyokalu/aseta-arvo :id (keyword id)
                                                      :piillotettu? piillotettu?)
                                  (tyokalu/paivita-arvo :lapset
                                                        (osien-paivitys-fn #(tyokalu/aseta-arvo %
                                                                                                :id :tehtava-nimi
                                                                                                :arvo nimi
                                                                                                :class #{(sarakkeiden-leveys :maara)})
                                                                           #(tyokalu/aseta-arvo %
                                                                                               :id (keyword (str id "-maara"))
                                                                                               :arvo maara
                                                                                               :class #{(sarakkeiden-leveys :maara)}
                                                                                               :on-change (fn [arvo]
                                                                                                            (e!
                                                                                                              (t/->PaivitaMaara osa/*this*
                                                                                                                                (-> arvo (.. -target -value))))))
                                                                           #(tyokalu/aseta-arvo %
                                                                                                :id :tehtava-yksikko
                                                                                                :arvo "Yksikkö"
                                                                                                :class #{(sarakkeiden-leveys :maara)})))
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
                       {:class "tehthhht"
                        :taulukon-paivitys-fn! taulukon-paivitys-fn!})
    ))

(defn valitaso-filtteri
  [_ app]
  (let [a :a]
    (fn [e! {:keys [tehtava-ja-maaraluettelo valinnat] :as app}]
      (log tehtava-ja-maaraluettelo)
     (let [valitasot (filter #(and
                               (= (get-in valinnat [:toimenpide :id]) (:vanhempi %))
                               (= "otsikko" (:tehtavaryhmatyyppi %))) tehtava-ja-maaraluettelo)
           ylatasot (filter #(= "ylataso" (:tehtavaryhmatyyppi %)) tehtava-ja-maaraluettelo)]
       
       [:div
        [:div.label-ja-alasveto
         [:span.alasvedon-otsikko "Toimenpide"]
         [yleiset/livi-pudotusvalikko {:valinta    (:toimenpide valinnat)
                                       :valitse-fn #(e! (t/->ValitseYlataso %))
                                       :format-fn  #(:nimi %)}
          ylatasot]]
        [:div.label-ja-alasveto
         [:span.alasvedon-otsikko "Välitaso"]
         [yleiset/livi-pudotusvalikko {:valinta    (:valitaso valinnat)
                                       :valitse-fn #(e! (t/->ValitseValitaso %))
                                       :format-fn #(:nimi %)}
          valitasot]]
        #_[:div.label-ja-alasveto
         [:span.alasvedon-otsikko "Hoitokausi"]
         [yleiset/livi-pudotusvalikko {:valinta    {}
                                       :valitse-fn #()
                                       :format-fn  #()}
          [:kesakausi :talvikausi]]]
        [:label.kopioi-tuleville-vuosille
         [:input {:type      "checkbox" :checked false
                  :on-change (r/partial #() :ei)}]
         "Kopioi kuluvan hoitovuoden summat tuleville vuosille samoille kuukausille"]]))))

(defn tehtavat*
  [e! app]
  (komp/luo
    (komp/piirretty (fn [this]
                      (let [#_taulukon-tehtavat #_(luo-taulukon-tehtavat e! (get app :tehtava-ja-maaraluettelo) true)
                            taul-teht (luo-taul-teht e! (get app :tehtava-ja-maaraluettelo))]
                        (e! (tuck-apurit/->MuutaTila [:tehtavat-taulukko] taul-teht)))))
    (fn [e! app]
      (let [{taulukon-tehtavat :tehtavat-taulukko} app]
        [:div
         [debug/debug app]
         (if taulukon-tehtavat
           [:div
            [valitaso-filtteri e! app]
            [p/piirra-taulukko taulukon-tehtavat]]
           ;[taulukko/taulukko taulukon-tehtavat]
           [yleiset/ajax-loader])]))))

(defn tehtavat []
  (tuck/tuck tila/suunnittelu-tehtavat tehtavat*))