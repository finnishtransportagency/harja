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

(defn luo-taulukon-tehtavat
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
        (log "Osa " osa)
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

(defn tehtavien-syote
  [this {:keys [e! id]} arvot]
  (log "yote" id this)
  (let [on-change-fn (fn [arvo]
                       (log "Päivitetään " id " ja " (keyword (str (name id) "-maara")) " arvo " arvo)
                       (when arvo
                         (e! (t/->PaivitaMaara id (keyword (str (name id) "-maara")) arvo))))
        osa (osa/->Syote
              (str id "-maara")
              {:on-change on-change-fn
               :on-blur #()
               :on-focus #()
               :on-key-down #()}
              {:on-change [:eventin-arvo]}
              {:type "text"})]
    (fn [this _ _]
      [:div
       [p/piirra-osa (-> osa
                         (tyokalu/aseta-arvo :arvo "jotai")
                         (assoc ::tama-komponentti this))]])))

(defn taul-teht
  [e! tehtavat-ja-maaraluettelo]
  (let [rivit (mapv
                tehtava-mankeli
                tehtavat-ja-maaraluettelo)
        tekstiteksti (fn [tekst] (log "Teksti teksti" tekst)
                       (-> tekst
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
        syottosyotto (fn [tekst] (log "Teksti teksti" tekst)
                       (mapv (fn [{:keys [nimi maara id tehtavaryhmatyyppi] :as tehtava}]
                               (log "Tehtttttt " tehtava)
                               (-> tekst
                                  (tyokalu/aseta-arvo :id (keyword id))
                                  (tyokalu/paivita-arvo :lapset
                                                        (osien-paivitys-fn #(tyokalu/aseta-arvo %
                                                                                                :id :tehtava-nimi
                                                                                                :arvo nimi
                                                                                                :class #{(sarakkeiden-leveys :maara)})
                                                                           #(-> %
                                                                                (tyokalu/aseta-arvo
                                                                                                 :id (keyword (str id "-maara"))
                                                                                                 :arvo maara
                                                                                                 :class #{(sarakkeiden-leveys :maara)})
                                                                                (assoc :komponentti tehtavien-syote
                                                                                       :komponentin-argumentit {:e! e!
                                                                                                                :id (keyword id)}))
                                                                           #(tyokalu/aseta-arvo %
                                                                                                :id :tehtava-yksikko
                                                                                                :arvo "Yksikkö"
                                                                                                :class #{(sarakkeiden-leveys :maara)})))
                                  ))
                             (filter #(= "alitaso" (:tehtavaryhmatyyppi %)) rivit)))]
    (muodosta-taulukko :tehtavat
                       {:teksti {:janan-tyyppi jana/Rivi
                                 :osat [osa/Teksti osa/Teksti osa/Teksti]}
                        :syotto {:janan-tyyppi jana/Rivi
                                 :osat [osa/Teksti osa/Komponentti osa/Teksti]}}
                       ["Tehtävä" "Määrä" "Yksikkö"]
                       [:teksti tekstiteksti
                        :syotto syottosyotto]
                       {:class "tehthhht"
                        :taulukon-paivitys-fn! tekstiteksti})
    ))

(defn tehtavat*
  [e! app]
  (komp/luo
    (komp/piirretty (fn [this]
                      (let [taulukon-tehtavat (luo-taulukon-tehtavat e! (get app :tehtava-ja-maaraluettelo) true)
                            taul-teht (taul-teht e! (get app :tehtava-ja-maaraluettelo))]
                        (e! (tuck-apurit/->MuutaTila [:tehtavat-taulukko] taulukon-tehtavat)))))
    (fn [e! app]
      (let [{taulukon-tehtavat :tehtavat-taulukko} app]
        [:div
         [debug/debug app]
         (if taulukon-tehtavat
           (do
             (log "Taulukon tehtavat " taulukon-tehtavat)
             ;(for [tehtava taulukon-tehtavat]
             ;  (do
             ;    (log "Tehtävä " (keys tehtava) tehtava)
             ;    ;[p/piirra-taulukko tehtava]
             ;    [:div "Moi"]))
             ;[p/piirra-taulukko taulukon-tehtavat]
             [taulukko/taulukko taulukon-tehtavat]
             )

           [yleiset/ajax-loader])]))))

(defn tehtavat []
  (tuck/tuck tila/suunnittelu-tehtavat tehtavat*))