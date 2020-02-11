(ns harja.views.urakka.laadunseuranta.sanktiot
  "Sanktioiden listaus"
  (:require [reagent.core :refer [atom] :as r]
            [harja.pvm :as pvm]
            [harja.views.urakka.valinnat :as urakka-valinnat]

            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.urakka.laadunseuranta.sanktiot :as tiedot]
            [harja.tiedot.navigaatio :as nav]

            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :as yleiset]

            [harja.loki :refer [log]]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.views.kartta :as kartta]
            [harja.tiedot.urakka.laadunseuranta.sanktiot :as sanktiot]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.domain.urakka :as u-domain]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.ui.viesti :as viesti]
            [harja.fmt :as fmt]
            [harja.domain.yllapitokohde :as yllapitokohde-domain]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.liitteet :as liitteet])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

(defn sanktion-tiedot
  [optiot]
  (let [muokattu (atom @tiedot/valittu-sanktio)
        voi-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-sanktiot
                                               (:id @nav/valittu-urakka))
        tallennus-kaynnissa (atom false)
        urakka-id (:id @nav/valittu-urakka)
        yllapitokohteet (conj (:yllapitokohteet optiot) {:id nil})
        mahdolliset-sanktiolajit @tiedot-urakka/urakkatyypin-sanktiolajit
        yllapito? (:yllapito? optiot)
        vesivayla? (:vesivayla? optiot)
        yllapitokohdeurakka? @tiedot-urakka/yllapitokohdeurakka?]
    [:div
     [napit/takaisin "Takaisin sanktioluetteloon" #(reset! tiedot/valittu-sanktio nil)]
     ;; Vaadi tarvittavat tiedot ennen rendausta
     (if (and mahdolliset-sanktiolajit
              (or (not yllapitokohdeurakka?)
                  (and yllapitokohdeurakka? yllapitokohteet)))
       [lomake/lomake
        {:otsikko (if (:id @muokattu)
                    (if (:suorasanktio @muokattu)
                      "Muokkaa suoraa sanktiota"
                      "Muokkaa laatupoikkeaman kautta tehtyä sanktiota")
                    "Luo uusi suora sanktio")
         :luokka :horizontal
         :muokkaa! #(reset! tiedot/valittu-sanktio %)
         :voi-muokata? voi-muokata?
         :footer-fn (fn [tarkastus]
                      [:span.nappiwrappi
                       [napit/palvelinkutsu-nappi
                        "Tallenna sanktio"
                        #(tiedot/tallenna-sanktio @muokattu urakka-id)
                        {:luokka "nappi-ensisijainen"
                         :ikoni (ikonit/tallenna)
                         :kun-onnistuu #(reset! tiedot/valittu-sanktio nil)
                         :disabled (or (not voi-muokata?)
                                       (not (lomake/voi-tallentaa? tarkastus)))}]
                       (when (and voi-muokata? (:id @muokattu))
                         [:button.nappi-kielteinen
                          {:class (when @tallennus-kaynnissa "disabled")
                           :on-click
                           (fn [e]
                             (.preventDefault e)
                             (varmista-kayttajalta/varmista-kayttajalta
                               {:otsikko "Sanktion poistaminen"
                                :sisalto (str "Haluatko varmasti poistaa sanktion "
                                              (or (str (:summa @muokattu) "€") "")
                                              " päivämäärällä "
                                              (pvm/pvm (:perintapvm @muokattu)) "?")
                                :hyvaksy "Poista"
                                :toiminto-fn #(do
                                                (let [res (tiedot/tallenna-sanktio
                                                            (assoc @muokattu
                                                              :poistettu true)
                                                            urakka-id)]
                                                  (do (viesti/nayta! "Sanktio poistettu")
                                                      (reset! tiedot/valittu-sanktio nil))))}))}
                          (ikonit/livicon-trash) " Poista sanktio"])])}
        [{:otsikko "Tekijä" :nimi :tekijanimi
          :hae (comp :tekijanimi :laatupoikkeama)
          :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :tekijanimi] arvo))
          :leveys 1 :tyyppi :string
          :muokattava? (constantly false)}

         (lomake/ryhma {:rivi? true}
                       {:otsikko "Havaittu" :nimi :laatupoikkeamaaika
                        :pakollinen? true
                        :hae (comp :aika :laatupoikkeama)
                        :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :aika] arvo))
                        :fmt pvm/pvm-aika :leveys 1 :tyyppi :pvm
                        :validoi [[:ei-tyhja "Valitse päivämäärä"]]
                        :huomauta [[:urakan-aikana-ja-hoitokaudella]]}
                       {:otsikko "Käsitelty" :nimi :kasittelyaika
                        :pakollinen? true
                        :hae (comp :kasittelyaika :paatos :laatupoikkeama)
                        :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :paatos :kasittelyaika] arvo))
                        :fmt pvm/pvm-aika :leveys 1 :tyyppi :pvm
                        :validoi [[:ei-tyhja "Valitse päivämäärä"]
                                  [:pvm-kentan-jalkeen (comp :aika :laatupoikkeama) "Ei voi olla ennen havaintoa"]]}
                       {:otsikko "Perintäpvm" :nimi :perintapvm
                        :pakollinen? true
                        :fmt pvm/pvm-aika :leveys 1 :tyyppi :pvm
                        :validoi [[:ei-tyhja "Valitse päivämäärä"]
                                  [:pvm-kentan-jalkeen (comp :aika :laatupoikkeama)
                                   "Ei voi olla ennen havaintoa"]]})

         (when yllapitokohdeurakka?
           {:otsikko "Ylläpitokohde" :tyyppi :valinta :nimi :yllapitokohde
            :palstoja 1 :pakollinen? false :muokattava? (constantly voi-muokata?)
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
         (when (and (not yllapitokohdeurakka?) (not vesivayla?))
           {:otsikko "Kohde" :tyyppi :string :nimi :kohde
            :hae (comp :kohde :laatupoikkeama)
            :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :kohde] arvo))
            :palstoja 1
            :pakollinen? true
            :muokattava? (constantly voi-muokata?)
            :validoi [[:ei-tyhja "Anna sanktion kohde"]]})

         {:otsikko "Käsitelty" :nimi :kasittelytapa
          :pakollinen? true
          :hae (comp :kasittelytapa :paatos :laatupoikkeama)
          :aseta #(assoc-in %1 [:laatupoikkeama :paatos :kasittelytapa] %2)
          :tyyppi :valinta
          :valinnat [:tyomaakokous :puhelin :kommentit :muu]
          :valinta-nayta #(if % (case %
                                  :tyomaakokous "Työmaakokous"
                                  :puhelin "Puhelimitse"
                                  :kommentit "Harja-kommenttien perusteella"
                                  :muu "Muu tapa"
                                  nil) "- valitse käsittelytapa -")
          :palstoja 1}

         (when yllapito?
           {:otsikko "Puute tai laiminlyönti"
            :nimi :vakiofraasi
            :tyyppi :valinta
            :valinta-arvo first
            :valinta-nayta second
            :valinnat sanktio-domain/+yllapidon-sanktiofraasit+
            :palstoja 2})
         {:otsikko "Perustelu" :nimi :perustelu
          :pakollinen? true
          :hae (comp :perustelu :paatos :laatupoikkeama)
          :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :paatos :perustelu] arvo))
          :palstoja 2 :tyyppi :text :koko [80 :auto]
          :validoi [[:ei-tyhja "Anna perustelu"]]}

         (when (= :muu (get-in @muokattu [:laatupoikkeama :paatos :kasittelytapa]))
           {:otsikko "Muu käsittelytapa" :nimi :muukasittelytapa :pakollinen? true
            :hae (comp :muukasittelytapa :paatos :laatupoikkeama)
            :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :paatos :muukasittelytapa] arvo))
            :leveys 2 :tyyppi :string
            :validoi [[:ei-tyhja "Anna lyhyt kuvaus käsittelytavasta."]]})

         (when-not vesivayla? ;; Vesiväylässä lajeina on vain sakko
           {:otsikko "Laji" :tyyppi :valinta :pakollinen? true
            :palstoja 1 :uusi-rivi? true :nimi :laji
            :hae (comp keyword :laji)
            :aseta (fn [rivi arvo]
                     (let [paivitetty (assoc rivi :laji arvo :tyyppi nil)
                           sanktiotyypit (sanktiot/lajin-sanktiotyypit arvo)
                           paivitetty (if-let [{tpk :toimenpidekoodi :as tyyppi} (and (= 1 (count sanktiotyypit)) (first sanktiotyypit))]
                                          (assoc paivitetty
                                            :tyyppi tyyppi
                                            :toimenpideinstanssi
                                            (when tpk
                                              (:tpi_id (tiedot-urakka/urakan-toimenpideinstanssi-toimenpidekoodille tpk))))
                                        paivitetty)]

                       (if-not (sanktio-domain/sakko? paivitetty)
                         (assoc paivitetty :summa nil :toimenpideinstanssi nil :indeksi nil)
                         paivitetty)))
            :valinnat (sort mahdolliset-sanktiolajit)
            :valinta-nayta #(case %
                              :A "Ryhmä A"
                              :B "Ryhmä B"
                              :C "Ryhmä C"
                              :muistutus "Muistutus"
                              :lupaussanktio "Lupaussanktio"
                              :vaihtosanktio "Vastuuhenkilöiden vaihtosanktio"
                              :testikeskiarvo-sanktio "Sanktio vastuuhenkilöiden testikeskiarvon laskemisesta"
                              :tenttikeskiarvo-sanktio "Sanktio vastuuhenkilöiden tenttikeskiarvon laskemisesta"
                              :yllapidon_muistutus "Muistutus"
                              :yllapidon_sakko "Sakko"
                              :yllapidon_bonus "Bonus"
                              :vesivayla_muistutus "Muistutus"
                              :vesivayla_sakko "Sakko"
                              :vesivayla_bonus "Bonus"
                              "- valitse laji -")
            :validoi [[:ei-tyhja "Valitse laji"]]})

         (when-not (or yllapito? vesivayla?)
           {:otsikko "Tyyppi" :tyyppi :valinta
            :palstoja 1
            :pakollinen? true
            :nimi :tyyppi
            :aseta (fn [sanktio {tpk :toimenpidekoodi :as tyyppi}]
                     (assoc sanktio
                       :tyyppi tyyppi
                       :toimenpideinstanssi
                       (when tpk
                         (:tpi_id (tiedot-urakka/urakan-toimenpideinstanssi-toimenpidekoodille tpk)))))
            ;; Kysely ei palauta sanktiotyyppien lajeja, joten tässä se pitää dissocata.
            :valinnat-fn (fn [_] (map #(dissoc % :laji) (sanktiot/lajin-sanktiotyypit (:laji @muokattu))))
            :valinta-nayta #(if % (:nimi %) " - valitse tyyppi -")
            :validoi [[:ei-tyhja "Valitse sanktiotyyppi"]]})

         (when (sanktio-domain/sakko? @muokattu)
           {:otsikko "Summa" :nimi :summa :palstoja 1 :tyyppi :positiivinen-numero
            :hae #(when (:summa %) (Math/abs (:summa %)))
            :pakollinen? true :uusi-rivi? true :yksikko "€"
            :validoi [[:ei-tyhja "Anna summa"] [:rajattu-numero 0 999999999 "Anna arvo väliltä 0 - 999 999 999"]]})

         (when (and (sanktio-domain/sakko? @muokattu) (urakka/indeksi-kaytossa?))
           {:otsikko "Indeksi" :nimi :indeksi :leveys 2
            :tyyppi :valinta
            :valinnat ["MAKU 2005" "MAKU 2010"]
            :valinta-nayta #(or % "Ei sidota indeksiin")
            :palstoja 1})

         (when (and (sanktio-domain/sakko? @muokattu))
           {:otsikko "Toimenpide"
            :pakollinen? true
            :nimi :toimenpideinstanssi
            :tyyppi :valinta
            :valinta-arvo :tpi_id
            :valinta-nayta #(if % (:tpi_nimi %) " - valitse toimenpide -")
            :valinnat @tiedot-urakka/urakan-toimenpideinstanssit
            :palstoja 1
            :validoi [[:ei-tyhja "Valitse toimenpide, johon sanktio liittyy"]]})

         (when (:suorasanktio @muokattu)
           {:otsikko "Liitteet" :nimi :liitteet
            :palstoja 2
            :tyyppi :komponentti
            :komponentti (fn [_]
                           [liitteet/liitteet-ja-lisays urakka-id (get-in @muokattu [:laatupoikkeama :liitteet])
                            {:uusi-liite-atom (r/wrap (:uusi-liite @tiedot/valittu-sanktio)
                                                      #(swap! tiedot/valittu-sanktio
                                                              (fn [] (assoc-in @muokattu [:laatupoikkeama :uusi-liite] %))))
                             :uusi-liite-teksti "Lisää liite sanktioon"
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
                                                                   liitteet))))}))}])})]
        @muokattu]
       [ajax-loader "Ladataan..."])]))

(defn- suodattimet-ja-toiminnot [valittu-urakka]
  [valinnat/urakkavalinnat {:urakka valittu-urakka}
   ^{:key "urakkavalinnat"}
   [urakka-valinnat/urakan-hoitokausi valittu-urakka]
   ^{:key "urakkatoiminnot"}
   [valinnat/urakkatoiminnot {:urakka valittu-urakka}
    (let [oikeus? (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-sanktiot
                                            (:id valittu-urakka))]
      (yleiset/wrap-if
        (not oikeus?)
        [yleiset/tooltip {} :%
         (oikeudet/oikeuden-puute-kuvaus :kirjoitus
                                         oikeudet/urakat-laadunseuranta-sanktiot)]
        ^{:key "Lisää sanktio"}
        [napit/uusi "Lisää sanktio"
         #(reset! tiedot/valittu-sanktio (tiedot/uusi-sanktio (:tyyppi valittu-urakka)))
         {:disabled (not oikeus?)}]))]])


(defn valitse-sanktio! [rivi sanktio-atom]
  (reset! sanktio-atom rivi)
  (if (= :virhe (tiedot/hae-sanktion-liitteet! (get-in rivi [:laatupoikkeama :urakka])
                                               (get-in rivi [:laatupoikkeama :id])
                                               sanktio-atom))
    (viesti/nayta! "Sanktion liitteiden hakeminen epäonnistui" :warning)
    (log "Liitteet haettiin onnistuneesti.")))

(defn sanktiolistaus
  [optiot valittu-urakka]
  (let [sanktiot (reverse (sort-by :perintapvm @tiedot/haetut-sanktiot))
        yllapito? (:yllapito? optiot)
        vesivayla? (:vesivayla? optiot)
        yhteensa (reduce + (map :summa sanktiot))
        yhteensa (when yhteensa
                   (if yllapito?
                     (- yhteensa) ; ylläpidossa sakot miinusmerkkisiä
                     yhteensa))
        yllapitokohdeurakka? @tiedot-urakka/yllapitokohdeurakka?]
    [:div.sanktiot
     [suodattimet-ja-toiminnot valittu-urakka]
     [grid/grid
      {:otsikko (if yllapito? "Sakot ja bonukset" "Sanktiot")
       :tyhja (if @tiedot/haetut-sanktiot "Ei löytyneitä tietoja" [ajax-loader "Haetaan sanktioita."])
       :rivi-klikattu #(valitse-sanktio! % tiedot/valittu-sanktio)}
      [{:otsikko "Päivä\u00ADmäärä" :nimi :perintapvm :fmt pvm/pvm-aika :leveys 1}
       (when yllapitokohdeurakka?
         {:otsikko "Yllä\u00ADpito\u00ADkoh\u00ADde" :nimi :kohde :leveys 2
          :hae (fn [rivi]
                 (if (get-in rivi [:yllapitokohde :id])
                   (yllapitokohde-domain/yllapitokohde-tekstina {:kohdenumero (get-in rivi [:yllapitokohde :numero])
                                                                 :nimi (get-in rivi [:yllapitokohde :nimi])})
                   "Ei liity kohteeseen"))})
       (when (and (not yllapitokohdeurakka?) (not vesivayla?))
         {:otsikko "Kohde" :nimi :kohde :hae (comp :kohde :laatupoikkeama) :leveys 1})
       {:otsikko "Perus\u00ADtelu" :nimi :kuvaus :hae (comp :perustelu :paatos :laatupoikkeama) :leveys 3}
       (if yllapito?
         {:otsikko "Puute tai laiminlyönti" :nimi :vakiofraasi
          :hae #(sanktio-domain/yllapidon-sanktiofraasin-nimi (:vakiofraasi %)) :leveys 3}
         {:otsikko "Tyyppi" :nimi :sanktiotyyppi :hae (comp :nimi :tyyppi) :leveys 3})
       {:otsikko "Tekijä" :nimi :tekija :hae (comp :tekijanimi :laatupoikkeama) :leveys 1}
       {:otsikko "Summa €" :nimi :summa :leveys 1 :tyyppi :numero :tasaa :oikea
        :hae #(or (when (:summa %)
                    (if yllapito?
                      (- (:summa %)) ;ylläpidossa on sakkoja ja -bonuksia, sakot miinusmerkillä
                      (:summa %)))
                  "Muistutus")}]
      sanktiot]
     (when yllapito?
       (yleiset/vihje "Huom! Sakot ovat miinusmerkkisiä ja bonukset plusmerkkisiä."))
     (when (> (count sanktiot) 0)
       [:div.pull-right.bold (str "Yhteensä " (fmt/euro-opt yhteensa))])]))

(defn sanktiot [optiot]
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan-ulos #(do
                         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                         (nav/vaihda-kartan-koko! :S))
                      #(nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko))
    (fn []
      [:span
       [kartta/kartan-paikka]
       (let [optiot (merge optiot
                           {:yllapitokohteet @laadunseuranta/urakan-yllapitokohteet-lomakkeelle
                            :yllapito? @tiedot-urakka/yllapidon-urakka?
                            :vesivayla? (u-domain/vesivaylaurakka? @nav/valittu-urakka)})]
         (if @tiedot/valittu-sanktio
           [sanktion-tiedot optiot]
           [sanktiolistaus optiot @nav/valittu-urakka]))])))
