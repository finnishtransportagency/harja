(ns harja.views.urakka.kulut
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [goog.string :as gstring]
            [goog.string.format]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.mhu-laskutus :as tiedot]
            [harja.ui.debug :as debug]
            [harja.ui.komponentti :as komp]
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.pvm :as pvm-valinta]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.ui.modal :as modal]
            [harja.ui.liitteet :as liitteet]
            [harja.loki :refer [log]]
            [harja.loki :as loki]
            [harja.ui.kentat :as kentat]
            [clojure.string :as str]
            [harja.asiakas.kommunikaatio :as k]
            [harja.transit :as t]
            [harja.pvm :as pvm]
            [clojure.string :as string])
  (:require-macros [harja.ui.taulukko.tyokalut :refer [muodosta-taulukko]]))

(defonce kuukaudet-strs {:tammikuu  "Tammikuu"
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

(defonce kuukaudet-keyword->number {:tammikuu  1
                                    :helmikuu  2
                                    :maaliskuu 3
                                    :huhtikuu  4
                                    :toukokuu  5
                                    :kesakuu   6
                                    :heinakuu  7
                                    :elokuu    8
                                    :syyskuu   9
                                    :lokakuu   10
                                    :marraskuu 11
                                    :joulukuu  12})

(defonce hoitovuodet-strs {:1-hoitovuosi "1. hoitovuosi"
                           :2-hoitovuosi "2. hoitovuosi"
                           :3-hoitovuosi "3. hoitovuosi"
                           :4-hoitovuosi "4. hoitovuosi"
                           :5-hoitovuosi "5. hoitovuosi"})

(def hoitovuosi->vuosiluku (into {} (map-indexed (fn [indeksi vuosi]
                                                   [(keyword (str (inc indeksi) "-hoitovuosi")) vuosi])
                                                 (range (-> @tila/yleiset :urakka :alkupvm pvm/vuosi)
                                                        (-> @tila/yleiset :urakka :loppupvm pvm/vuosi)))))

(defn- hallinnollisen-otsikointi [ryhma]
  (case ryhma
    :hallinnollinen "Hallinnollisiin toimenpiteisiin liittyviä kuluja saa syöttää manuaalisesti vain poikkeustilanteessa:"
    :ei-hallinnollinen "Valitse:"))

(defonce hallinnollinen-vihje-viesti
         [:span "Hallinnolliset toimenpiteet lasketaan automaattisesti mukaan maksueriin. "
          [:b "Näitä kuluja ei saa syöttää"]
          "manuaalisesti muulloin kuin poikkeustilanteissa."])

(defonce kuukaudet [:lokakuu :marraskuu :joulukuu :tammikuu :helmikuu :maaliskuu :huhtikuu :toukokuu :kesakuu :heinakuu :elokuu :syyskuu])

(defn- lomakkeen-osio [otsikko & osiot]
  (into [:div.palsta
         [:h5 otsikko]]
        osiot))

(defn- validi-ei-tarkistettu-tai-ei-koskettu? [{:keys [koskettu? validi? tarkistettu?]}]
  (cond
    (and (false? validi?)
         (true? tarkistettu?)) false
    :else true))

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
                  false))
              lomake
              polku))))

(defn paivita-validoinnit [lomake-meta lomake]
  (update
    lomake-meta
    :validius
    #(into {}
           (filter
             (fn [[polku _]]
               (polku-olemassa? lomake polku))
             %))))

(defn- lisatyon-validoinnit [lomake-meta {lomake :lomake indeksi :indeksi}]
  (let [siivotut-validoinnit (paivita-validoinnit lomake-meta lomake)]
    (lisaa-validointi siivotut-validoinnit
                      [{:polku       [:kohdistukset indeksi :toimenpideinstanssi]
                        :validoinnit (:kulut/toimenpideinstanssi tila/validoinnit)}
                       {:polku       [:kohdistukset indeksi :lisatyon-lisatieto]
                        :validoinnit (:kulut/lisatyon-lisatieto tila/validoinnit)}])))

(defn- maaramitallisen-validoinnit
  [lomake-meta {lomake :lomake indeksi :indeksi}]
  (lisaa-validointi (paivita-validoinnit lomake-meta lomake)
                    [{:polku       [:kohdistukset indeksi :summa]
                      :validoinnit (:kulut/summa tila/validoinnit)}
                     {:polku       [:kohdistukset indeksi :tehtavaryhma]
                      :validoinnit (:kulut/tehtavaryhma tila/validoinnit)}]))

(defn- jos-hallinnollinen
  [asia]
  (or
    (boolean (re-find #"oidonjohtopalkkio" (or (:tehtavaryhma asia)
                                               "")))
    (boolean (re-find #"rillishankinnat" (or (:tehtavaryhma asia)
                                             "")))
    (boolean (re-find #"ohto- ja hallintokorvaus" (or (:tehtavaryhma asia)
                                                      "")))))

(defn- hallinnollisen-ryhmittely
  [asia]
  (cond
    (boolean (re-find #"oidonjohtopalkkio" (:tehtavaryhma asia))) :hallinnollinen
    (boolean (re-find #"rillishankinnat" (:tehtavaryhma asia))) :hallinnollinen
    (boolean (re-find #"ohto- ja hallintokorvaus" (:tehtavaryhma asia))) :hallinnollinen
    :else :ei-hallinnollinen))

(defn lisaa-kohdistus [m]
  (conj m
        {:tehtavaryhma        nil
         :toimenpideinstanssi nil
         :summa               0
         :poistettu           false
         :rivi                (count m)}))

(defn- muokattava? [lomake]
  (not (nil? (:id lomake))))

(defn- kohdistuksen-poisto [indeksi kohdistukset]
  (apply conj
         (subvec kohdistukset 0 indeksi)
         (subvec kohdistukset (inc indeksi))))

(defn paivamaaran-valinta
  [{:keys [paivitys-fn erapaiva erapaiva-meta disabled koontilaskun-kuukausi]}]
  (loki/log "KK" koontilaskun-kuukausi)
  (let [kk (when-not (nil? koontilaskun-kuukausi)
             (-> koontilaskun-kuukausi (string/split #"/") first keyword kuukaudet-keyword->number))
        vuosi (when-not (nil? koontilaskun-kuukausi)
                (-> koontilaskun-kuukausi
                    (string/split #"/")
                    second
                    keyword
                    hoitovuosi->vuosiluku))
        vuosi (when-not (nil? vuosi)
                (if (< kk 10)
                  (inc vuosi)
                  vuosi))]
    (loki/log "kk vv hv" kk vuosi hoitovuosi->vuosiluku)
    [pvm-valinta/pvm-valintakalenteri-inputilla {:valitse       #(paivitys-fn {:validoitava? true} :erapaiva %)
                                                 :luokat        #{(str "input" (if (validi-ei-tarkistettu-tai-ei-koskettu? erapaiva-meta) "" "-error") "-default") "komponentin-input"}
                                                 :pvm           erapaiva
                                                 :pakota-suunta false
                                                 :disabled      disabled
                                                 :valittava?-fn #(when-not (nil? koontilaskun-kuukausi) (pvm/sama-kuukausi? % (pvm/->pvm (str "1." kk "." vuosi))))}])) ;pvm/jalkeen? % (pvm/nyt) --- otetaan käyttöön "joskus"


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

(defn tehtavaryhma-tai-toimenpide-dropdown
  [{:keys [paivitys-fn valittu indeksi valittu-meta valinnat disabled ryhmat nayta-vihje? vihje-viesti lisatyo?]}]
  (let [valittu-asia (some #(when
                              (= valittu
                                 (if lisatyo?
                                   (:toimenpideinstanssi %)
                                   (:id %)))
                              %)
                           valinnat)
        ryhmat (or ryhmat
                   {})
        valitse-tehtavaryhma-fn #(do
                                   (paivitys-fn
                                     [:kohdistukset indeksi :toimenpideinstanssi] (:toimenpideinstanssi %))
                                   (paivitys-fn {:validoitava? true}
                                                [:kohdistukset indeksi :tehtavaryhma] (:id %)))
        valitse-toimenpide-fn #(paivitys-fn
                                 [:kohdistukset indeksi :toimenpideinstanssi] (:toimenpideinstanssi %))
        optiot (merge ryhmat
                      {:virhe?        (not (validi-ei-tarkistettu-tai-ei-koskettu? valittu-meta))
                       :disabled      disabled
                       :vayla-tyyli?  true
                       :valinta       valittu-asia
                       :skrollattava? true
                       :valitse-fn    (if lisatyo?
                                        valitse-toimenpide-fn
                                        valitse-tehtavaryhma-fn)
                       :format-fn     (if lisatyo?
                                        #(get % :toimenpide)
                                        #(get % :tehtavaryhma))})]
    [:<>
     [yleiset/livi-pudotusvalikko optiot
      valinnat]
     (when (nayta-vihje? valittu-asia)
       [yleiset/vihje vihje-viesti])]))

(defn yksittainen-kohdistus
  [{:keys [paivitys-fn tehtavaryhma tehtavaryhmat tehtavaryhma-meta indeksi disabled
           toimenpiteet toimenpideinstanssi lisatyo? lisatyon-lisatieto lisatyon-lisatieto-meta]}]
  [:div.palstat
   [:div.palsta
    [kentat/vayla-lomakekentta
     (if lisatyo?
       "Toimenpide *"
       "Tehtäväryhmä *")
     :tyylit {:kontti #{"kulukentta"}}
     :komponentti tehtavaryhma-tai-toimenpide-dropdown
     :komponentin-argumentit {:paivitys-fn  paivitys-fn
                              :valittu      (if lisatyo?
                                              toimenpideinstanssi
                                              tehtavaryhma)
                              :valinnat     (if lisatyo?
                                              toimenpiteet
                                              tehtavaryhmat)
                              :valittu-meta tehtavaryhma-meta
                              :indeksi      indeksi
                              :lisatyo?     lisatyo?
                              :ryhmat       (when-not lisatyo?
                                              {:nayta-ryhmat   [:ei-hallinnollinen :hallinnollinen]
                                               :ryhmittely     hallinnollisen-ryhmittely
                                               :ryhman-otsikko hallinnollisen-otsikointi})
                              :nayta-vihje? (if lisatyo?
                                              #(false? true)
                                              jos-hallinnollinen)
                              :vihje-viesti hallinnollinen-vihje-viesti
                              :disabled     (or disabled)}]]
   [:div.palsta
    (when lisatyo?
      [kentat/vayla-lomakekentta
       "Lisätieto *"
       :arvo lisatyon-lisatieto
       :disabled (or disabled)
       :class #{(str "input" (if (validi-ei-tarkistettu-tai-ei-koskettu? lisatyon-lisatieto-meta) "" "-error") "-default") "komponentin-input"}
       :on-change #(paivitys-fn
                     [:kohdistukset indeksi :lisatyon-lisatieto]
                     (-> % .-target .-value))])]])

(defn useampi-kohdistus
  [{:keys [paivitys-fn disabled poistettu muokataan? indeksi
           tehtavaryhmat toimenpiteet
           tehtavaryhma tehtavaryhma-meta
           toimenpideinstanssi
           summa-meta summa
           lisatyo? lisatyon-lisatieto lisatyon-lisatieto-meta]}]
  [:div.palstat
   [:div.palsta
    (apply conj [:h3.flex-row]
           (filter #(not (nil? %))
                   [(when (and muokataan?
                               poistettu)
                      {:style {:color "#ff0000"}})
                    (str (if (and muokataan?
                                  poistettu) "Poistetaan kohdistus " "Kohdistus ")
                         (inc indeksi))
                    (let [input-id (gensym "kohdistus-lisatyo-")]
                      [:span
                       [:input.vayla-checkbox
                        {:type            :checkbox
                         :id              input-id
                         :disabled        disabled
                         :default-checked (if lisatyo?
                                            true
                                            false)
                         :on-change       #(let [kohdistusten-paivitys-fn (fn [kohdistukset]
                                                                            (let [lisatyo? (-> % .-target .-checked)
                                                                                  kohdistus-lisatyo {:lisatyo?           lisatyo?
                                                                                                     :lisatyon-lisatieto nil}
                                                                                  kohdistus-perus (get kohdistukset indeksi)
                                                                                  kohdistus (if lisatyo?
                                                                                              (merge (-> kohdistus-perus
                                                                                                         (dissoc :tehtavaryhma))
                                                                                                     kohdistus-lisatyo)
                                                                                              (-> kohdistus-perus
                                                                                                  (assoc :lisatyo? lisatyo?)
                                                                                                  (dissoc :lisatyon-lisatieto)))]
                                                                              (assoc kohdistukset indeksi kohdistus)))
                                                 meta-paivitys-fn (fn [lomake]
                                                                    (vary-meta
                                                                      lomake
                                                                      (if (-> % .-target .-checked)
                                                                        lisatyon-validoinnit
                                                                        maaramitallisen-validoinnit)
                                                                      {:lomake  lomake
                                                                       :indeksi indeksi}))]
                                             (paivitys-fn {:jalkiprosessointi-fn meta-paivitys-fn}
                                                          :kohdistukset kohdistusten-paivitys-fn))}]
                       [:label {:for input-id} "Lisätyö"]])]))
    [kentat/vayla-lomakekentta
     (if lisatyo?
       "Toimenpide *"
       "Tehtäväryhmä *")
     :tyylit {:kontti #{"kulukentta"}}
     :komponentti tehtavaryhma-tai-toimenpide-dropdown
     :komponentin-argumentit {:paivitys-fn  paivitys-fn
                              :valittu      (if lisatyo?
                                              toimenpideinstanssi
                                              tehtavaryhma)
                              :valinnat     (if lisatyo?
                                              toimenpiteet
                                              tehtavaryhmat)
                              :valittu-meta tehtavaryhma-meta
                              :indeksi      indeksi
                              :lisatyo?     lisatyo?
                              :ryhmat       (when-not lisatyo?
                                              {:nayta-ryhmat   [:ei-hallinnollinen :hallinnollinen]
                                               :ryhmittely     hallinnollisen-ryhmittely
                                               :ryhman-otsikko hallinnollisen-otsikointi})
                              :nayta-vihje? (if lisatyo?
                                              #(false? true)
                                              jos-hallinnollinen)
                              :vihje-viesti hallinnollinen-vihje-viesti
                              :disabled     (or poistettu
                                                disabled)}]
    [kentat/vayla-lomakekentta
     "Kustannus € *"
     :arvo summa
     :disabled (or poistettu
                   disabled)
     :class #{(str "input" (if (validi-ei-tarkistettu-tai-ei-koskettu? summa-meta) "" "-error") "-default") "komponentin-input"}
     :on-change #(paivitys-fn
                   [:kohdistukset indeksi :summa]
                   (-> % .-target .-value))
     :on-blur #(paivitys-fn
                 {:validoitava? true}
                 [:kohdistukset indeksi :summa]
                 (-> % .-target .-value tiedot/parsi-summa))]]
   [:div.palsta
    [:h3.kohdistuksen-poisto
     [napit/poista "" (cond
                        (and muokataan? poistettu)
                        #(paivitys-fn [:kohdistukset indeksi :poistettu] false)
                        (and muokataan? (not poistettu))
                        #(paivitys-fn [:kohdistukset indeksi :poistettu] true)
                        :else #(paivitys-fn {:jalkiprosessointi-fn (fn [lomake]
                                                                     (vary-meta lomake update :validius (fn [validius]
                                                                                                          (dissoc validius
                                                                                                                  [:kohdistukset indeksi :summa]
                                                                                                                  [:kohdistukset indeksi :tehtavaryhma]))))}
                                            :kohdistukset (r/partial kohdistuksen-poisto indeksi)))
      {:teksti-nappi? true
       :vayla-tyyli?  true}]]
    (when lisatyo?
      [kentat/vayla-lomakekentta
       "Lisätieto *"
       :arvo lisatyon-lisatieto
       :disabled (or poistettu
                     disabled)
       :class #{(str "input" (if (validi-ei-tarkistettu-tai-ei-koskettu? lisatyon-lisatieto-meta) "" "-error") "-default") "komponentin-input"}
       :on-change #(paivitys-fn
                     {:validoitava? true}
                     [:kohdistukset indeksi :lisatyon-lisatieto]
                     (-> % .-target .-value))])]])

(defn tehtavaryhma-maara
  [{:keys [tehtavaryhmat toimenpiteet kohdistukset-lkm paivitys-fn validius disabled muokataan?]} indeksi t]
  (let [{:keys [poistettu] :as kohdistus} t
        useampia-kohdistuksia? (> kohdistukset-lkm 1)
        summa-meta (get validius [:kohdistukset indeksi :summa])
        tehtavaryhma-meta (get validius [:kohdistukset indeksi :tehtavaryhma])
        lisatyon-lisatieto-meta (get validius [:kohdistukset indeksi :lisatyon-lisatieto])
        yhteiset-tiedot (merge kohdistus
                               {:paivitys-fn             paivitys-fn
                                :tehtavaryhmat           tehtavaryhmat
                                :toimenpiteet            toimenpiteet
                                :tehtavaryhma-meta       tehtavaryhma-meta
                                :indeksi                 indeksi
                                :disabled                disabled
                                :lisatyon-lisatieto-meta lisatyon-lisatieto-meta})]
    [:div (merge {} (when useampia-kohdistuksia?
                      {:class (apply conj #{"lomake-sisempi-osio"} (when poistettu #{"kohdistus-poistetaan"}))}))
     (if useampia-kohdistuksia?
       [useampi-kohdistus (merge yhteiset-tiedot
                                 {:summa-meta summa-meta
                                  :muokataan? muokataan?})]
       [yksittainen-kohdistus yhteiset-tiedot])]))

(defn- maara-summa
  [{:keys [paivitys-fn haetaan]}
   {{:keys [kohdistukset] :as lomake} :lomake}]
  (let [validius (meta lomake)
        summa-meta (get validius [:kohdistukset 0 :summa])]
    [:div.palsta
     [kentat/vayla-lomakekentta
      "Määrä € *"
      :otsikko-tag :h5
      :class #{(str "input" (if (validi-ei-tarkistettu-tai-ei-koskettu? summa-meta) "" "-error") "-default") "komponentin-input"}
      :disabled (or (> (count kohdistukset) 1)
                    (not= 0 haetaan))
      :arvo (or (when (> (count kohdistukset) 1)
                  (gstring/format "%.2f" (reduce
                                           (fn [a s]
                                             (+ a (tiedot/parsi-summa (if (true? (:poistettu s))
                                                                        0
                                                                        (:summa s)))))
                                           0
                                           kohdistukset)))
                (get-in lomake [:kohdistukset 0 :summa])
                0)
      :on-change #(paivitys-fn [:kohdistukset 0 :summa] (-> % .-target .-value))
      :on-blur #(paivitys-fn {:validoitava? true} [:kohdistukset 0 :summa] (-> % .-target .-value tiedot/parsi-summa))]]))

(defn- liitteen-naytto
  [e! {:keys [liite-id liite-nimi liite-tyyppi liite-koko] :as _liite}]
  [:div.liiterivi
   [:div.liitelista
    [liitteet/liitelinkki {:id     liite-id
                           :nimi   liite-nimi
                           :tyyppi liite-tyyppi
                           :koko   liite-koko} (str liite-nimi)]]
   [:div.liitepoisto
    [napit/poista ""
     #(e! (tiedot/->PoistaLiite liite-id))
     {:vayla-tyyli?  true
      :teksti-nappi? true}]]])

(defn- lisatiedot
  [{:keys [paivitys-fn haetaan]}
   {{:keys [lisatieto] :as _lomake} :lomake}]
  (lomakkeen-osio
    "Lisätiedot"
    [kentat/vayla-lomakekentta
     "Kirjoita tähän halutessasi lisätietoa"
     :disabled (not= 0 haetaan)
     :on-change #(paivitys-fn :lisatieto (-> % .-target .-value))
     :arvo lisatieto]))

(defn- liitteet
  [{:keys [e!]}
   {{liitteet :liitteet :as _lomake} :lomake}]
  [:div.palsta
   [kentat/vayla-lomakekentta
    "Liite"
    :otsikko-tag :h5
    :komponentti (fn [_]
                   [:div.liitelaatikko
                    [:div.liiterivit
                     (if-not (empty? liitteet)
                       (into [:<>] (mapv
                                     (r/partial liitteen-naytto e!)
                                     liitteet))
                       [:div.liitelista "Ei liitteitä"])]
                    [:div.liitenappi
                     [liitteet/lisaa-liite
                      (-> @tila/yleiset :urakka :id)
                      {:nayta-lisatyt-liitteet? false
                       :liite-ladattu           #(e! (tiedot/->LiiteLisatty %))}]]])]])

(defn- kulun-poistovarmistus-modaali
  [{:keys [varmistus-fn koontilaskun-kuukausi laskun-pvm kohdistukset tehtavaryhmat]}]
  [:div#kulun-poisto-modaali
   (for [k kohdistukset]
     ^{:key (gensym "trpoisto")}
     [:<>
      [:div.flex-row
       (str "Tehtäväryhmä: "
            (some #(when (= (:tehtavaryhma k) (:id %))
                     (:tehtavaryhma %))
                  tehtavaryhmat))]
      [:div.flex-row (str "Määrä: "
                          (:summa k))]])
   [:div.flex-row (str "Koontilaskun kuukausi: "
                       (let [[kk hv] (str/split koontilaskun-kuukausi #"/")]
                         (str (get kuukaudet-strs (keyword kk)) " - "
                              (get hoitovuodet-strs (keyword hv)))))]
   [:div.flex-row (str "Laskun päivämäärä: "
                       laskun-pvm)]
   [:div.flex-row (str "Kokonaissumma: "
                       (reduce
                         (fn [a s]
                           (+ a (tiedot/parsi-summa (:summa s))))
                         0
                         kohdistukset))]
   [:div
    [napit/yleinen-toissijainen
     "Peruuta"
     (fn []
       (modal/piilota!))
     {:vayla-tyyli? true
      :luokka       "suuri"}]
    [napit/poista
     "Poista tiedot"
     varmistus-fn
     {:vayla-tyyli? true
      :luokka       "suuri"}]]])

(defn tehtavien-syotto
  [{:keys [paivitys-fn haetaan]}
   {{:keys [kohdistukset] :as lomake} :lomake
    tehtavaryhmat                     :tehtavaryhmat
    toimenpiteet                      :toimenpiteet}]
  (let [kohdistukset-lkm (count kohdistukset)
        resetoi-kohdistukset (fn [kohdistukset]
                               [tila/kulut-kohdistus-default])]
    [:div.row {:style {:max-width "960px"}}
     [:div.palstat
      [:div.palsta
       [:h3 {:style {:width "100%"}}
        "Mihin työhön kulu liittyy?"]
       [:div.flex-row
        [:input#kulu-normaali.vayla-radio
         {:type            :radio
          :name            "kulu-group"
          :default-checked (cond
                             (> (count kohdistukset) 1) false
                             (= "lisatyo" (:maksueratyyppi (first kohdistukset))) false
                             :else true)
          :disabled        (not= 0 haetaan)
          :on-change       #(let [kohdistusten-paivitys-fn (if (.. % -target -checked)
                                                             resetoi-kohdistukset)
                                  jalkiprosessointi-fn (if (.. % -target -checked)
                                                         (fn [lomake]
                                                           (vary-meta
                                                             lomake
                                                             maaramitallisen-validoinnit
                                                             {:lomake  lomake
                                                              :indeksi 0})))]
                              (paivitys-fn {:jalkiprosessointi-fn jalkiprosessointi-fn} :kohdistukset kohdistusten-paivitys-fn))}]
        [:label {:for "kulu-normaali"} "Normaali suunniteltu tai määrämitattava hankintakulu"]]
       [:div.flex-row
        [:input#kulu-useampi.vayla-radio
         {:type            :radio
          :name            "kulu-group"
          :default-checked (if (> (count kohdistukset) 1)
                             true
                             false)
          :disabled        (not= 0 haetaan)
          :on-change       #(let [kohdistusten-paivitys-fn (if (.. % -target -checked)
                                                             lisaa-kohdistus
                                                             resetoi-kohdistukset)
                                  jalkiprosessointi-fn (if (.. % -target -checked)
                                                         (fn [lomake]
                                                           (vary-meta
                                                             lomake
                                                             maaramitallisen-validoinnit
                                                             {:lomake  lomake
                                                              :indeksi kohdistukset-lkm}))
                                                         (fn [lomake]
                                                           (vary-meta
                                                             lomake
                                                             paivita-validoinnit
                                                             lomake)))]
                              (paivitys-fn {:jalkiprosessointi-fn jalkiprosessointi-fn} :kohdistukset kohdistusten-paivitys-fn))}]
        [:label {:for "kulu-useampi"} "Kulut kohdistuvat eri tehtäville ja/tai osa kuluista on lisätöitä"]]
       [:div.flex-row
        [:input#kulu-lisatyo.vayla-radio
         {:type            :radio
          :name            "kulu-group"
          :default-checked (cond
                             (> (count kohdistukset) 1) false
                             (not (= "lisatyo" (:maksueratyyppi (first kohdistukset)))) false
                             :else true)
          :disabled        (not= 0 haetaan)
          :on-change       #(let [kohdistusten-paivitys-fn (if (.. % -target -checked)
                                                             (fn [kohdistukset]
                                                               (let [[kohdistukset] (resetoi-kohdistukset kohdistukset)]
                                                                 [(-> kohdistukset
                                                                      (dissoc :tehtavaryhma)
                                                                      (assoc :lisatyo? (.. % -target -checked)))])))
                                  jalkiprosessointi-fn (if (.. % -target -checked)
                                                         (fn [lomake]
                                                           (vary-meta
                                                             lomake
                                                             lisatyon-validoinnit
                                                             {:lomake  lomake
                                                              :indeksi 0})))]
                              (paivitys-fn {:jalkiprosessointi-fn jalkiprosessointi-fn} :kohdistukset kohdistusten-paivitys-fn))}]
        [:label {:for "kulu-lisatyo"} "Lisätyö"]]]]
     (into [:div.row] (map-indexed
                        (r/partial tehtavaryhma-maara
                                   {:tehtavaryhmat    tehtavaryhmat
                                    :toimenpiteet     toimenpiteet
                                    :kohdistukset-lkm kohdistukset-lkm
                                    :paivitys-fn      paivitys-fn
                                    :disabled         (not= 0 haetaan)
                                    :muokataan?       (muokattava? lomake)
                                    :validius         (:validius (meta lomake))})
                        kohdistukset))
     (when (> kohdistukset-lkm 1)
       [:div.lomake-sisempi-osio
        [napit/yleinen-toissijainen
         "Lisää kohdennus"
         #(paivitys-fn
            {:jalkiprosessointi-fn (fn [{:keys [kohdistukset] :as lomake}]
                                     (let [i (dec (count kohdistukset))]
                                       (vary-meta
                                         lomake
                                         maaramitallisen-validoinnit
                                         {:lomake  lomake
                                          :indeksi i})))}
            :kohdistukset lisaa-kohdistus)
         {:ikoni         [ikonit/plus-sign]
          :vayla-tyyli?  true
          :luokka        "suuri"
          :teksti-nappi? true}]])]))

(defn laskun-tiedot
  [{:keys [paivitys-fn haetaan]}
   {{:keys [koontilaskun-kuukausi laskun-numero erapaiva] :as lomake} :lomake}]
  (let [{:keys [validius]} (meta lomake)
        erapaiva-meta (get validius [:erapaiva])
        koontilaskun-kuukausi-meta (get validius [:koontilaskun-kuukausi])]
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
       :komponentin-argumentit {:disabled              (not= 0 haetaan)
                                :erapaiva              erapaiva
                                :paivitys-fn           paivitys-fn
                                :erapaiva-meta         erapaiva-meta
                                :koontilaskun-kuukausi koontilaskun-kuukausi}]
      [kentat/vayla-lomakekentta
       "Koontilaskun numero"
       :disabled (not= 0 haetaan)
       :arvo laskun-numero
       :on-change #(paivitys-fn
                     {:validoitava? true}
                     :laskun-numero (-> % .-target .-value))])))

(defn- kulujen-syottolomake
  [e! _]
  (let [paivitys-fn (fn [& opts-polut-ja-arvot]
                      (let [polut-ja-arvot (if (odd? (count opts-polut-ja-arvot))
                                             (rest opts-polut-ja-arvot)
                                             opts-polut-ja-arvot)
                            opts (when (odd? (count opts-polut-ja-arvot)) (first opts-polut-ja-arvot))]
                        (e! (tiedot/->PaivitaLomake polut-ja-arvot opts))))]
    (fn [e! {syottomoodi                   :syottomoodi
             {:keys [tehtavaryhma
                     kohdistukset
                     koontilaskun-kuukausi
                     erapaiva] :as lomake} :lomake
             tehtavaryhmat                 :tehtavaryhmat
             toimenpiteet                  :toimenpiteet
             {haetaan :haetaan}            :parametrit :as app}]
      (let [{:keys [nayta]} lomake]
        [:div.ajax-peitto-kontti
         #_[debug/debug app]
         #_[debug/debug lomake]
         #_[debug/debug (:validius (meta lomake))]
         [:div.palstat
          [:div.palsta
           [napit/takaisin
            "Takaisin"
            #(e! (tiedot/->KulujenSyotto (not syottomoodi)))
            {:vayla-tyyli?  true
             :teksti-nappi? true
             :style         {:font-size "14px"}}]
           [:h2 (str (if-not (nil? (:id lomake))
                       "Muokkaa kulua"
                       "Uusi kulu"))]]
          [:div.palsta.flex
           (when-not (nil? (:id lomake))
             [napit/poista "Poista kulu"
              #(modal/nayta! {:otsikko "Haluatko varmasti poistaa kulun?"}
                             [kulun-poistovarmistus-modaali {:varmistus-fn          (fn []
                                                                                      (modal/piilota!)
                                                                                      (e! (tiedot/->PoistaKulu (:id lomake))))
                                                             :kohdistukset          kohdistukset
                                                             :koontilaskun-kuukausi koontilaskun-kuukausi
                                                             :tehtavaryhma          tehtavaryhma
                                                             :laskun-pvm            (pvm/pvm erapaiva)
                                                             :tehtavaryhmat         tehtavaryhmat}])
              {:vayla-tyyli?  true
               :teksti-nappi? true
               :style         {:font-size   "14px"
                               :margin-left "auto"}}])]]
         [tehtavien-syotto {:paivitys-fn paivitys-fn
                            :haetaan     haetaan}
          {:lomake        lomake
           :tehtavaryhmat tehtavaryhmat
           :toimenpiteet  toimenpiteet}]
         [:div.palstat
          {:style {:margin-top    "56px"
                   :margin-bottom "56px"}}
          [laskun-tiedot {:paivitys-fn paivitys-fn
                          :haetaan     haetaan}
           {:lomake lomake}]
          [lisatiedot
           {:paivitys-fn paivitys-fn
            :haetaan     haetaan}
           {:lomake lomake}]]
         [:div.palstat
          {:style {:margin-top "56px"}}
          [maara-summa {:paivitys-fn paivitys-fn
                        :haetaan     haetaan} {:lomake lomake}]
          [liitteet {:e! e!} {:lomake lomake}]]
         [:div.kulu-napit
          [napit/tallenna
           "Tallenna"
           #(e! (tiedot/->TallennaKulu))
           {:vayla-tyyli? true
            :luokka       "suuri"}]
          [napit/peruuta
           "Peruuta"
           #(e! (tiedot/->KulujenSyotto (not syottomoodi)))
           {:ikoni        [ikonit/remove]
            :luokka       "suuri"
            :vayla-tyyli? true}]]
         (when (not= 0 haetaan)
           [:div.ajax-peitto [yleiset/ajax-loader "Odota"]])]))))

(defn- kohdistetut*
  [e! app]
  (komp/luo
    (komp/piirretty (fn [this]
                      (e! (tiedot/->HaeAliurakoitsijat))
                      (e! (tiedot/->HaeUrakanLaskutJaTiedot (select-keys (-> @tila/yleiset :urakka) [:id :alkupvm :loppupvm])))))
    (komp/ulos #(e! (tiedot/->NakymastaPoistuttiin)))
    (fn [e! {taulukko :taulukko syottomoodi :syottomoodi {:keys [hakuteksti haun-alkupvm haun-loppupvm]} :parametrit :as app}]
      [:div#vayla
       (if syottomoodi
         [kulujen-syottolomake e! app]
         [:div
          [:div.flex-row
           [:h2 "Kulujen kohdistus"]
           ^{:key "raporttixls"}
           [:form {:style  {:margin-left "auto"}
                   :target "_blank" :method "POST"
                   :action (k/excel-url :kulut)}
            [:input {:type  "hidden" :name "parametrit"
                     :value (t/clj->transit {:urakka-id   (-> @tila/yleiset :urakka :id)
                                             :urakka-nimi (-> @tila/yleiset :urakka :nimi)
                                             :alkupvm     haun-alkupvm
                                             :loppupvm    haun-loppupvm})}]
            [:button {:type  "submit"
                      :class #{"button-secondary-default" "suuri"}} "Tallenna Excel"]]
           ^{:key "raporttipdf"}
           [:form {:style  {:margin-left  "16px"
                            :margin-right "64px"}
                   :target "_blank" :method "POST"
                   :action (k/pdf-url :kulut)}
            [:input {:type  "hidden" :name "parametrit"
                     :value (t/clj->transit {:urakka-id   (-> @tila/yleiset :urakka :id)
                                             :urakka-nimi (-> @tila/yleiset :urakka :nimi)
                                             :alkupvm     haun-alkupvm
                                             :loppupvm    haun-loppupvm})}]
            [:button {:type  "submit"
                      :class #{"button-secondary-default" "suuri"}} "Tallenna PDF"]]
           [napit/yleinen-ensisijainen
            "Uusi kulu"
            #(e! (tiedot/->KulujenSyotto (not syottomoodi)))
            {:vayla-tyyli? true
             :luokka       "suuri"}]]

          [:div.flex-row
           #_[kentat/vayla-lomakekentta
              "Kirjoita tähän halutessasi lisätietoa"
              :on-change #(e! (tiedot/->AsetaHakuTeksti (-> % .-target .-value)))
              :arvo hakuteksti]
           [kentat/aikavali
            {:valinta-fn               #(e! (tiedot/->AsetaHakuparametri %1 %2))
             :pvm-alku                 (or haun-alkupvm
                                           (-> @tila/yleiset :urakka :alkupvm))
             :rajauksen-alkupvm        (-> @tila/yleiset :urakka :alkupvm)
             :rajauksen-loppupvm       (-> @tila/yleiset :urakka :loppupvm)
             :pvm-loppu                (or haun-loppupvm
                                           (-> @tila/yleiset :urakka :loppupvm))
             :ikoni                    ikonit/calendar
             :sumeutus-kun-molemmat-fn #(e! (tiedot/->HaeUrakanLaskut {:id       (-> @tila/yleiset :urakka :id)
                                                                       :alkupvm  %1
                                                                       :loppupvm %2}))}]]
          (when taulukko
            [p/piirra-taulukko taulukko])])])))

(defn kohdistetut-kulut
  []
  [tuck/tuck tila/laskutus-kohdistetut-kulut kohdistetut*])
