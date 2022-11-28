(ns harja.views.urakka.laadunseuranta.sanktiot-lomake
  "Sanktiolomake"
  (:require [reagent.core :refer [atom] :as r]
            [harja.pvm :as pvm]

            [harja.tiedot.urakka.laadunseuranta.sanktiot :as tiedot]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as tiedot-urakka]

            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.ui.liitteet :as liitteet]
            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.domain.yllapitokohde :as yllapitokohde-domain]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]))

(defn sanktio-lomake
  [sivupaneeli-auki?-atom lukutila? voi-muokata?]
  (let [muokattu (atom @tiedot/valittu-sanktio)
        muokataan-vanhaa? (some? (:id @muokattu))
        tallennus-kaynnissa (atom false)
        urakka-id (:id @nav/valittu-urakka)
        urakan-alkupvm (:alkupvm @nav/valittu-urakka)
        ;; TODO: Onko tämä käytännössä sama asia kuin alempi "yllapitokohdeurakka?". Ylläpitourakakka? on mukana lisäksi :valaistus-urakkatyypi
        ;;       Jos yllapitourakka? on OK, niin "yllapitokohdeurakka?" voi poistaa ja korvata viittaukset siihen "yllapitourakka?"-symbolilla.
        yllapitourakka? @tiedot-urakka/yllapitourakka?
        yllapitokohdeurakka? @tiedot-urakka/yllapitokohdeurakka?
        vesivaylaurakka? @tiedot-urakka/vesivaylaurakka?

        laskutuskuukaudet (tiedot/pyorayta-laskutuskuukausi-valinnat)
        ;; Lista ylläpitokohteista ylläpitourakoiden kohteenvalintaa varten
        yllapitokohteet (conj
                          @laadunseuranta/urakan-yllapitokohteet-lomakkeelle
                          {:id nil})
        ;; Valitulle urakalle mahdolliset sanktiolajit. Nämä voivat vaihdella urakan tyypin ja aloitusvuoden mukaan.
        mahdolliset-sanktiolajit @tiedot-urakka/valitun-urakan-sanktiolajit
        ;; Kaikkien sanktiotyyppien tiedot, i.e. [{:koodi 1 nimi "foo" toimenpidekoodi 24 ...} ...]
        ;; Näitä ei ole paljon ja ne muuttuvat harvoin, joten haetaan kaikki tyypit.
        kaikki-sanktiotyypit @tiedot/sanktiotyypit]


    ;; Vaadi tarvittavat tiedot ennen rendausta
    (if (and (seq mahdolliset-sanktiolajit) (seq kaikki-sanktiotyypit)
          (or (not yllapitokohdeurakka?)
            (and yllapitokohdeurakka? yllapitokohteet)))

      [:div
       #_[harja.ui.debug/debug @muokattu]

       [lomake/lomake
        {:otsikko "SANKTION TIEDOT"
         :otsikko-elementti :h4
         :ei-borderia? true
         :vayla-tyyli? true
         :luokka "padding-16 taustavari-taso3"
         :muokkaa! #(reset! tiedot/valittu-sanktio %)
         :validoi-alussa? false
         :voi-muokata? (and voi-muokata? (not lukutila?))
         :tarkkaile-ulkopuolisia-muutoksia? true
         :footer-fn (fn [sanktio]
                      [:span.nappiwrappi.flex-row
                       (when-not lukutila?
                         [napit/palvelinkutsu-nappi
                          (str "Tallenna" (when muokataan-vanhaa? " muutokset"))
                          #(tiedot/tallenna-sanktio (lomake/ilman-lomaketietoja @muokattu) urakka-id)
                          {:luokka "nappi-ensisijainen"
                           :ikoni (ikonit/tallenna)
                           :kun-onnistuu #(do
                                            (reset! tiedot/valittu-sanktio nil)
                                            (reset! sivupaneeli-auki?-atom false))
                           :disabled (or (not voi-muokata?)
                                       (not (lomake/voi-tallentaa? sanktio)))}])
                       (when (and voi-muokata? (:id @muokattu) (not lukutila?))
                         [:button.nappi-kielteinen.oikealle
                          {:class (when @tallennus-kaynnissa "disabled")
                           :on-click
                           (fn [e]
                             (.preventDefault e)
                             (varmista-kayttajalta/varmista-kayttajalta
                               {:otsikko "Sanktion poistaminen"
                                :sisalto "Haluatko varmasti poistaa sanktion? Toimintoa ei voi perua."
                                :modal-luokka "varmistus-modal"
                                :hyvaksy "Poista"
                                :toiminto-fn (fn []
                                               (tiedot/poista-suorasanktio
                                                 (:id @muokattu)
                                                 urakka-id
                                                 #(reset! sivupaneeli-auki?-atom false)))}))}
                          (ikonit/livicon-trash) " Poista"])
                       [napit/peruuta (if lukutila?
                                        "Sulje"
                                        "Peruuta")
                        #(do
                           (reset! sivupaneeli-auki?-atom false)
                           (reset! tiedot/valittu-sanktio nil))]])}
        [(when-not vesivaylaurakka? ;; Vesiväylässä lajeina on vain sakko
           {:otsikko "Sanktion laji" :tyyppi :valinta :pakollinen? true
            ::lomake/col-luokka "col-xs-12"
            :uusi-rivi? true :nimi :laji
            :hae (comp keyword :laji)
            :aseta (fn [rivi arvo]
                     (let [;; Ota vanha tyyppi talteen, mikäli se on asetettu
                           vanha-tyyppi (:tyyppi rivi)
                           rivi (-> rivi
                                  (assoc :laji arvo)
                                  (dissoc :tyyppi)
                                  (assoc :tyyppi nil))
                           s-tyypit (sanktio-domain/sanktiolaji->sanktiotyypit
                                      arvo kaikki-sanktiotyypit urakan-alkupvm)
                           rivi (cond
                                  ;; Ei saa resetoida toimenpideinsanssia nilliksi jos niitä on vain yksi
                                  ;; Koska alasvetovalinat ei lähetä uudesta valinnasta enää eventtiä
                                  (and
                                    (and (= 1 (count s-tyypit)) (first s-tyypit))
                                    (not= (count @tiedot-urakka/urakan-toimenpideinstanssit) 1))
                                  (assoc rivi
                                    :tyyppi (first s-tyypit)
                                    :toimenpideinstanssi
                                    (when (:toimenpidekoodi (first s-tyypit))
                                      (:tpi_id (tiedot-urakka/urakan-toimenpideinstanssi-toimenpidekoodille (:toimenpidekoodi (first s-tyypit))))))
                                  ;; Jos vanha tyyppi, löytyy sanktiolajin tyyppilistasta
                                  (and (> (count s-tyypit) 1)
                                    (some #(= vanha-tyyppi %) s-tyypit))
                                  (assoc rivi :tyyppi vanha-tyyppi
                                              :toimenpideinstanssi (:toimenpidekoodi vanha-tyyppi))
                                  ;; Muussa tapauksessa, ei tehdä muutoksia
                                  :else rivi)]
                       (if-not (sanktio-domain/muu-kuin-muistutus? rivi)
                         (assoc rivi :summa nil :toimenpideinstanssi nil :indeksi nil)
                         rivi)))
            :valinnat (vec mahdolliset-sanktiolajit)
            :valinta-nayta #(or (sanktio-domain/laji->teksti %) "- valitse laji -")
            :validoi [[:ei-tyhja "Valitse laji"]]})
         (when-not (or yllapitourakka? vesivaylaurakka?)
           (if (not lukutila?)
             {:otsikko "Tyyppi" :tyyppi :valinta
              :pakollinen? true
              ::lomake/col-luokka "col-xs-12"
              :nimi :tyyppi
              :aseta (fn [sanktio {tpk :toimenpidekoodi :as tyyppi}]
                       (assoc sanktio
                         :tyyppi tyyppi
                         :toimenpideinstanssi
                         (when tpk
                           (:tpi_id (tiedot-urakka/urakan-toimenpideinstanssi-toimenpidekoodille tpk)))))
              :valinta-arvo identity
              :aseta-vaikka-sama? true
              :valinnat (vec (sanktio-domain/sanktiolaji->sanktiotyypit
                               (:laji @muokattu) kaikki-sanktiotyypit urakan-alkupvm))
              :valinta-nayta (fn [arvo]
                               (if (or (nil? arvo) (nil? (:nimi arvo))) "Valitse sanktiotyyppi" (:nimi arvo)))
              :validoi [[:ei-tyhja "Valitse sanktiotyyppi"]]}

             ;; Näytetään lukutilassa valintakomponentin read-only -tilan sijasta tekstimuotoinen komponentti.
             ;; Vanhat poistetut sanktiotyypit eivät tule valintakomponenttiin vaihtoehdoiksi vanhoissa kirjauksissa,
             ;; joten näytetään tyyppi pelkkänä tekstinä.
             {:otsikko "Tyyppi" :tyyppi :teksti :nimi :tyyppi
              ::lomake/col-luokka "col-xs-12"
              :hae (comp :nimi :tyyppi)}))

         (when yllapitokohdeurakka?
           {:otsikko "Kohde" :tyyppi :valinta :nimi :yllapitokohde
            :pakollinen? false :muokattava? (constantly voi-muokata?)
            ::lomake/col-luokka "col-xs-12"
            :valinnat yllapitokohteet :jos-tyhja "Ei valittavia kohteita"
            :valinta-nayta (fn [arvo voi-muokata?]
                             (if (:id arvo)
                               (yllapitokohde-domain/yllapitokohde-tekstina
                                 arvo
                                 {:osoite {:tr-numero (:tr-numero arvo)
                                           :tr-alkuosa (:tr-alkuosa arvo)
                                           :tr-alkuetaisyys (:tr-alkuetaisyys arvo)
                                           :tr-loppuosa (:tr-loppuosa arvo)
                                           :tr-loppuetaisyys (:tr-loppuetaisyys arvo)}})
                               (if (and voi-muokata? (not arvo))
                                 "- Valitse kohde -"
                                 (if (and voi-muokata? (nil? (:id arvo)))
                                   "Ei liity kohteeseen"
                                   ""))))})

         (when (and (not yllapitokohdeurakka?) (not vesivaylaurakka?))
           {:otsikko "Tapahtumapaikka/kuvaus" :tyyppi :string :nimi :kohde
            :hae (comp :kohde :laatupoikkeama)
            ::lomake/col-luokka "col-xs-12"
            :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :kohde] arvo))
            :pakollinen? true
            :muokattava? (constantly voi-muokata?)
            :validoi [[:ei-tyhja "Anna sanktion tapahtumapaikka/kuvaus"]]})


         (when yllapitourakka?
           {:otsikko "Puute tai laiminlyönti"
            :nimi :vakiofraasi
            :tyyppi :valinta
            ::lomake/col-luokka "col-xs-12"
            :valinta-arvo first
            :valinta-nayta second
            :valinnat sanktio-domain/+yllapidon-sanktiofraasit+})

         {:otsikko "Perustelu"
          :nimi :perustelu
          :pakollinen? true
          ::lomake/col-luokka "col-xs-12"
          :hae (comp :perustelu :paatos :laatupoikkeama)
          :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :paatos :perustelu] arvo))
          :tyyppi :text :koko [80 :auto]
          :validoi [[:ei-tyhja "Anna perustelu"]]}

         (when (sanktio-domain/muu-kuin-muistutus? @muokattu)
           (if (not lukutila?)
             {:otsikko "Kulun kohdistus"
              :pakollinen? true
              ::lomake/col-luokka "col-xs-12"
              :nimi :toimenpideinstanssi
              :tyyppi :valinta
              :valinta-arvo :tpi_id
              :valinta-nayta #(if % (:tpi_nimi %) " - valitse toimenpide -")
              :valinnat @tiedot-urakka/urakan-toimenpideinstanssit
              :validoi [[:ei-tyhja "Valitse toimenpide, johon sanktio liittyy"]]}

             ;; Näytetään lukutilassa valintakomponentin read-only -tilan sijasta tekstimuotoinen komponentti.
             {:otsikko "Kulun kohdistus" :tyyppi :teksti :nimi :toimenpideinstanssi
              ::lomake/col-luokka "col-xs-12"
              :hae (fn [{:keys [toimenpideinstanssi]}]
                     (some
                       #(when (= (:tpi_id %) toimenpideinstanssi) (:tpi_nimi %))
                       @tiedot-urakka/urakan-toimenpideinstanssit))}))

         (apply lomake/ryhma {:rivi? true}
           (keep identity [(when (sanktio-domain/muu-kuin-muistutus? @muokattu)
                             {:otsikko "Summa" :nimi :summa :tyyppi :euro
                              :vaadi-positiivinen-numero? true
                              ::lomake/col-luokka "col-xs-4"
                              :hae #(when (:summa %) (Math/abs (:summa %)))
                              :pakollinen? true :uusi-rivi? true
                              :validoi [[:ei-tyhja "Anna summa"]
                                        [:rajattu-numero 0 999999999 "Anna arvo väliltä 0 - 999 999 999"]]})

                           (when (sanktio-domain/muu-kuin-muistutus? @muokattu)
                             {:otsikko (str "Indeksi") :nimi :indeksi
                              :tyyppi :valinta
                              ::lomake/col-luokka "col-xs-4"
                              :muokattava? (constantly (not lukutila?))
                              :hae (if (tiedot-urakka/indeksi-kaytossa-sakoissa?) :indeksi (constantly nil))
                              :disabled? (not (tiedot-urakka/indeksi-kaytossa-sakoissa?))
                              :valinnat (if (tiedot-urakka/indeksi-kaytossa-sakoissa?)
                                          [(:indeksi @nav/valittu-urakka) nil]
                                          [nil])
                              :valinta-nayta #(or % "Ei indeksiä")})]))

         (lomake/ryhma {:rivi? true}
           {:otsikko "Havaittu" :nimi :laatupoikkeamaaika
            :pakollinen? true
            ::lomake/col-luokka "col-xs-3"
            :hae (comp :aika :laatupoikkeama)
            :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :aika] arvo))
            :fmt pvm/pvm :tyyppi :pvm
            :validoi [[:ei-tyhja "Valitse päivämäärä"]]}
           ;; TODO:  Selvitä.
           ;;        Käsittelyaikaa pyöritellään myös laskutuskuukauden (eli :perintpvm nimisen input-kentän) kohdalla
           ;;        lomakkeella. Mistä on kyse? Tämä menee laatupoikkeaman käsittelyajaksi.
           ;;        Laskutuskuukaudessa haetaan myös laatupoikkeaman käsittelyajasta kuukausivalinta dropdown-valikkoon!
           {:otsikko "Käsitelty" :nimi :kasittelyaika
            :pakollinen? true
            ::lomake/col-luokka "col-xs-3"
            :hae (comp :kasittelyaika :paatos :laatupoikkeama)
            :aseta (fn [rivi arvo] (cond-> rivi
                                     (nil? (:laskutuskuukausi-komp-tiedot rivi))
                                     (assoc-in [:perintapvm] arvo)


                                     true
                                     (assoc-in [:laatupoikkeama :paatos :kasittelyaika] arvo)))
            :fmt pvm/pvm :tyyppi :pvm
            :validoi [[:ei-tyhja "Valitse päivämäärä"]]}
           (if (and voi-muokata? (not lukutila?))
             {:otsikko "Laskutuskuukausi" :nimi :perintapvm
              :pakollinen? true
              :tyyppi :komponentti
              ::lomake/col-luokka "col-xs-6"
              :huomauta [[:urakan-aikana-ja-hoitokaudella]]
              :komponentti (fn [{:keys [muokkaa-lomaketta data]}]
                             ;; TODO:  Selvitä.
                             ;;        Kirjoitustilassa haetaan laskutuskuukauden päivämääräksi laatupoikkeaman käsittelyaika!
                             ;;        Lomakkeen lukutilassa (alla) haetaan vain perintapvm.
                             ;;        Kummasta päivämäärästä (perintapvm/kasittelyaika) laskutuskuukauden päivämäärä
                             ;;        (eli UI:ssa näkyvä tietyn hoitovuoden kuukauden nimi) kuuluu hakea?
                             (let [kasittelyaika (get-in data [:laatupoikkeama :paatos :kasittelyaika])]
                               [:<>
                                [yleiset/livi-pudotusvalikko
                                 {:data-cy "koontilaskun-kk-dropdown"
                                  :vayla-tyyli? true
                                  :skrollattava? true
                                  :pakollinen? true
                                  :valinta (or (-> data :laskutuskuukausi-komp-tiedot)
                                             (some #(when (and
                                                            (= (pvm/vuosi kasittelyaika)
                                                              (:vuosi %))
                                                            (= (pvm/kuukausi kasittelyaika)
                                                              (:kuukausi %))) %)
                                               laskutuskuukaudet))
                                  :valitse-fn #(muokkaa-lomaketta
                                                 (assoc data
                                                   :laskutuskuukausi-komp-tiedot %
                                                   :perintapvm (:pvm %)))
                                  :format-fn :teksti}
                                 laskutuskuukaudet]
                                ;; Piilotetaan teksti ylläpitourakoilta, koska niillä ei ole laskutusyhteenvetoa
                                (when (not yllapitourakka?)
                                  [:div.small-caption.padding-vertical-4 "Näkyy laskutusyhteenvedolla"])]))}
             ;; TODO:  Selvitä.
             ;;        Lukutilassa haetaan laskutuskuukauden päivämääräksi perintäpvm, kirjoitustilassa laatupoikkeaman käsittelyaika!
             ;;        Kummasta päivämäärästä laskutuskuukauden päivämäärä (eli UI:ssa näkyvä hoitovuoden kuukauden nimi) kuuluu hakea?
             {:otsikko "Laskutuskuukausi"
              :nimi :perintapvm
              :fmt (fn [kk]
                     (some #(when (and
                                    (= (pvm/vuosi kk) (pvm/vuosi (:pvm %)))
                                    (= (pvm/kuukausi kk) (pvm/kuukausi (:pvm %)))) (:teksti %))
                       laskutuskuukaudet))
              :pakollinen? true
              :tyyppi :pvm
              ::lomake/col-luokka "col-xs-6"}))

         {:otsikko "Käsittelytapa" :nimi :kasittelytapa :tyyppi :valinta
          :pakollinen? true
          ::lomake/col-luokka "col-xs-12"
          :hae (comp :kasittelytapa :paatos :laatupoikkeama)
          :aseta #(assoc-in %1 [:laatupoikkeama :paatos :kasittelytapa] %2)
          :valinnat sanktio-domain/kasittelytavat
          :valinta-nayta #(or (sanktio-domain/kasittelytapa->teksti %) "- valitse käsittelytapa -")}
         (when (= :muu (get-in @muokattu [:laatupoikkeama :paatos :kasittelytapa]))
           {:otsikko "Muu käsittelytapa" :nimi :muukasittelytapa :pakollinen? true
            ::lomake/col-luokka "col-xs-12"
            :hae (comp :muukasittelytapa :paatos :laatupoikkeama)
            :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :paatos :muukasittelytapa] arvo))
            :tyyppi :string
            :validoi [[:ei-tyhja "Anna lyhyt kuvaus käsittelytavasta."]]})

         (when (and (not lukutila?)
                 (:suorasanktio @muokattu))
           {:otsikko "Liitteet" :nimi :liitteet :kaariva-luokka "sanktioliite"
            :tyyppi :komponentti
            ::lomake/col-luokka "col-xs-12"
            :komponentti (fn [_]
                           [liitteet/liitteet-ja-lisays urakka-id (get-in @muokattu [:laatupoikkeama :liitteet])
                            {:uusi-liite-atom (r/wrap (:uusi-liite @tiedot/valittu-sanktio)
                                                #(swap! tiedot/valittu-sanktio
                                                   (fn [] (assoc-in @muokattu [:laatupoikkeama :uusi-liite] %))))
                             :uusi-liite-teksti "Lisää liite"
                             :salli-poistaa-lisatty-liite? true
                             :poista-lisatty-liite-fn #(swap! tiedot/valittu-sanktio
                                                         (fn [] (assoc-in @muokattu [:laatupoikkeama :uusi-liite] nil)))
                             :salli-poistaa-tallennettu-liite? true
                             :poista-tallennettu-liite-fn
                             (fn [liite-id]
                               (liitteet/poista-liite-kannasta
                                 {:urakka-id urakka-id
                                  :domain :laatupoikkeama
                                  :domain-id (get-in @tiedot/valittu-sanktio [:laatupoikkeama :id])
                                  :liite-id liite-id
                                  :poistettu-fn (fn []
                                                  (let [liitteet (get-in @muokattu [:laatupoikkeama :liitteet])]
                                                    (swap! tiedot/valittu-sanktio assoc-in [:laatupoikkeama :liitteet]
                                                      (filter (fn [liite]
                                                                (not= (:id liite) liite-id))
                                                        liitteet))))}))}])})

         (when lukutila?
           {:otsikko "Kirjaaja" :nimi :tekijanimi
            :hae (comp :tekijanimi :laatupoikkeama)
            :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :tekijanimi] arvo))
            :tyyppi :string
            ::lomake/col-luokka "col-xs-12"
            :muokattava? (constantly false)})]
        @muokattu]]
      [ajax-loader "Ladataan..."])))
