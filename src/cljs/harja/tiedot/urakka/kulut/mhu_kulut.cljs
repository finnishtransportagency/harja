(ns harja.tiedot.urakka.kulut.mhu-kulut
  (:require
    [tuck.core :as tuck]
    [harja.ui.viesti :as viesti]
    [harja.tyokalut.tuck :as tuck-apurit]
    [harja.tiedot.urakka.urakka :as tila]
    [harja.domain.kulut :as kulut]
    [reagent.core :as r]
    [clojure.string :as str]
    [harja.tiedot.navigaatio :as navigaatio]
    [harja.pvm :as pvm])
  (:require-macros [harja.tyokalut.tuck :refer [varmista-kasittelyjen-jarjestys]]))

(defrecord LisaaKohdistus [lomake])
(defrecord PoistaKohdistus [indeksi])
(defrecord KohdistusTyyppi [tyyppi nro])
(defrecord ValitseTehtavaryhmaKohdistukselle [tehtavaryhma nro])
(defrecord TavoitehintaanKuuluminen [tavoitehinta nro])
(defrecord ValitseRahavarausKohdistukselle [rahavaraus nro])
(defrecord ValitseToimenpideKohdistukselle [toimenpide nro])
(defrecord LisatyonLisatieto [lisatieto nro])
(defrecord KohdistuksenSumma [summa nro])
(defrecord KoontilaskunKuukausi [arvo])
(defrecord ValitseErapaiva [erapaiva])
(defrecord KoontilaskunNumero [koontilaskunnumero])
(defrecord ValitseHoitokausi [vuosi])

(defrecord KulujenSyotto [auki?])
(defrecord TallennaKulu [])
(defrecord AvaaKulu [kulu])
(defrecord NakymastaPoistuttiin [])
(defrecord PoistaKulu [id])
(defrecord PoistoOnnistui [tulos])
(defrecord AsetaHakukuukausi [kuukausi])
(defrecord AsetaHakuPaivamaara [alkupvm loppupvm])
(defrecord AsetaHakuAlkuPvm [pvm ])
(defrecord AsetaHakuLoppuPvm [pvm])

(defrecord KuluHaettuLomakkeelle [kulu])

(defrecord LiiteLisatty [liite])
(defrecord LiitteenPoistoOnnistui [tulos parametrit])

(defrecord HaeUrakanToimenpiteetJaTehtavaryhmat [urakka])
(defrecord HaeUrakanKulut [hakuparametrit])
(defrecord HaeUrakanToimenpiteet [hakuparametrit])
(defrecord OnkoLaskunNumeroKaytossa [laskun-numero])

(defrecord KutsuEpaonnistui [tulos parametrit])

(defrecord TarkistusOnnistui [tulos parametrit])
(defrecord TallennusOnnistui [tulos parametrit])
(defrecord ToimenpidehakuOnnistui [tulos])
(defrecord KuluhakuOnnistui [tulos])

(defrecord LataaLiite [id])
(defrecord PoistaLiite [id])

;; Haetaan välikatselmukset, eli päätökset, koska kulua ei voi syöttää/päivittää niille hoitokausille, joille välikatselmus on jo tehty
(defrecord HaeUrakanValikatselmukset [])
(defrecord HaeUrakanValikatselmuksetOnnistui [vastaus])
(defrecord HaeUrakanValikatselmuksetEpaonnistui [vastaus])

;; Haetaan urakan rahavaraukset
(defrecord HaeUrakanRahavaraukset [])
(defrecord HaeUrakanRahavarauksetOnnistui [vastaus])
(defrecord HaeUrakanRahavarauksetEpaonnistui [vastaus])

(defonce kuukaudet [:lokakuu :marraskuu :joulukuu :tammikuu :helmikuu :maaliskuu :huhtikuu :toukokuu :kesakuu :heinakuu :elokuu :syyskuu])

(def vuoden-paatoksen-kulun-tyypit
  {:tavoitepalkkio "Tavoitepalkkio"
   :tavoitehinnan-ylitys "Urakoitsija maksaa tavoitehinnan ylityksestä"
   :kattohinnan-ylitys "Urakoitsija maksaa tavoite- ja kattohinnan ylityksestä"})

(def vuoden-paatoksen-tehtavaryhmien-nimet
  {:tavoitepalkkio "Hoitovuoden päättäminen / Tavoitepalkkio"
   :tavoitehinnan-ylitys "Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä"
   :kattohinnan-ylitys "Hoitovuoden päättäminen / Urakoitsija maksaa kattohinnan ylityksestä"})

(defn avain->tehtavaryhma [tehtavaryhmat avain]
  (first (filter #(= (:tehtavaryhma %) (get vuoden-paatoksen-tehtavaryhmien-nimet avain)) tehtavaryhmat)))

(defn parsi-summa [summa]
  (cond
    (not (string? summa)) summa
    (re-matches #"-?\d+(?:\.?,?\d+)?" (str summa))
    (-> summa
        str
        (str/replace "," ".")
        js/parseFloat)
    (not (or (str/blank? summa)
             (nil? summa))) summa
    :else 0))

(defn- merkitse-kentta-kosketuksi
  [{:keys [validoitava?]} args acc [polku arvo]]
  (let [paivitetty-lomake
        (apply
          (cond (and
                  (vector? polku)
                  (fn? arvo)) update-in
                (vector? polku) assoc-in
                (fn? arvo) update
                :else assoc)
          (into [acc polku arvo] (when (fn? arvo) args)))]
    (if (true? validoitava?)
      (vary-meta paivitetty-lomake (fn [lomake-meta]
                                     (update-in
                                       lomake-meta
                                       (conj [:validius]
                                             (if (keyword? polku)
                                               (vector polku)
                                               polku))
                                       (fn [meta-kentta]
                                         (assoc meta-kentta :tarkistettu? false
                                                            :koskettu? true)))))
      paivitetty-lomake)))

(defn lomakkeen-paivitys
  [lomake polut-ja-arvot {:keys [jalkiprosessointi-fn] :as optiot} & args]
  (let [jalkiprosessointi (or jalkiprosessointi-fn
                              identity)]
    (jalkiprosessointi
      (reduce (r/partial merkitse-kentta-kosketuksi optiot args)
              lomake
              (partition 2 polut-ja-arvot)))))

(defn- vuoden-paatoksen-kulu? [{:keys [tehtavaryhmat]} kulu]
  (let [vuoden-paatoksen-tehtavaryhmat-set
        (into #{}
          (map :id (filter #(str/includes? (:tehtavaryhma %) "Hoitovuoden päättäminen") tehtavaryhmat)))]
    (-> kulu
      :kohdistukset
      first
      :tehtavaryhma
      vuoden-paatoksen-tehtavaryhmat-set
      boolean)))

(defn kulu->lomake [app kulu]
  (let [{suorittaja :suorittaja} kulu
        kl
        (-> kulu
          (dissoc :suorittaja)
          (assoc :aliurakoitsija suorittaja)
          (assoc :vuoden-paatos-valittu? (vuoden-paatoksen-kulu? app kulu))
          (update :kohdistukset (fn [kohdistukset]
                                  (mapv (fn [kohdistus]
                                          (let [toimenpide (some #(when (= (:toimenpideinstanssi kohdistus) (:toimenpideinstanssi %))
                                                                    %) (:toimenpiteet app))
                                                tehtavaryhma (some #(when (= (:tehtavaryhma kohdistus) (:id %))
                                                                      %) (:tehtavaryhmat app))

                                                ;; Hoitovuoden päätöksen tyyppi pitää päätellä kohdistukselle
                                                ;; Ja tässä vaiheessa se tehdään päättelemällä tehtäväryhmästä, että onko se hoitovuoden päätös.
                                                ;; Jos joskus energiaa jää, niin tämä pitää ehdottomasti muuttaa niin, että kohdistuksella on oikeasti tyyppi, joka on hoitovuoden päätös, ja sille voi valita
                                                ;; ne tehtäväryhmät, jotka ovat sille tyypille mahdollisia.
                                                hoitovuoden-paatostyyppi (when tehtavaryhma
                                                                           (first (keep #(when (= (val %) (:tehtavaryhma tehtavaryhma))
                                                                                           (key %)) vuoden-paatoksen-tehtavaryhmien-nimet)))
                                                kohdistustyyppi (keyword (:tyyppi kohdistus))]
                                            (-> kohdistus
                                              (assoc :lisatyo? (if (= "lisatyo" (:maksueratyyppi kohdistus)) true false))
                                              (assoc :tyyppi kohdistustyyppi)
                                              (assoc :toimenpide toimenpide)
                                              (assoc :tehtavaryhma tehtavaryhma)
                                              (assoc :hoitovuoden-paatostyyppi hoitovuoden-paatostyyppi))))
                                    kohdistukset))))
        kl (with-meta kl (tila/kulun-validointi-meta kl))]
    kl))

(defn palauta-hk-valitusta-kk [s]
  ;; "joulukuu/2-hoitovuosi" -> 2
  (when-let [[_ hoitovuosi-str] (re-find #"/(\d+)-hoitovuosi" s)]
    (js/parseInt hoitovuosi-str)))

(defn hoitovuosi-vuodeksi [alkuvuosi hoitovuosi]
  (+ alkuvuosi (dec hoitovuosi)))

(defn paatos-tehty-rivin-vuodelle? [app s]
  ;; Onko päätös tehty vuodelle eg. "joulukuu/2-hoitovuosi" 
  (let [hoitovuosi (palauta-hk-valitusta-kk s)
        alkuvuosi (pvm/vuosi (:alkupvm @navigaatio/valittu-urakka))
        rivin-vuosi (hoitovuosi-vuodeksi alkuvuosi hoitovuosi)] 
    (some #(and (= rivin-vuosi (:vuosi %)) (:paatos-tehty? %))
      (:vuosittaiset-valikatselmukset app))))

(defn palauta-urakan-mahdolliset-koontilaskun-kuukaudet [app urakka-tila]
  (let [{:keys [alkupvm loppupvm]} urakka-tila
        alkuvuosi (pvm/vuosi alkupvm)
        loppuvuosi (pvm/vuosi loppupvm)
        hoitokauden-nro-vuodesta (fn [vuosi urakan-alkuvuosi urakan-loppuvuosi]
                                   (when (and (<= urakan-alkuvuosi vuosi) (>= urakan-loppuvuosi vuosi))
                                     (inc (- vuosi urakan-alkuvuosi))))
        hoitokaudet-ilman-valikatselmusta (keep #(hoitokauden-nro-vuodesta (:vuosi %) alkuvuosi loppuvuosi)
                                            (:vuosittaiset-valikatselmukset app))
        koontilaskun-kuukaudet (for [hv hoitokaudet-ilman-valikatselmusta
                                     kk kuukaudet]
                                 (str (name kk) "/" hv "-hoitovuosi"))]
    koontilaskun-kuukaudet))

(defn alusta-lomake [app]
  (let [urakan-alkupvm (:alkupvm @navigaatio/valittu-urakka)
        urakan-loppupvm (:loppupvm @navigaatio/valittu-urakka)
        hk-loppu-pvm (pvm/hoitokauden-loppupvm (pvm/vuosi urakan-loppupvm))
        kuluva-hoitovuoden-nro (pvm/paivamaara->mhu-hoitovuosi-nro urakan-alkupvm (pvm/nyt))
        ;; Kuluva kuukausi ei voi olla pienempi, kuin urakan alkupvm:n kuukausi
        pienin-nyt-hetki (if (pvm/sama-tai-jalkeen? (pvm/nyt) urakan-alkupvm)
                           (pvm/nyt)
                           urakan-alkupvm)
        erapaiva (cond
                   ;; Nyt menee yli urakan loppupvm
                   (pvm/jalkeen? (pvm/nyt) hk-loppu-pvm)
                   hk-loppu-pvm

                   (and
                     (= (pvm/iso8601 (get-in app [:lomake :erapaiva])) (pvm/iso8601 (pvm/nyt)))
                     (pvm/sama-tai-jalkeen? (pvm/nyt) urakan-alkupvm))
                   (get-in app [:lomake :erapaiva])
                   
                   ;; Jos eräpäpivä on ennen urakan alkua, niin siirretään eräpäivä urakan ensimmäiselle päivälle
                   :else
                   urakan-alkupvm)
        
        kuluva-kuukausi (pvm/kuukauden-nimi (pvm/kuukausi pienin-nyt-hetki))
        nyky-hoitokausi-lukittu? (some #(and
                                          (= (pvm/hoitokauden-alkuvuosi-nykyhetkesta (pvm/nyt)) (:vuosi %))
                                          (:paatos-tehty? %))
                                   (:vuosittaiset-valikatselmukset app))
        perus-koontilaskun-kuukausi (when (and
                                            (not nyky-hoitokausi-lukittu?)
                                            kuluva-hoitovuoden-nro
                                            (not= "kk ei välillä 1-12" kuluva-kuukausi))
                                      (str kuluva-kuukausi "/" kuluva-hoitovuoden-nro "-hoitovuosi"))
        
        koontilaskun-kuukaudet (palauta-urakan-mahdolliset-koontilaskun-kuukaudet app (-> @tila/tila :yleiset :urakka))

        ;; Jos nykyinen kk ei ole voimassa urakassa, aseta defaultti urakan viimeiseksi kuukaudeksi
        nykyinen-hk-voimassa? (some #(= % perus-koontilaskun-kuukausi) koontilaskun-kuukaudet)
        perus-koontilaskun-kuukausi (if nykyinen-hk-voimassa?
                                      perus-koontilaskun-kuukausi
                                      (last koontilaskun-kuukaudet))

        lomake tila/kulut-lomake-default
        lomake (-> lomake
                 (assoc :erapaiva erapaiva)
                 (assoc :koontilaskun-kuukausi perus-koontilaskun-kuukausi)
                 (with-meta (tila/kulun-validointi-meta lomake)))]
    lomake))

(defn- resetoi-kulunakyma []
  tila/kulut-default)

(defn palauta-tilapainen-erapaiva
  [{:keys [erapaiva-tilapainen] :as lomake}]
  (-> lomake
    (assoc :erapaiva erapaiva-tilapainen
      :koontilaskun-kuukausi (kulut/pvm->koontilaskun-kuukausi erapaiva-tilapainen (-> @tila/tila :yleiset :urakka :alkupvm)))
    (dissoc :erapaiva-tilapainen)))

(defn talleta-tilapainen-erapaiva
  [{:keys [erapaiva tarkistukset] :as lomake}]
  (let [numerolla-tarkistettu-pvm (-> tarkistukset :numerolla-tarkistettu-pvm :erapaiva)]
    (-> lomake
      (assoc :erapaiva-tilapainen erapaiva
        :koontilaskun-kuukausi (kulut/pvm->koontilaskun-kuukausi numerolla-tarkistettu-pvm (-> @tila/tila :yleiset :urakka :alkupvm)))
      (assoc :erapaiva numerolla-tarkistettu-pvm))))

(defn paivita-erapaivat-tarvittaessa
  [{:keys [tarkistukset erapaiva-tilapainen] :as lomake}]
  (let [numerolla-tarkistettu-pvm (-> tarkistukset :numerolla-tarkistettu-pvm)
        ei-konfliktia-ja-erapaiva-tallessa? (and
                                              (some? erapaiva-tilapainen)
                                              (false? numerolla-tarkistettu-pvm))
        konflikti-laskun-numerossa? (and (some? numerolla-tarkistettu-pvm)
                                      (not (false? numerolla-tarkistettu-pvm)))]
    (cond-> lomake
      ei-konfliktia-ja-erapaiva-tallessa?
      palauta-tilapainen-erapaiva

      konflikti-laskun-numerossa?
      talleta-tilapainen-erapaiva)))

(defn drop-nth
  "Droppaa collektionista indexillä n olevan alkio."
  [coll n]
  (keep-indexed #(when (not= %1 n) %2) coll))

(extend-protocol tuck/Event

  LisaaKohdistus
  (process-event [{lomake :lomake} app]
    (let [kohdistukset (into [] (:kohdistukset lomake))
          default tila/kulut-kohdistus-default
          default (assoc default :rivi (count kohdistukset))
          kohdistukset (into [] (conj (get-in app [:lomake :kohdistukset]) default))
          kohdistukset (into [] (sort-by :rivi < kohdistukset))
          app (assoc-in app [:lomake :kohdistukset] kohdistukset)]
      app))

  PoistaKohdistus
  (process-event [{indeksi :indeksi} app]
    (let [kohdistukset (into [] (get-in app [:lomake :kohdistukset]))
          poistettava (nth kohdistukset indeksi)
          muokatut-kohdistukset (into [] (sort-by :rivi < (drop-nth kohdistukset indeksi)))
          ;; Määritellään jäljelle jääneiden kohdistusten :rivi avaimen numerot uusiksi
          muokatut-kohdistukset (into [] (map-indexed (fn [i kohdistus]
                                                        (assoc kohdistus :rivi i))
                                           muokatut-kohdistukset))
          app (-> app
                (assoc-in [:lomake :kohdistukset] muokatut-kohdistukset)
                (update-in [:lomake :poistetut-kohdistukset] conj poistettava))
          lomake (:lomake app)
          lomake (-> lomake
                   (with-meta (tila/kulun-validointi-meta lomake)))
          app (assoc app :lomake lomake)]
      app))

  KohdistusTyyppi
  (process-event [{tyyppi :tyyppi nro :nro} app]
    (let [;; Vaihdetaan kohdistuksen tyyppi ja nollataan kaikki mahdolliset aiemmat valinnat
          lisatyo? (cond
                     (= :lisatyo tyyppi) true
                     :else false)
          app (-> app
                (assoc-in [:lomake :kohdistukset nro :tyyppi] tyyppi)
                (assoc-in [:lomake :kohdistukset nro :lisatyo?] lisatyo?)
                (assoc-in [:lomake :kohdistukset nro :lisatyon-lisatieto] nil)
                (assoc-in [:lomake :kohdistukset nro :tehtavaryhma] nil)
                (assoc-in [:lomake :kohdistukset nro :rahavaraus] nil)
                (assoc-in [:lomake :kohdistukset nro :toimenpide] nil)
                (assoc-in [:lomake :kohdistukset nro :tavoitehintainen] :false)
                (assoc-in [:lomake :kohdistukset nro :toimenpideinstanssi] nil))
          lomake (:lomake app)
          lomake (-> lomake
                   (with-meta (tila/kulun-validointi-meta lomake)))
          app (assoc app :lomake lomake)]
      app))

  ValitseTehtavaryhmaKohdistukselle
  (process-event [{nro :nro tehtavaryhma :tehtavaryhma} app]
    (let [;; Toimenpideinstanssi on saatavilla tehtäväryhmän tiedoista, joten asetetaan se samalla
          app (-> app
                (assoc-in [:lomake :kohdistukset nro :toimenpideinstanssi] (:toimenpideinstanssi tehtavaryhma))
                (assoc-in [:lomake :kohdistukset nro :tehtavaryhma] tehtavaryhma))]
      app))

  TavoitehintaanKuuluminen
  (process-event [{tavoitehinta :tavoitehinta nro :nro} app]
    (let [app (-> app
                (assoc-in [:lomake :kohdistukset nro :tavoitehintainen] tavoitehinta)
                (assoc-in [:lomake :kohdistukset nro :tehtavaryhma] nil)
                (assoc-in [:lomake :kohdistukset nro :toimenpideinstanssi] nil)
                (assoc-in [:lomake :kohdistukset nro :toimenpide] nil))
          lomake (:lomake app)
          lomake (-> lomake
                   (with-meta (tila/kulun-validointi-meta lomake)))
          app (assoc app :lomake lomake)]
      app))

  ValitseRahavarausKohdistukselle
  (process-event [{rahavaraus :rahavaraus nro :nro} app]
    (-> app
      ;; Poista mahdollinen tehtäväryhmä
      (assoc-in [:lomake :kohdistukset nro :tehtavaryhma] nil)
      ;; Aseta rahavaraus
      (assoc-in [:lomake :kohdistukset nro :rahavaraus] rahavaraus)))

  ValitseToimenpideKohdistukselle
  (process-event [{toimenpide :toimenpide nro :nro} app]
    (let [;; Toimenpideinstanssi on pakko antaa ja se on saatavilla toimenpiteen tiedoista, joten asetetaan se samalla
          app (-> app
                (assoc-in [:lomake :kohdistukset nro :toimenpide] toimenpide)
                (assoc-in [:lomake :kohdistukset nro :toimenpideinstanssi] (:toimenpideinstanssi toimenpide)))]
      app))

  LisatyonLisatieto
  (process-event [{lisatieto :lisatieto nro :nro} app]
    (assoc-in app [:lomake :kohdistukset nro :lisatyon-lisatieto] lisatieto))

  KohdistuksenSumma
  (process-event [{summa :summa nro :nro} app]
    (assoc-in app [:lomake :kohdistukset nro :summa] summa))

  KoontilaskunKuukausi
  (process-event [{arvo :arvo} app]
    (println "Valittu kk: " arvo)
    (let [paatos-tehty? (paatos-tehty-rivin-vuodelle? app arvo)
          erapaiva (kulut/koontilaskun-kuukausi->pvm
                     arvo
                     (-> @tila/yleiset :urakka :alkupvm)
                     (-> @tila/yleiset :urakka :loppupvm))]
      (-> app
        (assoc-in [:lomake :koontilaskun-kuukausi] arvo)
        (assoc-in [:lomake :paatos-tehty?] paatos-tehty?)
        ;; Eräpäivän pitää olla aina saman koontilaskun kuukauden sisällä.
        (assoc-in [:lomake :erapaiva] erapaiva))))

  ValitseErapaiva
  (process-event [{erapaiva :erapaiva} app]
    (assoc-in app [:lomake :erapaiva] erapaiva))

  KoontilaskunNumero
  (process-event [{koontilaskunnumero :koontilaskunnumero} app]
    (let [app (assoc-in app [:lomake :laskun-numero] koontilaskunnumero)]
      app))

  ValitseHoitokausi
  (process-event [{vuosi :vuosi} app]
    (let [alkupvm (pvm/hoitokauden-alkupvm vuosi)
          loppupvm (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))]

      ;; Haetaan koko hoitovuoden kulut
      (tuck/action!
        (fn [e!]
          (e! (->HaeUrakanKulut {:id (-> @tila/tila :yleiset :urakka :id)
                                 :alkupvm alkupvm
                                 :loppupvm loppupvm}))))

      (-> app
        (assoc :valittu-hoitokausi [alkupvm loppupvm])
        (assoc :hoitokauden-alkuvuosi vuosi)
        (assoc-in [:parametrit :haku-menossa] true)
        (assoc-in [:parametrit :haun-kuukausi] nil)
        (assoc-in [:parametrit :haun-alkupvm] nil)
        (assoc-in [:parametrit :haun-loppupvm] nil))))

  NakymastaPoistuttiin
  (process-event [_ _app]
    (resetoi-kulunakyma))

  LiitteenPoistoOnnistui
  (process-event [{_tulos :tulos {id :liite-id} :parametrit} app]
    (-> app
      (update-in
        [:lomake :liitteet]
        (fn [liitteet]
          (filter #(not (= id (:liite-id %))) liitteet)))
      (update-in [:parametrit :haetaan] dec)))

  PoistaLiite
  (process-event [{id :id} {:keys [lomake] :as app}]
    (if (nil? (:id lomake))
      (update-in app
        [:lomake :liitteet]
        (fn [liitteet]
          (filter #(not (= id (:liite-id %))) liitteet)))
      (do
        (tuck-apurit/post! :poista-kulun-liite
          {:urakka-id (-> @tila/tila :yleiset :urakka :id)
           :kulu-id  (:id lomake)
           :liite-id  id}
          {:onnistui            ->LiitteenPoistoOnnistui
           :onnistui-parametrit [{:liite-id id}]
           :epaonnistui         ->KutsuEpaonnistui
           :paasta-virhe-lapi?  true})
        (update-in app [:parametrit :haetaan] inc))))

  LataaLiite
  (process-event [_ app]
    app)

  LiiteLisatty
  (process-event [{{:keys [nimi id tyyppi koko]} :liite} app]
    (update-in app
      [:lomake :liitteet]
      conj
      {:liite-id     id
       :liite-nimi   nimi
       :liite-tyyppi tyyppi
       :liite-koko   koko}))

  TarkistusOnnistui
  (process-event [{tulos :tulos {:keys [ei-async-laskuria]} :parametrit} app]
    (->
      app
      (update-in [:parametrit :haetaan] (if ei-async-laskuria identity dec))
      (assoc-in [:lomake :tarkistukset :numerolla-tarkistettu-pvm] tulos)
      (update :lomake paivita-erapaivat-tarvittaessa)))

  TallennusOnnistui
  (process-event [_ {{:keys [viimeisin-haku]} :parametrit :as app}]
    ((tuck/current-send-function) (->HaeUrakanKulut viimeisin-haku))
    ((tuck/current-send-function) (->HaeUrakanValikatselmukset))
    (-> app
      (assoc :syottomoodi false)
      (assoc :lomake (alusta-lomake app))))

  KuluhakuOnnistui
  (process-event [{tulos :tulos} app]
    (-> app
      (assoc :kulut tulos)
      (assoc-in [:parametrit :haku-menossa] false)
      (update-in [:parametrit :haetaan] dec)))

  ToimenpidehakuOnnistui
  (process-event [{tulos :tulos} app]
    (let [kasitelty (set
                      (flatten
                        (mapv
                          (fn [{:keys [tehtavaryhma-id tehtavaryhma-nimi toimenpide jarjestys toimenpide-id toimenpideinstanssi]}]
                            (vector
                              {:toimenpideinstanssi toimenpideinstanssi :toimenpide-id toimenpide-id :toimenpide toimenpide :jarjestys jarjestys}
                              {:tehtavaryhma tehtavaryhma-nimi :id tehtavaryhma-id :toimenpide toimenpide-id :toimenpideinstanssi toimenpideinstanssi :jarjestys jarjestys}))
                          tulos)))
          {:keys [tehtavaryhmat toimenpiteet]} (reduce
                                                 (fn [k asia]
                                                   (let [toimenpide-rivi? (nil? (:tehtavaryhma asia))
                                                         toimenpide-rivi-olemassa? (when toimenpide-rivi?
                                                                                     (some #(and (= (:toimenpideinstanssi %)
                                                                                                    (:toimenpideinstanssi asia))
                                                                                              (= (:toimenpide-id %)
                                                                                                 (:toimenpide-id asia)))
                                                                                       (:toimenpiteet k)))]
                                                     (apply update k
                                                       (if toimenpide-rivi?
                                                         :toimenpiteet
                                                         :tehtavaryhmat)
                                                       (if (and toimenpide-rivi-olemassa?
                                                             toimenpide-rivi?)
                                                         [identity]
                                                         [conj asia]))))
                                                 {:tehtavaryhmat []
                                                  :toimenpiteet  []}
                                                 (sort-by :jarjestys kasitelty))]
      (-> app
        (update-in [:parametrit :haetaan] dec)
        (assoc :toimenpiteet toimenpiteet)
        (assoc :tehtavaryhmat tehtavaryhmat))))

  KutsuEpaonnistui
  (process-event [{{:keys [ei-async-laskuria viesti]} :parametrit} app]
    (when viesti (viesti/nayta! viesti :danger))
    (-> app
      (assoc-in [:parametrit :haku-menossa] false)
      (update-in [:parametrit :haetaan] (if ei-async-laskuria identity dec))))

  HaeUrakanToimenpiteet
  (process-event [{:keys [hakuparametrit]} app]
    (varmista-kasittelyjen-jarjestys
      (tuck-apurit/post! :tehtavaryhmat-ja-toimenpiteet
        {:urakka-id (:id hakuparametrit)}
        {:onnistui           ->ToimenpidehakuOnnistui
         :epaonnistui        ->KutsuEpaonnistui
         :epaonnistui-parametrit [{:viesti "Urakan tehtäväryhmien ja toimenpiteiden haku epäonnistui"}]
         :paasta-virhe-lapi? true}))
    (update-in app [:parametrit :haetaan] + 1))

  HaeUrakanKulut
  (process-event [{{:keys [id alkupvm loppupvm kuukausi] :as viimeisin-haku} :hakuparametrit} app]
    (let [alkupvm (or alkupvm (first kuukausi))
          loppupvm (or loppupvm (second kuukausi))]
      (tuck-apurit/post! :kulut-kohdistuksineen
        {:urakka-id id
         :alkupvm alkupvm
         :loppupvm loppupvm}
        {:onnistui ->KuluhakuOnnistui
         :epaonnistui ->KutsuEpaonnistui
         :epaonnistui-parametrit [{:viesti "Urakan kulujen haku epäonnistui"}]
         :paasta-virhe-lapi? true}))
    (-> app
      (assoc-in [:parametrit :viimeisin-haku] viimeisin-haku)
      (assoc-in [:parametrit :haku-menossa] true)
      (update-in [:parametrit :haetaan] inc)))

  HaeUrakanToimenpiteetJaTehtavaryhmat
  (process-event
    [{:keys [urakka]} app]
    (tuck-apurit/post! :tehtavaryhmat-ja-toimenpiteet
      {:urakka-id urakka}
      {:onnistui           ->ToimenpidehakuOnnistui
       :epaonnistui        ->KutsuEpaonnistui
       :epaonnistui-parametrit [{:viesti "Urakan toimenpiteiden ja tehtäväryhmien haku epäonnistui"}]
       :paasta-virhe-lapi? true})
    (update-in app [:parametrit :haetaan] inc))

  KuluHaettuLomakkeelle
  (process-event [{kulu :kulu} app]
    (-> app
      (update-in [:parametrit :haetaan] dec)
      (assoc :syottomoodi true
        :lomake (kulu->lomake app kulu))))

  AvaaKulu
  (process-event [{kulu :kulu} app]
    (-> app
      (tuck-apurit/post! :hae-kulu
        {:urakka-id (-> @tila/yleiset :urakka :id)
         :id kulu}
        {:onnistui ->KuluHaettuLomakkeelle
         :epaonnistui ->KutsuEpaonnistui})
      (update-in [:parametrit :haetaan] inc)))

  OnkoLaskunNumeroKaytossa
  (process-event [{laskun-numero :laskun-numero} app]
    (tuck-apurit/post! :tarkista-laskun-numeron-paivamaara
      {:laskun-numero (when (seq laskun-numero) laskun-numero)
       :urakka (-> @tila/yleiset :urakka :id)}
      {:onnistui ->TarkistusOnnistui
       :onnistui-parametrit [{:ei-async-laskuria true}]
       :epaonnistui ->KutsuEpaonnistui
       :epaonnistui-parametrit [{:ei-async-laskuria true}]
       :paasta-virhe-lapi? true})
    app)

  TallennaKulu
  (process-event
    [_ {{:keys [kohdistukset koontilaskun-kuukausi liitteet
                laskun-numero lisatieto erapaiva id] :as lomake} :lomake
        _ :parametrit :as app}]
    (let [urakka (-> @tila/yleiset :urakka :id)
          kokonaissumma (reduce #(+ %1 (if (true? (:poistettu %2))
                                         0
                                         (parsi-summa (:summa %2))))
                          0
                          kohdistukset)
          {validoi-fn :validoi} (meta lomake)
          validoitu-lomake (validoi-fn lomake)
          {validi? :validi?} (meta validoitu-lomake)
          tyyppi (or "laskutettava" "kiinteasti-hinnoiteltu")

          ;; Muuta tehtäväryhmä mäpit id:ksi
          kohdistukset (mapv
                         (fn [kohdistus]
                           (let [tehtavaryhmaid (get-in kohdistus [:tehtavaryhma :id])]
                             (assoc kohdistus :tehtavaryhma tehtavaryhmaid)))
                         kohdistukset)]
      (if (true? validi?)
        (tuck-apurit/post! :tallenna-kulu
          {:urakka-id urakka
           :kulu-kohdistuksineen
           {:kohdistukset kohdistukset
            :erapaiva erapaiva
            :id (when-not (nil? id) id)
            :urakka urakka
            :kokonaissumma kokonaissumma
            :laskun-numero (when (seq laskun-numero) laskun-numero)
            :lisatieto lisatieto
            :tyyppi tyyppi
            :liitteet liitteet
            :koontilaskun-kuukausi koontilaskun-kuukausi}}
          {:onnistui ->TallennusOnnistui
           :epaonnistui ->KutsuEpaonnistui
           :epaonnistui-parametrit [{:viesti "Kulun tallentaminen epäonnistui"}]})
        (js/console.error "Lomaketta ei tallennettu, koska lomake ei ole validi." (pr-str validi?)))
      (cond-> app
        true (assoc :lomake (assoc validoitu-lomake :paivita (inc (:paivita validoitu-lomake))))
        (true? validi?) (update-in [:parametrit :haetaan] inc))))

  PoistoOnnistui
  (process-event
    [_ {{:keys [viimeisin-haku]} :parametrit :as app}]
    ((tuck/current-send-function) (->HaeUrakanKulut viimeisin-haku))
    ((tuck/current-send-function) (->HaeUrakanValikatselmukset))
    (-> app
      (assoc :syottomoodi false)
      (assoc :lomake (alusta-lomake app))))

  PoistaKulu
  (process-event
    [{:keys [id]} app]
    (tuck-apurit/post! :poista-kulu
      {:urakka-id (-> @tila/yleiset :urakka :id)
       :id        id}
      {:onnistui    ->PoistoOnnistui
       :epaonnistui ->KutsuEpaonnistui
       :epaonnistui-parametrit [{:viesti "Poisto epäonistui"}]})
    (update-in app [:parametrit :haetaan] inc))

  AsetaHakukuukausi
  (process-event [{:keys [kuukausi]} app]
    (-> app
      (assoc-in [:parametrit :haun-alkupvm] nil)
      (assoc-in [:parametrit :haun-loppupvm] nil)
      (assoc-in [:parametrit :haun-kuukausi] kuukausi)))

  AsetaHakuPaivamaara
  (process-event
    [{:keys [alkupvm loppupvm]} app]
    (-> app
      (assoc-in [:parametrit :haun-kuukausi] nil)
      (assoc-in [:parametrit :haun-alkupvm] (or alkupvm (-> @tila/yleiset :urakka :alkupvm)))
      (assoc-in [:parametrit :haun-loppupvm] (or loppupvm (-> @tila/yleiset :urakka :loppupvm)))))

  AsetaHakuAlkuPvm
  (process-event
    [{:keys [pvm]} app]
    (-> app
      (assoc-in [:parametrit :haun-kuukausi] nil)
      (assoc-in [:parametrit :haun-alkupvm] pvm)))

  AsetaHakuLoppuPvm
  (process-event
    [{:keys [pvm]} app]
    (-> app
      (assoc-in [:parametrit :haun-kuukausi] nil)
      (assoc-in [:parametrit :haun-loppupvm] pvm)))

  HaeUrakanValikatselmukset
  (process-event [_ app]
    (tuck-apurit/post! :hae-urakan-valikatselmukset
      {:urakka-id (-> @tila/yleiset :urakka :id)}
      {:onnistui ->HaeUrakanValikatselmuksetOnnistui
       :epaonnistui ->HaeUrakanValikatselmuksetEpaonnistui})
    app)

  HaeUrakanValikatselmuksetOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :vuosittaiset-valikatselmukset vastaus))

  HaeUrakanValikatselmuksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :vuosittaiset-valikatselmukset nil))

  HaeUrakanRahavaraukset
  (process-event [_ app]
    (tuck-apurit/post! :hae-urakan-rahavaraukset
      {:urakka-id (-> @tila/yleiset :urakka :id)}
      {:onnistui ->HaeUrakanRahavarauksetOnnistui
       :epaonnistui ->HaeUrakanRahavarauksetEpaonnistui})
    app)

  HaeUrakanRahavarauksetOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :rahavaraukset vastaus))

  HaeUrakanRahavarauksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :rahavaraukset nil))

  KulujenSyotto
  (process-event
    [{:keys [auki?]} app]
    (-> app
      (assoc :lomake (alusta-lomake app))
      (assoc :syottomoodi auki?))))

(defn kasittele-tehtavaryhmat
  "Lisättäessä tai muokatessa kuluja tehtäväryhmä alasvetovalikosta poistetaan muutamia tehtäväryhmiä, joita ei enää saa valita.
   Jos niitä on saatu kululle jotain muuta kautta, niin ne kuitenkin näytetään.
   Näitä on:
   Hoitovuoden päättäminen / Tavoitepalkkio, yksilöllinen tunniste: '55c920e7-5656-4bb0-8437-1999add714a3'
   Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä, yksilöllinen tunniste: '19907c24-dd26-460f-9cb4-2ed974b891aa'
   Hoitovuoden päättäminen / Urakoitsija maksaa kattohinnan ylityksestä, yksilöllinen tunniste: 'be34116b-2264-43e0-8ac8-3762b27a9557'

   Anna tälle funktiolle tehtäväryhmälistaus, josta 'kielletyt' tehtäväryhmät poistetaan sekä potentiaalisesti jo valittu
   tehtavaryhma-id, joka näytetään, jos sellainen on kululle saatu lisättyä, esim automaattisen kulun lisäyksen kautta."
  [tehtavaryhmat tehtavaryhma-id]
  (let [kielletty-tr (some
                       (fn [rivi]
                         (when (and (= tehtavaryhma-id (:id rivi))
                                 (or
                                   (= "Hoitovuoden päättäminen / Tavoitepalkkio" (:tehtavaryhma rivi))
                                   (= "Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä" (:tehtavaryhma rivi))
                                   (= "Hoitovuoden päättäminen / Urakoitsija maksaa kattohinnan ylityksestä" (:tehtavaryhma rivi))))
                           rivi))
                       tehtavaryhmat)
        tehtavaryhmat (if kielletty-tr
                        tehtavaryhmat
                        (filter
                          (fn [rivi]
                            (when-not (or
                                        (= "Hoitovuoden päättäminen / Tavoitepalkkio" (:tehtavaryhma rivi))
                                        (= "Hoitovuoden päättäminen / Urakoitsija maksaa tavoitehinnan ylityksestä" (:tehtavaryhma rivi))
                                        (= "Hoitovuoden päättäminen / Urakoitsija maksaa kattohinnan ylityksestä" (:tehtavaryhma rivi))) rivi))
                          tehtavaryhmat))]
    tehtavaryhmat))

(defn koontilaskun-kk-formatter
  [a]
  (if (nil? a)
    "Ei valittu"
    (let [[kk hv] (str/split a #"/")]
      (str (pvm/kuukauden-nimi (pvm/kuukauden-numero kk) true) " - "
        (get kulut/hoitovuodet-strs (keyword hv))))))

(defn validoi-lomake [lomake]
  (let [lomake (with-meta lomake (tila/kulun-validointi-meta lomake))
        {validoi-fn :validoi} (meta lomake)
        validoitu-lomake (validoi-fn lomake)
        {validi? :validi?} (meta validoitu-lomake)]
    validi?))

(defn kuluva-hoitovuosi [paivamaara]
  (let [vuosi (pvm/vuosi paivamaara)
        kuukausi (pvm/kuukausi paivamaara)
        kuluva-hoitokausivuosi (if (< kuukausi 10)
                                 (dec vuosi)
                                 vuosi)]
    kuluva-hoitokausivuosi))
