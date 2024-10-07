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
                                       (t/summaa-lehtivektorit (get-in hankintakustannukset-yhteenvedot [:indeksikorjatut-summat :suunnitellut-hankinnat]))
                                       (t/summaa-lehtivektorit (get-in hankintakustannukset-yhteenvedot [:indeksikorjatut-summat :laskutukseen-perustuvat-hankinnat]))))
        yhteenveto (mapv (fn [summa]
                           {:summa summa})
                     (mapv +
                       (t/summaa-lehtivektorit (get-in hankintakustannukset-yhteenvedot [:summat :suunnitellut-hankinnat]))
                       (t/summaa-lehtivektorit (get-in hankintakustannukset-yhteenvedot [:summat :laskutukseen-perustuvat-hankinnat]))))]
    [:<>
     ;[:h2#hankintakustannukset-osio "Hankintakustannukset"]
     [:h2#hankintakustannukset-osio "Suunnitellut hankinnat"]
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
       [yleiset/ajax-loader])]))
