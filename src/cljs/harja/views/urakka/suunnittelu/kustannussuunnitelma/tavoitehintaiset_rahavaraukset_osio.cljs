(ns harja.views.urakka.suunnittelu.kustannussuunnitelma.tavoitehintaiset-rahavaraukset-osio
  (:require [clojure.string :as clj-str]
            [reagent.core :as r]
            [harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma :as t]
            [harja.ui.grid :as grid]
            [harja.fmt :as fmt]
            [harja.ui.yleiset :as yleiset]
            [harja.views.urakka.suunnittelu.kustannussuunnitelma.yhteiset :as ks-yhteiset :refer [e!]]))

(defn- tavoitehintaiset-rahavaraukset-yhteenveto
  [indeksit yhteensa-summat indeksikorjatut-yhteensa-summat kuluva-hoitokausi kantahaku-valmis?]
  (if (and yhteensa-summat kantahaku-valmis?)
    [:div.summa-ja-indeksilaskuri
     [ks-yhteiset/hintalaskuri {:otsikko "Yhteenveto"
                                :selite ""
                                :hinnat (mapv (fn [summa]
                                                {:summa summa})
                                          yhteensa-summat)
                                :data-cy "tavoitehintaiset-rahavaraukset-hintalaskuri"}
      kuluva-hoitokausi]
     [ks-yhteiset/indeksilaskuri-ei-indeksikorjausta
      (mapv (fn [summa] {:summa summa}) indeksikorjatut-yhteensa-summat)
      indeksit
      {:data-cy "tavoitehintaiset-rahavaraukset-indeksilaskuri"}]]
    [yleiset/ajax-loader]))

(defn tallenna-tavoitehintainen-rahavaraus! [vuosi loppuvuodet? indeksit rivi rivi-id]
  (let [indeksikerroin-vuodelle (:indeksikerroin (first (filter #(= (:vuosi %) vuosi) indeksit)))]
    (e! (t/->TallennaTavoitehintainenRahavaraus rivi-id (:summa rivi) (* indeksikerroin-vuodelle (:summa rivi)) vuosi loppuvuodet?))))

;; Tehdään tavanomainen taulukko rahavarausten näyttämiselle
(defn tavoitehintaiset-rahavaraukset-taulukko [rivit vuosi loppuvuodet? vahvistettu? indeksit]
  (let [;; Tehdään datasta atomi, jotta muokkausgridi voi muokata sitä - Muokataan datasta muokkausgridille valmis setti
        muokkaus-rahavaraukset (into {} (mapv (fn [rahavaraus]
                                                {(:id rahavaraus) {:jarjestys (:jarjestys rahavaraus)
                                                                   :nimi (:haettu-asia rahavaraus)
                                                                   :summa (:summa rahavaraus)
                                                                   :summa-indeksikorjattu (:summa-indeksikorjattu rahavaraus)}})
                                          rivit))
        rahavaraus-atom (r/atom muokkaus-rahavaraukset)
        ;; Laske yhteenvetoriville valmiiksi summat
        yhteensa-summat (reduce + 0 (map :summa rivit))
        yhteensa-indeksisummat (reduce + 0 (map :summa-indeksikorjattu rivit))]
    [:div
     [grid/muokkaus-grid
      {:id "tavoitehintaiset-rahavaraukset-grid"
       :otsikko "Tavoitehintaan vaikuttavat rahavaraukset"
       :otsikko-tyyli {:font-size "1.2rem"}
       :voi-muokata? (if vahvistettu? false true)
       :voi-poistaa? (constantly false)
       :voi-lisata? false
       :voi-kumota? false
       :piilota-toiminnot? true
       :jarjesta :jarjestys
       :tyhja "Ei rahavauksia."
       :disabloi-autocomplete? true
       :on-rivi-blur (r/partial tallenna-tavoitehintainen-rahavaraus! vuosi loppuvuodet? indeksit)
       :rivi-jalkeen ^{:luokka "table-default-sum"}
                     [{:teksti "Yhteensä" :luokka "lihavoitu"}
                      {:teksti (str (fmt/euro-opt false yhteensa-summat)) :tasaa :oikea :luokka "lihavoitu"}
                      {:teksti (str (fmt/euro-opt false yhteensa-indeksisummat))
                       :tasaa :oikea :luokka "lihavoitu"}]}

      [{:otsikko "Rahavaraus" :nimi :nimi :leveys "70%" :muokattava? (constantly false)
        :tyyppi :positiivinen-numero :tasaa :vasen}
       {:otsikko "Yhteensä, €/vuosi" :nimi :summa :leveys "15%"
        :tyyppi :euro
        :fmt #(fmt/euro-opt false %)
        :muokattava? (constantly true)
        :tasaa :oikea}
       {:otsikko "Indeksikorjattu" :nimi :summa-indeksikorjattu :leveys "15%" :tyyppi :numero :muokattava? (constantly false) :tasaa :oikea :fmt #(fmt/euro-opt false %)}]
      rahavaraus-atom]]))

(defn tallenna-tavoitehinnan-ulkopuolinen-rahavaraus! [vuosi loppuvuodet? rivi rivi-id]
  (e! (t/->TallennaTavoitehinnanUlkopuolinenRahavaraus rivi-id (:summa rivi) vuosi loppuvuodet?)))

(defn tavoitehinnan-ulkopuoliset-rahavaraukset-taulukko [rivit vuosi loppuvuodet? vahvistettu?]
  (let [;; Tehdään datasta atomi, jotta muokkausgridi voi muokata sitä - Muokataan datasta muokkausgridille valmis setti
        muokkaus-rahavaraukset (into {} (mapv (fn [rahavaraus]
                                                {(:vuosi rahavaraus) {:nimi (:haettu-asia rahavaraus)
                                                                      :summa (:summa rahavaraus)}})
                                          rivit))
        rahavaraus-atom (r/atom muokkaus-rahavaraukset)]
    [:div
     [grid/muokkaus-grid
      {:id "tavoitehintaiset-rahavaraukset-grid"
       :otsikko "Tavoitehinnan ulkopuoliset rahavaraukset"
       :otsikko-tyyli {:font-size "1.2rem"}
       :voi-muokata? (if vahvistettu? false true)
       :voi-poistaa? (constantly false)
       :voi-lisata? false
       :voi-kumota? false
       :piilota-toiminnot? true
       :tyhja "Ei rahavauksia."
       ;:virheet taulukko-virheet
       :disabloi-autocomplete? true
       :on-rivi-blur (r/partial tallenna-tavoitehinnan-ulkopuolinen-rahavaraus! vuosi loppuvuodet?)}

      [{:otsikko "Rahavaraus" :nimi :nimi :leveys "70%" :muokattava? (constantly false)
        :tyyppi :positiivinen-numero :tasaa :vasen}
       {:otsikko "Yhteensä, €/vuosi" :nimi :summa :leveys "15%"
        :tyyppi :euro
        :fmt #(fmt/euro-opt false %)
        :muokattava? (constantly true) :tasaa :oikea}]
      rahavaraus-atom]]))

(defn osio
  [vahvistettu?
   tavoitehintaiset-rahavaraukset-data
   tavoitehintaiset-rahavaraukset-yhteensa
   tavoitehintaiset-rahavaraukset-yhteensa-indeksikorjattu
   tavoitehinnan-ulkopuoliset-rahavaraukset
   indeksit
   kuluva-hoitokausi
   suodattimet
   kantahaku-valmis?
   urakan-alkuvuosi
   urakan-loppuvuosi]

  (let [;; Suodattimet saadaan monissa tapauksissa app-statesta liian myöhään
        valittu-vuosi (if (:hoitokauden-numero suodattimet)
                        (nth (range urakan-alkuvuosi urakan-loppuvuosi) (dec (:hoitokauden-numero suodattimet)))
                        1)
        nayta-tavoitehintaiset-rahavaraukset-grid? (and kantahaku-valmis? tavoitehintaiset-rahavaraukset-data)
        hoitokauden-rahavaraukset (filter #(= (:hoitokauden-numero %) (:hoitokauden-numero suodattimet)) tavoitehintaiset-rahavaraukset-data)
        hoitokauden-tavoitehinnan-ulkopuoliset-rahavaraukset (filter #(= (:hoitokauden-numero %) (:hoitokauden-numero suodattimet)) tavoitehinnan-ulkopuoliset-rahavaraukset)]
    [:<>
     [:h2 {:id (str (get t/hallinnollisten-idt :tavoitehintaiset-rahavaraukset) "-osio")} "Rahavaraukset"]
     [:div [:span "Tilaajan tekemät rahavaraukset, jotka vaikuttavat tavoitehintaan."]]

     [tavoitehintaiset-rahavaraukset-yhteenveto indeksit tavoitehintaiset-rahavaraukset-yhteensa tavoitehintaiset-rahavaraukset-yhteensa-indeksikorjattu
      kuluva-hoitokausi kantahaku-valmis?]

     [:div {:data-cy "rahavaraukset-taulukko-suodattimet"}
      [ks-yhteiset/yleis-suodatin suodattimet]]

     (if nayta-tavoitehintaiset-rahavaraukset-grid?
       [:div
        [tavoitehintaiset-rahavaraukset-taulukko
         hoitokauden-rahavaraukset
         valittu-vuosi
         (:kopioidaan-tuleville-vuosille? suodattimet)
         vahvistettu?
         indeksit]
        [tavoitehinnan-ulkopuoliset-rahavaraukset-taulukko
         hoitokauden-tavoitehinnan-ulkopuoliset-rahavaraukset
         valittu-vuosi
         (:kopioidaan-tuleville-vuosille? suodattimet)
         vahvistettu?]]
       [yleiset/ajax-loader])]))
