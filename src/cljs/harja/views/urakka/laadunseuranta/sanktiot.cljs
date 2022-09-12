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
            [harja.ui.sivupalkki :as sivupalkki]

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

(defn- laji->teksti
  [laji]
  (case laji
    :A "Ryhmä A"
    :B "Ryhmä B"
    :C "Ryhmä C"
    :muistutus "Muistutus"
    :vaihtosanktio "Vastuuhenkilöiden vaihtosanktio"
    :testikeskiarvo-sanktio "Sanktio vastuuhenkilöiden testikeskiarvon laskemisesta"
    :tenttikeskiarvo-sanktio "Sanktio vastuuhenkilöiden tenttikeskiarvon laskemisesta"
    :arvonvahennyssanktio "Arvonvähennys"
    :yllapidon_muistutus "Muistutus"
    :yllapidon_sakko "Sakko"
    :yllapidon_bonus "Bonus"
    :vesivayla_muistutus "Muistutus"
    :vesivayla_sakko "Sakko"
    :vesivayla_bonus "Bonus"
    "- valitse laji -"))

(defn sanktion-tiedot
  [optiot]
  (let [lukutila (atom true)]
    (komp/luo
      #_(komp/klikattu-ulkopuolelle ;{:luokat #{"ei-sulje-sivupaneelia"} :ulkopuolella-fn}
        #(reset! tiedot/valittu-sanktio nil))
      (fn [optiot]      
        (let [muokattu (atom @tiedot/valittu-sanktio)
                                        ; Jos urakkana on teiden-hoito (MHU) käyttäjä ei saa vapaasti valita indeksiä sanktiolle.
                                        ; Sanktioon kuuluva indeksi on pakollinen ja se on jo määritelty urakalle, joten se pakotetaan käyttöön.
              _ (when (= :teiden-hoito (:tyyppi @nav/valittu-urakka))
                  (swap! muokattu assoc :indeksi (:indeksi @nav/valittu-urakka)))
              voi-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-sanktiot
                             (:id @nav/valittu-urakka))
              tallennus-kaynnissa (atom false)
              urakka-id (:id @nav/valittu-urakka)
              yllapitokohteet (conj (:yllapitokohteet optiot) {:id nil})
              mahdolliset-sanktiolajit @tiedot-urakka/urakkatyypin-sanktiolajit
              yllapito? (:yllapito? optiot)
              vesivayla? (:vesivayla? optiot)
              yllapitokohdeurakka? @tiedot-urakka/yllapitokohdeurakka?
              muokataan-vanhaa? (some? (:id @muokattu))
              suorasanktio? (some? (:suorasanktio @muokattu))
              lukutila? (if (not muokataan-vanhaa?) false @lukutila)]
          [:div.padding-16.ei-sulje-sivupaneelia
           #_[napit/takaisin "Takaisin sanktioluetteloon" #(reset! tiedot/valittu-sanktio nil)]
           [:h2 (cond
                  (and lukutila? muokataan-vanhaa?)
                  (str (laji->teksti (:laji @muokattu)))

                  muokataan-vanhaa?
                  "Muokkaa sanktiota"
                  
                  :else
                  "Lisää uusi")]
           (when (and lukutila? muokataan-vanhaa?)
             [napit/yleinen-reunaton "Muokkaa" #(swap! lukutila not)])
                      
           ;; Vaadi tarvittavat tiedot ennen rendausta
           (if (and mahdolliset-sanktiolajit
                 (or (not yllapitokohdeurakka?)
                   (and yllapitokohdeurakka? yllapitokohteet)))
             
             [:div
              [lomake/lomake
               {:otsikko "Sanktion tiedot"
                :ei-borderia? true
                :vayla-tyyli? true
                :luokka "padding-16 taustavari-taso3"
                :muokkaa! #(reset! tiedot/valittu-sanktio %)
                :validoi-alussa? false
                :voi-muokata? (and voi-muokata? (not lukutila?))
                :footer-fn (fn [sanktio]
                             [:span.nappiwrappi.flex-row
                              (when-not lukutila?
                                [napit/palvelinkutsu-nappi
                                 (str "Tallenna" (when muokataan-vanhaa? " muutokset"))
                                 #(tiedot/tallenna-sanktio (lomake/ilman-lomaketietoja @muokattu) urakka-id)
                                 {:luokka "nappi-ensisijainen"
                                  :ikoni (ikonit/tallenna)
                                  :kun-onnistuu #(reset! tiedot/valittu-sanktio nil)
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
                                       :toiminto-fn #(do
                                                       (let [res (tiedot/tallenna-sanktio
                                                                   (assoc (lomake/ilman-lomaketietoja @muokattu)
                                                                     :poistettu true)
                                                                   urakka-id)]
                                                         (do (viesti/nayta! "Sanktio poistettu")
                                                             (reset! tiedot/valittu-sanktio nil))))}))}
                                 (ikonit/livicon-trash) " Poista"])
                              [napit/peruuta (if lukutila?
                                               "Sulje"
                                               "Peruuta")
                               #(reset! tiedot/valittu-sanktio nil)]])}
               [(when-not vesivayla? ;; Vesiväylässä lajeina on vain sakko
                  {:otsikko "Sanktion laji" :tyyppi :valinta :pakollinen? true
                   ::lomake/col-luokka "col-xs-12"
                   :uusi-rivi? true :nimi :laji
                   :hae (comp keyword :laji)
                   :aseta (fn [rivi arvo]
                            (let [rivi (-> rivi
                                         (assoc :laji arvo)
                                         (dissoc :tyyppi)
                                         (assoc :tyyppi nil))
                                  s-tyypit (sanktiot/lajin-sanktiotyypit arvo)
                                  rivi (if-let [{tpk :toimenpidekoodi :as tyyppi} (and (= 1 (count s-tyypit)) (first s-tyypit))]
                                         (assoc rivi
                                           :tyyppi (dissoc tyyppi :laji)
                                           :toimenpideinstanssi
                                           (when tpk
                                             (:tpi_id (tiedot-urakka/urakan-toimenpideinstanssi-toimenpidekoodille tpk))))
                                         rivi)]
                              (if-not (sanktio-domain/sakko? rivi)
                                (assoc rivi :summa nil :toimenpideinstanssi nil :indeksi nil)
                                rivi)))
                   :valinnat (sort mahdolliset-sanktiolajit)
                   :valinta-nayta laji->teksti
                   :validoi [[:ei-tyhja "Valitse laji"]]})                

                (when-not (or yllapito? vesivayla?)
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
                   :valinnat-fn (fn [_]
                                  (map #(dissoc % :laji) (sanktiot/lajin-sanktiotyypit (:laji @muokattu))))
                   :valinta-nayta (fn [arvo]
                                    (if (or (nil? arvo) (nil? (:nimi arvo))) "Valitse sanktiotyyppi" (:nimi arvo)))
                   :validoi [[:ei-tyhja "Valitse sanktiotyyppi"]]})
                
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
                (when (and (not yllapitokohdeurakka?) (not vesivayla?))
                  {:otsikko "Tapahtumapaikka/kuvaus" :tyyppi :string :nimi :kohde
                   :hae (comp :kohde :laatupoikkeama)
                   ::lomake/col-luokka "col-xs-12"
                   :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :kohde] arvo))
                   :pakollinen? true
                   :muokattava? (constantly voi-muokata?)
                   :validoi [[:ei-tyhja "Anna sanktion tapahtumapaikka/kuvaus"]]})         

                (when yllapito?
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

                (when (and (sanktio-domain/sakko? @muokattu))
                  {:otsikko "Kulun kohdistus"
                   :pakollinen? true
                   ::lomake/col-luokka "col-xs-12"
                   :nimi :toimenpideinstanssi
                   :tyyppi :valinta
                   :valinta-arvo :tpi_id
                   :valinta-nayta #(if % (:tpi_nimi %) " - valitse toimenpide -")
                   :valinnat @tiedot-urakka/urakan-toimenpideinstanssit
                   :validoi [[:ei-tyhja "Valitse toimenpide, johon sanktio liittyy"]]})        

                (apply lomake/ryhma {:rivi? true}
                  (keep identity [(when (sanktio-domain/sakko? @muokattu)
                                    {:otsikko "Summa" :nimi :summa :tyyppi :positiivinen-numero
                                     ::lomake/col-luokka "col-xs-4"
                                     :hae #(when (:summa %) (Math/abs (:summa %)))
                                     :pakollinen? true :uusi-rivi? true :yksikko "€"
                                     :validoi [[:ei-tyhja "Anna summa"] [:rajattu-numero 0 999999999 "Anna arvo väliltä 0 - 999 999 999"]]})

                                  (when (sanktio-domain/sakko? @muokattu)
                                    {:otsikko (str "Indeksi") :nimi :indeksi
                                     :tyyppi :valinta
                                     ::lomake/col-luokka "col-xs-4"
                                     :muokattava? (constantly (not lukutila?)) #_(constantly (not= :teiden-hoito (:tyyppi @nav/valittu-urakka)))
                                     :hae (if (urakka/indeksi-kaytossa-sakoissa?) :indeksi (constantly nil))
                                     :disabled? (not (urakka/indeksi-kaytossa-sakoissa?))
                                     :valinnat (into [] (keep identity [(when (urakka/indeksi-kaytossa-sakoissa?)
                                                                          (:indeksi @nav/valittu-urakka))
                                                                        "Ei indeksiä"]))
                                     :valinta-nayta #(or % "Ei indeksiä")})]))

                (lomake/ryhma {:rivi? true}
                  {:otsikko "Havaittu" :nimi :laatupoikkeamaaika
                   :pakollinen? true
                   ::lomake/col-luokka "col-xs-4"
                   :hae (comp :aika :laatupoikkeama)
                   :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :aika] arvo))
                   :fmt pvm/pvm-aika :tyyppi :pvm
                   :validoi [[:ei-tyhja "Valitse päivämäärä"]]
                   :huomauta [[:urakan-aikana-ja-hoitokaudella]]}
                  {:otsikko "Käsitelty" :nimi :kasittelyaika
                   :pakollinen? true
                   ::lomake/col-luokka "col-xs-4"
                   :hae (comp :kasittelyaika :paatos :laatupoikkeama)
                   :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :paatos :kasittelyaika] arvo))
                   :fmt pvm/pvm-aika :tyyppi :pvm
                   :validoi [[:ei-tyhja "Valitse päivämäärä"]
                             [:pvm-kentan-jalkeen (comp :aika :laatupoikkeama) "Ei voi olla ennen havaintoa"]]}
                  {:otsikko "Perintä" :nimi :perintapvm
                   :pakollinen? true
                   ::lomake/col-luokka "col-xs-4"
                   :fmt pvm/pvm-aika :tyyppi :pvm
                   :validoi [[:ei-tyhja "Valitse päivämäärä"]
                             [:pvm-kentan-jalkeen (comp :aika :laatupoikkeama)
                              "Ei voi olla ennen havaintoa"]]})

                {:otsikko "Käsittelytapa" :nimi :kasittelytapa
                 :pakollinen? true
                 ::lomake/col-luokka "col-xs-12"
                 :hae (comp :kasittelytapa :paatos :laatupoikkeama)
                 :aseta #(assoc-in %1 [:laatupoikkeama :paatos :kasittelytapa] %2)
                 :tyyppi :valinta
                 :valinnat [:tyomaakokous :puhelin :kommentit :muu]
                 :valinta-nayta #(if % (case %
                                         :tyomaakokous "Työmaakokous"
                                         :puhelin "Puhelimitse"
                                         :kommentit "Harja-kommenttien perusteella"
                                         :muu "Muu tapa"
                                         nil) "- valitse käsittelytapa -")}
                (when (= :muu (get-in @muokattu [:laatupoikkeama :paatos :kasittelytapa]))
                  {:otsikko "Muu käsittelytapa" :nimi :muukasittelytapa :pakollinen? true
                   ::lomake/col-luokka "col-xs-12"
                   :hae (comp :muukasittelytapa :paatos :laatupoikkeama)
                   :aseta (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :paatos :muukasittelytapa] arvo))
                   :tyyppi :string
                   :validoi [[:ei-tyhja "Anna lyhyt kuvaus käsittelytavasta."]]})

                (when (:suorasanktio @muokattu)
                  {:otsikko "Liitteet" :nimi :liitteet
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
                                                               liitteet))))}))}])})]
               @muokattu]]
             [ajax-loader "Ladataan..."])])))))

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
       :rivi-klikattu #(valitse-sanktio! % tiedot/valittu-sanktio)
       :rivi-jalkeen-fn #(let [yhteensa-summat (reduce + 0 (map :summa %))
                               ;; Ylläpidossa sekä bonuksia että sanktioita, käsiteltävä sakot miinusmerkkisinä
                               yhteensa-summat (if yllapito? (- yhteensa-summat) yhteensa-summat)
                               yhteensa-indeksit (reduce + 0 (map :indeksikorjaus %))]
                           [{:teksti "Yht." :luokka "lihavoitu"}
                            {:teksti (str (count %) " kpl") :sarakkeita 4 :luokka "lihavoitu"}
                            {:teksti (str (fmt/euro-opt true yhteensa-summat)) :tasaa :oikea :luokka "lihavoitu"}
                            {:teksti (fmt/euro-opt true yhteensa-indeksit)
                             :tasaa :oikea :luokka "lihavoitu"}])}
      [{:otsikko "Päivä\u00ADmäärä" :nimi :perintapvm :fmt pvm/pvm :leveys 1}
       {:otsikko "Laji" :nimi :laji :hae :laji :leveys 3 :fmt laji->teksti}
       (when yllapitokohdeurakka?
         {:otsikko "Kohde" :nimi :kohde :leveys 2
          :hae (fn [rivi]
                 (if (get-in rivi [:yllapitokohde :id])
                   (yllapitokohde-domain/yllapitokohde-tekstina {:kohdenumero (get-in rivi [:yllapitokohde :numero])
                                                                 :nimi (get-in rivi [:yllapitokohde :nimi])})
                   "Ei liity kohteeseen"))})
       (if yllapito?
         {:otsikko "Kuvaus" :nimi :vakiofraasi
          :hae #(sanktio-domain/yllapidon-sanktiofraasin-nimi (:vakiofraasi %)) :leveys 3}
         {:otsikko "Tyyppi" :nimi :sanktiotyyppi :hae (comp :nimi :tyyppi) :leveys 3})
       (when (not yllapito?) {:otsikko "Tapah\u00ADtuma\u00ADpaik\u00ADka/kuvaus" :nimi :tapahtumapaikka :hae (comp :kohde :laatupoikkeama) :leveys 3})
       {:otsikko "Perus\u00ADtelu" :nimi :perustelu :hae (comp :perustelu :paatos :laatupoikkeama) :leveys 3}
       {:otsikko "Määrä (€)" :nimi :summa :leveys 1 :tyyppi :numero :tasaa :oikea
        :hae #(or (let [summa (:summa %)]
                    (fmt/euro-opt false
                                  (when summa
                                    (if yllapito? (- summa) summa)))) ;ylläpidossa on sakkoja ja -bonuksia, sakot miinusmerkillä
                "Muistutus")}
       {:otsikko "Indeksi (€)" :nimi :indeksikorjaus :tasaa :oikea :fmt fmt/euro-opt :leveys 1}]
      sanktiot]
     (when yllapito?
       (yleiset/vihje "Huom! Sakot ovat miinusmerkkisiä ja bonukset plusmerkkisiä."))]))

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
         [:div
          (when @tiedot/valittu-sanktio           
            [sivupalkki/oikea
             {:leveys "600px" :sulku-fn #(reset! tiedot/valittu-sanktio nil)}
             [sanktion-tiedot optiot]])
          [sanktiolistaus optiot @nav/valittu-urakka]])])))
