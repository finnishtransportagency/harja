(ns harja.views.urakka.laadunseuranta.sanktiot
  "Sanktioiden listaus"
  (:require [reagent.core :refer [atom]]
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
            [harja.domain.laadunseuranta.sanktiot :as sanktio-domain]
            [harja.domain.tierekisteri :as tierekisteri])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

(defn sanktion-tiedot
  [optiot]
  (let [muokattu (atom @tiedot/valittu-sanktio)
        _ (log "muokattu sanktio: " (pr-str muokattu))
        voi-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-sanktiot
                                               (:id @nav/valittu-urakka))]
    (fn [optiot]
      (let [yllapitokohteet (:yllapitokohteet optiot)
            mahdolliset-sanktiolajit @tiedot-urakka/urakkatyypin-sanktiolajit
            yllapito? (:yllapito? optiot)
            yllapitokokohdeurakka? @tiedot-urakka/yllapitokohdeurakka?]
        [:div
         [napit/takaisin "Takaisin sanktioluetteloon" #(reset! tiedot/valittu-sanktio nil)]
         ;; Vaadi tarvittavat tiedot ennen rendausta
         (if (and mahdolliset-sanktiolajit
                  (or (not yllapitokokohdeurakka?)
                      (and yllapitokokohdeurakka? yllapitokohteet)))
           [lomake/lomake
            {:otsikko      (if (:id @muokattu)
                             (if (:suorasanktio @muokattu)
                               "Muokkaa suoraa sanktiota"
                               "Muokkaa laatupoikkeaman kautta tehtyä sanktiota")
                             "Luo uusi suora sanktio")
             :luokka       :horizontal
             :muokkaa!     #(reset! muokattu %)
             :voi-muokata? voi-muokata?
             :footer-fn    (fn [tarkastus]
                             [napit/palvelinkutsu-nappi
                              "Tallenna sanktio"
                              #(tiedot/tallenna-sanktio @muokattu (:id @nav/valittu-urakka))
                              {:luokka       "nappi-ensisijainen"
                               :ikoni        (ikonit/tallenna)
                               :kun-onnistuu #(reset! tiedot/valittu-sanktio nil)
                               :disabled     (or (not voi-muokata?)
                                                 (not (lomake/voi-tallentaa? tarkastus)))}])}
            [{:otsikko     "Tekijä" :nimi :tekijanimi
              :hae         (comp :tekijanimi :laatupoikkeama)
              :aseta       (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :tekijanimi] arvo))
              :leveys      1 :tyyppi :string
              :muokattava? (constantly false)}

             (lomake/ryhma {:rivi? true}
                           {:otsikko     "Havaittu" :nimi :laatupoikkeamaaika
                            :pakollinen? true
                            :hae         (comp :aika :laatupoikkeama)
                            :aseta       (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :aika] arvo))
                            :fmt         pvm/pvm-aika :leveys 1 :tyyppi :pvm
                            :validoi     [[:ei-tyhja "Valitse päivämäärä"]]
                            :huomauta    [[:urakan-aikana-ja-hoitokaudella]]}
                           {:otsikko     "Käsitelty" :nimi :kasittelyaika
                            :pakollinen? true
                            :hae         (comp :kasittelyaika :paatos :laatupoikkeama)
                            :aseta       (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :paatos :kasittelyaika] arvo))
                            :fmt         pvm/pvm-aika :leveys 1 :tyyppi :pvm
                            :validoi     [[:ei-tyhja "Valitse päivämäärä"]
                                          [:pvm-kentan-jalkeen (comp :aika :laatupoikkeama) "Ei voi olla ennen havaintoa"]]}
                           {:otsikko     "Perintäpvm" :nimi :perintapvm
                            :pakollinen? true
                            :fmt         pvm/pvm-aika :leveys 1 :tyyppi :pvm
                            :validoi     [[:ei-tyhja "Valitse päivämäärä"]
                                          [:pvm-kentan-jalkeen (comp :aika :laatupoikkeama)
                                           "Ei voi olla ennen havaintoa"]]})

             (if yllapito?
               {:otsikko       "Ylläpitokohde" :tyyppi :valinta :nimi :yllapitokohde
                :palstoja      1
                :pakollinen?   true
                :muokattava?   (constantly voi-muokata?)
                :valinnat      yllapitokohteet
                :jos-tyhja     "Ei valittavia kohteita"
                :valinta-nayta (fn [arvo voi-muokata?]
                                 (if arvo
                                   (tierekisteri/yllapitokohde-tekstina
                                     arvo
                                     {:osoite {:tr-numero        (:tr-numero arvo)
                                               :tr-alkuosa       (:tr-alkuosa arvo)
                                               :tr-alkuetaisyys  (:tr-alkuetaisyys arvo)
                                               :tr-loppuosa      (:tr-loppuosa arvo)
                                               :tr-loppuetaisyys (:tr-loppuetaisyys arvo)}})
                                   (if voi-muokata?
                                     "- Valitse kohde -"
                                     "")))
                :validoi       [[:ei-tyhja "Anna sanktion kohde"]]}
               {:otsikko     "Kohde" :tyyppi :string :nimi :kohde
                :hae         (comp :kohde :laatupoikkeama)
                :aseta       (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :kohde] arvo))
                :palstoja    1
                :pakollinen? true
                :muokattava? (constantly voi-muokata?)
                :validoi     [[:ei-tyhja "Anna sanktion kohde"]]})

             {:otsikko       "Käsitelty" :nimi :kasittelytapa
              :pakollinen?   true
              :hae           (comp :kasittelytapa :paatos :laatupoikkeama)
              :aseta         #(assoc-in %1 [:laatupoikkeama :paatos :kasittelytapa] %2)
              :tyyppi        :valinta
              :valinnat      [:tyomaakokous :puhelin :kommentit :muu]
              :valinta-nayta #(if % (case %
                                      :tyomaakokous "Työmaakokous"
                                      :puhelin "Puhelimitse"
                                      :kommentit "Harja-kommenttien perusteella"
                                      :muu "Muu tapa"
                                      nil) "- valitse käsittelytapa -")
              :palstoja      1}

             (when yllapito?
               {:otsikko       "Puute tai laiminlyönti"
                :nimi          :vakiofraasi
                :tyyppi        :valinta
                :valinta-arvo  first
                :valinta-nayta second
                :valinnat      sanktio-domain/+yllapidon-sanktiofraasit+
                :palstoja      2}
               )
             {:otsikko     "Perustelu" :nimi :perustelu
              :pakollinen? true
              :hae         (comp :perustelu :paatos :laatupoikkeama)
              :aseta       (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :paatos :perustelu] arvo))
              :palstoja    2 :tyyppi :text :koko [80 :auto]
              :validoi     [[:ei-tyhja "Anna perustelu"]]}

             (when (= :muu (get-in @muokattu [:laatupoikkeama :paatos :kasittelytapa]))
               {:otsikko "Muu käsittelytapa" :nimi :muukasittelytapa
                :hae     (comp :muukasittelytapa :paatos :laatupoikkeama)
                :aseta   (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :paatos :muukasittelytapa] arvo))
                :leveys  2 :tyyppi :string
                :validoi [[:ei-tyhja "Anna lyhyt kuvaus käsittelytavasta."]]})

             {:otsikko       "Laji" :tyyppi :valinta :pakollinen? true
              :palstoja      1 :uusi-rivi? true :nimi :laji
              :hae           (comp keyword :laji)
              :aseta         (fn [rivi arvo]
                               (let [paivitetty (assoc rivi :laji arvo :tyyppi nil)]
                                 (if-not (sanktio-domain/sakko? paivitetty)
                                   (assoc paivitetty :summa nil :toimenpideinstanssi nil :indeksi nil)
                                   paivitetty)))
              :valinnat      mahdolliset-sanktiolajit
              :valinta-nayta #(case %
                                :A "Ryhmä A"
                                :B "Ryhmä B"
                                :C "Ryhmä C"
                                :muistutus "Muistutus"
                                :yllapidon_muistutus "Muistutus"
                                :yllapidon_sakko "Sakko"
                                :yllapidon_bonus "Bonus"
                                "- valitse laji -")
              :validoi       [[:ei-tyhja "Valitse laji"]]}

             (when-not yllapito?
               {:otsikko       "Tyyppi" :tyyppi :valinta
                :palstoja      1
                :pakollinen?   true
                :nimi          :tyyppi
                :aseta         (fn [sanktio {tpk :toimenpidekoodi :as tyyppi}]
                                 (assoc sanktio
                                   :tyyppi tyyppi
                                   :toimenpideinstanssi
                                   (when tpk
                                     (:tpi_id (tiedot-urakka/urakan-toimenpideinstanssi-toimenpidekoodille tpk)))))
                ;; TODO: Kysely ei palauta sanktiotyyppien lajeja, joten tässä se pitää dissocata. Onko ok? Laatupoikkeamassa käytetään.
                :valinnat-fn   (fn [_] (map #(dissoc % :laji) (sanktiot/lajin-sanktiotyypit (:laji @muokattu))))
                :valinta-nayta #(if % (:nimi %) " - valitse tyyppi -")
                :validoi       [[:ei-tyhja "Valitse sanktiotyyppi"]]})

             (when (sanktio-domain/sakko? @muokattu)
               {:otsikko     "Summa" :nimi :summa :palstoja 1 :tyyppi :positiivinen-numero
                :hae         #(when (:summa %) (Math/abs (:summa %)))
                :pakollinen? true :uusi-rivi? true :yksikko "€"
                :validoi     [[:ei-tyhja "Anna summa"]]})

             (when (and (sanktio-domain/sakko? @muokattu) (urakka/indeksi-kaytossa?))
               {:otsikko       "Indeksi" :nimi :indeksi :leveys 2
                :tyyppi        :valinta
                :valinnat      ["MAKU 2005" "MAKU 2010"]
                :valinta-nayta #(or % "Ei sidota indeksiin")
                :palstoja      1})

             (when (and (sanktio-domain/sakko? @muokattu))
               {:otsikko       "Toimenpide"
                :pakollinen?   true
                :nimi          :toimenpideinstanssi
                :tyyppi        :valinta
                :valinta-arvo  :tpi_id
                :valinta-nayta #(if % (:tpi_nimi %) " - valitse toimenpide -")
                :valinnat      @tiedot-urakka/urakan-toimenpideinstanssit
                :palstoja      1
                :validoi       [[:ei-tyhja "Valitse toimenpide, johon sanktio liittyy"]]})]
            @muokattu]
           [ajax-loader "Ladataan..."])]))))

(defn sanktiolistaus
  [optiot valittu-urakka]
  (let [sanktiot (reverse (sort-by :perintapvm @tiedot/haetut-sanktiot))
        yllapito? (:yllapito? optiot)]
    [:div.sanktiot
     [urakka-valinnat/urakan-hoitokausi valittu-urakka]
     (let [oikeus? (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-sanktiot
                                             (:id valittu-urakka))]
       (yleiset/wrap-if
         (not oikeus?)
         [yleiset/tooltip {} :%
          (oikeudet/oikeuden-puute-kuvaus :kirjoitus
                                          oikeudet/urakat-laadunseuranta-sanktiot)]
         [napit/uusi "Lisää sanktio"
          #(reset! tiedot/valittu-sanktio (tiedot/uusi-sanktio (:tyyppi valittu-urakka)))
          {:disabled (not oikeus?)}]))

     [grid/grid
      {:otsikko       "Sanktiot"
       :tyhja         (if @tiedot/haetut-sanktiot "Ei löytyneitä tietoja" [ajax-loader "Haetaan sanktioita."])
       :rivi-klikattu #(reset! tiedot/valittu-sanktio %)}
      [{:otsikko "Päivä\u00ADmäärä" :nimi :perintapvm :fmt pvm/pvm-aika :leveys 1}
       (if yllapito?
         {:otsikko "Yllä\u00ADpito\u00ADkoh\u00ADde" :nimi :kohde :leveys 2
          :hae     (fn [rivi]
                     (if (get-in rivi [:yllapitokohde :id])
                       (tierekisteri/yllapitokohde-tekstina {:kohdenumero (get-in rivi [:yllapitokohde :numero])
                                                             :nimi        (get-in rivi [:yllapitokohde :nimi])})
                       (get-in rivi [:laatupoikkeama :kohde])))}
         {:otsikko "Kohde" :nimi :kohde :hae (comp :kohde :laatupoikkeama) :leveys 1})
       {:otsikko "Perus\u00ADtelu" :nimi :kuvaus :hae (comp :perustelu :paatos :laatupoikkeama) :leveys 3}
       (if yllapito?
         {:otsikko "Puute tai laiminlyönti" :nimi :vakiofraasi
          :hae     #(sanktio-domain/yllapidon-sanktiofraasin-nimi (:vakiofraasi %)) :leveys 3}
         {:otsikko "Tyyppi" :nimi :sanktiotyyppi :hae (comp :nimi :tyyppi) :leveys 3})
       {:otsikko "Tekijä" :nimi :tekija :hae (comp :tekijanimi :laatupoikkeama) :leveys 1}
       {:otsikko "Summa €" :nimi :summa :leveys 1 :tyyppi :numero :tasaa :oikea
        :hae     #(or (:summa %) "Muistutus")}]
      sanktiot]]))

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
                            :yllapito? @tiedot-urakka/yllapidon-urakka?})]
         (if @tiedot/valittu-sanktio
           [sanktion-tiedot optiot]
           [sanktiolistaus optiot @nav/valittu-urakka]))])))
