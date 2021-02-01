(ns harja.tiedot.urakka.toteumat.maarien-toteumat
  "UI controlleri määrien toteutumille"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [tuck.core :refer [process-event] :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.domain.toteuma :as t]
            [harja.pvm :as pvm]
            [harja.ui.lomake :as ui-lomake]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.toteumat.maarien-toteumat-kartalla :as maarien-toteumat-kartalla]
            [namespacefy.core :as namespacefy])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(declare hae-toteutuneet-maarat)
(declare hae-tehtavat)
(defrecord HaeToimenpiteenTehtavaYhteenveto [rivi])
(defrecord HaeToimenpiteenTehtavaYhteenvetoOnnistui [vastaus])
(defrecord HaeToimenpiteenTehtavaYhteenvetoEpaonnistui [vastaus])
(defrecord HaeTehtavanToteumat [tehtava])
(defrecord HaeTehtavanToteumatOnnistui [vastaus])
(defrecord HaeTehtavanToteumatEpaonnistui [vastaus])
(defrecord HaeToimenpiteet [])
(defrecord HaeKaikkiTehtavat [])
(defrecord ToimenpiteetHakuOnnistui [vastaus])
(defrecord ToimenpiteetHakuEpaonnistui [vastaus])
(defrecord ValitseToimenpide [urakka toimenpide])
(defrecord ValitseTehtava [tehtava])
(defrecord ValitseHoitokausi [urakka vuosi])
(defrecord ValitseAikavali [polku arvo])
(defrecord AsetaFiltteri [polku arvo])
(defrecord MuokkaaToteumaa [toteuma-id])
(defrecord ToteumaHakuOnnistui [vastaus])
(defrecord ToteumaHakuEpaonnistui [vastaus])
(defrecord ToteumanSyotto [auki tehtava toimenpide])
(defrecord TallennaToteuma [])
(defrecord TallennaToteumaOnnistui [vastaus])
(defrecord TallennaToteumaEpaonnistui [vastaus])
(defrecord PoistaToteuma [id])
(defrecord PoistaToteumaOnnistui [vastaus])
(defrecord PoistaToteumaEpaonnistui [vastaus])
(defrecord TehtavatHakuOnnistui [vastaus parametrit])
(defrecord TehtavatHakuEpaonnistui [vastaus])

(defrecord HaeTehtavat [parametrit])
(defrecord LahetaLomake [lomake])
(defrecord LisaaToteuma [lomake])
(defrecord PaivitaLomake [lomake polku indeksi])
(defrecord TyhjennaLomake [lomake])
(defrecord PaivitaSijainti [lomake indeksi])
(defrecord PaivitaSijaintiMonelle [sijainti indeksi])


(def tyyppi->tyyppi
  {"kokonaishintainen" "maaramitattava"
   "muut-rahavaraukset" "tilaajan-varaukset"
   "lisatyo" "lisatyo"
   "vahinkojen-korjaukset" "vahinkojen-korjaukset"
   "akillinen-hoitotyo" "akillinen-hoitotyo"})

(def oletuslomake {})

(def uusi-toteuma {::t/tehtava nil
                   ::t/toteuma-id nil
                   ::t/ei-sijaintia true
                   ::t/toteuma-tehtava-id nil
                   ::t/lisatieto nil
                   ::t/maara nil})

(defn validoinnit
  ([avain lomake indeksi]
   (avain {::t/maara [(tila/silloin-kun #(= :maaramitattava (::t/tyyppi lomake)) tila/ei-nil)
                      (tila/silloin-kun #(= :maaramitattava (::t/tyyppi lomake)) tila/ei-tyhja)
                      (tila/silloin-kun #(= :maaramitattava (::t/tyyppi lomake)) tila/numero)]
           ::t/lisatieto [(tila/silloin-kun #(= :lisatyo (::t/tyyppi lomake))
                                            tila/ei-nil)
                          (tila/silloin-kun #(= :lisatyo (::t/tyyppi lomake))
                                            tila/ei-tyhja)]
           ::t/toimenpide [tila/ei-nil tila/ei-tyhja]
           ::t/tehtava [tila/ei-nil tila/ei-tyhja]
           ::t/sijainti [(tila/silloin-kun #(nil? (get-in lomake [::t/toteumat indeksi ::t/ei-sijaintia]))
                                           tila/ei-nil)]
           ::t/tyyppi [tila/ei-nil]
           ::t/pvm [tila/ei-nil tila/ei-tyhja tila/paivamaara]}))
  ([avain lomake]
   (validoinnit avain lomake 0))
  ([avain]
   (validoinnit avain {} 0)))

(def toteuma-lomakkeen-oletus-validoinnit
  [[::t/toimenpide] (validoinnit ::t/toimenpide)
   [::t/pvm] (validoinnit ::t/pvm)
   [::t/tyyppi] (validoinnit ::t/tyyppi)])

(defn toteuma-lomakkeen-validoinnit [{toteumat ::t/toteumat :as lomake}]
  (apply tila/luo-validius-tarkistukset
         (concat toteuma-lomakkeen-oletus-validoinnit
                 (mapcat (fn [i]
                           [[::t/toteumat i ::t/maara] (validoinnit ::t/maara lomake i)
                            [::t/toteumat i ::t/tehtava] (validoinnit ::t/tehtava lomake i)
                            [::t/toteumat i ::t/sijainti] (validoinnit ::t/sijainti lomake i)
                            [::t/toteumat i ::t/lisatieto] (validoinnit ::t/lisatieto lomake)])
                         (range (count toteumat))))))

(defn- hae-tehtavat-tyypille
  ([toimenpide]
   (hae-tehtavat-tyypille toimenpide :maaramitattava))
  ([toimenpide tyyppi]
   (let [tehtavaryhma (when toimenpide
                        (:otsikko toimenpide))
         rajapinta (case tyyppi
                     :akillinen-hoitotyo :maarien-toteutumien-toimenpiteiden-tehtavat
                     :lisatyo :maarien-toteutumien-toimenpiteiden-tehtavat
                     :maarien-toteutumien-toimenpiteiden-tehtavat)
         toimenpide-re-string (when toimenpide
                                (cond
                                  (re-find #"TALVIHOITO" tehtavaryhma) "alvihoito"
                                  (re-find #"LIIKENNEYMPÄRISTÖN HOITO" tehtavaryhma) "Liikenneympäristön hoito|l.ymp.hoito"
                                  (re-find #"SORATEIDEN HOITO" tehtavaryhma) "Soratiet|sorateiden"
                                  :else ""))
         parametrit {:polku :tehtavat
                     :filtteri (case tyyppi
                                 :akillinen-hoitotyo
                                 #(re-find (re-pattern (str "(" toimenpide-re-string "|rahavaraus)")) (:tehtava %))

                                 :lisatyo
                                 #(re-find (re-pattern (str "(" toimenpide-re-string ")")) (:tehtava %))

                                 (constantly true))}]
     (tuck-apurit/post! rajapinta
                        {:tehtavaryhma (:id toimenpide)
                         :urakka-id (-> @tila/yleiset :urakka :id)}
                        {:onnistui ->TehtavatHakuOnnistui
                         :onnistui-parametrit [parametrit]
                         :epaonnistui ->TehtavatHakuEpaonnistui
                         :paasta-virhe-lapi? true}))))

(defn- poista-toteuma [id]
  (tuck-apurit/post! :poista-toteuma
                     {:toteuma-id id
                      :urakka-id (-> @tila/yleiset :urakka :id)}
                     {:onnistui ->PoistaToteumaOnnistui
                      :epaonnistui ->PoistaToteumaEpaonnistui
                      :paasta-virhe-lapi? true}))

(def filtteri->tyyppi {:maaramitattavat #{"kokonaishintainen"}
                       :lisatyot #{"lisatyo"}
                       :rahavaraukset #{"akillinen-hoitotyo" "muut-rahavaraukset" "vahinkojen-korjaukset"}})

(defn- tehtavien-filtteri-fn
  [filtterit]
  (fn [tehtava]
    (cond
      (nil? filtterit)
      true

      (contains?
        (into #{}
              (mapcat filtteri->tyyppi
                      (keys
                        (into {}
                              (filter (fn [[_ arvo]]
                                        (true? arvo))
                                      filtterit)))))
        (:tyyppi tehtava))
      true

      :else
      false)))

(defn ryhmittele-tehtavat
  [ryhmiteltavat filtterit]
  (let [ryhmiteltavat-filtteroitu (filter (tehtavien-filtteri-fn filtterit)
                                          ryhmiteltavat)
        ryhmitelty-tr (group-by :toimenpide
                                ryhmiteltavat-filtteroitu)]
    (sort-by first
             (into {}
                   (map
                     (fn [[tehtavaryhma tehtavat]]
                       [tehtavaryhma (sort-by first
                                              (group-by :tehtava tehtavat))])
                     ryhmitelty-tr)))))

(defn- aseta-akillisen-tyyppi
  [toteumat t]
  (if (= t :akillinen-hoitotyo)
    (let [{{:keys [tehtava]} ::t/tehtava} (first toteumat)]
      (cond
        (re-find #"ahavarau" tehtava) :tilaajan-varaukset
        (re-find #"korjaukset" tehtava) :vahinkojen-korjaukset
        :else t))
    t))

(defn- vaihda-toimenpide-tyypin-mukaan [app tyyppi]
  (cond
    (= tyyppi :akillinen-hoitotyo)
    (some (fn [toimenpide]
            (when (= "4 LIIKENTEEN VARMISTAMINEN ERIKOISTILANTEESSA" (:otsikko toimenpide))
              toimenpide))
          (get-in app [:toimenpiteet]))
    (= tyyppi :lisatyo)
    (some (fn [toimenpide]
            (when (= "7.0 LISÄTYÖT" (:otsikko toimenpide))
              toimenpide))
          (get-in app [:toimenpiteet]))
    :else {:otsikko "Kaikki" :id 0}))

(defn- paivita-sijainti-toteumiin [toteumat app]
  (map-indexed (fn [indeksi toteuma]
                 (if (get-in app [:lomake ::t/toteumat indeksi ::t/ei-sijaintia])
                   ;; Poista sijainti
                   (assoc toteuma ::t/sijainti nil)
                   ;; Lisää sijainti
                   (assoc toteuma ::t/sijainti (get-in app [:sijainti indeksi]))))
               toteumat))

(defn- uusi-pvm-lomakkeelle [app]
  (let [vuosi (if (>= (pvm/kuukausi (pvm/nyt)) 10)
                (:hoitokauden-alkuvuosi app)
                (+ 1 (:hoitokauden-alkuvuosi app)))
        kuukausi (- (pvm/kuukausi (pvm/nyt)) 1)
        paiva 1
        uusi-pvm (pvm/luo-pvm vuosi kuukausi paiva)]
    uusi-pvm))

(extend-protocol tuck/Event

  HaeToimenpiteenTehtavaYhteenveto
  (process-event [{rivi :rivi} app]
    (do
      (tuck-apurit/post! :hae-toimenpiteen-tehtava-yhteenveto
                         {:urakka-id (-> @tila/yleiset :urakka :id)
                          :toimenpide (:otsikko rivi)
                          :hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)}
                         {:onnistui ->HaeToimenpiteenTehtavaYhteenvetoOnnistui
                          :epaonnistui ->HaeToimenpiteenTehtavaYhteenvetoEpaonnistui})
      (assoc app :toimenpiteet-lataa true)))

  HaeToimenpiteenTehtavaYhteenvetoOnnistui
  (process-event [{vastaus :vastaus} app]
    (-> app
        (assoc :toimenpiteet-lataa false)
        (assoc :toteutuneet-maarat vastaus)
        (assoc :toteutuneet-maarat-grouped (ryhmittele-tehtavat vastaus (:hakufiltteri app)))))

  HaeToimenpiteenTehtavaYhteenvetoEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Haku epäonnistui!" :danger)
    (assoc app :toimenpiteet-lataa false))

  HaeTehtavanToteumat
  (process-event [{tehtava :tehtava} app]
    ;; Avataan tai suljetaan rivi
    (do
      (if (= (:avattu-tehtava app) (:tehtava tehtava))
        (-> app
          (dissoc :haetut-toteumat)
          (dissoc :avattu-tehtava))
        (do
          (tuck-apurit/post! :hae-tehtavan-toteumat
                             {:urakka-id (-> @tila/yleiset :urakka :id)
                              :toimenpidekoodi-id (:toimenpidekoodi_id tehtava)
                              :hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)}
                             {:onnistui ->HaeTehtavanToteumatOnnistui
                              :epaonnistui ->HaeTehtavanToteumatEpaonnistui})
          (-> app
              (assoc :haetut-toteumat-lataa true)
              (dissoc :haetut-toteumat)
              (assoc :avattu-tehtava (:tehtava tehtava)))))))

  HaeTehtavanToteumatOnnistui
  (process-event [{vastaus :vastaus} app]
    (-> app
        (assoc :haetut-toteumat-lataa false)
        (assoc :haetut-toteumat vastaus)))

  HaeTehtavanToteumatEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Tehtävän toteumahaku epäonnistui!" :danger)
    (assoc app :haetut-toteumat-lataa false))

  AsetaFiltteri
  (process-event [{polku :polku arvo :arvo} app]
    (as-> app app
          (assoc-in app [:hakufiltteri polku] arvo)
          (assoc-in app [:toteutuneet-maarat-grouped] (ryhmittele-tehtavat (:toteutuneet-maarat app)
                                                                           (:hakufiltteri app)))))
  HaeTehtavat
  (process-event [{{:keys [toimenpide]} :parametrit} app]
    (hae-tehtavat-tyypille app toimenpide)
    app)

  LahetaLomake
  (process-event [{lomake :lomake} app]
    (let [{loppupvm ::t/pvm
           tyyppi ::t/tyyppi
           toimenpide ::t/toimenpide
           toteumat ::t/toteumat} lomake
          toteumat (paivita-sijainti-toteumiin toteumat app)
          urakka-id (-> @tila/yleiset :urakka :id)
          aseta-akillisen-tyyppi (r/partial aseta-akillisen-tyyppi
                                            toteumat)
          {:keys [validoi] :as validoinnit} (toteuma-lomakkeen-validoinnit lomake)
          {:keys [validi? validius]} (validoi validoinnit lomake)
          toteumat (mapv namespacefy/unnamespacefy
                         toteumat)]
      (if (true? validi?)
        (tuck-apurit/post! :tallenna-toteuma
                           {:urakka-id urakka-id
                            :toimenpide toimenpide
                            :tyyppi (aseta-akillisen-tyyppi tyyppi)
                            :loppupvm loppupvm
                            :toteumat toteumat}
                           {:onnistui ->TallennaToteumaOnnistui
                            :epaonnistui ->TallennaToteumaEpaonnistui})
        (viesti/nayta! "Puuttuvia tai virheellisiä kenttiä, tarkista kentät!" :danger))
      (-> app
          (dissoc :avattu-tehtava)
          (assoc-in [:lomake ::tila/validius] validius)
          (assoc-in [:lomake ::tila/validi?] validi?))))

  ;; Käytetään, kun halutaan lisätä useampi toteuma kerralla toteumalomakkeeella
  LisaaToteuma
  (process-event [{lomake :lomake} app]
    (let [lomake (update lomake ::t/toteumat conj uusi-toteuma)]
      (assoc app :lomake lomake)))

  PaivitaSijaintiMonelle
  (process-event [{sijainti :sijainti indeksi :indeksi} app]
    (if (not (nil? (:loppuetaisyys sijainti)))
      (-> app
          ; Jos lomakkeen sisällä olevaa sijaintidataa päivittää, sijainnin valinta ei enää toimi
          ; Joten tallennetaan sijaintidata app-stateen lomakkeen ulkopuolelle.
          (assoc-in [:sijainti indeksi] sijainti))
      app))

  PaivitaSijainti
  (process-event [{lomake :lomake indeksi :indeksi} app]
    (let [osoite (get-in lomake [indeksi :tierekisteriosoite])
          ; Kun sijaintia muokataan, pitää vanha sijainti poistaa kartalta
          _ (reset! maarien-toteumat-kartalla/karttataso-toteumat nil)]
      (if (and
            (not (empty? osoite))
            (not (nil? (get osoite :loppuetaisyys))))
        (-> app
            ; Jos lomakkeen sisällä olevaa sijaintidataa päivittää, sijainnin valinta ei enää toimi
            ; Joten tallennetaan sijaintidata app-stateen lomakkeen ulkopuolelle.
            (assoc-in [:sijainti indeksi] osoite))
        app)))

  PaivitaLomake
  (process-event [{{useampi? ::t/useampi-toteuma
                    tyyppi ::t/tyyppi
                    toimenpide ::t/toimenpide
                    viimeksi-muokattu ::ui-lomake/viimeksi-muokattu-kentta
                    :as lomake} :lomake
                   polku :polku
                   indeksi :indeksi} app]
    (let [;; Toimenpidettä vaihdettaessa polkua ei tallenneta, mutta viimeksi-muokattu tallennetaan
          polku (if (and (nil? polku) viimeksi-muokattu)
                  viimeksi-muokattu
                  polku)
          ;; Siivotaan viimeksi muokattu pois
          app (assoc app ::ui-lomake/viimeksi-muokattu-kentta nil)
          useampi-aiempi? (get-in app [:lomake ::t/useampi-toteuma])
          tyyppi-aiempi (get-in app [:lomake ::t/tyyppi])
          app (assoc app :lomake lomake)
          vain-eka (fn [ts]
                     [(first ts)])
          maara-pois (fn [ts]
                       (mapv #(dissoc % ::t/maara) ts))
          ;; Vaihdettaessa tyyppi lisätyöksi tai äkilliseksi hoitotyöksi muutetaan toimenpide sen mukaan
          toimenpide (if (and (= polku ::t/tyyppi) (not= tyyppi :maaramitattava))
                       ;; Vaihda toimenpide tyypin mukaan
                       (vaihda-toimenpide-tyypin-mukaan app tyyppi)
                       toimenpide)
          ensimmainen-sijainti (get-in app [:sijainti 0])
          app (if (true? useampi?)
                (assoc-in app [:lomake ::t/toteumat 0 ::t/sijainti] ensimmainen-sijainti)
                app)
          app (if toimenpide
                (assoc-in app [:lomake ::t/toimenpide] toimenpide)
                app)
          ;; Jos yksittäisen toteuman sijainti muutetaan ei sijainnittomaksi
          app (if (= polku [:harja.domain.toteuma/toteumat indeksi :harja.domain.toteuma/ei-sijaintia])
                (do
                  (reset! maarien-toteumat-kartalla/karttataso-toteumat nil)
                  (-> app
                      (assoc-in [:lomake indeksi :tierekisteriosoite] nil)))
                app)
          app (do
                ;; Jos toimenpide tai tyyppi muuttuu
                (when
                  (or
                    (= polku ::t/toimenpide)
                    (= viimeksi-muokattu ::t/toimenpide)
                    (= viimeksi-muokattu ::t/tyyppi))
                  (hae-tehtavat-tyypille toimenpide tyyppi))

                ;; Jos poistetaan
                (when
                  (and (= polku ::t/poistettu)
                       (not (nil? (get-in app [:lomake ::t/toteumat 0 ::t/toteuma-id]))))
                  (poista-toteuma (get-in app [:lomake ::t/toteumat 0 ::t/toteuma-id])))

                ;; Default
                app)
          ;Valitoidaan lomake
          {:keys [validoi] :as validoinnit} (toteuma-lomakkeen-validoinnit lomake)
          {:keys [validi? validius]} (validoi validoinnit lomake)
          app (-> app
                  (assoc-in [:lomake ::tila/validius] validius)
                  (assoc-in [:lomake ::tila/validi?] validi?))
          uusi-app (update app :lomake (fn [lomake]
                                         (let [lomake (cond-> lomake
                                                              ; lisätään uusi toteumamappi, jos useampi toteuma- checkboxia klikattu
                                                              (and (true? useampi?)
                                                                   (= tyyppi :maaramitattava)
                                                                   (not= useampi? useampi-aiempi?))
                                                              (update ::t/toteumat conj uusi-toteuma)

                                                              ; tai resetoidaan
                                                              (and (not (true? useampi?))
                                                                   (= tyyppi :maaramitattava)
                                                                   (not= useampi? useampi-aiempi?))
                                                              (update ::t/toteumat #(conj [] (first %)))

                                                              ; onko toteumia poistettu, jos niin asetetaan useampi-toteuma oikein
                                                              (and (true? useampi?)
                                                                   (true? useampi-aiempi?)
                                                                   (= (count (::t/toteumat lomake)) 1))
                                                              (assoc ::t/useampi-toteuma false))]
                                           ; siivotaan tyyppiä vaihdettaessa turhat kentät
                                           (if (not= tyyppi tyyppi-aiempi)
                                             (case tyyppi
                                               :akillinen-hoitotyo (update lomake ::t/toteumat (comp vain-eka maara-pois))
                                               :lisatyo (update lomake ::t/toteumat (comp vain-eka maara-pois))
                                               lomake)
                                             lomake))))
          ;; Toimenpiteen vaihtuessa tyhjennetään valittu tehtävä
          uusi-app (if (or
                         (= ::t/toimenpide polku)
                         (= ::t/tyyppi polku))
                     (-> uusi-app
                         (assoc :tehtavat [])
                         (update-in [:lomake ::t/toteumat]
                                    (fn [tehtavat]
                                      (mapv #(assoc % ::t/tehtava nil) tehtavat))))
                     uusi-app)]
      uusi-app))

  TyhjennaLomake
  (process-event [_ app]
    (hae-tehtavat nil)
    (assoc app :syottomoodi false
               :tehtavat []
               :lomake (-> tila/toteumat-default-arvot
                           :maarien-toteumat
                           :lomake)))

  PoistaToteuma
  (process-event [{id :id} app]
    (poista-toteuma id)
    app)

  ValitseToimenpide
  (process-event [{urakka :urakka toimenpide :toimenpide} app]
    (do
      (hae-toteutuneet-maarat urakka toimenpide
                              (:hoitokauden-alkuvuosi app)
                              (:aikavali-alkupvm app)
                              (:aikavali-loppupvm app))
      (hae-tehtavat toimenpide)
      (-> app
          (assoc :toimenpiteet-lataa true)
          (assoc :ajax-loader true)
          (assoc :valittu-toimenpide toimenpide)
          (assoc :haetut-toteumat nil)
          (assoc-in [:lomake ::t/toimenpide] toimenpide)
          (assoc-in [:lomake ::t/toteumat 0 ::t/tehtava] nil))))

  ValitseTehtava
  (process-event [{tehtava :tehtava} app]
    (-> app
        (assoc-in [:toteuma :tehtava] tehtava)))

  MuokkaaToteumaa
  (process-event [{toteuma-id :toteuma-id} app]
    (tuck-apurit/post! :hae-maarien-toteuma {:id toteuma-id :urakka-id (:id @nav/valittu-urakka)}
                       {:onnistui ->ToteumaHakuOnnistui
                        :epaonnistui ->ToteumaHakuEpaonnistui
                        :paasta-virhe-lapi? true})
    app)

  ToteumaHakuOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [valittu-tehtava {:id (:tehtava_id vastaus) :tehtava (:tehtava vastaus) :yksikko (:yksikko vastaus)}
          valittu-toimenpide {:id (:toimenpide_id vastaus) :otsikko (:toimenpide_otsikko vastaus)}
          sijainti {:numero (:sijainti_numero vastaus)
                    :alkuosa (:sijainti_alku vastaus)
                    :alkuetaisyys (:sijainti_alkuetaisyys vastaus)
                    :loppuosa (:sijainti_loppu vastaus)
                    :loppuetaisyys (:sijainti_loppuetaisyys vastaus)}
          _ (hae-tehtavat valittu-toimenpide)
          _ (reset! maarien-toteumat-kartalla/karttataso-toteumat (:reitti vastaus))
          _ (js/console.log "Määrien toteumat reitti vastaus " (pr-str (:reitti vastaus)))
          app
          (-> app
              (assoc-in [:syottomoodi] true)
              (assoc-in [:lomake ::t/toteumat 0 ::t/toteuma-id] (:toteuma_id vastaus))
              (assoc-in [:lomake ::t/toteumat 0 ::t/toteuma-tehtava-id] (:toteuma_tehtava_id vastaus))
              (assoc-in [:lomake ::t/pvm] (:toteuma_aika vastaus))
              (assoc-in [:lomake ::t/toteumat 0 ::t/maara] (:toteutunut vastaus))
              (assoc-in [:lomake ::t/toteumat 0 ::t/lisatieto] (:lisatieto vastaus))
              (assoc-in [:lomake ::t/toteumat 0 ::t/tehtava] valittu-tehtava)
              (assoc-in [:lomake ::t/toteumat 0 ::t/sijainti] sijainti)
              (assoc-in [:lomake 0 :tierekisteriosoite] sijainti)
              (assoc-in [:lomake ::t/toteumat 0 ::t/ei-sijaintia] (some #(nil? (second %)) sijainti))
              (assoc-in [:lomake ::t/tyyppi] (-> vastaus :tyyppi tyyppi->tyyppi keyword))
              (assoc-in [:lomake ::t/toimenpide] valittu-toimenpide)
              (assoc-in [:lomake :vuosi] (:hoitokauden-alkuvuosi vastaus)))
          ;Valitoidaan lomake
          lomake (:lomake app)
          {:keys [validoi] :as validoinnit} (toteuma-lomakkeen-validoinnit lomake)
          {:keys [validi? validius]} (validoi validoinnit lomake)
          app (-> app
                  (assoc-in [:lomake ::tila/validius] validius)
                  (assoc-in [:lomake ::tila/validi?] validi?))]
      app))

  ToteumaHakuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Toteuman haku epäonnistui!" :danger)
    app)

  ToimenpiteetHakuOnnistui
  (process-event [{vastaus :vastaus} app]
    (-> app
        (assoc :toimenpiteet-lataa false)
        (assoc-in [:toimenpiteet] vastaus)))

  ToimenpiteetHakuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Toimenpiteiden haku epäonnistui!" :danger)
    (assoc app :toimenpiteet-lataa false))

  ValitseHoitokausi
  (process-event [{urakka :urakka vuosi :vuosi} app]
    (do
      (hae-toteutuneet-maarat urakka (:valittu-toimenpide app) vuosi nil nil)
      (-> app
          (assoc :ajax-loader true)
          (assoc :toimenpiteet-lataa true)
          (assoc-in [:hoitokauden-alkuvuosi] vuosi)
          (assoc-in [:toteuma :aikavali-alkupvm] nil)
          (assoc-in [:toteuma :aikavali-loppupvm] nil))))

  ValitseAikavali
  (process-event
    [{:keys [polku arvo]} app]
    (let [arvo (if (nil? arvo)
                 (get-in app [polku])
                 arvo)]
      (-> app
          (assoc-in [:hoitokauden-alkuvuosi] nil)
          (assoc-in [(case polku
                       :alkupvm :aikavali-alkupvm
                       :loppupvm :aikavali-loppupvm)] arvo))))

  HaeToimenpiteet
  (process-event [_ app]
    (tuck-apurit/post! :urakan-toteumien-toimenpiteet {}
                       {:onnistui ->ToimenpiteetHakuOnnistui
                        :epaonnistui ->ToimenpiteetHakuEpaonnistui
                        :paasta-virhe-lapi? true})
    app)

  HaeKaikkiTehtavat
  (process-event [_ app]
    (hae-tehtavat nil)
    app)

  TehtavatHakuOnnistui
  (process-event [{vastaus :vastaus {:keys [filtteri]} :parametrit} app]
    (let [haetut-tehtavat (if filtteri
                            (filter filtteri vastaus)
                            vastaus)]
      (assoc app :tehtavat haetut-tehtavat)))

  TehtavatHakuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Haku epäonnistui!" :danger)
    app)

  ToteumanSyotto
  (process-event [{auki :auki tehtava :tehtava toimenpide :toimenpide} app]
    (let [app
          (cond-> app
                  ;; Avaa syöttömoodi
                  true (assoc-in [:syottomoodi] auki)
                  ;; Siivoa vanhat pois
                  true (assoc-in [:lomake ::t/toteumat] [])
                  true (dissoc :sijainti)
                  ;; Määritä default tyyppi
                  true (assoc-in [:lomake ::t/tyyppi] :maaramitattava)
                  ;; Aseta valittu tehtävä
                  true (assoc-in [:lomake ::t/toteumat 0 ::t/tehtava] tehtava)
                  true (assoc :valittu-tehtava tehtava)
                  ;; Aseta valittu toimenpide
                  (and
                    (not (nil? toimenpide))
                    (not= {:otsikko "Kaikki" :id 0} toimenpide)) (assoc-in [:lomake ::t/toimenpide] toimenpide)
                  (= {:otsikko "Kaikki" :id 0} toimenpide) (assoc-in [:lomake ::t/toimenpide] nil)
                  ;; Laita default arvot lomakkeelle
                  true (assoc-in [:lomake ::t/toteumat 0 ::t/toteuma-id] nil)
                  true (assoc-in [:lomake ::t/toteumat 0 ::t/toteuma-tehtava-id] nil)
                  true (assoc-in [:lomake ::t/toteumat 0 ::t/lisatieto] nil)
                  true (assoc-in [:lomake ::t/toteumat 0 ::t/maara] nil)
                  true (assoc-in [:lomake ::t/toteumat 0 ::t/ei-sijaintia] true)
                  true (assoc-in [:lomake ::t/pvm] (uusi-pvm-lomakkeelle app)))
          lomake (get app :lomake)
          ;Valitoidaan lomake - ehkä on parmepi, ettei valitoida kun aletaan syöttämään tyhjää lomaketta?
          ;{:keys [validoi] :as validoinnit} (toteuma-lomakkeen-validoinnit lomake)
          ;{:keys [validi? validius]} (validoi validoinnit lomake)
          ;app
          #_(-> app
                (assoc-in [:lomake ::tila/validius] validius)
                (assoc-in [:lomake ::tila/validi?] validi?))]
      #_ (hae-toimenpiteelle-tehtavat-ja-aseta toimenpide tehtava)
      (hae-tehtavat toimenpide tehtava)
      app))

  PoistaToteumaOnnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Toteuma poistettu!")

    ;; Päivitä määrät välittömästi poiston jälkeen
    (hae-toteutuneet-maarat (:id @nav/valittu-urakka)
                            (:valittu-toimenpide app)
                            (get-in app [:hoitokauden-alkuvuosi])
                            (get-in app [:aikavali-alkupvm])
                            (get-in app [:aikavali-loppupvm]))

    (-> app
        (assoc :ajax-loader true)
        (assoc :syottomoodi false)))

  PoistaToteumaEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Toteuman poistaminen epäonnistui" :danger)
    app)

  TallennaToteumaOnnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Toteuma tallennettu!")
    ;; Päivitä määrät välittömästi lisäyksen jälkeen
    (hae-toteutuneet-maarat (:id @nav/valittu-urakka)
                            (:valittu-toimenpide app)
                            (get-in app [:hoitokauden-alkuvuosi])
                            (get-in app [:aikavali-alkupvm])
                            (get-in app [:aikavali-loppupvm]))
    (-> app
        (assoc :syottomoodi false
               :tehtavat [])
        (assoc :ajax-loader true)
        (assoc-in [:lomake ::t/toteumat] [])))

  TallennaToteumaEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Toteuman tallennus epäonnistui!" :danger)
    app))

(defn hae-toteutuneet-maarat [urakka-id toimenpide hoitokauden-alkuvuosi aikavali-alkupvm aikavali-loppupvm]
  (let [alkupvm (when hoitokauden-alkuvuosi
                  (str hoitokauden-alkuvuosi "-10-01"))
        #_alkupvm #_(if aikavali-alkupvm
                      aikavali-alkupvm alkupvm)
        loppupvm (when hoitokauden-alkuvuosi
                   (str (inc hoitokauden-alkuvuosi) "-09-30"))
        #_loppupvm #_(if aikavali-loppupvm
                       aikavali-loppupvm loppupvm)]
    (tuck-apurit/post! :hae-toimenpiteen-tehtava-yhteenveto
                       {:urakka-id urakka-id
                        :toimenpide (:otsikko toimenpide)
                        :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}
                       {:onnistui ->HaeToimenpiteenTehtavaYhteenvetoOnnistui
                        :epaonnistui ->HaeToimenpiteenTehtavaYhteenvetoEpaonnistui
                        :paasta-virhe-lapi? true})))

(defn- hae-tehtavat
  ([toimenpide] (hae-tehtavat toimenpide nil))
  ([toimenpide valittu-tehtava]
   (let [tehtavaryhma (when toimenpide
                        (:id toimenpide))]
     (tuck-apurit/post! :maarien-toteutumien-toimenpiteiden-tehtavat
                        {:tehtavaryhma tehtavaryhma
                         :urakka-id (-> @tila/yleiset :urakka :id)}
                        {:onnistui ->TehtavatHakuOnnistui
                         :epaonnistui ->TehtavatHakuEpaonnistui
                         :paasta-virhe-lapi? true}))))
