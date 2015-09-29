(ns harja.views.urakka.laadunseuranta.sanktiot
  "Sanktioiden listaus"
  (:require [reagent.core :refer [atom]]

            [harja.views.urakka.valinnat :as urakka-valinnat]

            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.urakka.sanktiot :as tiedot]
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
            [harja.views.kartta :as kartta])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

(defn sanktion-tiedot
  []
  (let [muokattu (atom @tiedot/valittu-sanktio)
        lomakkeen-virheet (atom {})
        voi-muokata? @laadunseuranta/voi-kirjata?
        voi-tallentaa? (reaction (and
                                  voi-muokata?
                                   (= (count @lomakkeen-virheet) 0)
                                   (> (count @muokattu) (count @tiedot/+uusi-sanktio+))))]
    (fn []
      [:div
       [napit/takaisin "Takaisin sanktioluetteloon" #(reset! tiedot/valittu-sanktio nil)]

       (if (:id @muokattu)
         (if (:suorasanktio @muokattu)
           [:h3 "Muokkaa suoraa sanktiota"]

           [:h3 "Muokkaa havainnon kautta tehtyä sanktiota"])

         [:h3 "Luo uusi suora sanktio"])

       [lomake/lomake
        {:luokka   :horizontal
         :muokkaa! #(reset! muokattu %)
         :virheet lomakkeen-virheet
         :voi-muokata? voi-muokata?
         :footer   [napit/palvelinkutsu-nappi
                    "Tallenna sanktio"
                    #(tiedot/tallenna-sanktio @muokattu)
                    {:luokka       "nappi-ensisijainen"
                     :ikoni        (ikonit/tallenna)
                     :kun-onnistuu #(do
                                     (tiedot/sanktion-tallennus-onnistui % @muokattu)
                                     (reset! tiedot/valittu-sanktio nil))
                     :disabled     (not @voi-tallentaa?)}]}
        [{:otsikko     "Tekijä" :nimi :tekijanimi
          :hae         (comp :tekijanimi :havainto)
          :aseta       (fn [rivi arvo] (assoc-in rivi [:havainto :tekijanimi] arvo))
          :leveys      1 :tyyppi :string
          :muokattava? (constantly false)}

         ;; TODO Mitkä päivämäärät tarvitaan?
         (lomake/ryhma {:otsikko "Aika" :ulkoasu :rivi :leveys 2}
                       {:otsikko "Havaittu" :nimi :havaintoaika
                        :pakollinen? true
                        :hae     (comp :aika :havainto)
                        :aseta   (fn [rivi arvo] (assoc-in rivi [:havainto :aika] arvo))
                        :fmt     pvm/pvm-aika :leveys 1 :tyyppi :pvm
                        :validoi [[:ei-tyhja "Valitse päivämäärä"]]
                        :varoita [[:urakan-aikana-ja-hoitokaudella]]}
                       {:otsikko "Käsitelty" :nimi :kasittelyaika
                        :pakollinen? true
                        :hae     (comp :kasittelyaika :paatos :havainto)
                        :aseta   (fn [rivi arvo] (assoc-in rivi [:havainto :paatos :kasittelyaika] arvo))
                        :fmt     pvm/pvm-aika :leveys 1 :tyyppi :pvm
                        :validoi [[:ei-tyhja "Valitse päivämäärä"]
                                  [:pvm-kentan-jalkeen (comp :aika :havainto) "Ei voida käsitellä havaintoa ennen"]]}
                       {:otsikko "Perintäpvm" :nimi :perintapvm
                        :pakollinen? true
                        :fmt     pvm/pvm-aika :leveys 1 :tyyppi :pvm
                        :validoi [[:ei-tyhja "Valitse päivämäärä"]
                                  [:pvm-kentan-jalkeen (comp :kasittelyaika :paatos :havainto)
                                   "Ei voida periä käsittelyä ennen"]]})

         ;; Päätös on aina sanktio
         #_{:otsikko "Päätös" :nimi :paatos
          :hae     (comp :paatos :paatos :havainto)
          :aseta   (fn [rivi arvo] (assoc-in rivi [:havainto :paatos :paatos] arvo))
          :leveys-col  3 :tyyppi :string
          :muokattava? (constantly false)}
         {:otsikko "Perustelu" :nimi :perustelu
          :pakollinen? true
          :hae     (comp :perustelu :paatos :havainto)
          :aseta   (fn [rivi arvo] (assoc-in rivi [:havainto :paatos :perustelu] arvo))
          :leveys-col  4 :tyyppi :text :koko [80 :auto]
          :validoi [[:ei-tyhja "Anna perustelu"]]}
         {:otsikko "Kohde" :nimi :kohde
          :hae     (comp :kohde :havainto)
          :aseta   (fn [rivi arvo] (assoc-in rivi [:havainto :kohde] arvo))
          :leveys-col  4 :tyyppi :string}

         ;; Ei havainnon kuvausta, koska annetaan suoraan perustelu
         #_{:otsikko "Kuvaus" :nimi :kuvaus
          :hae     (comp :kuvaus :havainto)
          :aseta   (fn [rivi arvo] (assoc-in rivi [:havainto :kuvaus] arvo))
          :leveys  3 :tyyppi :string}

         ;; TODO, eikö tekijän tyyppiä oikeasti tarvita? Hard-koodataanko?
         #_{:otsikko "Tekijätyyppi" :nimi :tekija
           :hae     (comp :tekija :havainto)
           :aseta   (fn [rivi arvo] (assoc-in rivi [:havainto :tekija] arvo))
           :leveys  1 :tyyppi :string}
         {:otsikko "Käsitelty" :nimi :kasittelytapa
          :pakollinen? true
          :hae (comp :kasittelytapa :paatos :havainto)
          :aseta #(assoc-in %1 [:havainto :paatos :kasittelytapa] %2)
          :tyyppi :valinta
          :valinnat [:tyomaakokous :puhelin :kommentit :muu]
          :valinta-nayta #(if % (case %
                                  :tyomaakokous "Työmaakokous"
                                  :puhelin "Puhelimitse"
                                  :kommentit "Harja-kommenttien perusteella"
                                  :muu "Muu tapa"
                                  nil) "- valitse käsittelytapa -")
          :leveys-col 4}

         (when (= :muu (get-in @muokattu [:havainto :paatos :kasittelytapa]))
           {:otsikko "Muu käsittelytapa" :nimi :muukasittelytapa
            :hae     (comp :muukasittelytapa :paatos :havainto)
            :aseta   (fn [rivi arvo] (assoc-in rivi [:havainto :paatos :muukasittelytapa] arvo))
            :leveys  2 :tyyppi :string
            :validoi [[:ei-tyhja "Anna lyhyt kuvaus käsittelytavasta."]]})

         (lomake/ryhma {:otsikko "Sanktio" :leveys 2 :ulkoasu :rivi}
                       {:otsikko "Summa" :nimi :summa :leveys-col 2 :tyyppi :positiivinen-numero
                        :pakollinen? true
                        :yksikko "€"
                        :validoi [[:ei-tyhja "Anna summa"]]}
                       {:otsikko "Indeksi" :nimi :indeksi :leveys 2
                        :tyyppi :valinta
                        :valinnat ["MAKU 2005" "MAKU 2010"]
                        :valinta-nayta #(or % "Ei sidota indeksiin")
                        :leveys-col 3})

         ;; TODO: Pitäisikö lomakkeessa kuitenkin näyttää, onko tämä sanktio tehty suoraan vai ei?
         #_{:otsikko "Suora sanktio?" :nimi :suorasanktio :leveys 2 :tyyppi :string}

         (lomake/ryhma {:otsikko "Luokittelu"  :ulkoasu :rivi}
                       {:otsikko       "Laji" :tyyppi :valinta
                        :pakollinen? true
                        :leveys-col 2
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
                        :leveys-col    6
                        :pakollinen?   true
                        :nimi          :tyyppi
                        :aseta         (fn [sanktio tyyppi]
                                         (assoc sanktio
                                           :tyyppi tyyppi
                                           :toimenpideinstanssi (:tpi_id (first
                                                                           (filter
                                                                             #(= (:toimenpidekoodi tyyppi) (:id %))
                                                                                @tiedot-urakka/urakan-toimenpideinstanssit)))))
                        ;; TODO: Kysely ei palauta sanktiotyyppien lajeja, joten tässä se pitää dissocata. Onko ok? Havainnossa käytetään.
                        :valinnat-fn   (fn [_] (map #(dissoc % :laji) (laadunseuranta/lajin-sanktiotyypit (:laji @muokattu))))
                        :valinta-nayta :nimi
                        :validoi       [[:ei-tyhja "Valitse sanktiotyyppi"]]})
         #_{:otsikko "Toimenpidekoodi" :nimi :toimenpidekoodi
          :hae     (comp :toimenpidekoodi :tyyppi)
          :aseta   (fn [rivi arvo] (assoc-in rivi [:tyyppi :toimenpidekoodi] arvo))
          :leveys  1 :tyyppi :string}]
        @muokattu]])))

(defn sanktiolistaus
  []
  [:div.sanktiot
   [urakka-valinnat/urakan-hoitokausi @nav/valittu-urakka]
   (when @laadunseuranta/voi-kirjata?
     [:button.nappi-ensisijainen
      {:on-click #(reset! tiedot/valittu-sanktio @tiedot/+uusi-sanktio+)}
      (ikonit/plus) " Lisää sanktio"])

   [grid/grid
    {:otsikko       "Sanktiot"
     :tyhja         (if @tiedot/haetut-sanktiot "Ei löytyneitä tietoja" [ajax-loader "Haetaan sanktioita."])
     :rivi-klikattu #(reset! tiedot/valittu-sanktio %)}
    [{:otsikko "Päivämäärä" :nimi :perintapvm :fmt pvm/pvm-aika :leveys 1}
     {:otsikko "Kohde" :nimi :kohde :hae (comp :kohde :havainto) :leveys 1}
     {:otsikko "Perustelu" :nimi :kuvaus :hae (comp :perustelu :paatos :havainto) :leveys 3}
     {:otsikko "Tyyppi" :nimi :sanktiotyyppi :hae (comp :nimi :tyyppi) :leveys 3}
     {:otsikko "Tekijä" :nimi :tekija :hae (comp :tekijanimi :havainto) :leveys 1}
     {:otsikko "Summa" :nimi :summa :leveys 1 :tyyppi :numero}]
    @tiedot/haetut-sanktiot
    ]])


(defn sanktiot []
  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (fn []
      [:span
       [kartta/kartan-paikka]
       (if @tiedot/valittu-sanktio
         [sanktion-tiedot]
         [sanktiolistaus])])))