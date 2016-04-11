(ns harja.views.urakka.laadunseuranta.sanktiot
  "Sanktioiden listaus"
  (:require [reagent.core :refer [atom]]

            [harja.views.urakka.valinnat :as urakka-valinnat]

            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.urakka.laadunseuranta.sanktiot :as tiedot]
            [harja.tiedot.navigaatio :as nav]

            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.pvm :as pvm]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]

            [harja.loki :refer [log]]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.views.kartta :as kartta]
            [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat :as laatupoikkeamat]
            [harja.tiedot.urakka.laadunseuranta.sanktiot :as sanktiot])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

(defn sanktion-tiedot
  []
  (let [muokattu (atom @tiedot/valittu-sanktio)
        voi-muokata? @laatupoikkeamat/voi-kirjata?
        ]
    (fn []
      [:div
       [napit/takaisin "Takaisin sanktioluetteloon" #(reset! tiedot/valittu-sanktio nil)]

       

       [lomake/lomake
        {:otsikko (if (:id @muokattu)
                    (if (:suorasanktio @muokattu)
                      "Muokkaa suoraa sanktiota"
                      "Muokkaa laatupoikkeaman kautta tehtyä sanktiota")
                    "Luo uusi suora sanktio")
         :luokka   :horizontal
         :muokkaa! #(reset! muokattu %)
         :voi-muokata? voi-muokata?
         :footer   [napit/palvelinkutsu-nappi
                    "Tallenna sanktio"
                    #(tiedot/tallenna-sanktio @muokattu)
                    {:luokka       "nappi-ensisijainen"
                     :ikoni        (ikonit/tallenna)
                     :kun-onnistuu #(do
                                     (tiedot/sanktion-tallennus-onnistui % @muokattu)
                                     (reset! tiedot/valittu-sanktio nil))
                     :disabled     (not (lomake/voi-tallentaa? @muokattu))}]}
        [{:otsikko     "Tekijä" :nimi :tekijanimi
          :hae         (comp :tekijanimi :laatupoikkeama)
          :aseta       (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :tekijanimi] arvo))
          :leveys      1 :tyyppi :string
          :muokattava? (constantly false)}

         ;; TODO Mitkä päivämäärät tarvitaan?
         (lomake/ryhma {:rivi? true}
                       {:otsikko "Havaittu" :nimi :laatupoikkeamaaika
                        :pakollinen? true
                        :hae     (comp :aika :laatupoikkeama)
                        :aseta   (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :aika] arvo))
                        :fmt     pvm/pvm-aika :leveys 1 :tyyppi :pvm
                        :validoi [[:ei-tyhja "Valitse päivämäärä"]]
                        :huomauta [[:urakan-aikana-ja-hoitokaudella]]}
                       {:otsikko "Käsitelty" :nimi :kasittelyaika
                        :pakollinen? true
                        :hae     (comp :kasittelyaika :paatos :laatupoikkeama)
                        :aseta   (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :paatos :kasittelyaika] arvo))
                        :fmt     pvm/pvm-aika :leveys 1 :tyyppi :pvm
                        :validoi [[:ei-tyhja "Valitse päivämäärä"]
                                  [:pvm-kentan-jalkeen (comp :aika :laatupoikkeama) "Ei voida käsitellä laatupoikkeamaa ennen"]]}
                       {:otsikko "Perintäpvm" :nimi :perintapvm
                        :pakollinen? true
                        :fmt     pvm/pvm-aika :leveys 1 :tyyppi :pvm
                        :validoi [[:ei-tyhja "Valitse päivämäärä"]
                                  [:pvm-kentan-jalkeen (comp :kasittelyaika :paatos :laatupoikkeama)
                                   "Ei voida periä käsittelyä ennen"]]})
         
         {:otsikko "Kohde" :nimi :kohde
          :hae     (comp :kohde :laatupoikkeama)
          :aseta   (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :kohde] arvo))
          :palstoja 1 :tyyppi :string}

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

         {:otsikko "Perustelu" :nimi :perustelu
          :pakollinen? true
          :hae     (comp :perustelu :paatos :laatupoikkeama)
          :aseta   (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :paatos :perustelu] arvo))
          :palstoja 2 :tyyppi :text :koko [80 :auto]
          :validoi [[:ei-tyhja "Anna perustelu"]]}

         (when (= :muu (get-in @muokattu [:laatupoikkeama :paatos :kasittelytapa]))
           {:otsikko "Muu käsittelytapa" :nimi :muukasittelytapa
            :hae     (comp :muukasittelytapa :paatos :laatupoikkeama)
            :aseta   (fn [rivi arvo] (assoc-in rivi [:laatupoikkeama :paatos :muukasittelytapa] arvo))
            :leveys  2 :tyyppi :string
            :validoi [[:ei-tyhja "Anna lyhyt kuvaus käsittelytavasta."]]})

         {:otsikko "Summa" :nimi :summa :palstoja 1 :tyyppi :positiivinen-numero
          :pakollinen? true
          :yksikko "€"
          :validoi [[:ei-tyhja "Anna summa"]]}
         {:otsikko "Indeksi" :nimi :indeksi :leveys 2
          :tyyppi :valinta
          :valinnat ["MAKU 2005" "MAKU 2010"]
          :valinta-nayta #(or % "Ei sidota indeksiin")
          :palstoja 1}

         {:otsikko       "Laji" :tyyppi :valinta
          :pakollinen? true
          :palstoja 1
          :nimi          :laji
          :hae           (comp keyword :laji)
          :aseta         #(assoc %1 :laji %2 :tyyppi nil)
          :valinnat      [:A :B :C]
          :valinta-nayta #(case %
                            :A "Ryhmä A"
                            :B "Ryhmä B"
                            :C "Ryhmä C"
                            "- valitse -")
          :validoi       [[:ei-tyhja "Valitse laji"]]}

         {:otsikko       "Tyyppi" :tyyppi :valinta
          :palstoja 1
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
          :valinta-nayta :nimi
          :validoi       [[:ei-tyhja "Valitse sanktiotyyppi"]]}
         {:otsikko       "Toimenpide"
          :pakollinen?   true
          :nimi          :toimenpideinstanssi
          :tyyppi        :valinta
          :valinta-arvo  :tpi_id
          :valinta-nayta :tpi_nimi
          :valinnat   @tiedot-urakka/urakan-toimenpideinstanssit
          :palstoja 1
          :validoi       [[:ei-tyhja "Valitse toimenpide, johon sanktio liittyy"]]}]
        @muokattu]])))

(defn sanktiolistaus
  []
  (let [sanktiot (reverse (sort-by :perintapvm @tiedot/haetut-sanktiot))]
    [:div.sanktiot
    [urakka-valinnat/urakan-hoitokausi @nav/valittu-urakka]
    (when @laatupoikkeamat/voi-kirjata?
      [:button.nappi-ensisijainen
       {:on-click #(reset! tiedot/valittu-sanktio @tiedot/+uusi-sanktio+)}
       (ikonit/plus) " Lisää sanktio"])

    [grid/grid
     {:otsikko       "Sanktiot"
      :tyhja         (if @tiedot/haetut-sanktiot "Ei löytyneitä tietoja" [ajax-loader "Haetaan sanktioita."])
      :rivi-klikattu #(reset! tiedot/valittu-sanktio %)}
     [{:otsikko "Päivä\u00ADmäärä" :nimi :perintapvm :fmt pvm/pvm-aika :leveys 1}
      {:otsikko "Kohde" :nimi :kohde :hae (comp :kohde :laatupoikkeama) :leveys 1}
      {:otsikko "Perus\u00ADtelu" :nimi :kuvaus :hae (comp :perustelu :paatos :laatupoikkeama) :leveys 3}
      {:otsikko "Tyyppi" :nimi :sanktiotyyppi :hae (comp :nimi :tyyppi) :leveys 3}
      {:otsikko "Tekijä" :nimi :tekija :hae (comp :tekijanimi :laatupoikkeama) :leveys 1}
      {:otsikko "Summa €" :nimi :summa :leveys 1 :tyyppi :numero}]
     sanktiot]]))


(defn sanktiot []
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (komp/sisaan-ulos #(do
                        (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                        (nav/vaihda-kartan-koko! :S))
                      #(nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko))
    (fn []
      [:span
       [kartta/kartan-paikka]
       (if @tiedot/valittu-sanktio
         [sanktion-tiedot]
         [sanktiolistaus])])))
