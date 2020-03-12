(ns harja.views.urakka.kulut
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [goog.string :as gstring]
            [goog.string.format]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.mhu-laskutus :as tiedot]
            [harja.ui.debug :as debug]
            [harja.ui.komponentti :as komp]
            [harja.ui.taulukko.taulukko :as taulukko]
            [harja.ui.taulukko.jana :as jana]
            [harja.ui.taulukko.osa :as osa]
            [harja.ui.taulukko.tyokalut :as tyokalu]
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.pvm :as pvm-valinta]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.ui.modal :as modal]
            [harja.ui.liitteet :as liitteet]
            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [harja.loki :as loki]
            [harja.ui.kentat :as kentat]
            [clojure.string :as str])
  (:require-macros [harja.ui.taulukko.tyokalut :refer [muodosta-taulukko]]))

(defn- lomakkeen-osio [otsikko & osiot]
  (into [:div.col-xs-12.col-sm-6
         [:h2 otsikko]]
        osiot))

(defn- validi-ei-tarkistettu-tai-ei-koskettu? [{:keys [koskettu? validi? tarkistettu?]}]
  (cond
    (and (false? validi?)
         (true? tarkistettu?)) false
    :else true))

(defn aliurakoitsija-modaali
  [_ _]
  (let [tila (r/atom {})]
    (fn [tallennus-fn sulku-fn]
      (let [{:keys [nimi ytunnus]} @tila]
        [:div.peitto-modal
         [:div.peitto-kontentti
          [:h1 "Lisää aliurakoitsija"
           [:button {:on-click sulku-fn}
            [ikonit/remove]]]
          [kentat/vayla-lomakekentta
           "Yrityksen nimi"
           :arvo nimi
           :on-change #(swap! tila assoc :nimi (-> % .-target .-value))]
          [kentat/vayla-lomakekentta
           "Y-tunnus"
           :arvo ytunnus
           :on-change #(swap! tila assoc :ytunnus (-> % .-target .-value))]
          [:div.napit
           [napit/tallenna
            "Tallenna"
            #(tallennus-fn @tila)
            {:ikoni        ikonit/ok
             :vayla-tyyli? true}]
           [napit/sulje
            "Sulje"
            sulku-fn
            {:vayla-tyyli? true
             :ikoni        ikonit/remove}]]]]))))

(def kuukaudet-strs {:tammikuu  "Tammikuu"
                     :helmikuu  "Helmikuu"
                     :maaliskuu "Maaliskuu"
                     :huhtikuu  "Huhtikuu"
                     :toukokuu  "Toukokuu"
                     :kesakuu   "Kesäkuu"
                     :heinakuu  "Heinäkuu"
                     :elokuu    "Elokuu"
                     :syyskuu   "Syyskuu"
                     :lokakuu   "Lokakuu"
                     :marraskuu "Marraskuu"
                     :joulukuu  "Joulukuu"})

(def hoitovuodet-strs {:1-hoitovuosi "1. hoitovuosi"
                       :2-hoitovuosi "2. hoitovuosi"
                       :3-hoitovuosi "3. hoitovuosi"
                       :4-hoitovuosi "4. hoitovuosi"
                       :5-hoitovuosi "5. hoitovuosi"})

(defn lisaa-kohdistus [m]
  (conj m
        {:tehtavaryhma        nil
         :toimenpideinstanssi nil
         :summa               nil
         :rivi                (count m)}))

(defonce kuukaudet [:lokakuu :marraskuu :joulukuu :tammikuu :helmikuu :maaliskuu :huhtikuu :toukokuu :kesakuu :heinakuu :elokuu :syyskuu])

(defn paivamaaran-valinta
  [{:keys [paivitys-fn erapaiva erapaiva-meta disabled]}]
  [pvm-valinta/pvm-valintakalenteri-inputilla {:valitse       #(paivitys-fn {:validoitava? true} :erapaiva %)
                                               :luokat        #{(str "input" (if (validi-ei-tarkistettu-tai-ei-koskettu? erapaiva-meta) "" "-error") "-default") "komponentin-input"}
                                               :pvm           erapaiva
                                               :pakota-suunta false
                                               :disabled      disabled
                                               :valittava?-fn #(true? true)}]) ;pvm/jalkeen? % (pvm/nyt) --- otetaan käyttöön "joskus"


(defn koontilaskun-kk-droppari
  [{:keys [koontilaskun-kuukausi paivitys-fn koontilaskun-kuukausi-meta disabled]}]
  [yleiset/livi-pudotusvalikko
   {:virhe?       (not (validi-ei-tarkistettu-tai-ei-koskettu? koontilaskun-kuukausi-meta))
    :disabled     disabled
    :vayla-tyyli? true
    :valinta      koontilaskun-kuukausi
    :valitse-fn   #(paivitys-fn {:validoitava? true}
                                :koontilaskun-kuukausi %)
    :format-fn    (fn [a]
                    (if (nil? a)
                      "Ei valittu"
                      (let [[kk hv] (str/split a #"/")]
                        (str (get kuukaudet-strs (keyword kk)) " - "
                             (get hoitovuodet-strs (keyword hv))))))}
   (for [hv (range 1 6)
         kk kuukaudet]
     (str (name kk) "/" hv "-hoitovuosi"))])

(defn laskun-tiedot
  [{:keys [paivitys-fn haetaan]}
   {{:keys [koontilaskun-kuukausi laskun-numero erapaiva kohdistukset] :as lomake} :lomake}]
  (let [{:keys [validius]} (meta lomake)
        erapaiva-meta (get validius [:erapaiva])
        koontilaskun-kuukausi-meta (get validius [:koontilaskun-kuukausi])
        summa-meta (get validius [:kohdistukset 0 :summa])]
    (lomakkeen-osio
      "Laskun tiedot"
      [kentat/vayla-lomakekentta
       "Koontilaskun kuukausi *"
       :komponentti koontilaskun-kk-droppari
       :komponentin-argumentit {:disabled                   (not= 0 haetaan)
                                :koontilaskun-kuukausi      koontilaskun-kuukausi
                                :koontilaskun-kuukausi-meta koontilaskun-kuukausi-meta
                                :paivitys-fn                paivitys-fn}]
      [kentat/vayla-lomakekentta
       "Laskun pvm *"
       :komponentti paivamaaran-valinta
       :komponentin-argumentit {:disabled      (not= 0 haetaan)
                                :erapaiva      erapaiva
                                :paivitys-fn   paivitys-fn
                                :erapaiva-meta erapaiva-meta}]
      [kentat/vayla-lomakekentta
       "Koontilaskun numero"
       :disabled (not= 0 haetaan)
       :arvo laskun-numero
       :on-change #(paivitys-fn
                     {:validoitava? true}
                     :laskun-numero (let [num (-> % .-target .-value js/parseInt)]
                                      (if (js/isNaN num)
                                        nil
                                        num)))]
      [kentat/vayla-lomakekentta
       "Kustannus € *"
       :class #{(str "input" (if (validi-ei-tarkistettu-tai-ei-koskettu? summa-meta) "" "-error") "-default") "komponentin-input"}
       :disabled (or (> (count kohdistukset) 1)
                     (not= 0 haetaan))
       :arvo (or (when (> (count kohdistukset) 1)
                   (gstring/format "%.2f" (reduce
                                            (fn [a s]
                                              (+ a (tiedot/parsi-summa (:summa s))))
                                            0
                                            kohdistukset)))
                 (get-in lomake [:kohdistukset 0 :summa])
                 0)
       :on-change #(paivitys-fn [:kohdistukset 0 :summa] (-> % .-target .-value))
       :on-blur #(paivitys-fn {:validoitava? true} [:kohdistukset 0 :summa] (-> % .-target .-value tiedot/parsi-summa))])))

(defn kohdistuksen-poisto [indeksi kohdistukset]
  (apply conj
         (subvec kohdistukset 0 indeksi)
         (subvec kohdistukset (inc indeksi))))

(defn tehtavaryhma-dropdown
  [{:keys [paivitys-fn tehtavaryhma indeksi tehtavaryhma-meta tehtavaryhmat disabled]}]
  [yleiset/livi-pudotusvalikko {:virhe?       (not (validi-ei-tarkistettu-tai-ei-koskettu? tehtavaryhma-meta))
                                :disabled     disabled
                                :vayla-tyyli? true
                                :valinta      (some #(when (= tehtavaryhma (:id %)) %) tehtavaryhmat)
                                :valitse-fn   #(do
                                                 (paivitys-fn
                                                   [:kohdistukset indeksi :toimenpideinstanssi] (:toimenpideinstanssi %))
                                                 (paivitys-fn {:validoitava? true}
                                                              [:kohdistukset indeksi :tehtavaryhma] (:id %)))
                                :format-fn    #(get % :tehtavaryhma)}
   tehtavaryhmat])

(defn tehtavaryhma-maara
  [{:keys [tehtavaryhmat kohdistukset-lkm paivitys-fn validius disabled]} indeksi t]
  (let [{:keys [tehtavaryhma summa]} t
        summa-meta (get validius [:kohdistukset indeksi :summa])
        tehtavaryhma-meta (get validius [:kohdistukset indeksi :tehtavaryhma])]
    [:div (merge {} (when (> kohdistukset-lkm 1)
                      {:class #{"lomake-sisempi-osio"}}))
     [kentat/vayla-lomakekentta
      "Tehtäväryhmä *"
      :tyylit {:kontti #{"kulukentta" "col-xs-6"}}
      :komponentti tehtavaryhma-dropdown
      :komponentin-argumentit {:disabled          disabled
                               :paivitys-fn       paivitys-fn
                               :tehtavaryhma      tehtavaryhma
                               :tehtavaryhmat     tehtavaryhmat
                               :tehtavaryhma-meta tehtavaryhma-meta
                               :indeksi           indeksi}]
     (when (> kohdistukset-lkm 1)
       [:div.col-xs-6
        [kentat/vayla-lomakekentta
         "Kustannus € *"
         :arvo summa
         :disabled disabled
         :class #{(str "input" (if (validi-ei-tarkistettu-tai-ei-koskettu? summa-meta) "" "-error") "-default") "komponentin-input"}
         :on-change #(paivitys-fn
                       [:kohdistukset indeksi :summa]
                       (-> % .-target .-value))
         :on-blur #(paivitys-fn
                     {:validoitava? true}
                     [:kohdistukset indeksi :summa]
                     (-> % .-target .-value tiedot/parsi-summa))]
        [napit/poista
         "Poista kohdistus"
         #(paivitys-fn {:jalkiprosessointi-fn (fn [lomake]
                                                (vary-meta lomake update :validius (fn [validius]
                                                                                     (dissoc validius
                                                                                             [:kohdistukset indeksi :summa]
                                                                                             [:kohdistukset indeksi :tehtavaryhma]))))}
                       :kohdistukset (r/partial kohdistuksen-poisto indeksi))
         {:vayla-tyyli? true}]])]))

(defn lisaa-validointi [lomake-meta validoinnit]
  (reduce (fn [kaikki {:keys [polku validoinnit]}]
            (assoc-in kaikki [:validius polku] {:validi?    false
                                                :koskettu?  false
                                                :validointi (tila/luo-validointi-fn validoinnit)}))
          lomake-meta
          validoinnit))

(defn polku-olemassa?
  [lomake polku]
  (not
    (false?
      (reduce (fn [l avain]
                (if (contains? l avain)
                  (get l avain)
                  false)) lomake polku))))

(defn paivita-validoinnit [lomake-meta lomake]
  (update
    lomake-meta
    :validius
    #(into {}
           (filter
             (fn [[polku _]]
               (polku-olemassa? lomake polku))
             %))))

(defn tehtavien-syotto
  [{:keys [paivitys-fn haetaan]}
   {{:keys [kohdistukset] :as lomake} :lomake
    tehtavaryhmat                     :tehtavaryhmat}]
  (let [kohdistukset-lkm (count kohdistukset)
        resetoi-kohdistukset (fn [kohdistukset]
                               [(first kohdistukset)])]
    [:div.row
     [:div.col-xs-12
      [:h2
       [:span "Mihin työhön kulu liittyy?"
        [:input#kulut-kohdistuvat-useammalle.vayla-checkbox
         {:type     :checkbox
          :disabled (not= 0 haetaan)
          :on-click #(let [kohdistusten-paivitys-fn (if (.. % -target -checked)
                                                      lisaa-kohdistus
                                                      resetoi-kohdistukset)
                           jalkiprosessointi-fn (if (.. % -target -checked)
                                                  (fn [lomake]
                                                    (vary-meta
                                                      lomake
                                                      lisaa-validointi
                                                      [{:polku       [:kohdistukset kohdistukset-lkm :summa]
                                                        :validoinnit (:kulut/summa tila/validoinnit)}
                                                       {:polku       [:kohdistukset kohdistukset-lkm :tehtavaryhma]
                                                        :validoinnit (:kulut/tehtavaryhma tila/validoinnit)}]))
                                                  (fn [lomake]
                                                    (vary-meta
                                                      lomake
                                                      paivita-validoinnit
                                                      lomake)))]
                       (paivitys-fn {:jalkiprosessointi-fn jalkiprosessointi-fn} :kohdistukset kohdistusten-paivitys-fn))}]
        [:label {:for "kulut-kohdistuvat-useammalle"} "Kulut kohdistuvat useammalle eri tehtävälle"]]]]
     (into [:div.row] (map-indexed
                        (r/partial tehtavaryhma-maara
                                   {:tehtavaryhmat    tehtavaryhmat
                                    :kohdistukset-lkm kohdistukset-lkm
                                    :paivitys-fn      paivitys-fn
                                    :disabled         (not= 0 haetaan)
                                    :validius         (:validius (meta lomake))})
                        kohdistukset))
     (when (> kohdistukset-lkm 1)
       [:div.lomake-sisempi-osio
        [napit/yleinen-toissijainen
         "Lisää kohdennus"
         #(paivitys-fn
            {:jalkiprosessointi-fn (fn [{:keys [kohdistukset] :as lomake}]
                                     (let [i (dec (count kohdistukset))]
                                       (vary-meta lomake lisaa-validointi [{:polku       [:kohdistukset i :summa]
                                                                            :validoinnit (:summa tila/validoinnit)}
                                                                           {:polku       [:kohdistukset i :tehtavaryhma]
                                                                            :validoinnit (:tehtavaryhma tila/validoinnit)}])))} :kohdistukset lisaa-kohdistus)
         {:ikoni        [ikonit/plus-sign]
          :vayla-tyyli? true}]])]))

(defn- aliurakoitsija-lisays-modaali
  [_]
  (let [aliurakoitsija-atomi (r/atom {:nimi "" :ytunnus "" :virhe? false :koskettu-nimi? false :koskettu-ytunnus? false})]
    (fn [{:keys [tallennus-fn haetaan]}]
      (let [y-tunnus-validi? (not (nil? (-> @aliurakoitsija-atomi :ytunnus tila/ei-tyhja tila/ei-nil tila/y-tunnus)))
            nimi-validi? (not (nil? (-> @aliurakoitsija-atomi :nimi tila/ei-tyhja tila/ei-nil)))
            {:keys [koskettu-nimi? koskettu-ytunnus?]} @aliurakoitsija-atomi]
        [:div
         [kentat/vayla-lomakekentta
          "Yrityksen nimi *"
          :disabled (not= 0 haetaan)
          :class #{(str "input" (if (or
                                      (false? koskettu-nimi?)
                                      (true? nimi-validi?)) "" "-error") "-default") "komponentin-input"}
          :arvo (:nimi @aliurakoitsija-atomi)
          :on-change (fn [event]
                       (swap! aliurakoitsija-atomi assoc :nimi (-> event .-target .-value) :koskettu-nimi? true))]
         [kentat/vayla-lomakekentta
          "Y-tunnus *"
          :disabled (not= 0 haetaan)
          :class #{(str "input" (if (or
                                      (false? koskettu-ytunnus?)
                                      (true? y-tunnus-validi?)) "" "-error") "-default") "komponentin-input"}
          :arvo (:ytunnus @aliurakoitsija-atomi)
          :on-change (fn [event] (swap! aliurakoitsija-atomi assoc :ytunnus (-> event .-target .-value) :koskettu-ytunnus? true))]
         (when (true? (-> @aliurakoitsija-atomi :virhe?))
           [:div "Tarkista annetut tiedot, tiedoissa virhe. Y-tunnus muotoa nnnnnnn-n"])
         [:div.napit
          [napit/tallenna
           "Tallenna"
           (fn []
             (if (and (true? nimi-validi?)
                      (true? y-tunnus-validi?))
               (do
                 (tallennus-fn (-> @aliurakoitsija-atomi (dissoc :virhe? :koskettu-nimi? :koskettu-ytunnus?)))
                 (modal/piilota!))
               (swap! aliurakoitsija-atomi assoc :virhe? true)))
           {:ikoni        ikonit/ok
            :vayla-tyyli? true}]
          [napit/sulje
           "Sulje"
           (fn [] (modal/piilota!))
           {:vayla-tyyli? true
            :ikoni        ikonit/remove}]]]))))

(defn- paivita-aliurakoitsija-jos-ytunnusta-muokattu
  [aliurakoitsija aliurakoitsijat paivitys-fn]
  (let [{:keys [id ytunnus]} aliurakoitsija
        {tallennettu-ytunnus :ytunnus} (some #(when (= (:id %) id) %) aliurakoitsijat)]
    (if-not (= tallennettu-ytunnus ytunnus)
      (paivitys-fn aliurakoitsija))))

(defn- lisatiedot [_ _]
  (let [y-tunnus-puuttuu "Y-tunnus puuttuu"
        aliurakoitsija-atomi (r/atom {:id nil :nimi nil :ytunnus y-tunnus-puuttuu})]
    (fn
      [{:keys [paivitys-fn haetaan e!]}
       {{:keys [aliurakoitsija liite-id liite-nimi liite-tyyppi liite-koko
                lisatieto] :as _lomake} :lomake
        aliurakoitsijat                 :aliurakoitsijat}]
      (let [{:keys [ytunnus]} @aliurakoitsija-atomi
            lisaa-aliurakoitsija (fn [tallennus-fn {sulje :sulje}]
                                   [:div
                                    {:on-click #(do
                                                  (sulje)
                                                  (modal/nayta! {;:sulje   sulku-fn
                                                                 :otsikko "Lisää aliurakoitsija"}
                                                                [aliurakoitsija-lisays-modaali {:tallennus-fn tallennus-fn
                                                                                                :haetaan      haetaan}]))}
                                    "Lisää aliurakoitsija"])]
        (lomakkeen-osio
          "Lisätiedot"
          [kentat/vayla-lomakekentta
           "Aliurakoitsija"
           :komponentti (fn []
                          [yleiset/alasveto-toiminnolla
                           (r/partial lisaa-aliurakoitsija (fn [aliurakoitsija]
                                                             (e!
                                                               (tiedot/->LuoUusiAliurakoitsija
                                                                 aliurakoitsija
                                                                 {:sivuvaikutus-tuloksella-fn #(reset! aliurakoitsija-atomi %)}))))
                           {:disabled     (not= 0 haetaan)
                            :valittu      (some #(when (= aliurakoitsija (:id %)) %) aliurakoitsijat)
                            :valinnat     aliurakoitsijat
                            :valinta-fn   #(do
                                             (swap! aliurakoitsija-atomi
                                                    assoc
                                                    :id (:id %)
                                                    :nimi (:nimi %)
                                                    :ytunnus (:ytunnus %))
                                             (paivitys-fn
                                               :suorittaja-nimi (:nimi %)
                                               :aliurakoitsija (:id %)))
                            :formaatti-fn #(get % :nimi)}])]
          [kentat/vayla-lomakekentta
           "Aliurakoitsijan y-tunnus"
           :disabled (not= 0 haetaan)
           :arvo (or
                   ytunnus
                   (some
                     #(when (= aliurakoitsija (:id %)) (:ytunnus %))
                     aliurakoitsijat)
                   y-tunnus-puuttuu)
           :on-focus #(when (= y-tunnus-puuttuu (-> % .-target .-value)) (swap! aliurakoitsija-atomi assoc :ytunnus ""))
           :on-change #(swap! aliurakoitsija-atomi assoc :ytunnus (-> % .-target .-value))
           :on-blur #(paivita-aliurakoitsija-jos-ytunnusta-muokattu
                       @aliurakoitsija-atomi
                       aliurakoitsijat
                       (fn [aliurakoitsija]
                         (e! (tiedot/->PaivitaAliurakoitsija aliurakoitsija))))]
          [kentat/vayla-lomakekentta
           "Kirjoita tähän halutessasi lisätietoa"
           :disabled (not= 0 haetaan)
           :on-change #(paivitys-fn :lisatieto (-> % .-target .-value))
           :arvo lisatieto]
          [kentat/vayla-lomakekentta
           "Liite"
           :komponentti (fn [_]
                          [:div.liiterivi
                           [:div.liitelista
                            (if-not (nil? liite-id) [liitteet/liitelinkki {:id     liite-id
                                                                           :nimi   liite-nimi
                                                                           :tyyppi liite-tyyppi
                                                                           :koko   liite-koko} (str liite-nimi)]
                                                    "Ei liitteitä")]
                           (when-not (nil? liite-id) [:div.liitepoisto
                                                      [napit/poista "Poista"
                                                       #(e! (tiedot/->PoistaLiite liite-id))
                                                       {:vayla-tyyli? true}]])
                           [:div.liitenappi
                            [liitteet/lisaa-liite
                             (-> @tila/yleiset :urakka :id)
                             {:nayta-lisatyt-liitteet? false
                              :liite-ladattu           #(e! (tiedot/->LiiteLisatty %))}]]])])))))



(defn- kulujen-syottolomake
  [e! _]
  (let [paivitys-fn (fn [& opts-polut-ja-arvot]
                      (let [polut-ja-arvot (if (odd? (count opts-polut-ja-arvot))
                                             (rest opts-polut-ja-arvot)
                                             opts-polut-ja-arvot)
                            opts (when (odd? (count opts-polut-ja-arvot)) (first opts-polut-ja-arvot))]
                        (e! (tiedot/->PaivitaLomake polut-ja-arvot opts))))]
    (fn [e! {syottomoodi   :syottomoodi lomake :lomake aliurakoitsijat :aliurakoitsijat
             tehtavaryhmat :tehtavaryhmat {haetaan :haetaan} :parametrit}]
      (let [{:keys [nayta]} lomake]
        [:div.ajax-peitto-kontti
         [:div.row
          [:h1 (str (if-not (nil? (:id lomake))
                      "Muokkaa kulua"
                      "Uusi kulu"))]
          [tehtavien-syotto {:paivitys-fn paivitys-fn
                             :haetaan     haetaan}
           {:lomake        lomake
            :tehtavaryhmat tehtavaryhmat}]]
         [:div.row
          [laskun-tiedot {:paivitys-fn paivitys-fn
                          :haetaan     haetaan}
           {:lomake lomake}]
          [lisatiedot {:paivitys-fn paivitys-fn
                       :haetaan     haetaan
                       :e!          e!} {:lomake          lomake
                                         :aliurakoitsijat aliurakoitsijat}]]
         [napit/tallenna
          "Tallenna"
          #(e! (tiedot/->TallennaKulu))
          {:vayla-tyyli? true}]
         [napit/peruuta
          "Peruuta"
          #(e! (tiedot/->KulujenSyotto (not syottomoodi)))
          {:ikoni        ikonit/remove
           :vayla-tyyli? true}]
         (when (= nayta :aliurakoitsija-modaali)
           [aliurakoitsija-modaali
            (fn [arvo]
              (e! (tiedot/->LuoUusiAliurakoitsija arvo))
              (paivitys-fn :nayta nil))
            #(paivitys-fn :nayta nil)])
         (when (not= 0 haetaan)
           [:div.ajax-peitto [yleiset/ajax-loader "Odota"]])]))))

(defn- kohdistetut*
  [e! app]
  (komp/luo
    (komp/piirretty (fn [this]
                      (e! (tiedot/->HaeAliurakoitsijat))
                      (e! (tiedot/->HaeUrakanLaskutJaTiedot (select-keys (-> @tila/yleiset :urakka) [:id :alkupvm :loppupvm])))))
    (fn [e! {:keys [taulukko syottomoodi] :as app}]
      [:div#kustannussuunnitelma
       (if syottomoodi
         [kulujen-syottolomake e! app]
         [:div
          [napit/yleinen-toissijainen
           "Tallenna Excel"
           #(loki/log "En tallenna vielä")
           {:vayla-tyyli? true
            :disabled     true}]
          [napit/yleinen-toissijainen
           "Tallenna PDF"
           #(loki/log "En tallenna vielä")
           {:vayla-tyyli? true
            :disabled     true}]
          [napit/yleinen-ensisijainen
           "Uusi kulu"
           #(e! (tiedot/->KulujenSyotto (not syottomoodi)))
           {:vayla-tyyli? true}]
          (when taulukko
            [p/piirra-taulukko taulukko])])])))

(defn kohdistetut-kulut
  []
  [tuck/tuck tila/laskutus-kohdistetut-kulut kohdistetut*])
