(ns harja.tiedot.urakka.mhu-kulut
  (:require
    [clojure.string :as string]
    [tuck.core :as tuck]
    [harja.ui.viesti :as viesti]
    [harja.tyokalut.tuck :as tuck-apurit]
    [harja.tiedot.urakka.urakka :as tila]
    [harja.domain.kulut :as kulut]
    [reagent.core :as r])
  (:require-macros [harja.tyokalut.tuck :refer [varmista-kasittelyjen-jarjestys]]))

(defrecord KulujenSyotto [auki?])
(defrecord TallennaKulu [])
(defrecord PaivitaLomake [polut-ja-arvot optiot])
(defrecord AvaaKulu [kulu])
(defrecord NakymastaPoistuttiin [])
(defrecord PoistaKulu [id])
(defrecord PoistoOnnistui [tulos])
(defrecord AsetaHakukuukausi [kuukausi])
(defrecord AsetaHakuPaivamaara [polku arvo])

(defrecord KuluHaettuLomakkeelle [kulu])

(defrecord LiiteLisatty [liite])
(defrecord LiitteenPoistoOnnistui [tulos parametrit])

(defrecord HaeUrakanToimenpiteetJaTehtavaryhmat [urakka])
(defrecord HaeUrakanKulut [hakuparametrit])
(defrecord HaeUrakanToimenpiteetJaMaksuerat [hakuparametrit])
(defrecord OnkoLaskunNumeroKaytossa [laskun-numero])

(defrecord KutsuEpaonnistui [tulos parametrit])

(defrecord TarkistusOnnistui [tulos parametrit])
(defrecord MaksueraHakuOnnistui [tulos])
(defrecord TallennusOnnistui [tulos parametrit])
(defrecord ToimenpidehakuOnnistui [tulos])
(defrecord KuluhakuOnnistui [tulos])

(defrecord LataaLiite [id])
(defrecord PoistaLiite [id])

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

(defn kulu->lomake [kulu]
  (let [{suorittaja :suorittaja} kulu]
    (-> kulu
        (dissoc :suorittaja)
        (assoc :aliurakoitsija suorittaja)
        (update :kohdistukset (fn [kohdistukset]
                                (mapv #(assoc %
                                         :lisatyo?
                                         (if (= "lisatyo" (:maksueratyyppi %))
                                           true
                                           false))
                                      kohdistukset)))
        (with-meta (tila/kulun-validointi-meta kulu)))))

(defn hae-avaimella-fn [{:keys [verrattava haettava palautettava]}]
  (fn [kohde]
    (let [palautuksen-avain (or palautettava
                                haettava)]
      (when (= verrattava (if (or (vector? haettava)
                                  (seq? haettava))
                            (get-in kohde haettava)
                            (haettava kohde)))
        (palautuksen-avain kohde)))))

(defn resetoi-kulut []
  tila/kulut-lomake-default)

(defn- resetoi-kulunakyma []
  tila/kulut-default)

(defn palauta-erapaiva-temporarysta 
  [{:keys [erapaiva-temporary] :as lomake}]
  (-> lomake 
    (assoc :erapaiva erapaiva-temporary
      :koontilaskun-kuukausi (kulut/pvm->koontilaskun-kuukausi erapaiva-temporary (-> @tila/tila :yleiset :urakka :alkupvm)))
    (dissoc :erapaiva-temporary)))

(defn talleta-erapaiva-temporaryyn 
  [{:keys [erapaiva tarkistukset] :as lomake}]
  (let [numerolla-tarkistettu-pvm (-> tarkistukset :numerolla-tarkistettu-pvm :erapaiva)]
    (-> lomake 
      (assoc :erapaiva-temporary erapaiva
        :koontilaskun-kuukausi (kulut/pvm->koontilaskun-kuukausi numerolla-tarkistettu-pvm (-> @tila/tila :yleiset :urakka :alkupvm)))
      (assoc :erapaiva numerolla-tarkistettu-pvm))))

(defn paivita-erapaivat-tarvittaessa 
  [{:keys [tarkistukset erapaiva-temporary] :as lomake}]
  (let [numerolla-tarkistettu-pvm (-> tarkistukset :numerolla-tarkistettu-pvm)
        ei-konfliktia-ja-erapaiva-tallessa? (and
                                              (some? erapaiva-temporary)
                                              (false? numerolla-tarkistettu-pvm))
        konflikti-laskun-numerossa? (and (some? numerolla-tarkistettu-pvm) 
                                      (not (false? numerolla-tarkistettu-pvm)))]
    (cond-> lomake                               
      ei-konfliktia-ja-erapaiva-tallessa?
      palauta-erapaiva-temporarysta 
      
      konflikti-laskun-numerossa?
      talleta-erapaiva-temporaryyn)))

(extend-protocol tuck/Event
  NakymastaPoistuttiin
  (process-event [_ app]
    (resetoi-kulunakyma))
  LiitteenPoistoOnnistui
  (process-event [{tulos :tulos {id :liite-id} :parametrit} app]
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
  (process-event [{id :id} app]
    app)
  LiiteLisatty
  (process-event [{{:keys [kuvaus nimi id tyyppi koko]} :liite} app]
    (update-in app
               [:lomake :liitteet]
               conj
               {:liite-id     id
                :liite-nimi   nimi
                :liite-tyyppi tyyppi
                :liite-koko   koko}))

  ;; SUCCESS

  TarkistusOnnistui
  (process-event [{tulos :tulos {:keys [ei-async-laskuria]} :parametrit} app]
    (->
      app
      (update-in [:parametrit :haetaan] (if ei-async-laskuria identity dec))
      (assoc-in [:lomake :tarkistukset :numerolla-tarkistettu-pvm] tulos)
      (update :lomake paivita-erapaivat-tarvittaessa)))
  MaksueraHakuOnnistui
  (process-event [{tulos :tulos} app]
    (->
      app
      (update-in [:parametrit :haetaan] dec)
      (assoc :maksuerat tulos)))
  TallennusOnnistui
  (process-event [{tulos :tulos} {{:keys [viimeisin-haku]} :parametrit :as app}]
    ((tuck/current-send-function) (->HaeUrakanKulut viimeisin-haku))
    (-> app
        (assoc :syottomoodi false)
        (update :lomake resetoi-kulut)))
  KuluhakuOnnistui
  (process-event [{tulos :tulos} {:keys [taulukko kulut toimenpiteet kulut] :as app}]
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
      (assoc app
        :toimenpiteet toimenpiteet
        :tehtavaryhmat tehtavaryhmat)))

  ;; FAIL

  KutsuEpaonnistui
  (process-event [{{:keys [ei-async-laskuria viesti]} :parametrit} app]
    (when viesti (viesti/nayta! viesti :danger))
    (update-in app [:parametrit :haetaan] (if ei-async-laskuria identity dec)))

  ;; HAUT

  HaeUrakanToimenpiteetJaMaksuerat
  (process-event [{:keys [hakuparametrit]} app]
    (varmista-kasittelyjen-jarjestys
      (tuck-apurit/post! :tehtavaryhmat-ja-toimenpiteet
                         {:urakka-id (:id hakuparametrit)}
                         {:onnistui           ->ToimenpidehakuOnnistui
                          :epaonnistui        ->KutsuEpaonnistui
                          :epaonnistui-parametrit [{:viesti "Urakan tehtäväryhmien ja toimenpiteiden haku epäonnistui"}]
                          :paasta-virhe-lapi? true})
      (tuck-apurit/post! :hae-urakan-maksuerat
                         (:id hakuparametrit)
                         {:onnistui           ->MaksueraHakuOnnistui
                          :epaonnistui        ->KutsuEpaonnistui
                          :epaonnistui-parametrit [{:viesti "Urakan maksuerien haku epäonnistui"}]
                          :paasta-virhe-lapi? true}))
    (update-in app [:parametrit :haetaan] + 1))
  HaeUrakanKulut
  (process-event [{{:keys [id alkupvm loppupvm kuukausi tallennuksen-jalkeen?] :as viimeisin-haku} :hakuparametrit} app]
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
                   :lomake (kulu->lomake kulu))))
  AvaaKulu
  (process-event [{kulu :kulu} app]
    (-> app
        (tuck-apurit/post! :kulu
                           {:urakka-id (-> @tila/yleiset :urakka :id)
                            :id kulu}
                           {:onnistui ->KuluHaettuLomakkeelle
                            :epaonnistui ->KutsuEpaonnistui}
                           )
        (update-in [:parametrit :haetaan] inc)))

  ;; VIENNIT

  OnkoLaskunNumeroKaytossa
  (process-event [{laskun-numero :laskun-numero} app]
    (tuck-apurit/post! :tarkista-laskun-numeron-paivamaara
                       {:laskun-numero laskun-numero
                        :urakka        (-> @tila/yleiset :urakka :id)}
                       {:onnistui               ->TarkistusOnnistui
                        :onnistui-parametrit    [{:ei-async-laskuria true}]
                        :epaonnistui            ->KutsuEpaonnistui
                        :epaonnistui-parametrit [{:ei-async-laskuria true}]
                        :paasta-virhe-lapi?     true})
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
        {:keys [viimeisin-haku]} :parametrit :as app}]
    (let [urakka (-> @tila/yleiset :urakka :id)
          kokonaissumma (reduce #(+ %1 (if (true? (:poistettu %2))
                                         0
                                         (parsi-summa (:summa %2))))
                                0
                                kohdistukset)
          {validoi-fn :validoi} (meta lomake)
          validoitu-lomake (validoi-fn lomake)
          {validi? :validi?} (meta validoitu-lomake)
          tyyppi (or "laskutettava" "kiinteasti-hinnoiteltu")]
      (when (true? validi?)
        (tuck-apurit/post! :tallenna-kulu
                           {:urakka-id     urakka
                            :kulu-kohdistuksineen 
                            {:kohdistukset          kohdistukset
                             :erapaiva              erapaiva
                             :id                    (when-not (nil? id) id)
                             :urakka                urakka
                             :kokonaissumma         kokonaissumma
                             :laskun-numero         laskun-numero
                             :lisatieto             lisatieto
                             :tyyppi                tyyppi
                             :liitteet              liitteet
                             :koontilaskun-kuukausi koontilaskun-kuukausi}}
                           {:onnistui            ->TallennusOnnistui
                            :epaonnistui         ->KutsuEpaonnistui
                            :epaonnistui-parametrit [{:viesti "Kulun tallentaminen epäonnistui"}]}))
      (cond-> app
              true (assoc :lomake (assoc validoitu-lomake :paivita (inc (:paivita validoitu-lomake))))
              (true? validi?) (update-in [:parametrit :haetaan] inc))))
  PoistoOnnistui
  (process-event
    [{tulos :tulos} {{:keys [viimeisin-haku]} :parametrit :as app}]
    ((tuck/current-send-function) (->HaeUrakanKulut viimeisin-haku))
    (-> app 
      (assoc :syottomoodi false)
      (update :lomake resetoi-kulut)))
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
    [{:keys [polku arvo]} app]
    (let [arvo (if (nil? arvo)
                 (-> @tila/yleiset :urakka polku)
                 arvo)]
      (-> app
          (assoc-in [:parametrit :haun-kuukausi] nil)
          (assoc-in [:parametrit (case polku
                                       :alkupvm :haun-alkupvm
                                       :loppupvm :haun-loppupvm)] arvo))))

  ;; FORMITOIMINNOT

  KulujenSyotto
  (process-event
    [{:keys [auki?]} app]
    (-> app
        (update :lomake resetoi-kulut)
        (assoc :syottomoodi auki?))))
