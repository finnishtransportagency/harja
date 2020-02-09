(ns harja.views.urakka.kulut
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
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
            [harja.ui.kentat :as kentat])
  (:require-macros [harja.ui.taulukko.tyokalut :refer [muodosta-taulukko]]))

(defn- ei-koskettu-tai-validi? [{:keys [koskettu? validi?]}]
  (loki/log "k? v?" koskettu? validi?)
  (or
    (false? koskettu?)
    (true? validi?)))
#_(defn nayta! [{:keys [sulje otsikko otsikko-tyyli footer luokka leveys]} sisalto]
    (reset! modal-sisalto {:otsikko       otsikko
                           :otsikko-tyyli otsikko-tyyli
                           :footer        footer
                           :sisalto       sisalto
                           :luokka        luokka
                           :sulje         sulje
                           :nakyvissa?    true
                           :leveys        leveys}))
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

(defn- validoi
  [pakolliset objekti]
  (some #(not (get % pakolliset)) (keys objekti)))



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



#_(defn- validi?
    [arvo tyyppi]
    (let [validius (case tyyppi
                     :numero (re-matches #"\d+(?:\.?,?\d+)?" (str arvo)))]
      (not (nil? validius))))

(defn- lomakkeen-osio [{:keys [otsikko osiot]}]
  [:div.col-sm-6.col-xs-12
   [:h2 otsikko]
   (for [osio osiot]
     osio)])

(defn laskun-tiedot [paivitys-fn {:keys [koontilaskun-kuukausi laskun-numero erapaiva viite kohdistukset] :as lomake}]
  (let [{:keys [validius]} (meta lomake)
        erapaiva-meta (get validius [:erapaiva])
        koontilaskun-kuukausi-meta (get validius [:koontilaskun-kuukausi])
        summa-meta (get validius [:kohdistukset 0 :summa])]
    (lomakkeen-osio {:otsikko "Koontilaskun tiedot"
                     :osiot   [[kentat/vayla-lomakekentta "Koontilaskun kuukausi *"
                                :komponentti (fn []
                                               [yleiset/livi-pudotusvalikko
                                                {:virhe?       (not (ei-koskettu-tai-validi? summa-meta))
                                                 :vayla-tyyli? true
                                                 :valinta      koontilaskun-kuukausi
                                                 :valitse-fn   #(paivitys-fn {:validoitava? true}
                                                                             :koontilaskun-kuukausi %)
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
                               [kentat/vayla-lomakekentta "Laskun pvm *"
                                :komponentti (fn []
                                               [pvm-valinta/pvm-valintakalenteri-inputilla {:valitse       #(paivitys-fn {:validoitava? true} :erapaiva %)
                                                                                            :luokat        #{(str "input" (ei-koskettu-tai-validi? erapaiva-meta) "-default") "komponentin-input"}
                                                                                            :pvm           erapaiva
                                                                                            :pakota-suunta false
                                                                                            :valittava?-fn #(pvm/jalkeen? % (pvm/nyt))}])]
                               [kentat/vayla-lomakekentta
                                "Laskun viite"
                                :arvo viite
                                :on-change #(paivitys-fn
                                              :viite (-> % .-target .-value))]
                               [kentat/vayla-lomakekentta
                                "Koontilaskun numero"
                                :arvo laskun-numero
                                :on-change #(paivitys-fn
                                              :laskun-numero (-> % .-target .-value))]
                               [kentat/vayla-lomakekentta
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
                                :on-blur #(paivitys-fn {:validoitava? true} [:kohdistukset 0 :summa] (-> % .-target .-value tiedot/parsi-summa))]]})))

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
     [kentat/vayla-lomakekentta "Tehtäväryhmä *" :komponentti (fn []
                                                                [yleiset/livi-pudotusvalikko {:vayla-tyyli? true
                                                                                              :valinta      (some #(when (= tehtavaryhma (:id %)) %) tehtavaryhmat)
                                                                                              :valitse-fn   #(do
                                                                                                               (paivitys-fn
                                                                                                                 [:kohdistukset indeksi :toimenpideinstanssi] (:toimenpideinstanssi %))
                                                                                                               (paivitys-fn {:validoitava? true}
                                                                                                                            [:kohdistukset indeksi :tehtavaryhma] (:id %)))
                                                                                              :format-fn    #(get % :tehtavaryhma)}
                                                                 tehtavaryhmat])]
     (when (> kohdistukset-lkm 1)
       [:<>
        [:div
         [kentat/vayla-lomakekentta "Kustannus € *"
          {:validoitava? true}
          :arvo summa
          :class #{(str "input" (if (ei-koskettu-tai-validi? summa-meta) "" "-error") "-default") "komponentin-input"}
          :on-change #(paivitys-fn [:kohdistukset indeksi :summa] (-> % .-target .-value))
          :on-blur #(paivitys-fn {:validoitava? true} [:kohdistukset indeksi :summa] (-> % .-target .-value tiedot/parsi-summa))]
         [napit/poista
          "Poista kohdistus"
          #(paivitys-fn {:jalkiprosessointi-fn (fn [lomake]
                                                 (vary-meta lomake update :validius (fn [validius]
                                                                                      (dissoc validius
                                                                                              [:kohdistukset indeksi :summa]
                                                                                              [:kohdistukset indeksi :tehtavaryhma]))))}
                        :kohdistukset (r/partial kohdistuksen-poisto indeksi))
          {:vayla-tyyli? true}]]])]))

(defn lisaa-validointi [lomake-meta validoinnit]
  (reduce (fn [kaikki {:keys [polku validointi-fn]}]
            (assoc-in kaikki [:validius polku] {:validi?    false
                                                :koskettu?  false
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
                                                                                                      (vary-meta lomake lisaa-validointi [{:polku         [:kohdistukset kohdistukset-lkm :summa]
                                                                                                                                           :validointi-fn (:kulut/summa tila/validoinnit)}
                                                                                                                                          {:polku         [:kohdistukset kohdistukset-lkm :tehtavaryhma]
                                                                                                                                           :validointi-fn (:kulut/tehtavaryhma tila/validoinnit)}]))
                                                                                                    resetoi-kohdistukset)]
                                                                         (paivitys-fn {:jalkiprosessointi-fn jalkiprosessointi-fn} :kohdistukset kohdistusten-paivitys-fn))}]
       [:label {:for "kulut-kohdistuvat-useammalle"} "Kulut kohdistuvat useammalle eri tehtävälle"]]]
     (into [:div.col-xs-12] (map-indexed
                              (r/partial tehtavaryhma-maara
                                         {:tehtavaryhmat    tehtavaryhmat
                                          :kohdistukset-lkm kohdistukset-lkm
                                          :paivitys-fn      paivitys-fn})
                              kohdistukset))
     (when (> kohdistukset-lkm 1)
       [:div.lomake-sisempi-osio
        [napit/yleinen-toissijainen
         "Lisää kohdennus"
         #(paivitys-fn
            {:jalkiprosessointi-fn (fn [{:keys [kohdistukset] :as lomake}]
                                     (vary-meta lomake lisaa-validointi [{:polku         [:kohdistukset (count kohdistukset) :summa]
                                                                          :validointi-fn (:summa tila/validoinnit)}
                                                                         {:polku         [:kohdistukset (count kohdistukset) :tehtavaryhma]
                                                                          :validointi-fn (:tehtavaryhma tila/validoinnit)}]))} :kohdistukset lisaa-kohdistus)
         {:ikoni        ikonit/plus-sign
          :vayla-tyyli? true}]])]))

(defn lisatiedot [paivitys-fn _ e! _]
  (let [aliurakoitsija-atomi (atom {:nimi "" :ytunnus ""})]
    (fn [paivitys-fn {:keys [aliurakoitsija] :as lomake} e! aliurakoitsijat]
      (let [modaalin-sisalto (fn [aliurakoitsija-atomi]
                               (loki/log "alik" @aliurakoitsija-atomi)
                               [:div
                                [kentat/vayla-lomakekentta
                                 "Yrityksen nimi"
                                 :arvo (:nimi @aliurakoitsija-atomi)
                                 :on-change (fn [event] (swap! aliurakoitsija-atomi assoc :nimi (-> event .-target .-value)))]
                                [kentat/vayla-lomakekentta
                                 "Y-tunnus"
                                 :arvo (:ytunnus @aliurakoitsija-atomi)
                                 :on-change (fn [event] (swap! aliurakoitsija-atomi assoc :ytunnus (-> event .-target .-value)))]
                                [:div.napit
                                 [napit/tallenna
                                  "Tallenna"
                                  (fn [] (e! (tiedot/->LuoUusiAliurakoitsija @aliurakoitsija-atomi)))
                                  {:ikoni        ikonit/ok
                                   :vayla-tyyli? true}]
                                 [napit/sulje
                                  "Sulje"
                                  (fn [] (modal/piilota!))
                                  {:vayla-tyyli? true
                                   :ikoni        ikonit/remove}]]])
            lisaa-aliurakoitsija (fn [{sulje :sulje}]
                                   [:div
                                    {:on-click #(do
                                                  (sulje)
                                                  (modal/nayta! {;:sulje   sulku-fn
                                                                 :otsikko "Lisää aliurakoitsija"}
                                                                [modaalin-sisalto aliurakoitsija-atomi])
                                                  #_(paivitys-fn :nayta :aliurakoitsija-modaali))}
                                    "Lisää aliurakoitsija"])]
        [lomakkeen-osio {:otsikko "Lisätiedot"
                         :osiot   [[kentat/vayla-lomakekentta
                                    "Aliurakoitsija"
                                    :komponentti (fn []
                                                   [yleiset/alasveto-toiminnolla
                                                    lisaa-aliurakoitsija
                                                    {:valittu      (some #(when (= aliurakoitsija (:id %)) %) aliurakoitsijat)
                                                     :valinnat     aliurakoitsijat
                                                     :valinta-fn   #(paivitys-fn :aliurakoitsija (:id %)
                                                                                 :suorittaja-nimi (:nimi %))
                                                     :formaatti-fn #(get % :nimi)}])]
                                   [kentat/vayla-lomakekentta
                                    "Aliurakoitsijan y-tunnus"
                                    :disabled true
                                    :arvo (or (some
                                                #(when (= aliurakoitsija (:id %)) (:ytunnus %))
                                                aliurakoitsijat)
                                              "Y-tunnus puuttuu")]
                                   [kentat/vayla-lomakekentta
                                    "Kirjoita tähän halutessasi lisätietoa"
                                    :on-change #(paivitys-fn :lisatieto (-> % .-target .-value))]
                                   [kentat/vayla-lomakekentta
                                    "Liite" :komponentti (fn []
                                                           [liitteet/lisaa-liite
                                                            (-> @tila/yleiset :urakka :id)
                                                            {:liite-ladattu #(e! (tiedot/->LiiteLisatty %))}])]]}]))))



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
         [napit/tallenna
          "Tallenna"
          #(e! (tiedot/->TallennaKulu))
          {:ikoni        ikonit/ok
           :vayla-tyyli? true}]
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
            [p/piirra-taulukko taulukko])])
       ])))

(defn kohdistetut-kulut
  []
  [tuck/tuck tila/laskutus-kohdistetut-kulut kohdistetut*])