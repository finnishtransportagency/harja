(ns harja.views.urakka.kulut.kulut
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [goog.string :as gstring]
            [goog.string.format]
            [harja.domain.kulut :as kulut]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.kulut.mhu-kulut :as tiedot]
            [harja.ui.debug :as debug]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.pvm :as pvm-valinta]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.ui.modal :as modal]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.kentat :as kentat]
            [clojure.string :as str]
            [harja.asiakas.kommunikaatio :as k]
            [harja.transit :as t]
            [harja.pvm :as pvm]
            [harja.ui.valinnat :as valinnat]
            [harja.fmt :as fmt]))

(defn- hallinnollisen-otsikointi [ryhma]
  (case ryhma
    :hallinnollinen "Hallinnollisiin toimenpiteisiin liittyviä kuluja saa syöttää manuaalisesti vain poikkeustilanteessa:"
    :ei-hallinnollinen "Valitse:"))

(defonce hallinnollinen-vihje-viesti
         "Hallinnolliset toimenpiteet lasketaan automaattisesti mukaan maksueriin. Näitä kuluja ei saa syöttää manuaalisesti muulloin kuin poikkeustilanteissa.")

(defonce kuukaudet [:lokakuu :marraskuu :joulukuu :tammikuu :helmikuu :maaliskuu :huhtikuu :toukokuu :kesakuu :heinakuu :elokuu :syyskuu])

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

(defn paivita-lomakkeen-arvo 
  "Helpperifunktio"
  [{:keys [paivitys-fn polku arvon-formatteri-fn optiot]} arvo]
  (let [arvo (if arvon-formatteri-fn 
               (arvon-formatteri-fn arvo)
               arvo)]
    (paivitys-fn optiot polku arvo)))

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
  [lomake-meta {lomake :lomake indeksi :indeksi urakoitsija-maksaa? :urakoitsija-maksaa?}]
  (lisaa-validointi (paivita-validoinnit lomake-meta lomake)
                    [{:polku [:kohdistukset indeksi :summa]
                      :validoinnit (if urakoitsija-maksaa?
                                     (:kulut/negatiivinen-summa tila/validoinnit)
                                     (:kulut/summa tila/validoinnit))}
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
    (boolean (re-find #"oitovuoden päättämi" (:tehtavaryhma asia))) :hallinnollinen
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
  [{:keys [paivitys-fn erapaiva erapaiva-meta disabled koontilaskun-kuukausi placeholder]}]
  [pvm-valinta/pvm-valintakalenteri-inputilla
   {:valitse (r/partial paivita-lomakkeen-arvo {:paivitys-fn paivitys-fn
                                                :polku :erapaiva
                                                :optiot {:validoitava? true}})
    :luokat #{(str "input" (if (or
                                 (validi-ei-tarkistettu-tai-ei-koskettu? erapaiva-meta)
                                 disabled) "" "-error") "-default")
              "komponentin-input"}
    :paivamaara (or
                  erapaiva
                  (kulut/koontilaskun-kuukausi->pvm
                    koontilaskun-kuukausi
                    (-> @tila/yleiset :urakka :alkupvm)
                    (-> @tila/yleiset :urakka :loppupvm)))
    :pakota-suunta false
    :disabled disabled
    :placeholder placeholder
    :valittava?-fn (kulut/koontilaskun-kuukauden-sisalla?-fn
                     koontilaskun-kuukausi
                     (-> @tila/yleiset :urakka :alkupvm)
                     (-> @tila/yleiset :urakka :loppupvm))}]) ;pvm/jalkeen? % (pvm/nyt) --- otetaan käyttöön "joskus"


(defn- koontilaskun-kk-formatter
  [a]
  (if (nil? a)
    "Ei valittu"
    (let [[kk hv] (str/split a #"/")]
      (str (pvm/kuukauden-nimi (pvm/kuukauden-numero kk) true) " - "
        (get kulut/hoitovuodet-strs (keyword hv))))))

(defn- valitse-tr-helper-fn
  "Koska lambdat aiheuttaa uudelleenrendauksia"
  [paivitys-fn indeksi arvo]
  (paivitys-fn
    [:kohdistukset indeksi :toimenpideinstanssi] (:toimenpideinstanssi arvo))
  (paivitys-fn {:validoitava? true}
    [:kohdistukset indeksi :tehtavaryhma] (:id arvo)))

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
        valitse-tehtavaryhma-fn (r/partial valitse-tr-helper-fn paivitys-fn indeksi)
        valitse-toimenpide-fn (r/partial paivita-lomakkeen-arvo 
                                {:paivitys-fn paivitys-fn
                                 :polku [:kohdistukset indeksi :toimenpideinstanssi] 
                                 :arvon-formatteri-fn :toimenpideinstanssi})
        optiot (merge ryhmat
                      {:virhe?        (not (validi-ei-tarkistettu-tai-ei-koskettu? valittu-meta))
                       :disabled      disabled
                       :vayla-tyyli?  true
                       :elementin-id  (str indeksi)
                       :valinta       valittu-asia
                       :skrollattava? true
                       :class (when disabled "tehtavaryhma-valinta-disabled")
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
           toimenpiteet toimenpideinstanssi lisatyo? lisatyon-lisatieto lisatyon-lisatieto-meta
           vuoden-paatos-valittu?]}]
  [:div.palstat
   [:div.palsta
    [:label (if lisatyo?
       "Toimenpide *"
       "Tehtäväryhmä *")]
    [tehtavaryhma-tai-toimenpide-dropdown {:paivitys-fn paivitys-fn
                                           :valittu (if lisatyo?
                                                      toimenpideinstanssi
                                                      tehtavaryhma)
                                           :valinnat (if lisatyo?
                                                       toimenpiteet
                                                       (filter #(= false (:passivoitu %)) tehtavaryhmat)) ;; Filteröidään vanhentuneet tehtäväryhmät kulukirjauksen valintalaatikosta
                                           :valittu-meta tehtavaryhma-meta
                                           :indeksi indeksi
                                           :lisatyo? lisatyo?
                                           :ryhmat (when-not lisatyo?
                                                     {:nayta-ryhmat [:ei-hallinnollinen :hallinnollinen]
                                                      :ryhmittely hallinnollisen-ryhmittely
                                                      :ryhman-otsikko hallinnollisen-otsikointi})
                                           :nayta-vihje? (if lisatyo?
                                                           #(false? true)
                                                           jos-hallinnollinen)
                                           :vihje-viesti hallinnollinen-vihje-viesti
                                           :disabled (or disabled
                                                       vuoden-paatos-valittu?)}]]
   [:div.palsta
    (when lisatyo?
      [kentat/tee-otsikollinen-kentta 
       {:otsikko "Lisätieto *"
        :luokka #{}
        :kentta-params {:disabled disabled
                        :vayla-tyyli? true
                        :virhe? (not (validi-ei-tarkistettu-tai-ei-koskettu? lisatyon-lisatieto-meta))
                        :tyyppi :string}
        :arvo-atom (r/wrap lisatyon-lisatieto (r/partial paivita-lomakkeen-arvo 
                                                {:paivitys-fn paivitys-fn 
                                                 :polku [:kohdistukset indeksi :lisatyon-lisatieto]}))}])]])

(defn useampi-kohdistus
  [{:keys [paivitys-fn disabled poistettu muokataan? indeksi
           tehtavaryhmat toimenpiteet
           tehtavaryhma tehtavaryhma-meta
           toimenpideinstanssi
           summa-meta summa
           lisatyo? lisatyon-lisatieto lisatyon-lisatieto-meta
           vuoden-paatos-valittu?
           kohdistus-otsikot
           urakoitsija-maksaa?]}]
  [:div.palstat
   [:div.palsta
    (apply conj [:h3.flex-row]
           (filter #(not (nil? %))
                   [(when (and muokataan?
                               poistettu)
                      {:style {:color "#ff0000"}})
                    (cond (and muokataan?
                            poistettu) (str "Poistetaan kohdistus " (inc indeksi))
                          (some? kohdistus-otsikot) (get kohdistus-otsikot indeksi)
                          :else (str "Kohdistus " (inc indeksi)))
                    (when-not vuoden-paatos-valittu?
                      (let [input-id (gensym "kohdistus-lisatyo-")]
                        [:span
                         [:input.vayla-checkbox
                          {:type :checkbox
                           :id input-id
                           :disabled disabled
                           :default-checked (if lisatyo?
                                              true
                                              false)
                           :on-change #(let [kohdistusten-paivitys-fn (fn [kohdistukset]
                                                                        (let [lisatyo? (-> % .-target .-checked)
                                                                              kohdistus-lisatyo {:lisatyo? lisatyo?
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
                                                                  {:lomake lomake
                                                                   :indeksi indeksi}))]
                                         (paivitys-fn {:jalkiprosessointi-fn meta-paivitys-fn}
                                           :kohdistukset kohdistusten-paivitys-fn))}]
                         [:label {:for input-id} "Lisätyö"]]))]))
    [tehtavaryhma-tai-toimenpide-dropdown
     {:paivitys-fn  paivitys-fn
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
                      (constantly false)
                      jos-hallinnollinen)
      :vihje-viesti hallinnollinen-vihje-viesti
      :disabled     (or poistettu
                      vuoden-paatos-valittu?
                      disabled)}]
    [kentat/tee-otsikollinen-kentta
     {:otsikko "Määrä € *"
      :luokka #{}
      :arvo-atom (r/wrap summa (r/partial
                                 paivita-lomakkeen-arvo
                                 {:paivitys-fn paivitys-fn
                                  :optiot {:validoitava? true}
                                  :polku [:kohdistukset indeksi :summa]
                                  :arvon-formatteri-fn tiedot/parsi-summa}))
      :kentta-params {:disabled? (or poistettu disabled)
                      :tyyppi :euro
                      :vaadi-negatiivinen? urakoitsija-maksaa?
                      :vaadi-positiivinen-numero? (not urakoitsija-maksaa?)
                      :virhe? (not (validi-ei-tarkistettu-tai-ei-koskettu? summa-meta))
                      :input-luokka "maara-input"
                      :vayla-tyyli? true}}]
    (when urakoitsija-maksaa? [:div.caption.margin-top-4 "Kulu kirjataan miinusmerkkisenä"])]
   [:div.palsta
    (when-not vuoden-paatos-valittu?
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
         :vayla-tyyli? true}]])
    (when lisatyo?
      [kentat/tee-otsikollinen-kentta
       {:otsikko "Lisätieto *"
        :luokka #{}
        :arvo-atom (r/wrap lisatyon-lisatieto
                     (r/partial paivita-lomakkeen-arvo 
                       {:paivitys-fn paivitys-fn
                        :optiot {:validoitava? true}
                        :polku [:kohdistukset indeksi :lisatyon-lisatieto]}))
        :kentta-params {:tyyppi :string
                        :vayla-tyyli? true
                        :disabled? (or poistettu
                                     disabled)
                        :tyylit #{(str "input" (if (validi-ei-tarkistettu-tai-ei-koskettu? lisatyon-lisatieto-meta) "" "-error") "-default") "komponentin-input"}} }])]])

(defn tehtavaryhma-maara
  [{:keys [tehtavaryhmat toimenpiteet kohdistukset-lkm paivitys-fn validius disabled muokataan?
           vuoden-paatos-valittu? kohdistus-otsikot urakoitsija-maksaa?]} indeksi t]
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
                                :vuoden-paatos-valittu? vuoden-paatos-valittu?
                                :lisatyon-lisatieto-meta lisatyon-lisatieto-meta})]
    [:div (merge {} (when useampia-kohdistuksia?
                      {:class (apply conj #{"lomake-sisempi-osio"} (when poistettu #{"kohdistus-poistetaan"}))}))
     (if useampia-kohdistuksia?
       [useampi-kohdistus (merge yhteiset-tiedot
                                 {:summa-meta summa-meta
                                  :muokataan? muokataan?
                                  :kohdistus-otsikot kohdistus-otsikot
                                  :urakoitsija-maksaa? urakoitsija-maksaa?})]
       [yksittainen-kohdistus yhteiset-tiedot])]))

(defn- maara-summa
  [{:keys [paivitys-fn haetaan kulu-lukittu?]}
   {{:keys [kohdistukset] :as lomake} :lomake
    urakoitsija-maksaa? :urakoitsija-maksaa?}]
  (let [validius (:validius (meta lomake))
        summa-meta (get validius [:kohdistukset 0 :summa])
        useampi-kohdistus? (< 1 (count kohdistukset))]
    [:div.palsta
     (if useampi-kohdistus?
       [:<>
        [:h5 "Yhteensä"]
        [:h2 (fmt/euro-opt (reduce
                             (fn [a s]
                               (+ a (tiedot/parsi-summa (if (true? (:poistettu s))
                                                          0
                                                          (:summa s)))))
                             0
                             kohdistukset))]]

       ^{:key (:tehtavaryhma (first kohdistukset))}
       [kentat/tee-otsikollinen-kentta
        {:otsikko "Määrä € *"
         :otsikon-tag :h5
         :luokka #{}
         :kentta-params {:tyyppi :euro
                         :disabled? (or (not= 0 haetaan) kulu-lukittu?)
                         :vaadi-negatiivinen? urakoitsija-maksaa?
                         :vaadi-positiivinen-numero? (not urakoitsija-maksaa?)
                         :input-luokka "maara-input"
                         :virhe? (when-not (validi-ei-tarkistettu-tai-ei-koskettu? summa-meta) true)
                         :vayla-tyyli? true}
         :arvo-atom (r/wrap (get-in lomake [:kohdistukset 0 :summa])
                      (r/partial paivita-lomakkeen-arvo
                        {:paivitys-fn paivitys-fn
                         :optiot {:validoitava? true}
                         :polku [:kohdistukset 0 :summa]
                         :arvon-formatteri-fn tiedot/parsi-summa}))}])
     (when (and
             urakoitsija-maksaa?
             (not useampi-kohdistus))
       [:div.caption.margin-top-4 "Kulu kirjataan miinusmerkkisenä"])]))

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
  [{:keys [paivitys-fn haetaan kulu-lukittu?]}
   {{:keys [lisatieto] :as _lomake} :lomake}]
  [:div.palsta
   [:h5 "Lisätiedot"]
   [kentat/tee-otsikollinen-kentta
    {:otsikko "Kirjoita tähän halutessasi lisätietoa"
     :luokka #{}
     :kentta-params {:tyyppi :string
                     :vayla-tyyli? true
                     :disabled? (or (not= 0 haetaan) kulu-lukittu?)}
     :arvo-atom (r/wrap lisatieto
                  (r/partial paivita-lomakkeen-arvo {:paivitys-fn paivitys-fn
                                                     :polku :lisatieto}))}]])

(defn- liitteet
  [{:keys [e! kulu-lukittu?]}
   {{liitteet :liitteet :as _lomake} :lomake}]
  [:div.palsta
   [:h5 "Liite"]
   [:div.liitelaatikko
    [:div.liiterivit
     (if-not (empty? liitteet)
       (into [:<>] (mapv
                     (r/partial liitteen-naytto e!)
                     liitteet))
       [:div.liitelista "Ei liitteitä"])]
    (when-not kulu-lukittu?
      [:div.liitenappi
       [liitteet/lisaa-liite
        (-> @tila/yleiset :urakka :id)
        {:nayta-lisatyt-liitteet? false
         :liite-ladattu #(e! (tiedot/->LiiteLisatty %))}]])]])

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
                         (str (pvm/kuukauden-nimi (pvm/kuukauden-numero kk) true) " - "
                              (get kulut/hoitovuodet-strs (keyword hv)))))]
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

(defn- vayla-radio [{:keys [id teksti ryhma oletus-valittu? disabloitu? muutos-fn]}]
  [:div.flex-row
   [:input#kulu-normaali.vayla-radio
    {:id              id
     :type            :radio
     :name            ryhma
     :default-checked oletus-valittu?
     :disabled        disabloitu?
     :on-change       muutos-fn}]
   [:label {:for id} teksti]])

(def vuoden-paatoksen-kulun-tyypit
  {:tavoitepalkkio "Tavoitepalkkio"
   :tavoitehinnan-ylitys "Urakoitsija maksaa tavoitehinnan ylityksestä"
   :kattohinnan-ylitys "Urakoitsija maksaa tavoite- ja kattohinnan ylityksestä"})

(def vuoden-paatoksen-tehtavaryhmien-nimet
  {:tavoitepalkkio "Hoitovuoden päättäminen / Tavoitepalkkio"
   :tavoitehinnan-ylitys "Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä"
   :kattohinnan-ylitys "Hoitovuoden päättäminen / Urakoitsija maksaa kattohinnan ylityksestä"})

(defn- avain->tehtavaryhma [tehtavaryhmat avain]
  (first (filter #(= (:tehtavaryhma %) (get vuoden-paatoksen-tehtavaryhmien-nimet avain)) tehtavaryhmat)))

(defn- vuoden-paatos-checkboxit [{:keys [paivitys-fn haetaan]}
                                 {{:keys [kohdistukset] :as lomake} :lomake
                                  tehtavaryhmat :tehtavaryhmat}]
  (let [aseta-kohdistus (fn [tehtavaryhma-avain]
                          (if-not (= tehtavaryhma-avain :kattohinnan-ylitys)
                            (let [tehtavaryhma (avain->tehtavaryhma tehtavaryhmat tehtavaryhma-avain)]
                              [(-> tila/kulut-kohdistus-default
                                 (assoc :tehtavaryhma (:id tehtavaryhma)
                                        :toimenpideinstanssi (:toimenpideinstanssi tehtavaryhma)))])
                            ;; Kattohinnan ylityksessä kirjataan myös tavoitehinnan ylitys.
                            (let [tehtavaryhma-kh (avain->tehtavaryhma tehtavaryhmat tehtavaryhma-avain)
                                  tehtavaryhma-th (avain->tehtavaryhma tehtavaryhmat :tavoitehinnan-ylitys)]
                              [(-> tila/kulut-kohdistus-default
                                 (assoc
                                   :tehtavaryhma (:id tehtavaryhma-th)
                                   :toimenpideinstanssi (:toimenpideinstanssi tehtavaryhma-th)))
                               (-> tila/kulut-kohdistus-default
                                 (assoc
                                   :rivi 1
                                   :tehtavaryhma (:id tehtavaryhma-kh)
                                   :toimenpideinstanssi (:toimenpideinstanssi tehtavaryhma-kh)))])))]
    [:div.palstat
     [:div.palsta
      [:h3.margin-top-48 "Kulun tyyppi"]
      (into [:div.row] (mapv (fn [[avain kulun-tyyppi]]
                               [:div.flex-row
                                [:input.vayla-radio
                                 {:id (name avain)
                                  :type :radio
                                  :name "vuoden-paatos-group"
                                  :default-checked (if (nil? (:id lomake))
                                                     ;; Jos ei muokata vanhaa, ensimmäinen listassa on valittu.
                                                      (= (first (keys vuoden-paatoksen-kulun-tyypit)) avain)

                                                     ;; Muuten päätellään muokattavan tyypin valinta
                                                     ;; viimeisestä kohdistuksesta.
                                                      (= (:tehtavaryhma (last kohdistukset))
                                                        (:id (avain->tehtavaryhma tehtavaryhmat avain))))
                                  :disabled (not= 0 haetaan)
                                  :on-change #(let [kohdistusten-paivitys-fn (when (.. % -target -checked)
                                                                               (fn [_]
                                                                                 (aseta-kohdistus avain)))
                                                    jalkiprosessointi-fn (if (.. % -target -checked)
                                                                           (fn [lomake]
                                                                             ;; Kun valitaan kattohinnan ylitys,
                                                                             ;; päivitetään molempien lomakkeiden
                                                                             ;; validointi
                                                                             (cond-> lomake
                                                                               (= avain :kattohinnan-ylitys)
                                                                               (vary-meta
                                                                                 maaramitallisen-validoinnit
                                                                                 {:lomake lomake
                                                                                  :indeksi 1
                                                                                  :urakoitsija-maksaa? true})

                                                                               :aina
                                                                               (vary-meta
                                                                                 maaramitallisen-validoinnit
                                                                                 {:lomake lomake
                                                                                  :indeksi 0
                                                                                  :urakoitsija-maksaa? (not= :tavoitepalkkio avain)})))
                                                                           (fn [lomake]
                                                                             (vary-meta
                                                                               lomake
                                                                               paivita-validoinnit
                                                                               lomake)))]
                                                (paivitys-fn {:jalkiprosessointi-fn jalkiprosessointi-fn} :kohdistukset kohdistusten-paivitys-fn))}]
                                [:label {:for (name avain)} kulun-tyyppi]])
                         vuoden-paatoksen-kulun-tyypit))]]))

(def vuoden-paatos-kohdistus-otsikot
  ["Tavoitehinnan ylitys"
   "Kattohinnan ylitys"])

(defn tehtavien-syotto
  [{:keys [paivitys-fn haetaan kulu-lukittu?] :as opts}
   {{:keys [kohdistukset vuoden-paatos-valittu?] :as lomake} :lomake
    tehtavaryhmat :tehtavaryhmat
    toimenpiteet :toimenpiteet
    urakoitsija-maksaa? :urakoitsija-maksaa? :as tila}]
  (let [kohdistukset-lkm (count kohdistukset)
        resetoi-kohdistukset (fn [kohdistukset]
                               [tila/kulut-kohdistus-default])]
    [:div.row {:style {:max-width "960px"}}
     [:div.palstat
      [:div.palsta
       [:h3 {:style {:width "100%"}}
        "Mihin työhön kulu liittyy?"]
       [vayla-radio {:id "kulu-normaali"
                     :teksti "Normaali suunniteltu tai määrämitattava hankintakulu"
                     :ryhma "kulu-group"
                     :oletus-valittu? (cond
                                        vuoden-paatos-valittu? false
                                        (> (count kohdistukset) 1) false
                                        (= "lisatyo" (:maksueratyyppi (first kohdistukset))) false
                                        :else true)
                     :disabloitu? (or (not= 0 haetaan) kulu-lukittu?)
                     :muutos-fn (r/partial #(let [kohdistusten-paivitys-fn (if (.. % -target -checked)
                                                                             resetoi-kohdistukset)
                                                  jalkiprosessointi-fn (if (.. % -target -checked)
                                                                         (fn [lomake]
                                                                           (vary-meta
                                                                             lomake
                                                                             maaramitallisen-validoinnit
                                                                             {:lomake lomake
                                                                              :indeksi 0})))]
                                              (paivitys-fn {:jalkiprosessointi-fn jalkiprosessointi-fn} :kohdistukset kohdistusten-paivitys-fn
                                                :vuoden-paatos-valittu? (constantly false))))}]
       [:div.flex-row
        [:input#kulu-useampi.vayla-radio
         {:type :radio
          :name "kulu-group"
          :default-checked (if (and (not vuoden-paatos-valittu?) (> (count kohdistukset) 1))
                             true
                             false)
          :disabled (or (not= 0 haetaan) kulu-lukittu?)
          :on-change #(let [kohdistusten-paivitys-fn (if (.. % -target -checked)
                                                       lisaa-kohdistus
                                                       resetoi-kohdistukset)
                            jalkiprosessointi-fn (if (.. % -target -checked)
                                                   (fn [lomake]
                                                     (vary-meta
                                                       lomake
                                                       maaramitallisen-validoinnit
                                                       {:lomake lomake
                                                        :indeksi kohdistukset-lkm}))
                                                   (fn [lomake]
                                                     (vary-meta
                                                       lomake
                                                       paivita-validoinnit
                                                       lomake)))]
                        (paivitys-fn {:jalkiprosessointi-fn jalkiprosessointi-fn} :kohdistukset kohdistusten-paivitys-fn
                          :vuoden-paatos-valittu? (constantly false)))}]
        [:label {:for "kulu-useampi"} "Kulut kohdistuvat eri tehtäville ja/tai osa kuluista on lisätöitä"]]
       [:div.flex-row
        [:input#kulu-lisatyo.vayla-radio
         {:type :radio
          :name "kulu-group"
          :default-checked (cond
                             vuoden-paatos-valittu? false
                             (> (count kohdistukset) 1) false
                             (not (= "lisatyo" (:maksueratyyppi (first kohdistukset)))) false
                             :else true)
          :disabled (or (not= 0 haetaan) kulu-lukittu?)
          :on-change #(let [kohdistusten-paivitys-fn (if (.. % -target -checked)
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
                                                       {:lomake lomake
                                                        :indeksi 0})))]
                        (paivitys-fn {:jalkiprosessointi-fn jalkiprosessointi-fn} :kohdistukset kohdistusten-paivitys-fn
                          :vuoden-paatos-valittu? (constantly false)))}]
        [:label {:for "kulu-lisatyo"} "Lisätyö"]]
       [:div.flex-row
        [:input#kulu-hoitovuoden-paatos.vayla-radio
         {:type :radio
          :name "kulu-group"
          :default-checked vuoden-paatos-valittu?
          :disabled (or (not= 0 haetaan) kulu-lukittu?)
          :on-change #(let [kohdistusten-paivitys-fn (when (.. % -target -checked)
                                                       (fn [_]
                                                         (let [tavoitepalkkio-tr (avain->tehtavaryhma tehtavaryhmat :tavoitepalkkio)]
                                                           [(-> tila/kulut-kohdistus-default
                                                              (assoc :tehtavaryhma (:id tavoitepalkkio-tr)
                                                                     :toimenpideinstanssi (:toimenpideinstanssi tavoitepalkkio-tr)))])))
                            jalkiprosessointi-fn (when (.. % -target -checked)
                                                   (fn [lomake]
                                                     (vary-meta
                                                       lomake
                                                       maaramitallisen-validoinnit
                                                       {:lomake lomake
                                                        :indeksi 0})))]
                        (paivitys-fn {:jalkiprosessointi-fn jalkiprosessointi-fn} :kohdistukset kohdistusten-paivitys-fn
                          :vuoden-paatos-valittu? (constantly true)))}]
        [:label {:for "kulu-hoitovuoden-paatos"} "Hoitovuoden päätös"]]]]
     (when vuoden-paatos-valittu?
       [vuoden-paatos-checkboxit opts tila])
     (into [:div.row] (map-indexed
                        (r/partial tehtavaryhma-maara
                          {:tehtavaryhmat tehtavaryhmat
                           :toimenpiteet toimenpiteet
                           :kohdistukset-lkm kohdistukset-lkm
                           :paivitys-fn paivitys-fn
                           :disabled (or (not= 0 haetaan) kulu-lukittu?)
                           :muokataan? (muokattava? lomake)
                           :vuoden-paatos-valittu? vuoden-paatos-valittu?
                           :validius (:validius (meta lomake))
                           :kohdistus-otsikot (when vuoden-paatos-valittu? vuoden-paatos-kohdistus-otsikot)
                           :urakoitsija-maksaa? urakoitsija-maksaa?})
                        kohdistukset))
     (when (and (not urakoitsija-maksaa?) (> kohdistukset-lkm 1))
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

(defn kulun-tiedot
  [{:keys [paivitys-fn e! haetaan]}
   {:keys [koontilaskun-kuukausi laskun-numero erapaiva erapaiva-tilapainen tarkistukset testidata] :as lomake}
   vuosittaiset-valikatselmukset
   kulu-lukittu?]
  (let [{:keys [validius]} (meta lomake)
        erapaiva-meta (get validius [:erapaiva])
        koontilaskun-kuukausi-meta (get validius [:koontilaskun-kuukausi])
        laskun-nro-lukittu? (and (some? (:numerolla-tarkistettu-pvm tarkistukset))
                              (not (false? (:numerolla-tarkistettu-pvm tarkistukset))))
        laskun-nro-virhe? (if (and (some? (:numerolla-tarkistettu-pvm tarkistukset))
                                (not (false? (:numerolla-tarkistettu-pvm tarkistukset)))
                                (or
                                  (nil? erapaiva-tilapainen)
                                  (and (some? erapaiva-tilapainen)
                                    (not (pvm/sama-pvm? erapaiva-tilapainen (get-in tarkistukset [:numerolla-tarkistettu-pvm :erapaiva]))))))
                            true
                            false)
        {:keys [alkupvm loppupvm]} (-> @tila/tila :yleiset :urakka)
        alkuvuosi (pvm/vuosi alkupvm)
        loppuvuosi (pvm/vuosi loppupvm)
        hoitokauden-nro-vuodesta (fn [vuosi urakan-alkuvuosi urakan-loppuvuosi]
                                   (when (and (<= urakan-alkuvuosi vuosi) (>= urakan-loppuvuosi vuosi))
                                     (inc (- vuosi urakan-alkuvuosi))))
        hoitokaudet-ilman-valikatselmusta (keep #(when (not= true (:paatos-tehty? %))
                                                   (hoitokauden-nro-vuodesta (:vuosi %) alkuvuosi loppuvuosi))
                                            vuosittaiset-valikatselmukset)
        koontilaskun-kuukaudet (for [hv hoitokaudet-ilman-valikatselmusta
                                     kk kuukaudet]
                                 (str (name kk) "/" hv "-hoitovuosi"))
        kk-droppari-disabled (or
                               (not= 0 haetaan)
                               laskun-nro-virhe?
                               laskun-nro-lukittu?)]
    [:div.palsta
     [:h5 "Laskun tiedot"]
     [:label "Koontilaskun kuukausi *"]
     [yleiset/livi-pudotusvalikko
      {:virhe? (and
                 (not kk-droppari-disabled)
                 (not (validi-ei-tarkistettu-tai-ei-koskettu? koontilaskun-kuukausi-meta)))
       :data-cy "koontilaskun-kk-dropdown"
       :disabled (or kk-droppari-disabled kulu-lukittu?)
       :vayla-tyyli? true
       :skrollattava? true
       :valinta koontilaskun-kuukausi
       :valitse-fn (r/partial paivita-lomakkeen-arvo {:paivitys-fn paivitys-fn
                                                      :polku :koontilaskun-kuukausi
                                                      :optiot {:validoitava? true}})
       :format-fn koontilaskun-kk-formatter}
      koontilaskun-kuukaudet]
     [:label "Laskun pvm *"]
     [paivamaaran-valinta {:disabled              (or 
                                                    (not= 0 haetaan)
                                                    laskun-nro-virhe?
                                                    laskun-nro-lukittu?
                                                    (nil? koontilaskun-kuukausi)
                                                    kulu-lukittu?)
                           :placeholder (when (nil? koontilaskun-kuukausi) "Valitse koontilaskun kuukausi")
                           :erapaiva              erapaiva
                           :paivitys-fn           paivitys-fn
                           :erapaiva-meta         erapaiva-meta
                           :koontilaskun-kuukausi koontilaskun-kuukausi}]
     [kentat/tee-otsikollinen-kentta 
      {:kentta-params {:tyyppi :string
                       :vayla-tyyli? true
                       :on-blur #(e! (tiedot/->OnkoLaskunNumeroKaytossa (.. % -target -value)))
                       :virhe? laskun-nro-virhe?
                       :disabled? kulu-lukittu?}
       :otsikko "Koontilaskun numero"
       :luokka #{}
       :arvo-atom (r/wrap 
                    laskun-numero 
                    (r/partial paivita-lomakkeen-arvo 
                      {:paivitys-fn paivitys-fn 
                       :optiot {:validoitava? true} 
                       :polku :laskun-numero}))}]
     (when (or laskun-nro-lukittu? laskun-nro-virhe?)
       [:label (str "Annetulla numerolla on jo olemassa kirjaus, jonka päivämäärä on " 
               (-> tarkistukset
                 :numerolla-tarkistettu-pvm
                 :erapaiva
                 pvm/pvm)
               ". Yhdellä laskun numerolla voi olla yksi päivämäärä, joten kulu kirjataan samalle päivämäärälle. Jos haluat kirjata laskun eri päivämäärälle, vaihda laskun numero.")])]))



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
                     erapaiva
                     vuoden-paatos-valittu?] :as lomake} :lomake
             tehtavaryhmat                 :tehtavaryhmat
             toimenpiteet                  :toimenpiteet
             {haetaan :haetaan}            :parametrit :as app}]
      (let [{:keys [nayta]} lomake
            validi? (:validi? (meta lomake))
            urakoitsija-maksaa? (and vuoden-paatos-valittu?
                                  (=
                                    (:id (avain->tehtavaryhma tehtavaryhmat :tavoitehinnan-ylitys))
                                    (:tehtavaryhma (first (:kohdistukset lomake)))))
            ;; Jos kulun eräpäivä osuu vuodelle, josta on välikatselmus pidetty, kulu lukitaan
            erapaivan-hoitovuosi (when erapaiva
                                   (pvm/vuosi (first (pvm/paivamaaran-hoitokausi erapaiva))))
            kulu-lukittu? (when erapaivan-hoitovuosi
                            (some #(and
                                     (= erapaivan-hoitovuosi (:vuosi %))
                                     (:paatos-tehty? %))
                              (:vuosittaiset-valikatselmukset app)))
            kulu-lukittu-teksti "Hoitokauden välikatselmus on pidetty ja kuluja ei voi enää lisätä tai muokata."]
        [:div.ajax-peitto-kontti.kulujen-kirjaus
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
           (when (and (not (nil? (:id lomake))) (not kulu-lukittu?))
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
         (when kulu-lukittu?
           [:div.palstat
            [:div.palsta.punainen-teksti kulu-lukittu-teksti]])
         [tehtavien-syotto {:paivitys-fn paivitys-fn
                            :haetaan     haetaan
                            :kulu-lukittu? kulu-lukittu?}
          {:lomake        lomake
           :tehtavaryhmat tehtavaryhmat
           :toimenpiteet  toimenpiteet
           :urakoitsija-maksaa? urakoitsija-maksaa?}]
         [:div.palstat
          {:style {:margin-top    "56px"
                   :margin-bottom "56px"}}
          [kulun-tiedot {:paivitys-fn paivitys-fn
                          :haetaan     haetaan
                          :e!          e!}
           lomake
           (:vuosittaiset-valikatselmukset app)
           kulu-lukittu?]
          [lisatiedot
           {:paivitys-fn paivitys-fn
            :haetaan     haetaan
            :kulu-lukittu? kulu-lukittu?}
           {:lomake lomake}]]
         [:div.palstat
          {:style {:margin-top "56px"}}
          [maara-summa {:paivitys-fn paivitys-fn
                        :haetaan     haetaan
                        :kulu-lukittu? kulu-lukittu?}
           {:lomake lomake
            :urakoitsija-maksaa? urakoitsija-maksaa?}]
          [liitteet {:e! e!
                     :kulu-lukittu? kulu-lukittu?} {:lomake lomake}]]
         [:div.kulu-napit
          [napit/tallenna
           "Tallenna"
           #(e! (tiedot/->TallennaKulu))
           {:vayla-tyyli? true
            :luokka       "suuri"
            :disabled     (not validi?)}]
          [napit/peruuta
           "Peruuta"
           #(e! (tiedot/->KulujenSyotto (not syottomoodi)))
           {:ikoni        [ikonit/remove]
            :luokka       "suuri"
            :vayla-tyyli? true}]]
         (when kulu-lukittu?
           [:div.palstat
            [:div.palsta.punainen-teksti kulu-lukittu-teksti]])
         (when (not= 0 haetaan)
           [:div.ajax-peitto [yleiset/ajax-loader "Odota"]])]))))

(defn toimenpide-otsikko
  [auki? toimenpiteet tpi summa erapaiva maksuera]
  [:tr.table-default-strong.klikattava
   {:on-click #(swap! auki? not)}
   [:td.col-xs-1 (str (pvm/pvm erapaiva))]
   [:td.col-xs-1.sailyta-rivilla (str "HA" maksuera)]
   [:td.col-xs-4 (get-in toimenpiteet [tpi :toimenpide])]
   [:td.col-xs-4 
    [:span.col-xs-6  "Yhteensä"]
    [:span.col-xs-6  
     (if @auki? 
       [ikonit/harja-icon-navigation-up]
       [ikonit/harja-icon-navigation-down])]]
   [:td.col-xs-1.tasaa-oikealle.sailyta-rivilla (fmt/euro-opt summa)]
   [:td.col-xs-1 ""]])

(defn koontilasku-otsikko 
  [nro summa]
  [:tr.table-default-thin.valiotsikko.table-default-strong
   [:td {:colSpan "4"}
    (str (if (zero? nro)
           "Kulut ilman koontilaskun nroa"
           (str "Koontilasku nro " nro)) " yhteensä")] 
   [:td.tasaa-oikealle.sailyta-rivilla (fmt/euro-opt summa)]
   [:td ""]])

(defn laskun-erapaiva-otsikko
  [erapaiva]
  [:tr.table-default-thin.valiotsikko.table-default-strong
   [:td {:colSpan "6"} (str erapaiva)]])

(defn kulu-rivi 
  [{:keys [e!]} {:keys [id toimenpide-nimi tehtavaryhma-nimi maksuera liitteet summa erapaiva]}]
  [:tr.klikattava 
   {:on-click (fn [] (e! (tiedot/->AvaaKulu id)))}
   [:td.col-xs-1 (str (when erapaiva (pvm/pvm erapaiva)))]
   [:td.col-xs-1.sailyta-rivilla (str "HA" maksuera)]
   [:td.col-xs-4 toimenpide-nimi]
   [:td.col-xs-4 tehtavaryhma-nimi]
   [:td.col-xs-1.tasaa-oikealle.sailyta-rivilla (fmt/euro-opt summa)]
   [:td.col-xs-1.tasaa-oikealle (when-not (empty? liitteet) [ikonit/harja-icon-action-add-attachment])]])

(defn toimenpide-expandattava
  [_ {:keys [toimenpiteet tehtavaryhmat maksuerat]}]
  (let [auki? (r/atom false)]
    (fn [[_ tpi summa rivit] {:keys [e!]}]
      (if (> (count rivit) 1)
        (let [maksuera (some (tiedot/hae-avaimella-fn {:verrattava   (-> rivit first :toimenpideinstanssi)
                                                       :haettava     [:toimenpideinstanssi :id]
                                                       :palautettava :numero})
                             maksuerat)] 
          [:<>
           [toimenpide-otsikko auki? toimenpiteet tpi summa (-> rivit first :erapaiva) maksuera] 
           (when @auki? 
             (into [:<>] 
                   (loop [[{:keys [id toimenpideinstanssi tehtavaryhma liitteet summa] :as rivi} & loput] rivit
                          odd? false
                          elementit []]                   
                     (if (nil? rivi) 
                       elementit
                       (recur loput
                              (not odd?)
                              ^{:key (gensym "rivi-")} 
                              (conj elementit [kulu-rivi 
                                               {:e! e! :odd? odd?} 
                                               {:toimenpide-nimi (get-in toimenpiteet [toimenpideinstanssi :toimenpide]) 
                                                :tehtavaryhma-nimi (get-in tehtavaryhmat [tehtavaryhma :tehtavaryhma])
                                                :maksuera maksuera
                                                :summa summa
                                                :liitteet liitteet
                                                :erapaiva nil
                                                :id id}]))))))])
        (let [{:keys [id toimenpideinstanssi tehtavaryhma liitteet summa erapaiva]} (first rivit)] 
          [kulu-rivi 
           {:e! e! :odd? false} 
           {:toimenpide-nimi (get-in toimenpiteet [toimenpideinstanssi :toimenpide]) 
            :tehtavaryhma-nimi (get-in tehtavaryhmat [tehtavaryhma :tehtavaryhma])
            :maksuera (some (tiedot/hae-avaimella-fn {:verrattava   toimenpideinstanssi
                                                      :haettava     [:toimenpideinstanssi :id]
                                                      :palautettava :numero})
                            maksuerat)
            :summa summa
            :liitteet liitteet
            :erapaiva erapaiva
            :id id}])))))

(defn taulukko-tehdas
  [{:keys [maksuerat toimenpiteet tehtavaryhmat e!]} t]
  (cond 
    (and (vector? t)
         (= (first t) :pvm))
    (let [[_ erapaiva & _loput] t] 
      ^{:key (gensym "erap-")} [laskun-erapaiva-otsikko erapaiva])

    (and (vector? t)
         (= (first t) :laskun-numero))
    (let [[_ nro summa] t]
      ^{:key (gensym "kl-")} [koontilasku-otsikko nro summa])

    (and (vector? t)
         (= (first t) :tpi))
    ^{:key (gensym "tp-")} [toimenpide-expandattava t {:toimenpiteet toimenpiteet 
                                                       :tehtavaryhmat tehtavaryhmat 
                                                       :e! e! :maksuerat maksuerat}]

    :else
    ^{:key (gensym "d-")} [:tr]))

(defn kulutaulukko 
  [{:keys [e! tiedot tehtavaryhmat toimenpiteet haetaan? maksuerat]}]
  (let [tehtavaryhmat  (reduce #(assoc %1 (:id %2) %2) {} tehtavaryhmat)
        toimenpiteet (reduce #(assoc %1 (:toimenpideinstanssi %2) %2) {} toimenpiteet)]
    [:div.livi-grid 
     [:table.grid
      [:thead
       [:tr
        [:th.col-xs-1 "Pvm"]
        [:th.col-xs-1 "Maksuerä"]
        [:th.col-xs-4 "Toimenpide"]
        [:th.col-xs-4 "Tehtäväryhmä"]
        [:th.col-xs-1.tasaa-oikealle "Määrä"]
        [:th.col-xs-1 ""]]]
      [:tbody
       (cond 
         (and (empty? tiedot)
              (not haetaan?))
         [:tr 
          [:td {:colSpan "6"} "Annetuilla hakuehdoilla ei näytettäviä kuluja"]]

         haetaan?
         [:tr 
          [:td {:colSpan "6"} "Haku käynnissä, odota hetki"]]

         :else
         (into [:<>] (comp (map (r/partial taulukko-tehdas {:toimenpiteet toimenpiteet 
                                                            :tehtavaryhmat tehtavaryhmat
                                                            :e! e! :maksuerat maksuerat}))
                           (keep identity))
               tiedot))]]]))

(defn- kohdistetut*
  [e! app]
  (komp/luo
   (komp/piirretty (fn [this]
                     (e! (tiedot/->HaeUrakanToimenpiteetJaMaksuerat (select-keys (-> @tila/yleiset :urakka) [:id :alkupvm :loppupvm])))
                     (e! (tiedot/->HaeUrakanKulut {:id (-> @tila/yleiset :urakka :id)
                                                   :alkupvm (first (pvm/kuukauden-aikavali (pvm/nyt)))
                                                   :loppupvm (second (pvm/kuukauden-aikavali (pvm/nyt)))}))
                     (e! (tiedot/->HaeUrakanValikatselmukset))))
   (komp/ulos #(e! (tiedot/->NakymastaPoistuttiin)))
   (fn [e! {kulut :kulut syottomoodi :syottomoodi 
            {:keys [haetaan haun-kuukausi haun-alkupvm haun-loppupvm]}
            :parametrit tehtavaryhmat :tehtavaryhmat 
            toimenpiteet :toimenpiteet maksuerat :maksuerat :as app}]
     (let [[hk-alkupvm hk-loppupvm] (pvm/paivamaaran-hoitokausi (pvm/nyt))
           kuukaudet (pvm/aikavalin-kuukausivalit
                      [hk-alkupvm
                       hk-loppupvm])]
       [:div#vayla.kulujen-kohdistus
        [debug/debug app]
        (if syottomoodi
          [:div
           [kulujen-syottolomake e! app]]
          [:div
           [:div.flex-row
            [:h2 "Kulujen kohdistus"]
            ^{:key "raporttixls"}
            [:form {:style {:margin-left "auto"}
                    :target "_blank" :method "POST"
                    :action (k/excel-url :kulut)}
             [:input {:type "hidden" :name "parametrit"
                      :value (t/clj->transit {:urakka-id (-> @tila/yleiset :urakka :id)
                                              :urakka-nimi (-> @tila/yleiset :urakka :nimi)
                                              :alkupvm (or (first haun-kuukausi) haun-alkupvm)
                                              :loppupvm (or (second haun-kuukausi) haun-loppupvm)})}]
             [:button {:type "submit"
                       :class #{"button-secondary-default" "suuri"}} "Tallenna Excel"]]
            ^{:key "raporttipdf"}
            [:form {:style {:margin-left "16px"
                            :margin-right "64px"}
                    :target "_blank" :method "POST"
                    :action (k/pdf-url :kulut)}
             [:input {:type "hidden" :name "parametrit"
                      :value (t/clj->transit {:urakka-id (-> @tila/yleiset :urakka :id)
                                              :urakka-nimi (-> @tila/yleiset :urakka :nimi)
                                              :alkupvm (or (first haun-kuukausi) haun-alkupvm)
                                              :loppupvm (or (second haun-kuukausi) haun-loppupvm)})}]
             [:button {:type "submit"
                       :class #{"button-secondary-default" "suuri"}} "Tallenna PDF"]]
            [napit/yleinen-ensisijainen
             "Uusi kulu"
             #(e! (tiedot/->KulujenSyotto (not syottomoodi)))
             {:vayla-tyyli? true
              :luokka "suuri"}]]

           [:div.flex-row {:style {:justify-content "flex-start"}}
            [valinnat/kuukausi {:nil-valinta yleiset/valitse-text
                                :vayla-tyyli? true
                                :valitse-fn #(do
                                               (e! (tiedot/->AsetaHakukuukausi %))
                                               (e! (tiedot/->HaeUrakanKulut {:id (-> @tila/yleiset :urakka :id)
                                                                             :kuukausi %})))}
             kuukaudet haun-kuukausi]
            [:div.label-ja-alasveto.aikavali
             [:span.alasvedon-otsikko "Aikaväli"]
             [valinnat/aikavali (r/wrap [haun-alkupvm haun-loppupvm] (fn [[alkupvm loppupvm :as parametrit]]
                                                                       (when (every? some? parametrit)
                                                                         (e! (tiedot/->AsetaHakuPaivamaara alkupvm loppupvm))
                                                                         (e! (tiedot/->HaeUrakanKulut {:id (-> @tila/yleiset :urakka :id)
                                                                                                       :alkupvm alkupvm
                                                                                                       :loppupvm loppupvm})))))
              {:vayla-tyyli? true
               :nayta-otsikko? false}]]]
           (when kulut
             [kulutaulukko {:e! e! :haetaan? (> haetaan 0) 
                            :tiedot kulut :tehtavaryhmat tehtavaryhmat 
                            :toimenpiteet toimenpiteet :maksuerat maksuerat}])])]))))

(defn kohdistetut-kulut
  []
  [tuck/tuck tila/laskutus-kohdistetut-kulut kohdistetut*])

