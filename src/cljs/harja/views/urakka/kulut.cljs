(ns harja.views.urakka.kulut
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.ui.debug :as debug]
            [harja.pvm :as pvm]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.mhu-laskutus :as tiedot]
            [harja.ui.taulukko.taulukko :as taulukko]
            [harja.ui.taulukko.jana :as jana]
            [harja.ui.taulukko.osa :as osa]
            [harja.ui.taulukko.tyokalut :as tyokalu]
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.modal :as modal]
            [harja.ui.liitteet :as liitteet]
            [harja.loki :refer [log]]
            [harja.loki :as loki]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.pvm :as pvm-valinta]
            [harja.ui.ikonit :as ikonit])
  (:require-macros [harja.ui.taulukko.tyokalut :refer [muodosta-taulukko]]))

(def avaimet->proppi {:arvo :value})

(defn- ei-koskettu-tai-validi? [{:keys [koskettu? validi?]}]
  (loki/log "k? v?" koskettu? validi?)
  (or
    (false? koskettu?)
    (true? validi?)))

(defn kulukentta
  [otsikko & params]
  (let [id (gensym "kulukentta-")
        propit (apply assoc {:type :text
                             :for  id}
                      (flatten
                        (mapv
                          (fn [[avain arvo]]
                            [(if (contains? avaimet->proppi avain)
                               (avain avaimet->proppi)
                               avain)
                             arvo])
                          (partition 2 params))))
        {komponentti :komponentti tyylit :tyylit} propit
        propit (apply dissoc propit #{:komponentti :tyylit})]
    [:div {:class (or (:kontti tyylit)
                      #{"kulukentta"})}
     [:label {:id    id
              :class (or (:otsikko tyylit)
                         #{})} otsikko]
     (if komponentti
       [komponentti]
       [:input.input-default.komponentin-input
        propit])]))

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
          [kulukentta "Yrityksen nimi" :arvo nimi :on-change #(swap! tila assoc :nimi (-> % .-target .-value))]
          [kulukentta "Y-tunnus" :arvo ytunnus :on-change #(swap! tila assoc :ytunnus (-> % .-target .-value))]
          [:div.napit
           [:button
            {:class    #{"tallenna"}
             :on-click #(tallennus-fn @tila)}
            [ikonit/ok]
            [:span "Tallenna"]]
           [:button
            {:class    #{"sulje"}
             :on-click sulku-fn}
            [ikonit/remove]
            [:span "Sulje"]]]]]))))

(defn- validoi
  [pakolliset objekti]
  (some #(not (get % pakolliset)) (keys objekti)))

(defn alasveto-toiminnolla
  [_ _]
  (let [auki? (r/atom false)]
    (komp/luo
      (komp/klikattu-ulkopuolelle #(reset! auki? false))
      (fn [toiminto {:keys [valittu valinnat valinta-fn formaatti-fn]}]
        (loki/log valinnat valittu)
        [:div {:class #{(str "select-default") (when @auki? "open")}}
         [:button.nappi-alasveto
          [:div.valittu {:on-click #(swap! auki? not)}
           (or (formaatti-fn valittu)
               "Ei valittu")]]
         [:ul {:style {:display (if @auki?
                                  "block"
                                  "none")}}
          (for [v valinnat]
            [:li.harja-alasvetolistaitemi
             {:on-click #(do
                           (swap! auki? not)
                           (valinta-fn v))}
             [:span (formaatti-fn v)]])
          [:li.harja-alasvetolistaitemi [toiminto {:sulje #(swap! auki? not)}]]]]))))

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

(defn lisatiedot [paivitys-fn _ _ _]
  (let [lisaa-aliurakoitsija (fn [{sulje :sulje}]
                               [:div
                                {:on-click #(do
                                              (sulje)
                                              (paivitys-fn :nayta :aliurakoitsija-modaali))}
                                "Lisää aliurakoitsija"])]
    (fn [paivitys-fn {:keys [aliurakoitsija] :as lomake} e! aliurakoitsijat]
      [:div.col-sm-6.col-xs-12
       [:h2 "Lisätiedot"]
       [kulukentta "Aliurakoitsija"
        :komponentti (fn []
                       [alasveto-toiminnolla
                        lisaa-aliurakoitsija
                        {:valittu      (some #(when (= aliurakoitsija (:id %)) %) aliurakoitsijat)
                         :valinnat     aliurakoitsijat
                         :valinta-fn   #(paivitys-fn :aliurakoitsija (:id %)
                                                     :suorittaja-nimi (:nimi %))
                         :formaatti-fn #(get % :nimi)}])]
       [kulukentta "Aliurakoitsijan y-tunnus" :disabled true :arvo (or (some #(when (= aliurakoitsija (:id %)) (:ytunnus %)) aliurakoitsijat)
                                                                       "Y-tunnus puuttuu")]
       [kulukentta "Kirjoita tähän halutessasi lisätietoa" :on-change #(paivitys-fn :lisatieto (-> % .-target .-value))]
       [kulukentta "Liite" :komponentti (fn []
                                          [liitteet/lisaa-liite (-> @tila/yleiset :urakka :id) {:liite-ladattu #(e! (tiedot/->LiiteLisatty %))}])]
       ;:kuvaus, :fileyard-hash, :urakka, :nimi,
       ;:id,:lahde,:tyyppi, :koko 65528
       ])))

#_(defn- validi?
    [arvo tyyppi]
    (let [validius (case tyyppi
                     :numero (re-matches #"\d+(?:\.?,?\d+)?" (str arvo)))]
      (not (nil? validius))))

(defn laskun-tiedot [paivitys-fn {:keys [koontilaskun-kuukausi laskun-numero erapaiva viite kohdistukset] :as lomake}]
  (let [{:keys [validius]} (meta lomake)
        erapaiva-meta (get validius [:erapaiva])
        koontilaskun-kuukausi-meta (get validius [:koontilaskun-kuukausi])
        summa-meta (get validius [:kohdistukset 0 :summa])]
    [:div.col-sm-6.col-xs-12
     [:h2 "Koontilaskun tiedot"]
     [kulukentta "Koontilaskun kuukausi *" :komponentti (fn []
                                                          [yleiset/livi-pudotusvalikko
                                                           {:vayla-tyyli? true
                                                            :valinta      koontilaskun-kuukausi
                                                            :valitse-fn   #(paivitys-fn :koontilaskun-kuukausi %)
                                                            :format-fn    (fn [a]
                                                                            (if (nil? a)
                                                                              "Ei valittu"
                                                                              (str (get kuukaudet-strs (keyword (namespace a))) " - " (get hoitovuodet-strs (keyword (name a))))))}
                                                           (flatten
                                                             (mapv
                                                               (fn [kk]
                                                                 (map
                                                                   (fn [hv]
                                                                     (keyword
                                                                       (str
                                                                         (name kk)
                                                                         "/"
                                                                         (name hv))))
                                                                   (sort #{:1-hoitovuosi
                                                                           :2-hoitovuosi
                                                                           :3-hoitovuosi
                                                                           :4-hoitovuosi
                                                                           :5-hoitovuosi})))
                                                               kuukaudet))])]
     [kulukentta "Laskun pvm *" :komponentti (fn []
                                               [pvm-valinta/pvm-valintakalenteri-inputilla {:valitse       #(paivitys-fn :erapaiva %)
                                                                                            :luokat        #{(str "input" (ei-koskettu-tai-validi? erapaiva-meta) "-default") "komponentin-input"}
                                                                                            :pvm           erapaiva
                                                                                            :pakota-suunta false
                                                                                            :valittava?-fn #(pvm/jalkeen? % (pvm/nyt))}])]
     [kulukentta "Laskun viite" :arvo viite :on-change #(paivitys-fn :viite (-> % .-target .-value))]
     [kulukentta "Koontilaskun numero" :arvo laskun-numero :on-change #(paivitys-fn :laskun-numero (-> % .-target .-value))]
     [kulukentta
      "Kustannus € *"
      :class #{(str "input" (if (ei-koskettu-tai-validi? summa-meta) "" "-error") "-default") "komponentin-input"}
      :disabled (or (> (count kohdistukset) 1) false)
      :arvo (or (when (> (count kohdistukset) 1)
                  (reduce (fn [a s]
                            (+ a (tiedot/parsi-summa (:summa s))))
                          0
                          kohdistukset))
                (get-in lomake [:kohdistukset 0 :summa])
                0)
      :on-change #(paivitys-fn [:kohdistukset 0 :summa] (-> % .-target .-value))
      :on-blur #(paivitys-fn [:kohdistukset 0 :summa] (-> % .-target .-value tiedot/parsi-summa))]]))

(defn kohdistuksen-poisto [indeksi kohdistukset]
  (apply conj
         (subvec kohdistukset 0 indeksi)
         (subvec kohdistukset (inc indeksi))))

(defn tehtavaryhma-maara
  [{:keys [tehtavaryhmat kohdistukset-lkm paivitys-fn validius]} indeksi t]
  (let [{:keys [tehtavaryhma summa]} t
        summa-meta (get validius [:kohdistukset indeksi :summa])
        tehtavaryhma-meta (get validius [:kohdistukset indeksi :tehtavaryhma])]
    (loki/log "TR" tehtavaryhma (some #(when (= tehtavaryhma (:id %)) (:tehtavaryhma %)) tehtavaryhmat))
    [:div.col-xs-6 {:class (apply conj #{} (filter #(not (nil? %)) (list "" (when (> kohdistukset-lkm 1) "lomake-sisempi-osio"))))}
     [kulukentta "Tehtäväryhmä *" :komponentti (fn []
                                                 [yleiset/livi-pudotusvalikko {:vayla-tyyli? true
                                                                               :valinta      (some #(when (= tehtavaryhma (:id %)) %) tehtavaryhmat)
                                                                               :valitse-fn   #(paivitys-fn [:kohdistukset indeksi :tehtavaryhma] (:id %)
                                                                                                           [:kohdistukset indeksi :toimenpideinstanssi] (:toimenpideinstanssi %))
                                                                               :format-fn    #(get % :tehtavaryhma)}
                                                  tehtavaryhmat])]
     (when (> kohdistukset-lkm 1)
       [:<>
        [:div
         [kulukentta "Kustannus € *" :arvo summa
          :class #{(str "input" (if (ei-koskettu-tai-validi? summa-meta) "" "-error") "-default") "komponentin-input"}
          :on-change #(paivitys-fn [:kohdistukset indeksi :summa] (-> % .-target .-value))
          :on-blur #(paivitys-fn [:kohdistukset indeksi :summa] (-> % .-target .-value tiedot/parsi-summa))]
         [:button.nappi.nappi-toissijainen {:on-click #(paivitys-fn {:ohita-meta-paivitys? true
                                                                     :jalkiprosessointi-fn (fn [lomake]
                                                                                             (vary-meta lomake update :validius (fn [validius]
                                                                                                                                  (dissoc validius [:kohdistukset indeksi :summa] [:kohdistukset indeksi :tehtavaryhma]))))} :kohdistukset (r/partial kohdistuksen-poisto indeksi))} "Poista kohdistus"]]])]))

(defn lisaa-validointi [lomake-meta validoinnit]
  (reduce (fn [kaikki {:keys [polku validointi-fn]}]
            (assoc-in kaikki [:validius polku] {:validi? false
                                                :koskettu? false
                                                :validointi validointi-fn}))
          lomake-meta
          validoinnit))

(defn tehtavien-syotto [paivitys-fn {:keys [kohdistukset] :as lomake} tehtavaryhmat]
  (let [kohdistukset-lkm (count kohdistukset)
        resetoi-kohdistukset (fn [kohdistukset]
                               [(first kohdistukset)])]
    [:div.col-xs-12
     [:h2
      [:span "Mihin työhön kulu liittyy?"
       [:input#kulut-kohdistuvat-useammalle.vayla-checkbox {:type     :checkbox
                                                            :on-click #(let [kohdistusten-paivitys-fn (if (.. % -target -checked)
                                                                                                        lisaa-kohdistus
                                                                                                        resetoi-kohdistukset)
                                                                             jalkiprosessointi-fn (if (.. % -target -checked)
                                                                                                    (fn [{:keys [kohdistukset] :as lomake}]
                                                                                                      (vary-meta lomake lisaa-validointi [{:polku         [:kohdistukset (count kohdistukset) :summa]
                                                                                                                                           :validointi-fn (:summa tila/validoinnit)}
                                                                                                                                          {:polku         [:kohdistukset (count kohdistukset) :tehtavaryhma]
                                                                                                                                           :validointi-fn (:tehtavaryhma tila/validoinnit)}]))
                                                                                                    resetoi-kohdistukset)]
                                                                         (paivitys-fn {:jalkiprosessointi-fn jalkiprosessointi-fn
                                                                                       :ohita-meta-paivitys? true} :kohdistukset kohdistusten-paivitys-fn))}]
       [:label {:for "kulut-kohdistuvat-useammalle"} "Kulut kohdistuvat useammalle eri tehtävälle"]]]
     (into [:div.col-xs-12] (map-indexed (r/partial tehtavaryhma-maara {:tehtavaryhmat tehtavaryhmat :kohdistukset-lkm kohdistukset-lkm :paivitys-fn paivitys-fn}) kohdistukset))
     (when (> kohdistukset-lkm 1)
       [:div.lomake-sisempi-osio
        [:button.nappi.nappi-toissijainen {:on-click #(paivitys-fn {:ohita-meta-paivitys? true
                                                                    :jalkiprosessointi-fn (fn [{:keys [kohdistukset] :as lomake}]
                                                                                            (vary-meta lomake lisaa-validointi [{:polku         [:kohdistukset (count kohdistukset) :summa]
                                                                                                                                 :validointi-fn (:summa tila/validoinnit)}
                                                                                                                                {:polku         [:kohdistukset (count kohdistukset) :tehtavaryhma]
                                                                                                                                 :validointi-fn (:tehtavaryhma tila/validoinnit)}]))} :kohdistukset lisaa-kohdistus)} [ikonit/plus-sign] "Lisää kohdennus"]])]))

(defn- lomakkeen-osio [{:keys [otsikko osiot]}]
  [:div.col-sm-6.col-xs-12
   [:h2 otsikko]])

(defn- kulujen-syottolomake
  [e! _]
  (let [paivitys-fn (fn [& opts-polut-ja-arvot]
                      (let [polut-ja-arvot (if (odd?
                                                 (count opts-polut-ja-arvot))
                                             (rest opts-polut-ja-arvot)
                                             opts-polut-ja-arvot)
                            opts (when (odd? (count opts-polut-ja-arvot)) (first opts-polut-ja-arvot))]
                        (e! (tiedot/->PaivitaLomake polut-ja-arvot opts))))]
    (fn [e! {:keys [syottomoodi lomake aliurakoitsijat tehtavaryhmat]}]
      (loki/log "metaa " (meta lomake))
      (let [{:keys [nayta]} lomake]
        [:div
         [debug/debug @tila/yleiset]
         [debug/debug lomake]
         [:div.row
          [:h1 "Uusi kulu"]
          [tehtavien-syotto paivitys-fn lomake tehtavaryhmat]]
         [:div.row
          [laskun-tiedot paivitys-fn lomake]
          [lisatiedot paivitys-fn lomake e! aliurakoitsijat]]
         [:button {:class    #{"nappi" "nappi-ensisijainen"}
                   :on-click #(e! (tiedot/->TallennaKulu))}
          [ikonit/ok] "Tallenna"]
         [:button {:class    #{"nappi" "nappi-toissijainen"}
                   :on-click #(e! (tiedot/->KulujenSyotto (not syottomoodi)))}
          [ikonit/remove] "Peruuta"]
         (when (= nayta :aliurakoitsija-modaali)
           [aliurakoitsija-modaali
            (fn [arvo]
              (e! (tiedot/->LuoUusiAliurakoitsija arvo))
              (paivitys-fn :nayta nil))
            #(paivitys-fn :nayta nil)])]))))

(defn- kohdistetut*
  [e! app]
  (komp/luo
    (komp/piirretty (fn [this]
                      (e! (tiedot/->HaeAliurakoitsijat))
                      (e! (tiedot/->HaeUrakanLaskutJaTiedot (select-keys (-> @tila/yleiset :urakka) [:id :alkupvm :loppupvm])))))
    (fn [e! {:keys [taulukko syottomoodi lomake] :as app}]
      (loki/log "aa" lomake (meta lomake))
      [:div
       (if syottomoodi
         [kulujen-syottolomake e! app]
         [:div
          [debug/debug app]
          [debug/debug taulukko]
          [:button {:class    #{"nappi" "nappi-toissijainen"}
                    :disabled true} "Tallenna Excel"]
          [:button {:class    #{"nappi" "nappi-toissijainen"}
                    :disabled true} "Tallenna PDF"]
          [:button {:class    #{"nappi" "nappi-ensisijainen"}
                    :on-click #(e! (tiedot/->KulujenSyotto (not syottomoodi)))} "Uusi kulu"]
          (when taulukko
            [p/piirra-taulukko taulukko])])
       ])))

(defn kohdistetut-kulut
  []
  [tuck/tuck tila/laskutus-kohdistetut-kulut kohdistetut*])