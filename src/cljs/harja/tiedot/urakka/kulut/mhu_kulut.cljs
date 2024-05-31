(ns harja.tiedot.urakka.kulut.mhu-kulut
  (:require
    [clojure.string :as string]
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
(defrecord PoistaKohdistus [lomake])
(defrecord KohdistusTyyppi [kohdistustyyppi nro])
(defrecord ValitseTehtavaryhmaKohdistukselle [tehtavaryhma nro kohdistus])
(defrecord TavoitehintaanKuuluminen [tavoitehinta nro])
(defrecord ValitseRahavarausKohdistukselle [rahavaraus nro])
(defrecord ValitseToimenpideKohdistukselle [toimenpide nro])
(defrecord LisatyonLisatieto [lisatieto nro])
(defrecord KohdistuksenSumma [summa nro])
(defrecord KoontilaskunKuukausi [arvo])
(defrecord ValitseErapaiva [erapaiva])
(defrecord KoontilaskunNumero [koontilaskunnumero])

(defrecord KulujenSyotto [auki?])
(defrecord TallennaKulu [])
(defrecord PaivitaLomake [polut-ja-arvot optiot])
(defrecord AvaaKulu [kulu])
(defrecord NakymastaPoistuttiin [])
(defrecord PoistaKulu [id])
(defrecord PoistoOnnistui [tulos])
(defrecord AsetaHakukuukausi [kuukausi])
(defrecord AsetaHakuPaivamaara [alkupvm loppupvm])

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

(defn parsi-summa [summa]
  (cond
    (not (string? summa)) summa
    (re-matches #"-?\d+(?:\.?,?\d+)?" (str summa))
    (-> summa
        str
        (string/replace "," ".")
        js/parseFloat)
    (not (or (string/blank? summa)
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

(defn- vuoden-paatoksen-kulu? [{:keys [tehtavaryhmat] :as app} kulu]
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
        _ (js/console.log "kulu->lomake :: kulu:" (pr-str kulu))]
    (-> kulu
      (dissoc :suorittaja)
      (assoc :aliurakoitsija suorittaja)
      (assoc :vuoden-paatos-valittu? (vuoden-paatoksen-kulu? app kulu))
      (update :kohdistukset (fn [kohdistukset]
                              (mapv (fn [kohdistus]
                                      (let [tehtavaryhma (some #(when (= (:tehtavaryhma kohdistus) (:id %))
                                                                  %) (:tehtavaryhmat app))
                                            kohdistustyyppi (cond
                                                              (= "lisatyo" (:maksueratyyppi kohdistus)) :lisatyo
                                                              ;; Jos rahavaraus on annettu, niin on pakko olla rahavaraus
                                                              (:rahavaraus kohdistus) :rahavaraus
                                                              ;; Jos on tehtäväryhmä ja toimenpideinstanssi, niin silloin on hankintakulu
                                                              (and (:tehtavaryhma kohdistus) (:toimenpideinstanssi kohdistus)) :hankintakulu
                                                              ;; else
                                                              :else :muukulu)]
                                        (-> kohdistus
                                          (assoc :lisatyo? (if (= "lisatyo" (:maksueratyyppi kohdistus)) true false))
                                          (assoc :kohdistustyyppi kohdistustyyppi)
                                          (assoc :tehtavaryhma tehtavaryhma))))
                                kohdistukset)))
      (with-meta (tila/kulun-validointi-meta kulu)))))

(defn alusta-lomake [app]
  (let [urakan-alkupvm (:alkupvm @navigaatio/valittu-urakka)
        kuluva-hoitovuoden-nro (pvm/paivamaara->mhu-hoitovuosi-nro urakan-alkupvm (pvm/nyt))
        kuluva-kuukausi (pvm/kuukauden-nimi (pvm/kuukausi (pvm/nyt)))
        nyky-hoitokausi-lukittu? (some #(and
                                     (= (pvm/hoitokauden-alkuvuosi-nykyhetkesta (pvm/nyt)) (:vuosi %))
                                     (:paatos-tehty? %))
                              (:vuosittaiset-valikatselmukset app))
        perus-koontilaskun-kuukausi (when (and
                                            (not nyky-hoitokausi-lukittu?)
                                            kuluva-hoitovuoden-nro
                                            (not= "kk ei välillä 1-12" kuluva-kuukausi))
                                      (str kuluva-kuukausi "/" kuluva-hoitovuoden-nro "-hoitovuosi"))
        lomake tila/kulut-lomake-default
        lomake (-> lomake
                 (assoc :koontilaskun-kuukausi perus-koontilaskun-kuukausi)
                 (with-meta (tila/kulun-validointi-meta lomake)))
        ]
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

(extend-protocol tuck/Event

  LisaaKohdistus
  (process-event [{lomake :lomake} app]
    (let [kohdistukset (:kohdistukset lomake)
          _ (js/console.log "Lisää kohdistus :: lomake" (pr-str lomake))
          _ (js/console.log "Lisää kohdistus :: kohdistukset" (pr-str kohdistukset))
          _ (js/console.log "Lisää kohdistus :: tila" (pr-str tila/kulut-kohdistus-default))
          default tila/kulut-kohdistus-default
          default (assoc default :rivi (count kohdistukset))
          app (assoc-in app [:lomake :kohdistukset (count kohdistukset)] default)
          ]
      ;; Lisätään lomakkeen kohdistuksiin uusi rivi -- kulut-kohdistus-default

      app))

  PoistaKohdistus
  (process-event [{lomake :lomake} app]
    (js/console.log "Poista kohdistus :: lomake" (pr-str lomake))
    app)

  KohdistusTyyppi
  (process-event [{kohdistustyyppi :kohdistustyyppi nro :nro} app]
    (let [_ (js/console.log "KohdistusTyyppi :: kohdistustyyppi" (pr-str kohdistustyyppi))
          _ (js/console.log "KohdistusTyyppi :: nro" (pr-str nro))
          ;; Vaihdetaan kohdistuksen tyyppi ja nollataan kaikki mahdolliset aiemmat valinnat
          app (-> app
                (assoc-in [:lomake :kohdistukset nro :kohdistustyyppi] kohdistustyyppi)
                (assoc-in [:lomake :kohdistukset nro :tehtavaryhma] nil)
                (assoc-in [:lomake :kohdistukset nro :rahavaraus] nil)
                (assoc-in [:lomake :kohdistukset nro :toimenpide] nil)
                (assoc-in [:lomake :kohdistukset nro :toimenpideinstanssi] nil)
                )]

      app))

  ValitseTehtavaryhmaKohdistukselle
  (process-event [{kohdistus :kohdistus nro :nro tehtavaryhma :tehtavaryhma} app]
    (let [_ (js/console.log "ValitseTehtavaryhmaKohdistukselle :: kohdistus" (pr-str kohdistus))
          _ (js/console.log "ValitseTehtavaryhmaKohdistukselle :: nro" (pr-str nro))
          _ (js/console.log "ValitseTehtavaryhmaKohdistukselle :: tehtavaryhma" (pr-str tehtavaryhma))
          ;; Toimenpideinstanssi on saatavilla tehtäväryhmän tiedoista, joten asetetaan se samalla
          app (-> app
                (assoc-in [:lomake :kohdistukset nro :toimenpideinstanssi] (:toimenpideinstanssi tehtavaryhma))
                (assoc-in [:lomake :kohdistukset nro :tehtavaryhma] tehtavaryhma))
          ]
      app))

  TavoitehintaanKuuluminen
  (process-event [{tavoitehinta :tavoitehinta nro :nro} app]
    (let [_ (js/console.log "TavoitehintaanKuuluminen :: tavoitehinta" (pr-str tavoitehinta))
          _ (js/console.log "ValitseTehtavaryhmaKohdistukselle :: nro" (pr-str nro))
          app (assoc-in app [:lomake :kohdistukset nro :tavoitehinta?] tavoitehinta)
          ]
      app))

  ValitseRahavarausKohdistukselle
  (process-event [{rahavaraus :rahavaraus nro :nro} app]
    (let [_ (js/console.log "ValitseRahavarausKohdistukselle :: rahavaraus" (pr-str rahavaraus))
          _ (js/console.log "ValitseTehtavaryhmaKohdistukselle :: nro" (pr-str nro))
          app (assoc-in app [:lomake :kohdistukset nro :rahavaraus] rahavaraus)
          ]
      app))

  ValitseToimenpideKohdistukselle
  (process-event [{toimenpide :toimenpide nro :nro} app]
    (let [_ (js/console.log "ValitseToimenpideKohdistukselle :: toimenpide " (pr-str toimenpide))
          _ (js/console.log "ValitseToimenpideKohdistukselle :: nro" (pr-str nro))
          ;; Toimenpideinstanssi on pakko antaa ja se on saatavilla toimenpiteen tiedoista, joten asetetaan se samalla
          app (-> app
                (assoc-in [:lomake :kohdistukset nro :toimenpide] toimenpide)
                (assoc-in [:lomake :kohdistukset nro :toimenpideinstanssi] (:toimenpideinstanssi toimenpide)))
          ]
      app))

  LisatyonLisatieto
  (process-event [{lisatieto :lisatieto nro :nro} app]
    (let [_ (js/console.log "LisatyonLisatieto :: lisatieto" (pr-str lisatieto))
          _ (js/console.log "LisatyonLisatieto :: nro" (pr-str nro))
          app (assoc-in app [:lomake :kohdistukset nro :lisatyon-lisatieto] lisatieto)
          ]
      app))

  KohdistuksenSumma
  (process-event [{summa :summa nro :nro} app]
    (let [_ (js/console.log "KohdistuksenSumma :: summa" (pr-str summa))
          _ (js/console.log "KohdistuksenSumma :: nro" (pr-str nro))
          app (assoc-in app [:lomake :kohdistukset nro :summa] summa)
          ]
      app))

  KoontilaskunKuukausi
  (process-event [{arvo :arvo} app]
    (let [_ (js/console.log "KoontilaskunKuukausi :: arvo" (pr-str arvo))
          erapaiva (kulut/koontilaskun-kuukausi->pvm
                     arvo
                     (-> @tila/yleiset :urakka :alkupvm)
                     (-> @tila/yleiset :urakka :loppupvm))
          _ (js/console.log "KoontilaskunKuukausi :: uusi erapaiva" (pr-str erapaiva))
          app (-> app
                (assoc-in [:lomake :koontilaskun-kuukausi] arvo)
                ;; Eräpäivän pitää olla aina saman koontilaskun kuukauden sisällä.
                (assoc-in [:lomake :erapaiva] erapaiva))]
      app))

  ValitseErapaiva
  (process-event [{erapaiva :erapaiva} app]
    (let [_ (js/console.log "ValitseErapaiva :: erapaiva" (pr-str erapaiva))
          app (assoc-in app [:lomake :erapaiva] erapaiva)
          ]
      app))

  KoontilaskunNumero
  (process-event [{koontilaskunnumero :koontilaskunnumero} app]
    (let [_ (js/console.log "KoontilaskunNumero :: koontilaskunnumero" (pr-str koontilaskunnumero))
          app (assoc-in app [:lomake :laskun-numero] koontilaskunnumero)
          ]
      app))

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
    (update-in app [:parametrit :haetaan] (if ei-async-laskuria identity dec)))

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

  PaivitaLomake
  (process-event [{polut-ja-arvot :polut-ja-arvot optiot :optiot} app]
    (let [app (update app :lomake lomakkeen-paivitys polut-ja-arvot optiot)
          lomake (:lomake app)
          {validoi-fn :validoi} (meta lomake)
          validoitu-lomake (validoi-fn lomake)
          {validi? :validi?} (meta validoitu-lomake)
          app (-> app
                (assoc-in [:lomake :validi?] validi?)
                (assoc :lomake (assoc validoitu-lomake :paivita (inc (:paivita validoitu-lomake)))))]
      app))

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
          _ (js/console.log "TallennaKulu :: validoitu-lomake :" (pr-str validoitu-lomake))
          _ (js/console.log "TallennaKulu :: (meta lomake) :" (pr-str (meta lomake)))
          {validi? :validi?} (meta validoitu-lomake)
          _ (js/console.log "TallennaKulu :: (meta validoitu-lomake) :" (pr-str (meta validoitu-lomake)))
          tyyppi (or "laskutettava" "kiinteasti-hinnoiteltu")

          ;; Muuta tehtäväryhmä mäpit id:ksi
          kohdistukset (mapv
                        (fn [kohdistus]
                          (let [tehtavaryhmaid (get-in kohdistus [:tehtavaryhma :id])]
                            (assoc kohdistus :tehtavaryhma tehtavaryhmaid)))
                        kohdistukset)
          ]
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
        (js/console.log "TallennaKulu: Ei tallennettu, koska lomake ei ole validi :: validi?" (pr-str validi?)))
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
  (process-event
    [{:keys [kuukausi]} app]
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

(defn koontilaskun-kk-formatter
  [a]
  (if (nil? a)
    "Ei valittu"
    (let [[kk hv] (str/split a #"/")]
      (str (pvm/kuukauden-nimi (pvm/kuukauden-numero kk) true) " - "
        (get kulut/hoitovuodet-strs (keyword hv))))))

(defonce kuukaudet [:lokakuu :marraskuu :joulukuu :tammikuu :helmikuu :maaliskuu :huhtikuu :toukokuu :kesakuu :heinakuu :elokuu :syyskuu])

(defn validoi-lomake [lomake]
  (let [{validoi-fn :validoi} (meta lomake)
        validoitu-lomake (validoi-fn lomake)
        {validi? :validi?} (meta validoitu-lomake)]
    validi?))
