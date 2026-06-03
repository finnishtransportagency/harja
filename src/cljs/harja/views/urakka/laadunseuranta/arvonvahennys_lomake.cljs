(ns harja.views.urakka.laadunseuranta.arvonvahennys-lomake
  "Arvonvähennyksen lomake"
  (:require [reagent.core :refer [atom] :as r]

            [harja.pvm :as pvm]

            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.urakka.laadunseuranta.sanktiot :as tiedot]
            [harja.tiedot.urakka.laadunseuranta.arvonvahennys-tiedot :as arvonvahennys-tiedot]

            [harja.ui.lomake :as lomake]
            [harja.ui.debug :as debug]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.ui.liitteet :as liitteet]

            [harja.domain.laadunseuranta.sanktio :as sanktio-domain])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- valittavat-kulun-kohdistukset
  "MHU25 urakoilta poistetaan listalta hoidon johto toimenpideinstanssi. Muut saa nähdä kaikki vaihtoehdot."
  [toimenpideinstanssit mhu25?]
  (if mhu25?
    ;; MHU25 urakoilta poistetaan hoidon johto
    (remove
      #(= (:t2_koodi %) "23150")
      toimenpideinstanssit)
    toimenpideinstanssit))

(defn arvonvahennys-lomake
  [e! app sivupaneeli-auki?-atom lukutila? voi-muokata? mhu25?]
  (let [muokattu (atom @tiedot/valittu-sanktio)
        muokataan-vanhaa? (some? (:id @muokattu))
        tallennus-kaynnissa (atom false)
        urakka-id (:id @nav/valittu-urakka)
        tehtavaryhmat (:tehtavaryhmat app)
        ;; Muokataan tehtäväryhmien nimet sopivaksi alasvetovalikolle
        tehtavaryhmat (map #(assoc % :nimi (:tehtavaryhma_nimi %)) tehtavaryhmat)
        mahdolliset-kulun-kohdistukset (valittavat-kulun-kohdistukset @tiedot-urakka/urakan-toimenpideinstanssit mhu25?)
        tehtavat (:tehtavat app)
        yllapitourakka? @tiedot-urakka/yllapitourakka?
        laskutuskuukaudet (tiedot/pyorayta-laskutuskuukausi-valinnat)
        laskutuskuukausi-id (str "laskutuskuukausi-dropdown-" (gensym))
        liitteet-id (str "liitteet-element-id-" (gensym))]

    [:div
     [debug/debug @muokattu]
     [lomake/lomake
      {:otsikko "SANKTION TIEDOT"
       :otsikko-elementti :h3
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
                        (fn []
                          (tiedot/tallenna-sanktio
                            (lomake/ilman-lomaketietoja @muokattu)
                            urakka-id
                            #(reset! sivupaneeli-auki?-atom false)))
                        {:luokka "nappi-ensisijainen"
                         :ikoni (ikonit/tallenna)
                         :disabled (or (not voi-muokata?)
                                     (not (lomake/voi-tallentaa? sanktio)))}])
                     (when (and voi-muokata? (:id @muokattu) (not lukutila?))
                       [:button.nappi-kielteinen.oikealle
                        {:class (when @tallennus-kaynnissa "disabled")
                         :on-click
                         (fn [e]
                           (.preventDefault e)
                           (varmista-kayttajalta/varmista-kayttajalta
                             {:otsikko "Arvonvähennyksen poistaminen"
                              :sisalto "Haluatko varmasti poistaa arvonvähennyksen? Toimintoa ei voi perua."
                              :modal-luokka "varmistus-modal"
                              :hyvaksy "Poista"
                              :toiminto-fn (fn []
                                             (tiedot/poista-suorasanktio
                                               (:id @muokattu)
                                               urakka-id
                                               #(reset! sivupaneeli-auki?-atom false)))}))}
                        (ikonit/livicon-trash) " Poista"])
                     [napit/peruuta (if lukutila? "Sulje" "Peruuta")
                      #(do
                         (reset! sivupaneeli-auki?-atom false)
                         (reset! tiedot/valittu-sanktio nil))]])}
      [;; Tapahtumapaikka/kuvaus
       {:otsikko "Tapahtumapaikka/kuvaus"
        :tyyppi :text
        :nimi :kohde
        :hae (comp :kohde :laatupoikkeama)
        :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :kohde] arvo))
        :pakollinen? true
        ::lomake/col-luokka "col-xs-12"
        :validoi [[:ei-tyhja "Anna tapahtumapaikka/kuvaus"]]}

       ;; Perustelu
       {:otsikko "Perustelu"
        :nimi :perustelu
        :pakollinen? true
        ::lomake/col-luokka "col-xs-12"
        :hae (comp :perustelu :paatos :laatupoikkeama)
        :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :paatos :perustelu] arvo))
        :tyyppi :text :koko [80 :auto]
        :validoi [[:ei-tyhja "Anna perustelu"]]}

       ;; Tavoitehinta? radio-group

       (if lukutila?
         {:otsikko "Vaikuttaa tavoitehintaan"
          :nimi :vaikuttaatavoitehintaan
          :nayta-rivina? true
          ::lomake/col-luokka "col-xs-12"
          :tyyppi :teksti
          :hae (fn [rivi]
                 (if (= :true (:vaikuttaatavoitehintaan rivi))
                   "Vaikuttaa tavoitehintaan"
                   "Ei vaikuta tavoitehintaan"))}
         {:otsikko ""
          :nimi :vaikuttaatavoitehintaan
          :tyyppi :radio-group
          :pakollinen? true
          :piilota-label? true
          :nayta-rivina? true
          :vayla-tyyli? true
          ::lomake/col-luokka "col-xs-12"
          :vaihtoehdot [:true :false]
          :vaihtoehto-nayta {:true "Vaikuttaa tavoitehintaan"
                             :false "Ei vaikuta tavoitehintaan"}
          :aseta (fn [rivi arvo] (assoc rivi :vaikuttaatavoitehintaan arvo))})

       ;; Tavoitehinnan alennus
       (when (= (:vaikuttaatavoitehintaan @muokattu) :true)
         {:otsikko "Tavoitehinnan alennus"
          :nimi :tavoitehinnanalennus
          :tyyppi :euro
          :pakollinen? true
          :aseta (fn [rivi arvo]
                   (assoc rivi :tavoitehinnanalennus
                     (when arvo (- (Math/abs arvo)))))
          :validoi [[:ei-tyhja "Anna tavoitehinnan alennus"]
                    [:rajattu-numero -9999999 0 "Anna arvo väliltä 0 - -9 999 999"]]})

       ;; Vähennyksen määrä
       {:otsikko "Vähennyksen määrä"
        :nimi :summa
        :tyyppi :euro
        :pakollinen? true
        :uusi-rivi? true
        :aseta (fn [rivi arvo]
                 (assoc rivi :summa
                   (when arvo (- (Math/abs arvo)))))
        :validoi [[:ei-tyhja "Anna vähennyksen määrä"]
                  [:rajattu-numero -9999999 0 "Anna arvo väliltä 0 - -9 999 999"]]}

       ;; Kulun kohdistus
       ;; MHU25 urakoille näytetään vain, jos tavoitehinta? false
       ;; Muille kuin mhu25 urakoille näytetään aina
       (when (or
               (and mhu25? (= (:vaikuttaatavoitehintaan @muokattu) :false))
               (not mhu25?))
         {:otsikko "Kulun kohdistus"
          :pakollinen? true
          :uusi-rivi? true
          :disabled? (when (empty? @tiedot-urakka/urakan-toimenpideinstanssit) true)
          ::lomake/col-luokka "col-xs-8"
          :nimi :toimenpideinstanssi
          :tyyppi :valinta
          :valinta-arvo :tpi_id
          :valinta-nayta #(if % (:tpi_nimi %) " - valitse toimenpide -")
          :valinnat mahdolliset-kulun-kohdistukset
          :validoi [[:ei-tyhja "Valitse toimenpide, johon sanktio liittyy"]]})

       ;; Tehtäväryhmä - Näytetään vain mhu25 urakoille ja vain jos tavoitehinta? true
       (when (and mhu25? (= (:vaikuttaatavoitehintaan @muokattu) :true))
         {:otsikko "Tehtäväryhmä"
          :nimi :tehtavaryhma
          :tyyppi :valinta
          :valinnat tehtavaryhmat
          :valinta-nayta #(if % (:nimi %) " - valitse tehtäväryhmä -")
          :uusi-rivi? true
          :aseta (fn [rivi valittu]
                   ;; Hae valitun tehtäväryhmän tehtävät
                   (when (:tehtavaryhma valittu)
                     (e! (arvonvahennys-tiedot/->HaeTehtavaryhmanTehtavat (:tehtavaryhma valittu))))
                   (-> rivi
                     (assoc :tehtavaryhma valittu)
                     (assoc :tehtava nil)))
          :pakollinen? true
          ::lomake/col-luokka "col-xs-6"
          :validoi [[:ei-tyhja "Valitse tehtäväryhmä"]]})


       ;; Tehtävä - Näytetään vain mhu25 urakoille ja vain jos tavoitehinta? true
       (when (and mhu25? (= (:vaikuttaatavoitehintaan @muokattu) :true))
         {:otsikko "Tehtävä"
          :nimi :tehtava
          :tyyppi :valinta
          :uusi-rivi? true
          :disabled? (empty? tehtavat)
          :valinnat (if (empty? tehtavat) [{:id nil :nimi "Tehtäväryhmällä ei ole tehtäviä"}] tehtavat)
          :valinta-nayta #(cond
                            (and % (:id %) (:nimi %)) (:nimi %) ;; Normaali tilanne, jossa tehtäväryhmällä on tehtäviä ja voidaan valita jokin niistä
                            (and % (nil? (:id %)) (:nimi %)) "Tehtäväryhmällä ei ole tehtäviä" ;; Kun Tehtäväryhmällä ei ole tehtäviä
                            (and % (nil? (:id %)) (nil? (:nimi %))) "-" ;; Kun on tallennettu arvonvähennys, jolla ei ole tehtävää.
                            :else " - valitse tehtävä -") ;; Kehotetaan valitsemaan tehtävä
          :pakollinen? (seq tehtavat)
          ::lomake/col-luokka "col-xs-6"})

       ;; Havaittu ja Määrätty päivämäärät
       (lomake/ryhma {:rivi? true}
         {:otsikko "Havaittu"
          :nimi :laatupoikkeamaaika
          :pakollinen? true
          ::lomake/col-luokka "col-xs-3"
          :hae (comp :aika :laatupoikkeama)
          :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :aika] arvo))
          :fmt pvm/pvm-opt :tyyppi :pvm
          :validoi [[:ei-tyhja "Valitse päivämäärä"]]}

         {:otsikko (if mhu25? "Määrätty" "Käsitelty")
          :nimi :kasittelyaika
          :pakollinen? true
          ::lomake/col-luokka "col-xs-3"
          :hae (comp :kasittelyaika :paatos :laatupoikkeama)
          :aseta (fn [rivi arvo] (cond-> rivi
                                   ;; Jos perintäpvm ei ole vielä valittu, asetetaan esivalinta
                                   (nil? (:laskutuskuukausi-komp-tiedot rivi))
                                   (assoc-in [:perintapvm] arvo)

                                   true
                                   (assoc-in [:laatupoikkeama :paatos :kasittelyaika] arvo)))
          :fmt pvm/pvm-opt :tyyppi :pvm
          :validoi [[:ei-tyhja "Valitse päivämäärä"]]}

         ;; Laskutuskuukausi näytetään mhu24 urakoille
         (when (and (not mhu25?) voi-muokata? (not lukutila?))
           {:otsikko "Laskutuskuukausi"
            :label-for-id laskutuskuukausi-id
            :nimi :perintapvm
            :pakollinen? true
            :tyyppi :komponentti
            ::lomake/col-luokka "col-xs-6"
            :huomauta [[:urakan-aikana-ja-hoitokaudella]]
            :komponentti (fn [{:keys [muokkaa-lomaketta data]}]
                           (let [perintapvm (get-in data [:perintapvm])]
                             [:<>
                              [yleiset/livi-pudotusvalikko
                               {:data-cy "koontilaskun-kk-dropdown"
                                :vayla-tyyli? true
                                :skrollattava? true
                                :elementin-id laskutuskuukausi-id
                                :pakollinen? true
                                :valinta (or
                                           ;; Näytetään valintana joko valittua laskutuskuukautta, tai
                                           (-> data :laskutuskuukausi-komp-tiedot)
                                           ;; jos käyttäjä ei tehnyt/muuttanut valintaa, käytetään tietokannasta haettua arvoa
                                           (when perintapvm
                                             (some #(when (and
                                                            (= (pvm/vuosi perintapvm)
                                                              (:vuosi %))
                                                            (= (pvm/kuukausi perintapvm)
                                                              (:kuukausi %))) %)
                                               laskutuskuukaudet)))
                                :valitse-fn #(muokkaa-lomaketta
                                               (assoc data
                                                 ;; Tallennetaan tieto koko laskutuskuukauden valinnasta erikseen, jotta
                                                 ;;  sitä voi hyödyntää muualla lomakkeessa.
                                                 :laskutuskuukausi-komp-tiedot %
                                                 ;; Varsinainen perintapvm poimitaan valitun laskutuskuukauden pvm-kentästä.
                                                 :perintapvm (:pvm %)))
                                :format-fn :teksti}
                               laskutuskuukaudet]
                              ;; Piilotetaan teksti ylläpitourakoilta, koska niillä ei ole laskutusyhteenvetoa
                              (when (not yllapitourakka?)
                                [:div.small-caption.padding-vertical-4 "Näkyy laskutusyhteenvedolla"])]))})

           (when (and (not mhu25?) lukutila?)
           {:otsikko "Laskutuskuukausi"
            :nimi :perintapvm
            :fmt (fn [pvm]
                   ;; Lukutilassa haetaan näytettävä laskutuskuukausi suoraan lomakkeen avaimesta
                   (when pvm
                     (some #(when (and
                                    (= (pvm/vuosi pvm) (pvm/vuosi (:pvm %)))
                                    (= (pvm/kuukausi pvm) (pvm/kuukausi (:pvm %)))) (:teksti %))
                       laskutuskuukaudet)))
            :pakollinen? true
            :tyyppi :pvm
            ::lomake/col-luokka "col-xs-6"}))

       ;; Määräystapa radio-group näytetään vain mhu25 urakoille
       (if mhu25?
         {:otsikko "Määräystapa"
          :nimi :maaraystapa
          :tyyppi :radio-group
          :pakollinen? true
          :nayta-rivina? true
          ::lomake/col-luokka "col-xs-12"
          :vaihtoehdot [:tyomaakokous :valikatselmus]
          :vaihtoehto-nayta {:tyomaakokous "Työmaakokous"
                             :valikatselmus "Välikatselmus"}}
         ;; MHU24 urakoiden määräystapa on alasvetovalikko
         {:otsikko "Määräystapa" :nimi :maaraystapa :tyyppi :valinta
          :pakollinen? true
          ::lomake/col-luokka "col-xs-6"
          :valinnat sanktio-domain/kasittelytavat
          :valinta-nayta #(or (sanktio-domain/kasittelytapa->teksti %) "- valitse käsittelytapa -")})
       ;; Muu käsittelytapa (eli määräystapa) voi olla vain muilla kuin mhu25 urakoilla.
       (when (and (not mhu25?) (= :muu (:maaraystapa @muokattu)))
         {:otsikko "Muu käsittelytapa" :nimi :muukasittelytapa :pakollinen? true
          ::lomake/col-luokka "col-xs-12"
          :hae (comp :muukasittelytapa :paatos :laatupoikkeama)
          :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :paatos :muukasittelytapa] arvo))
          :tyyppi :string
          :validoi [[:ei-tyhja "Anna lyhyt kuvaus käsittelytavasta."]]})

       ;; Käsittelytapa (aina välikatselmus) - Tämä näytetään vain mhu25 urakoille
       (when mhu25?
         {:otsikko "Käsittelytapa" :nimi :kasittelytapa :tyyppi :valinta
          :pakollinen? true
          ::lomake/col-luokka "col-xs-12"
          :hae (comp :kasittelytapa :paatos :laatupoikkeama)
          :aseta #(assoc-in %1 [:laatupoikkeama :paatos :kasittelytapa] %2)
          :valinnat [:valikatselmus]
          :valinta-nayta #(or (sanktio-domain/kasittelytapa->teksti %) "- valitse käsittelytapa -")})

       ;; Liitteet
       (when (and (not lukutila?)
               (:suorasanktio @muokattu))
         {:otsikko "Liitteet" :nimi :liitteet :kaariva-luokka "sanktioliite"
          :tyyppi :komponentti
          :label-for-id liitteet-id
          ::lomake/col-luokka "col-xs-12"
          :komponentti (fn [_]
                         [liitteet/liitteet-ja-lisays urakka-id (get-in @muokattu [:laatupoikkeama :liitteet])
                          {:uusi-liite-atom (r/wrap (:uusi-liite @tiedot/valittu-sanktio)
                                              #(swap! tiedot/valittu-sanktio
                                                 (fn [_] (assoc-in @muokattu [:laatupoikkeama :uusi-liite] %))))
                           :uusi-liite-teksti "Lisää liite"
                           :elementin-id liitteet-id
                           :nayta-koko? true
                           :salli-poistaa-lisatty-liite? true
                           :poista-lisatty-liite-fn #(swap! tiedot/valittu-sanktio
                                                       (fn [_] (assoc-in @muokattu [:laatupoikkeama :uusi-liite] nil)))
                           :salli-poistaa-tallennettu-liite? true
                           :poista-tallennettu-liite-fn
                           (fn [liite-id]
                             (liitteet/poista-liite-kannasta
                               {:urakka-id urakka-id
                                :domain :laatupoikkeama
                                :domain-id (get-in @tiedot/valittu-sanktio [:laatupoikkeama :id])
                                :liite-id liite-id
                                :poistettu-fn (fn []
                                                (let [liitteet-nyt (get-in @muokattu [:laatupoikkeama :liitteet])]
                                                  (swap! tiedot/valittu-sanktio assoc-in [:laatupoikkeama :liitteet]
                                                    (filter (fn [liite]
                                                              (not= (:id liite) liite-id))
                                                      liitteet-nyt))))}))}])})
       (when lukutila?
         {:otsikko "Liitteet" :nimi :liitteet :kaariva-luokka "sanktioliite"
          :tyyppi :komponentti
          ::lomake/col-luokka "col-xs-12"
          :komponentti (fn [_]
                         [:div
                          (if (and (get-in @muokattu [:laatupoikkeama :liitteet])
                                (not (empty? (get-in @muokattu [:laatupoikkeama :liitteet]))))
                            (doall
                              (for [l (get-in @muokattu [:laatupoikkeama :liitteet])]
                                ^{:key l}
                                [liitteet/liitetiedosto l {:salli-poisto? false
                                                           :nayta-koko? true}]))
                            "Ei liitettä")])})]
      @muokattu]]))




