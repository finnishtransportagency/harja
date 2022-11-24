(ns harja.views.urakka.laadunseuranta.bonukset
  "Bonuksien käsittely ja luonti"
  (:require [reagent.core :as r]
            [tuck.core :as tuck]

            [harja.pvm :as pvm]

            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.domain.yllapitokohde :as yllapitokohde-domain]

            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.tiedot.urakka.laadunseuranta.bonukset :as tiedot]
            [harja.tiedot.urakka.laadunseuranta.sanktiot :as tiedot-sanktiot]

            [harja.ui.yleiset :as yleiset]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]))

(defn- kannasta->lomake
  [avain]
  (or (get tiedot/konversioavaimet avain)    
    avain))


(defn- bonus->lomake
  ([bonus]
   (bonus->lomake bonus {}))
  ([bonus acc]
   (reduce (fn [acc [avain tiedot]]
             (cond
               (map? tiedot)
               (bonus->lomake tiedot acc)

               (and
                 (some? tiedot)
                 (nil? (get acc avain))
                 (not= avain (kannasta->lomake avain)))
               (assoc acc (kannasta->lomake avain) tiedot)

               (and (some? tiedot)
                 (nil? (get acc avain)))
               (assoc acc avain tiedot)
               
               :else
               acc))
     acc bonus)))

(defn- hae-tpi-idlla
  [tpi-id]
  (some
    #(when (= tpi-id (:tpi_id %))
       %)
    @tiedot-urakka/urakan-toimenpideinstanssit))

(defn bonukset-lomake
  "MH-urakoidan ja ylläpitourakoiden yhteinen bonuslomake.
  Huomioitavaa on, että ylläpidon urakoiden bonukset tallennetaankin oikeasti sanktioina, eikä bonuksina.
  Ylläpidon urakoiden bonuslomakkeessa on myös muita pieniä poikkeuksia."
  [sulje-fn lukutila? voi-muokata? e! app]
  (let [{lomakkeen-tiedot :lomake :keys [uusi-liite voi-sulkea? liitteet-haettu?]} app
        urakka-id (:id @nav/valittu-urakka)
        urakan-alkuvuosi (-> nav/valittu-urakka deref :alkupvm pvm/vuosi)
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
                   (when-not lukutila?
                     [:<>
                      [:span.nappiwrappi.flex-row
                       [napit/yleinen-ensisijainen "Tallenna" #(e! (tiedot/->TallennaBonus))
                        {:disabled (not (empty? (::lomake/puuttuvat-pakolliset-kentat bonus)))}]
                       (when (:id lomakkeen-tiedot)
                         [napit/kielteinen "Poista" (fn [_]
                                                      (varmista-kayttajalta/varmista-kayttajalta
                                                        {:otsikko "Bonuksen poistaminen"
                                                         :sisalto "Haluatko varmasti poistaa bonuksen? Toimintoa ei voi perua."
                                                         :modal-luokka "varmistus-modal"
                                                         :hyvaksy "Poista"
                                                         :toiminto-fn #(e! (tiedot/->PoistaBonus))}))
                          {:luokka "oikealle"}])
                       [napit/peruuta "Sulje" #(e! (tiedot/->TyhjennaLomake sulje-fn))]]]))}
     [(let [hae-tpin-tiedot (comp hae-tpi-idlla :toimenpideinstanssi)
            tpi (hae-tpin-tiedot lomakkeen-tiedot)]
        {:otsikko "Bonus"
         :nimi :tyyppi
         :tyyppi :valinta
         :pakollinen? true
         ;; Valitse ainoa, jos tyyppejä on vain yksi.
         ;; Esimerkiksi ylläpitourakoiden tapauksessa on saatavilla vain "yllapidon_bonus"
         :valitse-ainoa? true
         :valinnat (sanktio-domain/luo-kustannustyypit (:tyyppi @nav/valittu-urakka) (:id @istunto/kayttaja) tpi)
         :valinta-nayta sanktio-domain/bonustyypin-teksti
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
       :nimi :toimenpideinstanssi
       :tyyppi :valinta
       :pakollinen? true
       :valitse-oletus? true
       :valinta-arvo :tpi_id
       :valinta-nayta #(if % (:tpi_nimi %) " - valitse toimenpide -")
       :valinnat (if (= :teiden-hoito (:tyyppi @nav/valittu-urakka))
                   (filter #(= "23150" (:t2_koodi %)) @tiedot-urakka/urakan-toimenpideinstanssit)
                   @tiedot-urakka/urakan-toimenpideinstanssit)
       ::lomake/col-luokka "col-xs-12"
       :disabled? true}
      (lomake/ryhma
        {:rivi? true}
        {:otsikko "Summa"
         :nimi :rahasumma
         :tyyppi :euro
         :vaadi-positiivinen-numero? true
         :pakollinen? true
         ::lomake/col-luokka "col-xs-4"
         :validoi [[:ei-tyhja "Anna summa"] [:rajattu-numero 0 999999999 "Anna arvo väliltä 0 - 999 999 999"]]}
        (let [valinnat (when (and
                               (<= urakan-alkuvuosi 2020)
                               (= :asiakastyytyvaisyysbonus (:tyyppi lomakkeen-tiedot)))
                         [(:indeksi @nav/valittu-urakka) nil])]
          {:otsikko "Indeksi"
           :nimi :indeksin_nimi
           :tyyppi :valinta
           :disabled? (nil? valinnat)
           ::lomake/col-luokka "col-xs-4"
           :valinnat (or valinnat [nil])
           :valinta-nayta #(or % "Ei indeksiä")}))
      (lomake/ryhma
        {:rivi? true}
        {:otsikko "Käsitelty"
         :nimi :pvm
         :tyyppi :pvm
         :pakollinen? true
         ::lomake/col-luokka "col-xs-4"
         :validoi [[:ei-tyhja "Valitse päivämäärä"]]
         :aseta (fn [rivi arvo]
                  (let [lk (some #(when (and
                                          (some? (:kuukausi %))
                                          (= (:kuukausi %) (pvm/kuukausi arvo))
                                          (= (:vuosi %) (pvm/vuosi arvo)))
                                    %)
                             laskutuskuukaudet)]
                    (cond-> rivi
                      (nil? (:laskutuskuukausi rivi))
                      (assoc :laskutuskuukausi (:pvm lk))

                      (nil? (:laskutuskuukausi-komp-tiedot rivi))
                      (assoc :laskutuskuukausi-komp-tiedot lk)

                      true (assoc :pvm arvo))))}
        (if (and voi-muokata? (not lukutila?))
          {:otsikko "Laskutuskuukausi" :nimi :laskutuskuukausi
           :pakollinen? true
           :tyyppi :komponentti
           ::lomake/col-luokka "col-xs-6"
           :huomauta [[:urakan-aikana-ja-hoitokaudella]]
           :komponentti (fn [{:keys [muokkaa-lomaketta data]}]
                          [:<>
                           [yleiset/livi-pudotusvalikko
                            {:data-cy "koontilaskun-kk-dropdown"
                             :vayla-tyyli? true
                             :skrollattava? true
                             :pakollinen? true
                             :valinta (or (-> data :laskutuskuukausi-komp-tiedot)
                                        (some #(when (= (-> data :laskutuskuukausi) (:pvm %)) %) laskutuskuukaudet))
                             :valitse-fn #(muokkaa-lomaketta
                                            (assoc data
                                              :laskutuskuukausi-komp-tiedot %
                                              :laskutuskuukausi (:pvm %)))
                             :format-fn :teksti}
                            laskutuskuukaudet]
                           ;; Piilotetaan teksti ylläpitourakoilta, koska niillä ei ole laskutusyhteenvetoa
                           (when (not @tiedot-urakka/yllapitourakka?)
                             [:div.small-caption.padding-4 "Näkyy laskutusyhteenvedolla"])])}
          {:otsikko "Laskutuskuukausi"
           :nimi :laskutuskuukausi
           :fmt (fn [kk] (some #(when (= kk (:pvm %)) (:teksti %)) laskutuskuukaudet))
           :pakollinen? true
           :tyyppi :pvm
           ::lomake/col-luokka "col-xs-6"}))
      {:otsikko "Käsittelytapa"
       :nimi :kasittelytapa
       :tyyppi :valinta
       :pakollinen? true
       ::lomake/col-luokka "col-xs-12"
       :valinta-nayta #(if % (case %
                               :tyomaakokous "Työmaakokous"
                               :valikatselmus "Välikatselmus"
                               :puhelin "Puhelimitse"
                               :kommentit "Harja-kommenttien perusteella"
                               :muu "Muu tapa"
                               nil)
                         "- valitse käsittelytapa -")
       :valinnat sanktio-domain/kasittelytavat}
      ;; Piilota liitteet lukutilassa kokonaan, koska ne eivät nyt tue pelkästään lukutilaa.
      (when-not lukutila?
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
                          :poista-tallennettu-liite-fn #(e! (tiedot/->PoistaTallennettuLiite %))}])})]
     lomakkeen-tiedot]))


(defn bonukset*
  [auki? avattu-bonus haetut-sanktiot lukutila? voi-muokata?]
  (let [sulje-fn #(do
                    (when (some? (:id %))
                      (if (some (fn [rivi] (= (:id rivi) (:id %))) @haetut-sanktiot)
                        (swap! haetut-sanktiot (fn [sanktiolistaus]
                                                 (if (:poistettu %)
                                                   (into []
                                                     (remove (fn [rivi]
                                                               (= (:id rivi) (:id %)))
                                                       sanktiolistaus))
                                                   (mapv (fn [rivi]
                                                           (if (= (:id rivi) (:id %))
                                                             %
                                                             rivi))
                                                     sanktiolistaus))))
                        (swap! haetut-sanktiot conj %)))
                    (reset! auki? false))
        bonukset-tila (r/atom {:liitteet-haettu? false
                               :lomake (or
                                         (when (some? (:id avattu-bonus))
                                           (bonus->lomake avattu-bonus))
                                         {})})]
    (fn [_ _ _ lukutila? voi-muokata?]
      [tuck/tuck bonukset-tila
       (r/partial bonukset-lomake sulje-fn lukutila? voi-muokata?)])))
