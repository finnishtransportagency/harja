(ns harja.views.urakka.suunnittelu.kustannussuunnitelma.tavoitehintaiset-rahavaraukset-osio
  (:require [reagent.core :as r]
            [harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma :as t]
            [harja.ui.grid :as grid]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
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
                                :data-cy "erillishankinnat-hintalaskuri"}
      kuluva-hoitokausi]
     [ks-yhteiset/indeksilaskuri-ei-indeksikorjausta
      (mapv (fn [summa] {:summa summa}) indeksikorjatut-yhteensa-summat)
      indeksit
      {:data-cy "erillishankinnat-indeksilaskuri"}]]
    [yleiset/ajax-loader]))

(defn tallenna! [vuosi loppuvuodet? indeksit rivi rivi-id]
  (let [indeksikerroin-vuodelle (:indeksikerroin (first (filter #(= (:vuosi %) vuosi) indeksit)))]
    (e! (t/->TallennaTavoitehintainenRahavaraus rivi-id (:summa rivi) (* indeksikerroin-vuodelle (:summa rivi)) vuosi loppuvuodet?))))

;; TODO: Hox. virheiden käsittelyä ei ole vielä tehty
(defonce taulukko-virheet (r/atom {}))

;; Tehdään tavanomainen taulukko rahavarausten näyttämiselle
(defn tavoitehintaiset-rahavaraukset-taulukko [rivit vuosi loppuvuodet? vahvistettu? indeksit]
  (let [;; Tehdään datasta atomi, jotta muokkausgridi voi muokata sitä - Muokataan datasta muokkausgridille valmis setti
        muokkaus-rahavaraukset (into {} (mapv (fn [rahavaraus]
                                                {(:id rahavaraus) {:nimi (:haettu-asia rahavaraus)
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
       :voi-muokata? (if vahvistettu? false true)
       :voi-poistaa? (constantly false)
       :voi-lisata? false
       :voi-kumota? false
       :piilota-toiminnot? true
       :tyhja "Ei rahavauksia."
       :virheet taulukko-virheet
       :disabloi-autocomplete? true
       :on-rivi-blur (r/partial tallenna! vuosi loppuvuodet? indeksit)
       :rivi-jalkeen ^{:luokka "table-default-sum"}
                     [{:teksti "Yhteensä" :luokka "lihavoitu"}
                      {:teksti (str (fmt/euro-opt false yhteensa-summat)) :tasaa :oikea :luokka "lihavoitu"}
                      {:teksti (str (fmt/euro-opt false yhteensa-indeksisummat))
                       :tasaa :oikea :luokka "lihavoitu"}]}

      [{:otsikko "Rahavaraus" :nimi :nimi :leveys "70%" :muokattava? (constantly false)
        :tyyppi :positiivinen-numero :tasaa :vasen}
       {:otsikko "Yhteensä, €/vuosi" :nimi :summa :leveys "15%" :tyyppi :positiivinen-numero :muokattava? (constantly true) :tasaa :oikea :fmt fmt/euro-opt}
       {:otsikko "Indeksikorjattu" :nimi :summa-indeksikorjattu :leveys "15%" :tyyppi :numero :muokattava? (constantly false) :tasaa :oikea}]
      rahavaraus-atom]]))

(defn osio
  [vahvistettu?
   tavoitehintaiset-rahavaraukset-data
   tavoitehintaiset-rahavaraukset-yhteensa
   tavoitehintaiset-rahavaraukset-yhteensa-indeksikorjattu
   indeksit
   kuluva-hoitokausi
   suodattimet
   kantahaku-valmis?]

  (let [nayta-tavoitehintaiset-rahavaraukset-grid? (and kantahaku-valmis? tavoitehintaiset-rahavaraukset-data)
        hoitokauden-rahavaraukset (filter #(= (:hoitokauden-numero %) (:hoitokauden-numero suodattimet)) tavoitehintaiset-rahavaraukset-data)]
    [:<>
     [:h2 {:id (str (get t/hallinnollisten-idt :tavoitehintaiset-rahavaraukset) "-osio")} "Rahavaraukset"]
     [:div [:span "Tilaajan tekemät rahavaraukset, jotka vaikuttavat tavoitehintaan."]]

     [tavoitehintaiset-rahavaraukset-yhteenveto indeksit tavoitehintaiset-rahavaraukset-yhteensa tavoitehintaiset-rahavaraukset-yhteensa-indeksikorjattu
      kuluva-hoitokausi kantahaku-valmis?]

     [:div {:data-cy "rahavaraukset-taulukko-suodattimet"}
      [ks-yhteiset/yleis-suodatin suodattimet]]

     (if nayta-tavoitehintaiset-rahavaraukset-grid?
       [tavoitehintaiset-rahavaraukset-taulukko
        hoitokauden-rahavaraukset
        (pvm/vuosi (first (:pvmt kuluva-hoitokausi)))
        (:kopioidaan-tuleville-vuosille? suodattimet)
        vahvistettu?
        indeksit]
       [yleiset/ajax-loader])]))
