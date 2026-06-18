(ns harja.views.urakka.laadunseuranta.bonukset-lomake
  "Bonuksien käsittely ja luonti"
  (:require [reagent.core :as r]
            [tuck.core :as tuck]

            [harja.pvm :as pvm]

            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.domain.yllapitokohde :as yllapitokohde-domain]

            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.urakka.urakka :as uu-tiedot]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.tiedot.urakka.laadunseuranta.bonukset :as tiedot]
            [harja.tiedot.urakka.laadunseuranta.sanktiot :as tiedot-sanktiot]

            [harja.ui.yleiset :as yleiset]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]))

(defn- hae-tpi-idlla
  [tpi-id]
  (some
    #(when (= tpi-id (:tpi_id %))
       %)
    @tiedot-urakka/urakan-toimenpideinstanssit))

(defn bonus-lomake*
  "MH-urakoidan ja ylläpitourakoiden yhteinen bonuslomake.
  Huomioitavaa on, että ylläpidon urakoiden bonukset tallennetaankin oikeasti sanktioina, eikä bonuksina.
  Ylläpidon urakoiden bonuslomakkeessa on myös muita pieniä poikkeuksia."
  [sulje-fn lukutila? voi-muokata? e! app]
  (let [{lomakkeen-tiedot :lomake :keys [uusi-liite voi-sulkea? liitteet-haettu?]} app
        urakka-id (:id @nav/valittu-urakka)
        urakan-alkuvuosi (-> nav/valittu-urakka deref :alkupvm pvm/vuosi)
        urakan-tyyppi (:tyyppi @nav/valittu-urakka)
        mhu25? (uu-tiedot/mhu25-urakka? @nav/valittu-urakka)
        laskutuskuukaudet (tiedot-sanktiot/pyorayta-laskutuskuukausi-valinnat)

        ;; Lista ylläpitokohteista ylläpitourakoiden kohteenvalintaa varten
        yllapitokohteet (conj
                          @laadunseuranta/urakan-yllapitokohteet-lomakkeelle
                          {:id nil})]
    (when voi-sulkea? (e! (tiedot/->TyhjennaLomake sulje-fn)))
    (when-not liitteet-haettu? (e! (tiedot/->HaeLiitteet)))
    [lomake/lomake
     {:otsikko "BONUKSEN TIEDOT"
      :otsikko-elementti :h4
      :ei-borderia? true
      :vayla-tyyli? true
      :tarkkaile-ulkopuolisia-muutoksia? true
      :luokka "padding-16 taustavari-taso3"
      :validoi-alussa? false
      :voi-muokata? (and voi-muokata? (not lukutila?))
      :muokkaa! #(e! (tiedot/->PaivitaLomaketta %))
      :footer-fn (fn [bonus]
                   [:<>
                    [:span.nappiwrappi.flex-row
                     [:div.col-xs-8 {:style {:padding-left "0"}}
                      (when-not lukutila?
                        [napit/yleinen-ensisijainen "Tallenna" #(e! (tiedot/->TallennaBonus))
                         {:disabled (not (empty? (::lomake/puuttuvat-pakolliset-kentat bonus)))}])]
                     [:div.col-xs-4 {:style (merge
                                              {:text-align "end" :float "right"}
                                              (when (not lukutila?)
                                                {:display "contents"}))}
                      (when (and (not lukutila?) (:id lomakkeen-tiedot))
                        [napit/kielteinen "Poista" (fn [_]
                                                     (varmista-kayttajalta/varmista-kayttajalta
                                                       {:otsikko "Bonuksen poistaminen"
                                                        :sisalto "Haluatko varmasti poistaa bonuksen? Toimintoa ei voi perua."
                                                        :modal-luokka "varmistus-modal"
                                                        :hyvaksy "Poista"
                                                        :toiminto-fn #(e! (tiedot/->PoistaBonus))}))
                         {:luokka "oikealle"}])
                      [napit/peruuta "Sulje" #(e! (tiedot/->TyhjennaLomake sulje-fn))]]]])}
     [(let [hae-tpin-tiedot (comp hae-tpi-idlla :toimenpideinstanssi)
            tpi (hae-tpin-tiedot lomakkeen-tiedot)]
        {:otsikko "Bonus"
         ;; Laji on bonuksen tyyppi. Tämä on vastaava käsite kuin sanktion laji.
         :nimi :laji
         :tyyppi :valinta
         :pakollinen? true
         ;; Valitse ainoa, jos tyyppejä on vain yksi.
         ;; Esimerkiksi ylläpitourakoiden tapauksessa on saatavilla vain "yllapidon_bonus"
         :valitse-ainoa? true
         ;; Aseta toimenpideinstanssi, jos se voidaan tietää ennalta
         :aseta (fn [rivi arvo]
                  ;; Aseta toimenpideinstanssi, mikäli sitä ei ole asetettu ennakkoon oikein
                  (let [asetettava-tpi (cljs.core/cond (= :teiden-hoito (:tyyppi @nav/valittu-urakka))
                                         (first (filter #(= "23150" (:t2_koodi %)) @tiedot-urakka/urakan-toimenpideinstanssit))

                                         :else
                                         ;; Muuten otetaan vain listan ensimmäinen
                                         (first @tiedot-urakka/urakan-toimenpideinstanssit))]
                    (-> rivi
                      (assoc :toimenpideinstanssi (:tpi_id asetettava-tpi))
                      (assoc :laji arvo))))
         :valinnat (sanktio-domain/luo-kustannustyypit (:tyyppi @nav/valittu-urakka) (:id @istunto/kayttaja) tpi)
         :valinta-nayta #(or (sanktio-domain/bonuslaji->teksti %) "- Valitse tyyppi -")
         ::lomake/col-luokka "col-xs-12"
         :validoi [[:ei-tyhja "Valitse laji"]]})

      (when @tiedot-urakka/yllapitourakka?
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

      {:otsikko "Perustelu"
       :nimi :lisatieto
       :tyyppi :text
       :pakollinen? true
       ::lomake/col-luokka "col-xs-12"
       :validoi [[:ei-tyhja "Anna perustelu"]]}

      {:otsikko "Kulun kohdistus"
       :tyyppi :string
       :nimi :toimenpideinstanssi
       :muokattava? (constantly false)
       ::lomake/col-luokka "col-xs-12"
       :hae (fn [rivi]
              (let [tpi-id (:toimenpideinstanssi rivi)]
                (or (some #(when (= (:tpi_id %) tpi-id) (:tpi_nimi %))
                      @tiedot-urakka/urakan-toimenpideinstanssit)
                  "")))}

      (lomake/ryhma
        {:rivi? true}
        {:otsikko "Summa"
         :nimi :summa
         :tyyppi :euro
         :vaadi-positiivinen-numero? true
         :pakollinen? true
         ::lomake/col-luokka "col-xs-4"
         :validoi [[:ei-tyhja "Anna summa"] [:rajattu-numero 0 999999999 "Anna arvo väliltä 0 - 999 999 999"]]}
        ;; Indeksi näytetään vain 19/20 alkaneille urakoille
        (when (or (= 2019 urakan-alkuvuosi) (= 2020 urakan-alkuvuosi))
          (let [valinnat (when (and
                                 (<= urakan-alkuvuosi 2020)
                                 (= :asiakastyytyvaisyysbonus (:laji lomakkeen-tiedot)))
                           [(:indeksi @nav/valittu-urakka) nil])]
            {:otsikko "Indeksi"
             :nimi :indeksi
             :tyyppi :valinta
             :disabled? (nil? valinnat)
             ::lomake/col-luokka "col-xs-4"
             :valinnat (or valinnat [nil])
             :valinta-nayta #(or % "Ei indeksiä")})))
      (lomake/ryhma
        {:rivi? true}
        {:otsikko "Käsitelty"
         ;; Hox: Sanktioissa kasittelyaika päätyy laatupoikkeaman käsittelyajaksi, bonuksissa erilliskustannuksen pvm:ksi
         ;;      Käytetään käsittelyajasta bonuksienkin puolella laatupoikkeamista tuttua termiä.
         :nimi :kasittelyaika
         :tyyppi :pvm
         :pakollinen? true
         ::lomake/col-luokka "col-xs-4"
         :validoi [[:ei-tyhja "Valitse päivämäärä"]]
         :aseta (fn [rivi arvo]
                  (let [;; MHU25-urakoille laskutuskuukausi on aina 15.9. hoitokauden päättymisvuodelle
                        ;; Hoitokausi: 1.10.YYYY - 30.9.YYYY+1
                        ;; Jos käsittelyaika on loka-joulukuussa -> 15.9.(vuosi+1), muuten 15.9.(sama vuosi)
                        mhu25-perintapvm (when (and mhu25? arvo)
                                           (let [v (pvm/vuosi arvo)
                                                 kk (pvm/kuukausi arvo)
                                                 kohde-vuosi (if (>= kk 10) (inc v) v)]
                                             (pvm/->pvm (str "15.9." kohde-vuosi))))
                        mhu25-laskutuskuukausi-komp (when mhu25-perintapvm
                                                      (some #(when (and
                                                                     (= (pvm/vuosi mhu25-perintapvm) (:vuosi %))
                                                                     (= 9 (:kuukausi %)))
                                                               %)
                                                        laskutuskuukaudet))]
                    (cond-> rivi
                      ;; MHU25: asetetaan laskutuskuukausi automaattisesti hoitokauden syyskuulle
                      mhu25?
                      (-> (assoc :perintapvm mhu25-perintapvm)
                        (assoc :laskutuskuukausi-komp-tiedot mhu25-laskutuskuukausi-komp))

                      ;; Muille: Jos laskutuskuukautta ei ole vielä valittu ja bonusta ei ole tallennettu (id nil),
                      ;; niin asetetaan esivalintana perintapvm valittu kasittelyn pvm
                      (and (not mhu25?) (nil? (:laskutuskuukausi-komp-tiedot rivi)) (nil? (:id rivi)))
                      (assoc :perintapvm arvo)

                      ;; Tallennetaan aina valittu käsittelyaika :kasittelyaika avaimen alle
                      true
                      (assoc :kasittelyaika arvo))))}
        (if (and voi-muokata? (not lukutila?))
          {:otsikko "Laskutuskuukausi"
           ;; HOX: Sanktion tapauksessa laskutuskuukausi tallennetaan sanktion 'perintapvm'-sarakkeeseen.
           ;;      Bonuksissa (erilliskustannus-taulu) ei ole perintapvm-saraketta, vaan laskutuskuukausi-sarake
           ;;      johon tämä tieto tallennetaan. Lisäksi, yllapidon_bonus tallennetaan poikkeuksellisesti sanktiona.
           ;;      Yhteneväisyyden vuoksi käytetään bonuslomakkeella laskutuskuukaudesta nimeä 'perintapvm'
           :nimi :perintapvm
           :pakollinen? true
           :tyyppi :komponentti
           ::lomake/col-luokka "col-xs-6"
           :huomauta [[:urakan-aikana-ja-hoitokaudella]]
           :komponentti (fn [{:keys [muokkaa-lomaketta data]}]
                          (if-not mhu25?
                            [:<>
                             [yleiset/livi-pudotusvalikko
                              {:data-cy "koontilaskun-kk-dropdown"
                               :vayla-tyyli? true
                               :skrollattava? true
                               :pakollinen? true
                               ;; MHU25-urakoille laskutuskuukausi määräytyy käsittelyajan perusteella, ei käyttäjän valinnasta
                               :disabled mhu25?
                               :valinta (or
                                          ;; Näytetään valintana joko valittua laskutuskuukautta, tai
                                          (-> data :laskutuskuukausi-komp-tiedot)
                                          ;; jos käyttäjä ei tehnyt/muuttanut valintaa, käytetään tietokannasta haettua arvoa
                                          (when (:perintapvm data)
                                            (some #(when (and
                                                           (= (pvm/vuosi (:perintapvm data))
                                                             (:vuosi %))
                                                           (= (pvm/kuukausi (:perintapvm data))
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
                             (when (not @tiedot-urakka/yllapitourakka?)
                               [:div.small-caption.padding-4 "Näkyy laskutusyhteenvedolla"])]

                            ;; Jos mhu25 urakka, niin ei voi muokata. Näytetään tekstinä
                            [:div (some #(when (and
                                                 (= (pvm/vuosi (:perintapvm data)) (pvm/vuosi (:pvm %)))
                                                 (= (pvm/kuukausi (:perintapvm data)) (pvm/kuukausi (:pvm %)))) (:teksti %))
                                    laskutuskuukaudet)]))}
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
      {:otsikko "Käsittelytapa"
       :nimi :kasittelytapa :tyyppi :valinta
       :pakollinen? true
       :muokattava? (if (and (= :teiden-hoito urakan-tyyppi) (>= urakan-alkuvuosi 2025)) (constantly false) (constantly (and voi-muokata? (not lukutila?))))
       ::lomake/col-luokka "col-xs-12"
       ;; MHU25 urakoille näytetään vain Välikatselmus käsittelytapana
       :valinnat (if (and (= :teiden-hoito urakan-tyyppi) (>= urakan-alkuvuosi 2025)) [:valikatselmus] sanktio-domain/kasittelytavat)
       :valinta-nayta #(or (sanktio-domain/kasittelytapa->teksti %) "- valitse käsittelytapa -")}

      ;; Piilota liitteet lukutilassa kokonaan, koska ne eivät nyt tue pelkästään lukutilaa.
      (if-not lukutila?
        {:otsikko "Liitteet" :nimi :liitteet :kaariva-luokka "sanktioliite"
         :tyyppi :komponentti
         ::lomake/col-luokka "col-xs-12"
         :komponentti (fn [_]
                        [liitteet/liitteet-ja-lisays urakka-id (get-in app [:lomake :liitteet])
                         {:uusi-liite-atom (r/wrap uusi-liite
                                             #(e! (tiedot/->LisaaLiite %)))
                          :uusi-liite-teksti "Lisää liite"
                          :salli-poistaa-lisatty-liite? true
                          :poista-lisatty-liite-fn #(e! (tiedot/->PoistaLisattyLiite))
                          :salli-poistaa-tallennettu-liite? true
                          :nayta-lisatyt-liitteet? false
                          :poista-tallennettu-liite-fn #(e! (tiedot/->PoistaTallennettuLiite %))}])}
        {:otsikko "Liitteet" :nimi :liitteet :kaariva-luokka "sanktioliite"
         :tyyppi :komponentti
         ::lomake/col-luokka "col-xs-12"
         :komponentti (fn [_]
                        [:div
                         (if (and (get-in app [:lomake :liitteet])
                               (not (empty? (get-in app [:lomake :liitteet]))))
                           (doall
                             (for [l (get-in app [:lomake :liitteet])]
                               ^{:key l}
                               [liitteet/liitetiedosto l {:salli-poisto? false
                                                          :nayta-koko? true}]))
                           "Ei liitettä")])})]
     lomakkeen-tiedot]))


(defn bonus-lomake
  [sivupaneeli-auki?-atom avattu-bonus tallennus-onnistui-fn]
  (let [tallennus-onnistui-fn (if (fn? tallennus-onnistui-fn) tallennus-onnistui-fn (constantly nil))
        sulje-fn (fn [tallennus-onnistui?]
                   (when tallennus-onnistui?
                     (tallennus-onnistui-fn))
                   (reset! sivupaneeli-auki?-atom false))
        bonukset-tila (r/atom {:liitteet-haettu? false
                               :lomake (or
                                         ;; Muokataan vanhaa bonusta
                                         (when (some? (:id avattu-bonus))
                                           avattu-bonus)
                                         ;; tai alustetaan bonuslomakkeen tila
                                         (tiedot/uusi-bonus))})]
    (fn [_ _ _ lukutila? voi-muokata?]
      [:<>
       #_[harja.ui.debug/debug @bonukset-tila]

       [tuck/tuck bonukset-tila
        (r/partial bonus-lomake* sulje-fn lukutila? voi-muokata?)]])))
