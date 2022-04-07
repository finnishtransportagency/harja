(ns harja.views.urakka.suunnittelu.kustannussuunnitelma.grid-apurit
  (:require [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma :as t]
            [harja.ui.taulukko.grid-pohjat :as g-pohjat]
            [harja.ui.taulukko.grid :as grid]
            [harja.ui.taulukko.impl.solu :as solu]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.yhteiset :as ks-yhteiset :refer [e!]]))



;; -- Maarataulukko grid apufunktio--
(defn- maarataulukon-pohja [taulukon-id
                            indeksikorjaus?
                            polun-osa
                            root-asetus!
                            root-asetukset
                            kuukausitasolla?-polku
                            on-change
                            on-blur
                            nappia-painettu!
                            on-change-kk
                            on-blur-kk]
  {:pre [(string? taulukon-id)
         (every? fn? #{nappia-painettu! on-change on-blur on-change-kk on-blur-kk})]}
  (let [yhteenveto-grid-rajapinta-asetukset {:rajapinta :yhteenveto
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
        g (g-pohjat/uusi-taulukko
            {:header (cond-> [{:tyyppi :teksti
                               :leveys 3
                               :luokat #{"table-default" "table-default-header" "lihavoitu"}}
                              {:tyyppi :teksti
                               :leveys 2
                               :luokat #{"table-default" "table-default-header" "lihavoitu"}}
                              {:tyyppi :teksti
                               :leveys 2
                               :luokat #{"table-default" "table-default-header" "lihavoitu"}}]
                       indeksikorjaus? (conj {:tyyppi :teksti
                                              :leveys 1
                                              :luokat #{"table-default" "table-default-header" "harmaa-teksti" "lihavoitu"}}))
             :body [{:tyyppi :taulukko
                     :nimi ::t/data-rivi
                     :osat [{:tyyppi :rivi
                             :nimi ::t/data-yhteenveto
                             :osat
                             (cond->
                               [{:tyyppi :laajenna
                                 :aukaise-fn (fn [this auki?]
                                               (if auki?
                                                 (ks-yhteiset/rivi-kuukausifiltterilla! this
                                                   true
                                                   :kuukausitasolla?
                                                   kuukausitasolla?-polku
                                                   [:. ::t/yhteenveto] yhteenveto-grid-rajapinta-asetukset
                                                   [:. ::t/valinta] {:rajapinta :kuukausitasolla?
                                                                     :solun-polun-pituus 1
                                                                     :datan-kasittely (fn [kuukausitasolla?]
                                                                                        [kuukausitasolla? nil nil nil])})
                                                 (do
                                                   (ks-yhteiset/rivi-ilman-kuukausifiltteria! this
                                                     [:.. ::t/data-yhteenveto] yhteenveto-grid-rajapinta-asetukset)
                                                   (e! (tuck-apurit/->MuutaTila [:gridit polun-osa :kuukausitasolla?] false))))
                                               (t/laajenna-solua-klikattu this auki? taulukon-id
                                                 [::g-pohjat/data] {:sulkemis-polku [:.. :.. :.. 1]}))
                                 :auki-alussa? false
                                 :luokat #{"table-default" "lihavoitu"}}
                                {:tyyppi :syote
                                 :luokat #{"input-default"}
                                 :toiminnot {:on-change (fn [arvo]
                                                          (when ks-yhteiset/esta-blur_
                                                            (set! ks-yhteiset/esta-blur_ false))
                                                          (on-change arvo))
                                             :on-focus (fn [event]
                                                         (let [arvo (.. event -target -value)]
                                                           (when (= arvo t/vaihtelua-teksti)
                                                             (set! ks-yhteiset/esta-blur_ true)
                                                             (set! (.. event -target -value) nil))))
                                             :on-blur on-blur
                                             :on-key-down (fn [event]
                                                            (when (= "Enter" (.. event -key))
                                                              (.. event -target blur)))}
                                 :kayttaytymiset {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                              {:eventin-arvo {:f ks-yhteiset/poista-tyhjat}}]
                                                  :on-blur [:numero-pisteella
                                                            :positiivinen-numero
                                                            {:eventin-arvo {:f ks-yhteiset/poista-tyhjat}}
                                                            {:oma {:f ks-yhteiset/esta-blur-ja-lisaa-vaihtelua-teksti}}]}
                                 :parametrit {:size 2}
                                 :fmt ks-yhteiset/summa-formatointi
                                 :fmt-aktiivinen ks-yhteiset/summa-formatointi-aktiivinen}
                                {:tyyppi :teksti
                                 :luokat #{"table-default"}
                                 :fmt ks-yhteiset/summa-formatointi}]
                               indeksikorjaus? (conj {:tyyppi :teksti
                                                      :luokat #{"table-default" "harmaa-teksti"}
                                                      :fmt ks-yhteiset/summa-formatointi}))}
                            {:tyyppi :taulukko
                             :nimi ::t/data-sisalto
                             :luokat #{"piilotettu"}
                             :osat
                             (vec (repeatedly 12
                                    (fn []
                                      {:tyyppi :rivi
                                       :osat (cond->
                                               [{:tyyppi :teksti
                                                 :luokat #{"table-default"}
                                                 :fmt ks-yhteiset/aika-tekstilla-fmt}
                                                {:tyyppi :syote-tayta-alas
                                                 :nappi-nakyvilla? false
                                                 :nappia-painettu! nappia-painettu!
                                                 :toiminnot {:on-change on-change-kk
                                                             :on-focus (fn [_]
                                                                         (grid/paivita-osa! solu/*this*
                                                                           (fn [solu]
                                                                             (assoc solu :nappi-nakyvilla? true))))
                                                             :on-blur on-blur-kk
                                                             :on-key-down (fn [event]
                                                                            (when (= "Enter" (.. event -key))
                                                                              (.. event -target blur)))}
                                                 :kayttaytymiset {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                              {:eventin-arvo {:f ks-yhteiset/poista-tyhjat}}]
                                                                  :on-blur [:positiivinen-numero {:eventin-arvo {:f ks-yhteiset/poista-tyhjat}}]}
                                                 :parametrit {:size 2}
                                                 :luokat #{"input-default"}
                                                 :fmt ks-yhteiset/summa-formatointi
                                                 :fmt-aktiivinen ks-yhteiset/summa-formatointi-aktiivinen}
                                                {:tyyppi :teksti
                                                 :luokat #{"table-default"}
                                                 :fmt ks-yhteiset/summa-formatointi}]
                                               indeksikorjaus? (conj {:tyyppi :teksti
                                                                      :luokat #{"table-default" "harmaa-teksti"}
                                                                      :fmt ks-yhteiset/summa-formatointi}))})))}]}]
             :footer (cond-> [{:tyyppi :teksti
                               :luokat #{"table-default" "table-default-sum"}}
                              {:tyyppi :teksti
                               :luokat #{"table-default" "table-default-sum"}}
                              {:tyyppi :teksti
                               :luokat #{"table-default" "table-default-sum"}
                               :fmt ks-yhteiset/yhteenveto-format}]
                       indeksikorjaus? (conj {:tyyppi :teksti
                                              :luokat #{"table-default" "table-default-sum" "harmaa-teksti"}
                                              :fmt ks-yhteiset/yhteenveto-format}))
             :taulukon-id taulukon-id
             :root-asetus! root-asetus!
             :root-asetukset root-asetukset
             :root-luokat (when (t/alin-taulukko? taulukon-id)
                            #{"viimeinen-taulukko"})})]
    g))

(defn maarataulukko-grid
  "Luo määrataulukko-tyyppisen gridin ja tallentaa sen tilan annetulla nimellä :gridit-polkuun, kuten muutkin gridit."
  ([nimi yhteenvedot-polku] (maarataulukko-grid nimi yhteenvedot-polku {}))
  ([nimi yhteenvedot-polku {:keys [paivita-kattohinta? indeksikorjaus? tallennus-onnistui-event tallennus-epaonnistui-event]
                            :or {paivita-kattohinta? true indeksikorjaus? true}}]
   (let [toiminto-fn! (fn -t-fn!
                        ([polun-osa solu]
                         (println "toiminto-fn maara" polun-osa)
                         (let [vanhempiosa (grid/osa-polusta solu [:.. :..])
                               tallennettavien-arvojen-osat (if (= ::t/data-rivi (grid/hae-osa vanhempiosa :nimi))
                                                              (grid/hae-grid (grid/osa-polusta vanhempiosa [1]) :lapset)
                                                              (grid/hae-grid (grid/osa-polusta vanhempiosa [:.. 1]) :lapset))]
                           (e! (t/->TallennaKustannusarvoitu polun-osa (mapv #(grid/solun-asia (get (grid/hae-grid % :lapset) 1)
                                                                                :tunniste-rajapinnan-dataan)
                                                                         tallennettavien-arvojen-osat)
                                 {:onnistui-event tallennus-onnistui-event
                                  :epaonnistui-event tallennus-epaonnistui-event})))
                         (when paivita-kattohinta?
                           (e! (t/->TallennaJaPaivitaTavoiteSekaKattohinta)))))
         toiminto-nappi-fn! (fn -t-nappi-fn!
                              ([polun-osa rivit-alla]
                               (println "toiminto-fn maara nappi" polun-osa)
                               (e! (t/->TallennaKustannusarvoitu polun-osa
                                     (vec (keep (fn [rivi]
                                                  (let [maara-solu (grid/get-in-grid rivi [1])
                                                        piilotettu? (grid/piilotettu? rivi)]
                                                    (when-not piilotettu?
                                                      (grid/solun-asia maara-solu :tunniste-rajapinnan-dataan))))
                                            rivit-alla))
                                     {:onnistui-event tallennus-onnistui-event
                                      :epaonnistui-event tallennus-epaonnistui-event}))
                               (when paivita-kattohinta?
                                 (e! (t/->TallennaJaPaivitaTavoiteSekaKattohinta)))))
         toiminto-kk-fn! (fn -t-kk-fn!
                           ([polun-osa solu]
                            (println "toiminto-fn maara kk" polun-osa)
                            (e! (t/->TallennaKustannusarvoitu polun-osa
                                  [(grid/solun-asia solu :tunniste-rajapinnan-dataan)]
                                  {:onnistui-event tallennus-onnistui-event
                                   :epaonnistui-event tallennus-epaonnistui-event}))
                            (when paivita-kattohinta?
                              (e! (t/->TallennaJaPaivitaTavoiteSekaKattohinta)))))
         polun-osa (keyword nimi)
         disablerivit-avain (keyword (str nimi "-disablerivit"))
         aseta-yhteenveto-avain (keyword (str "aseta-" nimi "-yhteenveto!"))
         aseta-avain (keyword (str "aseta-" nimi "!"))
         jarjestysvektori (with-meta (if indeksikorjaus?
                                       [:nimi :maara :yhteensa :indeksikorjattu]
                                       [:nimi :maara :yhteensa])
                            {:nimi :mapit})
         jarjestysvektori-body (with-meta (if indeksikorjaus?
                                            [:aika :maara :yhteensa :indeksikorjattu]
                                            [:aika :maara :yhteensa])
                                 {:nimi :mapit})
         yhteenveto-grid-rajapinta-asetukset {:rajapinta :yhteenveto
                                              :solun-polun-pituus 1
                                              :jarjestys [jarjestysvektori]
                                              :datan-kasittely (fn [yhteenveto]
                                                                 (mapv (fn [[_ v]]
                                                                         v)
                                                                   yhteenveto))
                                              :tunnisteen-kasittely (fn [osat _]
                                                                      (mapv (fn [osa]
                                                                              (when (instance? solu/Syote osa)
                                                                                :maara))
                                                                        (grid/hae-grid osat :lapset)))}
         g (maarataulukon-pohja
             ;; Lisää -taulukko pääte, jotta HTML ID-attribuutti sekoitu helposti muihin ID:siin kustannussuunnitelma sivulla.
             (str (t/hallinnollisten-idt polun-osa) "-taulukko")
             indeksikorjaus?
             polun-osa
             (fn [g]
               (swap! tila/suunnittelu-kustannussuunnitelma
                 (fn [tila]
                   (assoc-in tila [:gridit polun-osa :grid] g))))
             {:haku (fn [] (get-in @tila/suunnittelu-kustannussuunnitelma [:gridit polun-osa :grid]))
              :paivita! (fn [f]
                          (swap! tila/suunnittelu-kustannussuunnitelma
                            (fn [tila]
                              (update-in tila [:gridit polun-osa :grid] f))))}
             [:gridit polun-osa :kuukausitasolla?]
             (fn [arvo]
               (when arvo
                 (t/paivita-solun-arvo {:paivitettava-asia aseta-yhteenveto-avain
                                        :arvo arvo
                                        :solu solu/*this*
                                        :ajettavat-jarjestykset #{:mapit}
                                        :triggeroi-seuranta? false}
                   false)))
             (fn [arvo]
               (t/paivita-solun-arvo {:paivitettava-asia aseta-yhteenveto-avain
                                      :arvo arvo
                                      :solu solu/*this*
                                      :ajettavat-jarjestykset #{:mapit}
                                      :triggeroi-seuranta? true}
                 true)
               (toiminto-fn! polun-osa solu/*this*))
             (fn [rivit-alla arvo]
               (when (not (empty? rivit-alla))
                 (doseq [rivi rivit-alla
                         :let [maara-solu (grid/get-in-grid rivi [1])]]
                   (t/paivita-solun-arvo {:paivitettava-asia aseta-avain
                                          :arvo arvo
                                          :solu maara-solu
                                          :ajettavat-jarjestykset #{:mapit}
                                          :triggeroi-seuranta? true}
                     true))
                 (toiminto-nappi-fn! polun-osa rivit-alla)))
             (fn [arvo]
               (when arvo
                 (t/paivita-solun-arvo {:paivitettava-asia aseta-avain
                                        :arvo arvo
                                        :solu solu/*this*
                                        :ajettavat-jarjestykset #{:mapit}}
                   false)))
             (fn [arvo]
               (t/paivita-solun-arvo {:paivitettava-asia aseta-avain
                                      :arvo arvo
                                      :solu solu/*this*
                                      :ajettavat-jarjestykset true
                                      :triggeroi-seuranta? true}
                 true)
               (println aseta-avain)
               (toiminto-kk-fn! polun-osa solu/*this*)))
         rajapinta (t/maarataulukon-rajapinta polun-osa aseta-yhteenveto-avain aseta-avain)]

     (grid/rajapinta-grid-yhdistaminen! g
       rajapinta
       (t/maarataulukon-dr indeksikorjaus? rajapinta polun-osa yhteenvedot-polku aseta-avain aseta-yhteenveto-avain)
       {[::g-pohjat/otsikko]
        {:rajapinta :otsikot
         :solun-polun-pituus 1
         :jarjestys [jarjestysvektori]
         :datan-kasittely (fn [otsikot]
                            (mapv (fn [otsikko]
                                    otsikko)
                              (vals otsikot)))}

        [::g-pohjat/data 0 ::t/data-yhteenveto]
        yhteenveto-grid-rajapinta-asetukset

        [::g-pohjat/data 0 ::t/data-sisalto]
        {:rajapinta polun-osa
         :solun-polun-pituus 2
         :jarjestys [{:keyfn :aika
                      :comp (fn [aika-1 aika-2]
                              (pvm/ennen? aika-1 aika-2))}
                     jarjestysvektori-body]
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
                                                                   (when (instance? g-pohjat/SyoteTaytaAlas osa)
                                                                     {:osa :maara
                                                                      :aika (:aika (get data j))
                                                                      :osan-paikka [i j]}))
                                                      (grid/hae-grid rivi :lapset))))
                                     (grid/hae-grid data-sisalto-grid :lapset))))}

        [::g-pohjat/yhteenveto]
        {:rajapinta :yhteensa
         :solun-polun-pituus 1
         :jarjestys [jarjestysvektori]
         :datan-kasittely (fn [yhteensa]
                            (mapv (fn [[_ nimi]]
                                    nimi)
                              yhteensa))}})

     (grid/grid-tapahtumat g
       tila/suunnittelu-kustannussuunnitelma
       {disablerivit-avain {:polut [[:gridit polun-osa :kuukausitasolla?]]
                            :toiminto! (fn [g _ kuukausitasolla?]
                                         (ks-yhteiset/maara-solujen-disable! (grid/get-in-grid g [::g-pohjat/data 0 ::t/data-sisalto])
                                           (not kuukausitasolla?))
                                         (ks-yhteiset/maara-solujen-disable! (if-let [osa (grid/get-in-grid g [::g-pohjat/data 0 0 ::t/yhteenveto])]
                                                                               osa
                                                                               (grid/get-in-grid g [::g-pohjat/data 0 ::t/data-yhteenveto]))
                                           kuukausitasolla?))}}))))
