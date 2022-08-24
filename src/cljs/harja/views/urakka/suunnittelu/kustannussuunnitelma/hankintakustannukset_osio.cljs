(ns harja.views.urakka.suunnittelu.kustannussuunnitelma.hankintakustannukset-osio
  (:require [reagent.core :as r]
            [clojure.string :as clj-str]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma :as t]
            [harja.ui.modal :as modal]
            [harja.ui.taulukko.grid-pohjat :as g-pohjat]
            [harja.ui.taulukko.grid :as grid]
            [harja.ui.taulukko.impl.solu :as solu]
            [harja.ui.taulukko.grid-oletusarvoja :as konf]
            [harja.ui.yleiset :as yleiset]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.yhteiset :as ks-yhteiset :refer [e!]]))


;; -- Hankintakustannuksiin liittyvät gridit --
(defn- hankintojen-pohja
  [taulukon-id
   root-asetus!
   root-asetukset
   nappia-painettu!
   on-change
   on-blur]
  (g-pohjat/uusi-taulukko {:header [{:tyyppi :teksti
                                     :leveys 3
                                     :luokat #{"table-default" "table-default-header" "lihavoitu"}}
                                    {:tyyppi :teksti
                                     :leveys 2
                                     :luokat #{"table-default" "table-default-header" "lihavoitu"}}
                                    {:tyyppi :teksti
                                     :leveys 2
                                     :luokat #{"table-default" "table-default-header" "lihavoitu"}}
                                    {:tyyppi :teksti
                                     :leveys 1
                                     :luokat #{"table-default" "table-default-header" "harmaa-teksti" "lihavoitu"}}]
                           :body
                           (mapv (fn [hoitokauden-numero]
                                   {:tyyppi :taulukko
                                    :osat [{:tyyppi :rivi
                                            :nimi ::t/data-yhteenveto
                                            :osat [{:tyyppi :laajenna
                                                    :aukaise-fn
                                                    (fn [this auki?]
                                                      (t/laajenna-solua-klikattu this auki? taulukon-id [::g-pohjat/data]))
                                                    :auki-alussa? false
                                                    :luokat #{"table-default" "lihavoitu"}}
                                                   {:tyyppi :teksti
                                                    :luokat #{"table-default"}}
                                                   {:tyyppi :teksti
                                                    :luokat #{"table-default"}
                                                    :fmt ks-yhteiset/yhteenveto-format}
                                                   {:tyyppi :teksti
                                                    :luokat #{"table-default" "harmaa-teksti"}
                                                    :fmt ks-yhteiset/yhteenveto-format}]}
                                           {:tyyppi :taulukko
                                            :nimi ::t/data-sisalto
                                            :luokat #{"piilotettu"}
                                            :osat
                                            (vec (repeatedly 12
                                                   (fn []
                                                     {:tyyppi :rivi
                                                      :osat [{:tyyppi :teksti
                                                              :luokat #{"table-default"}
                                                              :fmt ks-yhteiset/aika-tekstilla-fmt}
                                                             {:tyyppi :syote-tayta-alas
                                                              :nappi-nakyvilla? false
                                                              :nappia-painettu! (partial nappia-painettu! hoitokauden-numero)
                                                              :toiminnot
                                                              {:on-change (partial on-change hoitokauden-numero)
                                                               :on-focus (fn [_]
                                                                           (grid/paivita-osa! solu/*this*
                                                                             (fn [solu]
                                                                               (assoc solu :nappi-nakyvilla? true))))
                                                               :on-blur (partial on-blur hoitokauden-numero)
                                                               :on-key-down (fn [event]
                                                                              (when (= "Enter" (.. event -key))
                                                                                (.. event -target blur)))}
                                                              :kayttaytymiset
                                                              {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                           {:eventin-arvo {:f ks-yhteiset/poista-tyhjat}}]
                                                               :on-blur [:positiivinen-numero
                                                                         {:eventin-arvo {:f ks-yhteiset/poista-tyhjat}}]}
                                                              :parametrit {:size 2}
                                                              :luokat #{"input-default"}
                                                              :fmt ks-yhteiset/summa-formatointi
                                                              :fmt-aktiivinen ks-yhteiset/summa-formatointi-aktiivinen}
                                                             {:tyyppi :teksti
                                                              :luokat #{"table-default"}
                                                              :fmt ks-yhteiset/summa-formatointi}
                                                             {:tyyppi :teksti
                                                              :fmt ks-yhteiset/summa-formatointi
                                                              :luokat #{"table-default" "harmaa-teksti"}}]})))}]})
                             (range 1 6))
                           :footer [{:tyyppi :teksti
                                     :luokat #{"table-default" "table-default-sum"}}
                                    {:tyyppi :teksti
                                     :luokat #{"table-default" "table-default-sum"}}
                                    {:tyyppi :teksti
                                     :luokat #{"table-default" "table-default-sum"}
                                     :fmt ks-yhteiset/yhteenveto-format}
                                    {:tyyppi :teksti
                                     :luokat #{"table-default" "table-default-sum" "harmaa-teksti"}
                                     :fmt ks-yhteiset/yhteenveto-format}]
                           :taulukon-id taulukon-id
                           :root-asetus! root-asetus!
                           :root-asetukset root-asetukset}))


(defn suunnitellut-hankinnat-grid []
  (let [g (hankintojen-pohja "suunnitellut-hankinnat-taulukko"
            (fn [g] (e! (tuck-apurit/->MuutaTila [:gridit :suunnitellut-hankinnat :grid] g)))
            {:haku (fn [] (get-in @tila/suunnittelu-kustannussuunnitelma [:gridit :suunnitellut-hankinnat :grid]))
             :paivita! (fn [f]
                         (swap! tila/suunnittelu-kustannussuunnitelma
                           (fn [tila]
                             (update-in tila [:gridit :suunnitellut-hankinnat :grid] f))))}
            (fn [hoitokauden-numero rivit-alla arvo]
              (when (not (empty? rivit-alla))
                (doseq [rivi rivit-alla
                        :let [maara-solu (grid/get-in-grid rivi [1])
                              piilotettu? (grid/piilotettu? rivi)]]
                  (when-not piilotettu?
                    (t/paivita-solun-arvo {:paivitettava-asia :aseta-suunnitellut-hankinnat!
                                           :arvo arvo
                                           :solu maara-solu
                                           ;; Hox: Jarjestykset määritellään grid-kasittelija:ssa ":jarjestys [^{:nimi :mapit} ....]"
                                           :ajettavat-jarjestykset #{:mapit}
                                           :triggeroi-seuranta? true}
                      true
                      hoitokauden-numero
                      :hankinnat)))
                (e! (t/->TallennaHankintojenArvot :hankintakustannus
                      hoitokauden-numero
                      (vec (keep (fn [rivi]
                                   (let [maara-solu (grid/get-in-grid rivi [1])
                                         piilotettu? (grid/piilotettu? rivi)]
                                     (when-not piilotettu?
                                       (grid/solun-asia maara-solu :tunniste-rajapinnan-dataan))))
                             rivit-alla))))))
            (fn [hoitokauden-numero arvo]
              (when arvo
                (t/paivita-solun-arvo {:paivitettava-asia :aseta-suunnitellut-hankinnat!
                                       :arvo arvo
                                       :solu solu/*this*
                                       ;; Hox: Jarjestykset määritellään grid-kasittelija:ssa ":jarjestys [^{:nimi :mapit} ....]"
                                       :ajettavat-jarjestykset #{:mapit}
                                       :triggeroi-seuranta? false}
                  false
                  hoitokauden-numero
                  :hankinnat)))
            (fn [hoitokauden-numero arvo]
              (t/paivita-solun-arvo {:paivitettava-asia :aseta-suunnitellut-hankinnat!
                                     :arvo arvo
                                     :solu solu/*this*
                                     :ajettavat-jarjestykset true
                                     :triggeroi-seuranta? true}
                true
                hoitokauden-numero
                :hankinnat)
              (e! (t/->TallennaHankintojenArvot :hankintakustannus hoitokauden-numero
                    [(grid/solun-asia solu/*this* :tunniste-rajapinnan-dataan)]))))]

    (grid/rajapinta-grid-yhdistaminen! g
      t/suunnitellut-hankinnat-rajapinta
      (t/suunnitellut-hankinnat-dr)
      (merge {[::g-pohjat/otsikko] {:rajapinta :otsikot
                                    :solun-polun-pituus 1
                                    :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
                                    :datan-kasittely (fn [otsikot]
                                                       (mapv (fn [otsikko]
                                                               otsikko)
                                                         (vals otsikot)))}
              [::g-pohjat/yhteenveto] {:rajapinta :yhteensa
                                       :solun-polun-pituus 1
                                       :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
                                       :datan-kasittely (fn [yhteensa]
                                                          (mapv (fn [[_ nimi]]
                                                                  nimi)
                                                            yhteensa))}}

        (reduce (fn [grid-kasittelijat hoitokauden-numero]
                  (merge grid-kasittelijat
                    {[::g-pohjat/data (dec hoitokauden-numero) ::t/data-yhteenveto]
                     {:rajapinta (keyword (str "yhteenveto-" hoitokauden-numero))
                      :solun-polun-pituus 1
                      :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
                      :datan-kasittely (fn [yhteenveto]
                                         (mapv (fn [[_ v]]
                                                 v)
                                           yhteenveto))
                      :tunnisteen-kasittely (fn [osat _]
                                              (mapv (fn [osa]
                                                      (when (instance? solu/Syote osa)
                                                        :maara))
                                                (grid/hae-grid osat :lapset)))}

                     [::g-pohjat/data (dec hoitokauden-numero) ::t/data-sisalto]
                     {:rajapinta (keyword (str "suunnitellut-hankinnat-" hoitokauden-numero))
                      :solun-polun-pituus 2
                      :jarjestys [{:keyfn :aika
                                   :comp (fn [aika-1 aika-2]
                                           (pvm/ennen? aika-1 aika-2))}
                                  ^{:nimi :mapit} [:aika :maara :yhteensa :indeksikorjattu]]
                      :datan-kasittely (fn [vuoden-hoidonjohtopalkkiot]
                                         (mapv (fn [rivi]
                                                 (mapv (fn [[_ v]]
                                                         v)
                                                   rivi))
                                           vuoden-hoidonjohtopalkkiot))
                      :tunnisteen-kasittely (fn [data-sisalto-grid data]
                                              (vec
                                                (map-indexed
                                                  (fn [i rivi]
                                                    (vec
                                                      (map-indexed (fn [j osa]
                                                                     (when (or (instance? solu/Syote osa)
                                                                             (instance? g-pohjat/SyoteTaytaAlas osa))
                                                                       {:osa :maara
                                                                        :aika (:aika (get data j))
                                                                        :osan-paikka [i j]}))
                                                        (grid/hae-grid rivi :lapset))))
                                                  (grid/hae-grid data-sisalto-grid :lapset))))}}))
          {}
          (range 1 6))))))

(defn hankinnat-laskutukseen-perustuen-grid
  "Laskutukseen perustuvat hankinnat grid"
  []
  (let [g (hankintojen-pohja "suunnitellut-hankinnat-laskutukseen-perustuen-taulukko"
            (fn [g] (e! (tuck-apurit/->MuutaTila [:gridit :laskutukseen-perustuvat-hankinnat :grid] g)))
            {:haku (fn [] (get-in @tila/suunnittelu-kustannussuunnitelma [:gridit :laskutukseen-perustuvat-hankinnat :grid]))
             :paivita! (fn [f]
                         (swap! tila/suunnittelu-kustannussuunnitelma
                           (fn [tila]
                             (update-in tila [:gridit :laskutukseen-perustuvat-hankinnat :grid] f))))}
            (fn [hoitokauden-numero rivit-alla arvo]
              (when (not (empty? rivit-alla))
                (doseq [rivi rivit-alla
                        :let [maara-solu (grid/get-in-grid rivi [1])
                              piilotettu? (grid/piilotettu? rivi)]]
                  (when-not piilotettu?
                    (t/paivita-solun-arvo {:paivitettava-asia :aseta-laskutukseen-perustuvat-hankinnat!
                                           :arvo arvo
                                           :solu maara-solu
                                           ;; Hox: Jarjestykset määritellään grid-kasittelija:ssa ":jarjestys [^{:nimi :mapit} ....]"
                                           :ajettavat-jarjestykset #{:mapit}
                                           :triggeroi-seuranta? true}
                      true
                      hoitokauden-numero
                      :hankinnat)))
                (e! (t/->TallennaHankintojenArvot :laskutukseen-perustuva-hankinta
                      hoitokauden-numero
                      (vec (keep (fn [rivi]
                                   (let [maara-solu (grid/get-in-grid rivi [1])
                                         piilotettu? (grid/piilotettu? rivi)]
                                     (when-not piilotettu?
                                       (grid/solun-asia maara-solu :tunniste-rajapinnan-dataan))))
                             rivit-alla))))))
            (fn [hoitokauden-numero arvo]
              (when arvo
                (t/paivita-solun-arvo {:paivitettava-asia :aseta-laskutukseen-perustuvat-hankinnat!
                                       :arvo arvo
                                       :solu solu/*this*
                                       ;; Hox: Jarjestykset määritellään grid-kasittelija:ssa ":jarjestys [^{:nimi :mapit} ....]"
                                       :ajettavat-jarjestykset #{:mapit}
                                       :triggeroi-seuranta? false}
                  false
                  hoitokauden-numero
                  :hankinnat)))
            (fn [hoitokauden-numero arvo]
              (t/paivita-solun-arvo {:paivitettava-asia :aseta-laskutukseen-perustuvat-hankinnat!
                                     :arvo arvo
                                     :solu solu/*this*
                                     :ajettavat-jarjestykset true
                                     :triggeroi-seuranta? true}
                true
                hoitokauden-numero
                :hankinnat)
              (e! (t/->TallennaHankintojenArvot :laskutukseen-perustuva-hankinta hoitokauden-numero [(grid/solun-asia solu/*this* :tunniste-rajapinnan-dataan)]))))]
    (grid/rajapinta-grid-yhdistaminen! g
      t/laskutukseen-perustuvat-hankinnat-rajapinta
      (t/laskutukseen-perustuvat-hankinnat-dr)
      (merge {[::g-pohjat/otsikko] {:rajapinta :otsikot
                                    :solun-polun-pituus 1
                                    :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
                                    :datan-kasittely (fn [otsikot]
                                                       (mapv (fn [otsikko]
                                                               otsikko)
                                                         (vals otsikot)))}
              [::g-pohjat/yhteenveto] {:rajapinta :yhteensa
                                       :solun-polun-pituus 1
                                       :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
                                       :datan-kasittely (fn [yhteensa]
                                                          (mapv (fn [[_ nimi]]
                                                                  nimi)
                                                            yhteensa))}}

        (reduce (fn [grid-kasittelijat hoitokauden-numero]
                  (merge grid-kasittelijat
                    {[::g-pohjat/data (dec hoitokauden-numero) ::t/data-yhteenveto]
                     {:rajapinta (keyword (str "yhteenveto-" hoitokauden-numero))
                      :solun-polun-pituus 1
                      :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
                      :datan-kasittely (fn [yhteenveto]
                                         (mapv (fn [[_ v]]
                                                 v)
                                           yhteenveto))
                      :tunnisteen-kasittely (fn [osat _]
                                              (mapv (fn [osa]
                                                      (when (instance? solu/Syote osa)
                                                        :maara))
                                                (grid/hae-grid osat :lapset)))}

                     [::g-pohjat/data (dec hoitokauden-numero) ::t/data-sisalto]
                     {:rajapinta (keyword (str "laskutukseen-perustuvat-hankinnat-" hoitokauden-numero))
                      :solun-polun-pituus 2
                      :jarjestys [{:keyfn :aika
                                   :comp (fn [aika-1 aika-2]
                                           (pvm/ennen? aika-1 aika-2))}
                                  ^{:nimi :mapit} [:aika :maara :yhteensa :indeksikorjattu]]
                      :datan-kasittely (fn [vuoden-hoidonjohtopalkkiot]
                                         (mapv (fn [rivi]
                                                 (mapv (fn [[_ v]]
                                                         v)
                                                   rivi))
                                           vuoden-hoidonjohtopalkkiot))
                      :tunnisteen-kasittely (fn [data-sisalto-grid data]
                                              (vec
                                                (map-indexed (fn [i rivi]
                                                               (vec
                                                                 (map-indexed (fn [j osa]
                                                                                (when (or (instance? solu/Syote osa)
                                                                                        (instance? g-pohjat/SyoteTaytaAlas osa))
                                                                                  {:osa :maara
                                                                                   :aika (:aika (get data j))
                                                                                   :osan-paikka [i j]}))
                                                                   (grid/hae-grid rivi :lapset))))
                                                  (grid/hae-grid data-sisalto-grid :lapset))))}}))
          {}
          (range 1 6))))))


(defn- tayta-alas-napin-toiminto [asettajan-nimi tallennettava-asia maara-solun-index rivit-alla arvo]
  (when (and arvo (not (empty? rivit-alla)))
    (doseq [rivi rivit-alla
            :let [maara-solu (grid/get-in-grid rivi [maara-solun-index])
                  piilotettu? (grid/piilotettu? rivi)]]
      (when-not piilotettu?
        (t/paivita-solun-arvo {:paivitettava-asia asettajan-nimi
                               :arvo arvo
                               :solu maara-solu
                               ;; Hox: Jarjestykset määritellään grid-kasittelija:ssa ":jarjestys [^{:nimi :mapit} ....]"
                               :ajettavat-jarjestykset #{:mapit}
                               :triggeroi-seuranta? true}
          true)))
    (when (= asettajan-nimi :aseta-rahavaraukset!)
      (e! (t/->TallennaKustannusarvoitu tallennettava-asia
            (vec (keep (fn [rivi]
                         (let [maara-solu (grid/get-in-grid rivi [1])
                               piilotettu? (grid/piilotettu? rivi)]
                           (when-not piilotettu?
                             (grid/solun-asia maara-solu :tunniste-rajapinnan-dataan))))
                   rivit-alla))
            nil)))))

(defn rahavarausten-grid []
  (let [dom-id "rahavaraukset-taulukko"
        g (grid/grid
            {:nimi ::t/root
             :dom-id dom-id
             :root-fn (fn [] (get-in @tila/suunnittelu-kustannussuunnitelma [:gridit :rahavaraukset :grid]))
             :paivita-root! (fn [f]
                              (swap! tila/suunnittelu-kustannussuunnitelma
                                (fn [tila]
                                  (update-in tila [:gridit :rahavaraukset :grid] f))))
             :alueet [{:sarakkeet [0 1] :rivit [0 3]}]
             :koko (-> konf/auto
                     (assoc-in [:rivi :nimet]
                       {::t/otsikko 0
                        ::t/data 1
                        ::t/yhteenveto 2})
                     (assoc-in [:rivi :korkeudet] {0 "40px"
                                                   2 "40px"}))
             :osat [(grid/rivi
                      {:nimi ::t/otsikko
                       :koko (-> konf/livi-oletuskoko
                               (assoc-in [:sarake :leveydet] {0 "3fr"
                                                              3 "1fr"})
                               (assoc-in [:sarake :oletus-leveys] "2fr"))
                       :osat (mapv (fn [index]
                                     (if (= 3 index)
                                       (solu/teksti {:parametrit
                                                     {:class #{"table-default" "table-default-header" "harmaa-teksti"}}})
                                       (solu/teksti {:parametrit
                                                     {:class #{"table-default" "table-default-header"}}})))
                               (range 4))
                       :luokat #{"salli-ylipiirtaminen"}
                       :data-cy "otsikko-rivi"}
                      [{:sarakkeet [0 4] :rivit [0 1]}])
                    (grid/dynamic-grid
                      {:nimi ::t/data
                       :alueet [{:sarakkeet [0 1] :rivit [0 1]}]
                       :koko konf/auto
                       :luokat #{"salli-ylipiirtaminen"}
                       :osien-maara-muuttui! (fn [g _]
                                               #_(println "### Toimenpidettä vaihdettu")
                                               (t/paivita-raidat! (grid/osa-polusta (grid/root g) [::t/data])))
                       :toistettavan-osan-data (fn [{:keys [arvot valittu-toimenpide hoitokauden-numero]}]
                                                 {:valittu-toimenpide valittu-toimenpide
                                                  :hoitokauden-numero hoitokauden-numero
                                                  ;; HOX: arvot-kokoelman avain on mappi, jossa on :tyyppi ja :toimenpide
                                                  ;;   Arvot palautetaan :rahavaraukset-rajapinnasta
                                                  :tyypit (mapv (comp :tyyppi key) arvot)})
                       :toistettava-osa
                       (fn [{:keys [tyypit valittu-toimenpide hoitokauden-numero]}]
                         (mapv
                           (fn [tyyppi]
                             (with-meta
                               (grid/grid
                                 {:alueet [{:sarakkeet [0 1] :rivit [0 2]}]
                                  :nimi ::t/datarivi
                                  :koko (-> konf/auto
                                          (assoc-in [:rivi :nimet]
                                            {::t/data-yhteenveto 0
                                             ::t/data-sisalto 1}))
                                  :luokat #{"salli-ylipiirtaminen"}
                                  :osat [(with-meta
                                           (grid/rivi
                                             {:nimi ::t/data-yhteenveto
                                              :koko {:seuraa {:seurattava ::t/otsikko
                                                              :sarakkeet :sama
                                                              :rivit :sama}}
                                              :osat [(solu/laajenna
                                                       {:aukaise-fn
                                                        (fn [this auki?]
                                                          (let [rajapinta (keyword (str "rahavaraukset-yhteenveto-" tyyppi "-" valittu-toimenpide "-" hoitokauden-numero))]
                                                            (if auki?
                                                              (do
                                                                (ks-yhteiset/rivi-kuukausifiltterilla! this
                                                                  false
                                                                  (keyword (str "rahavaraukset-kuukausitasolla-" tyyppi "?"))
                                                                  [:gridit :rahavaraukset :kuukausitasolla? tyyppi]
                                                                  ;; yhteenveto
                                                                  [:. ::t/yhteenveto]
                                                                  {:rajapinta rajapinta
                                                                   :solun-polun-pituus 1
                                                                   :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
                                                                   :datan-kasittely (fn [yhteenveto]
                                                                                      (mapv (fn [[_ v]]
                                                                                              v)
                                                                                        yhteenveto))
                                                                   :tunnisteen-kasittely (fn [osat _]
                                                                                           (mapv (fn [osa]
                                                                                                   (when (instance? solu/Syote osa)
                                                                                                     {:osa :maara
                                                                                                      :tyyppi tyyppi}))
                                                                                             (grid/hae-grid osat :lapset)))}
                                                                  ;; valinta
                                                                  [:. ::t/valinta]
                                                                  {:rajapinta (keyword (str "rahavaraukset-kuukausitasolla-" tyyppi "?"))
                                                                   :solun-polun-pituus 1
                                                                   :datan-kasittely (fn [kuukausitasolla?]
                                                                                      [kuukausitasolla? nil nil nil])})

                                                                (grid/triggeroi-tapahtuma! this :rahavaraukset-disablerivit))

                                                              (ks-yhteiset/rivi-ilman-kuukausifiltteria! this
                                                                ;; data-yhteenveto
                                                                [:.. ::t/data-yhteenveto]
                                                                {:rajapinta rajapinta
                                                                 :solun-polun-pituus 1
                                                                 :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
                                                                 :datan-kasittely (fn [yhteenveto]
                                                                                    (mapv (fn [[_ v]]
                                                                                            v)
                                                                                      yhteenveto))
                                                                 :tunnisteen-kasittely (fn [osat _]
                                                                                         (mapv (fn [osa]
                                                                                                 (when (instance? solu/Syote osa)
                                                                                                   {:osa :maara
                                                                                                    :tyyppi tyyppi}))
                                                                                           (grid/hae-grid osat :lapset)))})))
                                                          (t/laajenna-solua-klikattu this auki? dom-id [::t/data] {:sulkemis-polku [:.. :.. :.. 1]}))
                                                        :auki-alussa? false
                                                        :parametrit {:class #{"table-default" "lihavoitu"}}})
                                                     (solu/syote
                                                       {:toiminnot
                                                        {:on-change (fn [arvo]
                                                                      (when arvo
                                                                        (when ks-yhteiset/esta-blur_
                                                                          (set! ks-yhteiset/esta-blur_ false))
                                                                        (t/paivita-solun-arvo {:paivitettava-asia :aseta-rahavaraukset-yhteenveto!
                                                                                               :arvo arvo
                                                                                               :solu solu/*this*
                                                                                               ;; Hox: Jarjestykset määritellään grid-kasittelija:ssa ":jarjestys [^{:nimi :mapit} ....]"
                                                                                               :ajettavat-jarjestykset #{:mapit}})))
                                                         :on-focus (fn [event]
                                                                     (let [arvo (.. event -target -value)]
                                                                       (when (= arvo t/vaihtelua-teksti)
                                                                         (set! ks-yhteiset/esta-blur_ true)
                                                                         (set! (.. event -target -value) nil))))
                                                         :on-blur (fn [arvo]
                                                                    (t/paivita-solun-arvo {:paivitettava-asia :aseta-rahavaraukset!
                                                                                           :arvo arvo
                                                                                           :solu solu/*this*
                                                                                           :ajettavat-jarjestykset true
                                                                                           :triggeroi-seuranta? true}
                                                                      true)
                                                                    (let [vanhempiosa (grid/osa-polusta solu/*this* [:.. :..])
                                                                          tallennettavien-arvojen-osat (if (= ::t/datarivi (grid/hae-osa vanhempiosa :nimi))
                                                                                                         (grid/hae-grid (grid/osa-polusta vanhempiosa [1]) :lapset)
                                                                                                         (grid/hae-grid (grid/osa-polusta vanhempiosa [:.. 1]) :lapset))
                                                                          tunnisteet (mapv #(grid/solun-asia (get (grid/hae-grid % :lapset) 1)
                                                                                              :tunniste-rajapinnan-dataan)
                                                                                       tallennettavien-arvojen-osat)]
                                                                      (e! (t/->TallennaKustannusarvoitu tyyppi tunnisteet nil))))
                                                         :on-key-down (fn [event]
                                                                        (when (= "Enter" (.. event -key))
                                                                          (.. event -target blur)))}
                                                        :kayttaytymiset {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                                     {:eventin-arvo {:f ks-yhteiset/poista-tyhjat}}]
                                                                         :on-blur [:positiivinen-numero
                                                                                   {:eventin-arvo {:f ks-yhteiset/poista-tyhjat}}
                                                                                   {:oma {:f ks-yhteiset/esta-blur-ja-lisaa-vaihtelua-teksti}}]}
                                                        :parametrit {:size 2
                                                                     :class #{"input-default"}}
                                                        :fmt ks-yhteiset/summa-formatointi
                                                        :fmt-aktiivinen ks-yhteiset/summa-formatointi-aktiivinen})
                                                     (solu/teksti {:parametrit {:class #{"table-default"}}
                                                                   :fmt ks-yhteiset/summa-formatointi})
                                                     (solu/teksti {:parametrit {:class #{"table-default" "harmaa-teksti"}}
                                                                   :fmt ks-yhteiset/summa-formatointi})]
                                              :luokat #{"salli-ylipiirtaminen"}}
                                             [{:sarakkeet [0 4] :rivit [0 1]}])
                                           {:key (str tyyppi "-yhteenveto")})

                                         ;; Kuukaudet-alitaulukko
                                         (with-meta
                                           (grid/taulukko {:nimi ::t/data-sisalto
                                                           :alueet [{:sarakkeet [0 1] :rivit [0 12]}]
                                                           :koko konf/auto
                                                           :luokat #{"piilotettu" "salli-ylipiirtaminen"}}
                                             (mapv
                                               (fn [index]
                                                 (with-meta
                                                   (grid/rivi
                                                     {:koko {:seuraa {:seurattava ::t/otsikko
                                                                      :sarakkeet :sama
                                                                      :rivit :sama}}
                                                      ;; -- Taulukon rivin solut--
                                                      :osat [;; vuosi/kk
                                                             (with-meta
                                                               (solu/teksti {:parametrit {:class #{"table-default"}}
                                                                             :fmt ks-yhteiset/aika-tekstilla-fmt})
                                                               {:key (str tyyppi "-" index "-otsikko")})
                                                             ;; Määrä €/kk
                                                             (with-meta
                                                               (g-pohjat/->SyoteTaytaAlas (gensym "rahavaraus")
                                                                 false
                                                                 (partial tayta-alas-napin-toiminto
                                                                   :aseta-rahavaraukset!
                                                                   tyyppi
                                                                   1)
                                                                 {:on-change (fn [arvo]
                                                                               (when arvo
                                                                                 (t/paivita-solun-arvo {:paivitettava-asia :aseta-rahavaraukset!
                                                                                                        :arvo arvo
                                                                                                        :solu solu/*this*
                                                                                                        ;; Hox: Jarjestykset määritellään grid-kasittelija:ssa ":jarjestys [^{:nimi :mapit} ....]"
                                                                                                        :ajettavat-jarjestykset #{:mapit}}
                                                                                   false)))
                                                                  :on-focus (fn [_]
                                                                              (grid/paivita-osa! solu/*this*
                                                                                (fn [solu]
                                                                                  (assoc solu :nappi-nakyvilla? true))))
                                                                  :on-blur (fn [arvo]
                                                                             (t/paivita-solun-arvo {:paivitettava-asia :aseta-rahavaraukset!
                                                                                                    :arvo arvo
                                                                                                    :solu solu/*this*
                                                                                                    :ajettavat-jarjestykset true
                                                                                                    :triggeroi-seuranta? true}
                                                                               true)
                                                                             (e! (t/->TallennaKustannusarvoitu tyyppi
                                                                                   [(grid/solun-asia solu/*this* :tunniste-rajapinnan-dataan)]
                                                                                   nil)))
                                                                  :on-key-down (fn [event]
                                                                                 (when (= "Enter" (.. event -key))
                                                                                   (.. event -target blur)))}
                                                                 {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                              {:eventin-arvo {:f ks-yhteiset/poista-tyhjat}}]
                                                                  :on-blur [:positiivinen-numero {:eventin-arvo {:f ks-yhteiset/poista-tyhjat}}]}
                                                                 {:size 2
                                                                  :class #{"input-default"}}
                                                                 ks-yhteiset/summa-formatointi
                                                                 ks-yhteiset/summa-formatointi-aktiivinen)
                                                               {:key (str tyyppi "-" index "-maara")})
                                                             ;; Yhteensä
                                                             (with-meta
                                                               (solu/teksti {:parametrit {:class #{"table-default"}}
                                                                             :fmt ks-yhteiset/summa-formatointi})
                                                               {:key (str tyyppi "-" index "-yhteensa")})
                                                             ;; Indeksikorjattu
                                                             (with-meta
                                                               (solu/teksti {:parametrit {:class #{"table-default"}}
                                                                             :fmt ks-yhteiset/summa-formatointi})
                                                               {:key (str tyyppi "-" index "-indeksikorjattu")})]
                                                      :luokat #{"salli-ylipiirtaminen"}}
                                                     [{:sarakkeet [0 4] :rivit [0 1]}])
                                                   {:key (str tyyppi "-" index)}))
                                               (range 12)))
                                           {:key (str tyyppi "-data-sisalto")})]})
                               {:key tyyppi}))
                           tyypit))})
                    (grid/rivi {:nimi ::t/yhteenveto
                                :koko {:seuraa {:seurattava ::t/otsikko
                                                :sarakkeet :sama
                                                :rivit :sama}}
                                :osat (conj (vec (repeatedly 2 (fn []
                                                                 (solu/teksti {:parametrit {:class #{"table-default" "table-default-sum"}}}))))
                                        (solu/teksti {:parametrit {:class #{"table-default" "table-default-sum"}}
                                                      :fmt ks-yhteiset/yhteenveto-format})
                                        (solu/teksti {:parametrit {:class #{"table-default" "table-default-sum" "harmaa-teksti"}}
                                                      :fmt ks-yhteiset/yhteenveto-format}))}
                      [{:sarakkeet [0 4] :rivit [0 1]}])]})]


    (e! (tuck-apurit/->MuutaTila [:gridit :rahavaraukset :grid] g))

    (grid/rajapinta-grid-yhdistaminen! g
      t/rahavarausten-rajapinta
      (t/rahavarausten-dr)
      {[::t/otsikko] {:rajapinta :rahavaraukset-otsikot
                      :solun-polun-pituus 1
                      :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
                      :datan-kasittely (fn [otsikot]
                                         (mapv (fn [otsikko]
                                                 otsikko)
                                           (vals otsikot)))}
       [::t/yhteenveto] {:rajapinta :rahavaraukset-yhteensa
                         :solun-polun-pituus 1
                         :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
                         :datan-kasittely (fn [yhteensa]
                                            (mapv (fn [[_ nimi]]
                                                    nimi)
                                              yhteensa))}
       [::t/data] {:rajapinta :rahavaraukset
                   :solun-polun-pituus 0
                   ;; NOTE: :jarjestys on välttämätöntä määritellä, koska muuten rivit voivat mennä epäjärjestykseen,
                   ;;   kun kuukausialitaulukon avaa riviltä.
                   :jarjestys [{:keyfn key
                                :comp (fn [{tyyppi1 :tyyppi toimenpide1 :toimenpide}
                                           {tyyppi2 :tyyppi toimenpide2 :toimenpide}]
                                        (compare
                                          (t/toimenpiteen-rahavaraustyypin-jarjestys-gridissa toimenpide1 tyyppi1)
                                          (t/toimenpiteen-rahavaraustyypin-jarjestys-gridissa toimenpide2 tyyppi2)))}]
                   :luonti (fn [{:keys [arvot valittu-toimenpide hoitokauden-numero]}]
                             (map (fn [[{:keys [tyyppi toimenpide]} _]]
                                    (when-not (nil? tyyppi)
                                      ;; Rahavarausrivit luodaan indeksin mukaan järjestyksessä
                                      (let [index (t/toimenpiteen-rahavaraustyypin-jarjestys-gridissa toimenpide tyyppi)]
                                        ;; data-yhteenveto rivi
                                        {[:. index ::t/data-yhteenveto]
                                         {:rajapinta (keyword (str "rahavaraukset-yhteenveto-" tyyppi "-" valittu-toimenpide
                                                                "-" hoitokauden-numero))
                                          :solun-polun-pituus 1
                                          :jarjestys [^{:nimi :mapit} [:nimi :maara :yhteensa :indeksikorjattu]]
                                          :datan-kasittely (fn [yhteenveto]
                                                             (mapv (fn [[_ v]]
                                                                     v)
                                                               yhteenveto))
                                          :tunnisteen-kasittely (fn [osat _]
                                                                  (mapv (fn [osa]
                                                                          (when (instance? solu/Syote osa)
                                                                            {:osa :maara
                                                                             :tyyppi tyyppi}))
                                                                    (grid/hae-grid osat :lapset)))}

                                         ;; data-sisalto (yhteenvetoriviltä avautuva kuukaudet-alitaulukko) rivit
                                         [:. index ::t/data-sisalto]
                                         {:rajapinta (keyword (str "rahavaraukset-data-" tyyppi "-" valittu-toimenpide
                                                                "-" hoitokauden-numero))
                                          :solun-polun-pituus 2
                                          :jarjestys [{:keyfn :aika
                                                       :comp (fn [aika-1 aika-2]
                                                               (when (and aika-1 aika-2)
                                                                 (pvm/ennen? aika-1 aika-2)))}
                                                      ^{:nimi :mapit} [:aika :maara :yhteensa :indeksikorjattu]]
                                          :datan-kasittely (fn [vuoden-hoidonjohtopalkkiot]
                                                             (mapv (fn [rivi]
                                                                     (mapv (fn [[_ v]]
                                                                             v)
                                                                       rivi))
                                                               vuoden-hoidonjohtopalkkiot))
                                          :tunnisteen-kasittely (fn [data-sisalto-grid data]
                                                                  (vec
                                                                    (map-indexed
                                                                      (fn [i rivi]
                                                                        (vec
                                                                          (map-indexed
                                                                            (fn [j osa]
                                                                              (when (or (instance? solu/Syote osa)
                                                                                      (instance? g-pohjat/SyoteTaytaAlas osa))
                                                                                {:osa :maara
                                                                                 :tyyppi tyyppi
                                                                                 :osan-paikka [i j]}))
                                                                            (grid/hae-grid rivi :lapset))))
                                                                      (grid/hae-grid data-sisalto-grid :lapset))))}})))
                               arvot))
                   :datan-kasittely (fn [arvot]
                                      {:arvot arvot
                                       :valittu-toimenpide (:valittu-toimenpide (meta arvot))
                                       :hoitokauden-numero (:hoitokauden-numero (meta arvot))})}})
    (grid/grid-tapahtumat g
      tila/suunnittelu-kustannussuunnitelma
      {:rahavaraukset-disablerivit
       {:polut [[:gridit :rahavaraukset :kuukausitasolla?]
                [:suodattimet :hankinnat :toimenpide]]
        :toiminto! (fn [g _ kuukausitasolla-kaikki-tyypit valittu-toimenpide]
                     (doseq [[tyyppi kuukausitasolla?] kuukausitasolla-kaikki-tyypit
                             :let [index (t/toimenpiteen-rahavaraustyypin-jarjestys-gridissa valittu-toimenpide tyyppi)]]
                       ;; Toimenpidettä vaihtaessa -disablerivit triggeröidään.
                       ;; Tässä on tärkeää disabloida ja enabloida ainoastaan toimenpiteen tyyppiin liittyvien indeksien
                       ;; mukaisia gridin osia.
                       (when index
                         #_(println ":rahavaraukset-disablerivit valittu toimenpide:" valittu-toimenpide
                             "index:" index "tyyppi: " tyyppi ", kuukausitasolla?: " kuukausitasolla?)
                         (ks-yhteiset/maara-solujen-disable! (grid/get-in-grid g [::t/data index ::t/data-sisalto])
                           (not kuukausitasolla?))
                         (ks-yhteiset/maara-solujen-disable! (grid/get-in-grid g [::t/data index ::t/data-yhteenveto])
                           kuukausitasolla?))))}})))

;; | -- Gridit päättyy



;; -----
;; -- Hankintakustannukset osion apufunktiot --

(defn- laskutukseen-perustuvan-taulukon-nakyvyys! []
  (let [{:keys [toimenpide laskutukseen-perustuen-valinta]} (-> @tila/suunnittelu-kustannussuunnitelma :suodattimet :hankinnat)
        laskutukseen-perustuvat-hankinnat-taulukko (get-in @tila/suunnittelu-kustannussuunnitelma [:gridit :laskutukseen-perustuvat-hankinnat :grid])]
    (if (contains? laskutukseen-perustuen-valinta toimenpide)
      (grid/nayta! laskutukseen-perustuvat-hankinnat-taulukko)
      (grid/piilota! laskutukseen-perustuvat-hankinnat-taulukko))))

(defn- arvioidaanko-laskutukseen-perustuen [_ _ _]
  (let [vaihda-fn (fn [toimenpide event]
                    (let [valittu? (.. event -target -checked)
                          paivita-ui! (fn []
                                        (e! (tuck-apurit/->PaivitaTila [:suodattimet :hankinnat :laskutukseen-perustuen-valinta]
                                              (fn [valinnat]
                                                (disj valinnat toimenpide))))
                                        (laskutukseen-perustuvan-taulukon-nakyvyys!)
                                        (modal/piilota!))]
                      (if valittu?
                        (do (e! (tuck-apurit/->PaivitaTila [:suodattimet :hankinnat :laskutukseen-perustuen-valinta]
                                  (fn [valinnat]
                                    (conj valinnat toimenpide))))
                            (laskutukseen-perustuvan-taulukon-nakyvyys!))
                        (t/poista-laskutukseen-perustuen-data! toimenpide
                          paivita-ui!
                          (r/partial (fn [data-hoitokausittain poista!]
                                       (ks-yhteiset/poista-modal! :maaramitattava
                                         data-hoitokausittain
                                         (comp poista!
                                           paivita-ui!)
                                         {:toimenpide toimenpide})))))))]
    (fn [{:keys [toimenpide]} laskutukseen-perustuen? on-oikeus?]
      [:div#laskutukseen-perustuen-filter
       [:input#laskutukseen-perustuen.vayla-checkbox
        {:type "checkbox" :checked laskutukseen-perustuen?
         :on-change (partial vaihda-fn toimenpide) :disabled (not on-oikeus?)}]
       [:label {:for "laskutukseen-perustuen"}
        "Haluan suunnitella myös määrämitattavia töitä toimenpiteelle: "
        [:b (t/toimenpide-formatointi toimenpide)]]])))


(defn- laskutukseen-perustuen-wrapper [g nayta-laskutukseen-perustuva-taulukko?]
  (when-not nayta-laskutukseen-perustuva-taulukko?
    (grid/piilota! g))
  (fn [g _]
    [grid/piirra g]))


;; -- Filtterit

(defn- maksetaan-filter [_ _]
  (let [kausi-tekstiksi (fn [kausi]
                          (case kausi
                            :kesakausi "Kesäkaudella"
                            :talvikausi "Talvikaudella"
                            :molemmat "Kesä- ja talvikaudella"))]
    (fn [valitse-kausi maksetaan]
      [:div.pudotusvalikko-filter
       [:span "Maksetaan"]
       [yleiset/livi-pudotusvalikko {:valinta maksetaan
                                     :valitse-fn valitse-kausi
                                     :format-fn kausi-tekstiksi
                                     :vayla-tyyli? true}
        [:kesakausi :talvikausi :molemmat]]])))

(defn- hankintojen-filter [_ _ _]
  (let [toimenpide-tekstiksi (r/partial (fn [toimenpide]
                                          (-> toimenpide t/toimenpide-formatointi clj-str/upper-case)))
        valitse-toimenpide (r/partial (fn [toimenpide]
                                        (e! (tuck-apurit/->MuutaTila [:suodattimet :hankinnat :toimenpide] toimenpide))
                                        (laskutukseen-perustuvan-taulukon-nakyvyys!)))
        valitse-kausi (fn [suunnitellut-hankinnat-grid laskutukseen-perustuvat-hankinnat-grid kausi]
                        (e! (tuck-apurit/->MuutaTila [:suodattimet :hankinnat :maksetaan] kausi))
                        (e! (t/->MaksukausiValittu))
                        (t/paivita-raidat! (grid/osa-polusta suunnitellut-hankinnat-grid [::g-pohjat/data]))
                        (t/paivita-raidat! (grid/osa-polusta laskutukseen-perustuvat-hankinnat-grid [::g-pohjat/data])))
        vaihda-fn (fn [event]
                    (.preventDefault event)
                    (e! (tuck-apurit/->PaivitaTila [:suodattimet :hankinnat :kopioidaan-tuleville-vuosille?] not)))]
    (fn [suunnitellut-hankinnat-grid laskutukseen-perustuvat-hankinnat-grid {:keys [toimenpide maksetaan kopioidaan-tuleville-vuosille?]}]
      (let [toimenpide (toimenpide-tekstiksi toimenpide)]
        [:div
         [:div.kustannussuunnitelma-filter
          [:div
           [:span "Toimenpide"]
           [yleiset/livi-pudotusvalikko {:valinta toimenpide
                                         :valitse-fn valitse-toimenpide
                                         :format-fn toimenpide-tekstiksi
                                         :vayla-tyyli? true
                                         :data-cy "suunnitellut-hankinnat-toimenpide-select"}
            (sort-by t/toimenpiteiden-jarjestys t/toimenpiteet)]]
          [maksetaan-filter (r/partial valitse-kausi suunnitellut-hankinnat-grid laskutukseen-perustuvat-hankinnat-grid) maksetaan]]
         [:input#kopioi-hankinnat-tuleville-hoitovuosille.vayla-checkbox
          {:type "checkbox" :checked kopioidaan-tuleville-vuosille?
           :on-change vaihda-fn}]
         [:label {:for "kopioi-hankinnat-tuleville-hoitovuosille"}
          "Kopioi kuluvan hoitovuoden summat tuleville vuosille samoille kuukausille"]]))))




;; ### Hankintakustannukset osion pääkomponentti ###
(defn osio [vahvistettu?
            kirjoitusoikeus?
            indeksit
            kuluva-hoitokausi
            suunnitellut-hankinnat-grid
            laskutukseen-perustuvat-hankinnat-grid
            rahavaraukset-grid
            hankintakustannukset-yhteenvedot
            kantahaku-valmis?
            suodattimet]
  (let [{:keys [toimenpide laskutukseen-perustuen-valinta]} (:hankinnat suodattimet)
        suunnitellut-hankinnat-taulukko-valmis? (and suunnitellut-hankinnat-grid kantahaku-valmis?)
        laskutukseen-perustuva-taulukko-valmis? (and laskutukseen-perustuvat-hankinnat-grid kantahaku-valmis?)
        rahavaraukset-taulukko-valmis? (and rahavaraukset-grid kantahaku-valmis?)
        nayta-laskutukseen-perustuva-taulukko? (contains? laskutukseen-perustuen-valinta toimenpide)
        yhteenveto-indeksikorjattu (mapv (fn [summa-indeksikorjattu]
                                          {:summa summa-indeksikorjattu})
                                     (mapv +
                                       (t/summaa-lehtivektorit (get-in hankintakustannukset-yhteenvedot [:indeksikorjatut-summat :rahavaraukset]))
                                       (t/summaa-lehtivektorit (get-in hankintakustannukset-yhteenvedot [:indeksikorjatut-summat :suunnitellut-hankinnat]))
                                       (t/summaa-lehtivektorit (get-in hankintakustannukset-yhteenvedot [:indeksikorjatut-summat :laskutukseen-perustuvat-hankinnat]))))
        yhteenveto (mapv (fn [summa]
                           {:summa summa})
                     (mapv +
                       (t/summaa-lehtivektorit (get-in hankintakustannukset-yhteenvedot [:summat :rahavaraukset]))
                       (t/summaa-lehtivektorit (get-in hankintakustannukset-yhteenvedot [:summat :suunnitellut-hankinnat]))
                       (t/summaa-lehtivektorit (get-in hankintakustannukset-yhteenvedot [:summat :laskutukseen-perustuvat-hankinnat]))))]
    [:<>
     [:h2#hankintakustannukset-osio "Hankintakustannukset"]
     (if yhteenveto
       ^{:key "hankintakustannusten-yhteenveto"}
       [:div.summa-ja-indeksilaskuri
        [ks-yhteiset/hintalaskuri {:otsikko "Yhteenveto"
                                   :selite "Talvihoito + Liikenneympäristön hoito + Sorateiden hoito + Päällystepaikkaukset + MHU Ylläpito + MHU Korvausinvestointi"
                                   :hinnat yhteenveto
                                   :data-cy "hankintakustannukset-hintalaskuri"}
         kuluva-hoitokausi]
        [ks-yhteiset/indeksilaskuri-ei-indeksikorjausta
         yhteenveto-indeksikorjattu
         indeksit
         {:data-cy "hankintakustannukset-indeksilaskuri"}]]

       ^{:key "hankintakustannusten-loader"}
       [yleiset/ajax-loader "Hankintakustannusten yhteenveto..."])
     [:h3 "Suunnitellut hankinnat"]
     [hankintojen-filter suunnitellut-hankinnat-grid laskutukseen-perustuvat-hankinnat-grid (:hankinnat suodattimet)]
     (if suunnitellut-hankinnat-taulukko-valmis?
       ;; FIXME: "Osio-vahvistettu" luokka on väliaikainen hack, jolla osion input kentät saadaan disabloitua kunnes muutosten seuranta ehditään toteuttaa.
       [:div {:class (when vahvistettu? "osio-vahvistettu")}
        [grid/piirra suunnitellut-hankinnat-grid]]
       [yleiset/ajax-loader])
     [arvioidaanko-laskutukseen-perustuen (:hankinnat suodattimet) nayta-laskutukseen-perustuva-taulukko? kirjoitusoikeus?]
     (if laskutukseen-perustuva-taulukko-valmis?
       ^{:key "nayta-lpt"}
       ;; FIXME: "Osio-vahvistettu" luokka on väliaikainen hack, jolla osion input kentät saadaan disabloitua kunnes muutosten seuranta ehditään toteuttaa.
       [:div {:class (when vahvistettu? "osio-vahvistettu")}
        [laskutukseen-perustuen-wrapper laskutukseen-perustuvat-hankinnat-grid nayta-laskutukseen-perustuva-taulukko?]]
       [yleiset/ajax-loader])
     (when (contains? t/toimenpiteen-rahavaraukset-gridissa toimenpide)
       ^{:key "rahavaraukset-otsikko"}
       [:<>
        [:h3 "Toimenpiteen rahavaraukset (lasketaan tavoitehintaan)"]
        [:div {:data-cy "hankintakustannukset-rahavaraukset-suodattimet"}
         [ks-yhteiset/yleis-suodatin (dissoc suodattimet :hankinnat)]]

        (if rahavaraukset-taulukko-valmis?
          ;; FIXME: "Osio-vahvistettu" luokka on väliaikainen hack, jolla osion input kentät saadaan disabloitua kunnes muutosten seuranta ehditään toteuttaa.
          [:div {:class (when vahvistettu? "osio-vahvistettu")}
           [grid/piirra rahavaraukset-grid]]
          [yleiset/ajax-loader])])]))
