(ns harja.tiedot.urakka.toteumat.velho-varusteet-tiedot
  (:require [clojure.string :as str]
            [harja.ui.protokollat :as protokollat]
            [tuck.core :refer [process-event] :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.varusteet-kartalla :as varusteet-kartalla])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; Varustetyypit on muista valinnoista poiketen toteutettu atomilla.
;; Voisi olla mahdollista toteuttaa käyttäen r/wrapia, mutta se osoittautui toistaiseksi liian haastavaksi.
(def varustetyypit (atom nil))

(def kuntoluokat [{:id 1 :nimi "Erittäin hyvä" :css-luokka "kl-erittain-hyva"}
                  {:id 2 :nimi "Hyvä" :css-luokka "kl-hyva"}
                  {:id 3 :nimi "Tyydyttävä" :css-luokka "kl-tyydyttava"}
                  {:id 4 :nimi "Huono" :css-luokka "kl-huono"}
                  {:id 5 :nimi "Erittäin huono" :css-luokka "kl-erittain-huono"}
                  {:id 6 :nimi "Puuttuu" :css-luokka "kl-puuttuu"}
                  {:id 7 :nimi "Ei voitu tarkastaa" :css-luokka "kl-ei-voitu-tarkistaa"}])

(defn muodosta-tr-osoite [{:keys [tr-numero tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys] :as rivi}]
  (if tr-loppuosa
    (str tr-numero "/" tr-alkuosa "/" tr-alkuetaisyys "/" tr-loppuosa "/" tr-loppuetaisyys)
    (str tr-numero "/" tr-alkuosa "/" tr-alkuetaisyys)))

(defn tee-varustetyyppihaku [valinnat nimikkeisto]
  (reify protokollat/Haku
    (hae [_ teksti]
      (go (let [varustetyypit (flatten (into [] (if (:kohdeluokat valinnat)
                                                  (vals (select-keys nimikkeisto (:kohdeluokat valinnat)))
                                                  (vals nimikkeisto))))

                itemit (if (< (count teksti) 1)
                         varustetyypit
                         (filter #(and
                                    (:otsikko %)
                                    (not= (.indexOf (.toLowerCase (:otsikko %))
                                               (.toLowerCase teksti)) -1))
                           varustetyypit))]
            (vec (sort-by :otsikko itemit)))))))

(defn muodosta-varustetyypin-hakuparametri [varustetyyppi]
  {:kohdeluokka (:kohdeluokka varustetyyppi)
   :nimiavaruus (:nimiavaruus varustetyyppi)
   :tyyppi ((comp #(str/join "/" %) (juxt :tyyppi_avain :nimi)) varustetyyppi)})

(defn muodosta-ns-nimi-hakuparametri [param]
  (when param
    (str/join "/" [(:nimiavaruus param) (:nimi param)])))

(defn hakuparametrit [{:keys [valinnat]}]
  (let [varustetyypit (map muodosta-varustetyypin-hakuparametri @varustetyypit)
        kuntoluokat (map muodosta-ns-nimi-hakuparametri (:kuntoluokat valinnat))
        toimenpide (muodosta-ns-nimi-hakuparametri (:toimenpide valinnat))]
    (merge
      (select-keys valinnat [:tie :aosa :aeta :losa :leta :hoitokauden-alkuvuosi :hoitovuoden-kuukausi :kuntoluokat :toteuma])
      {:urakka-id @nav/valittu-urakka-id
       :varustetyypit varustetyypit
       :kuntoluokat kuntoluokat
       :toimenpide toimenpide})))

(def +max-toteumat+ 1000)

(defrecord ValitseHoitokausi [hoitokauden-alkuvuosi])
(defrecord ValitseHoitovuodenKuukausi [hoitovuoden-kuukausi])
(defrecord ValitseTR-osoite [arvo avain])
(defrecord ValitseKohdeluokka [kohdeluokka valittu?])
(defrecord ValitseKuntoluokka [kuntoluokka valittu?])
(defrecord ValitseToteuma [toteuma])
(defrecord ValitseToimenpide [toimenpide])
(defrecord HaeVarusteet [])
(defrecord HaeVarusteetOnnistui [vastaus])
(defrecord HaeVarusteetEpaonnistui [vastaus])
(defrecord HaeVarusteenHistoria [varuste])
(defrecord HaeVarusteenHistoriaOnnistui [vastaus])
(defrecord HaeVarusteenHistoriaEpaonnistui [vastaus])
(defrecord JarjestaVarusteet [jarjestys])
(defrecord AvaaVarusteLomake [varuste])
(defrecord SuljeVarusteLomake [])
(defrecord TyhjennaSuodattimet [hoitokauden-alkuvuosi])
(defrecord TaydennaTR-osoite-suodatin [tie aosa aeta losa leta])
(defrecord HaeNimikkeisto [])
(defrecord HaeNimikkeistoOnnistui [vastaus])
(defrecord HaeNimikkeistoEpaonnistui [vastaus])

(def fin-hk-alkupvm "01.10.")
(def fin-hk-loppupvm "30.09.")

(defn- kaanteinen-jarjestaja [a b]
  (compare b a))

(defn- pavittaa-valitut [app avain arvo valittu?]
  (let [valitut (or (get-in app [:valinnat avain]) #{})
        uudet-valitut-tai-nil (if (= "Kaikki" arvo)
                                nil
                                (let [uudet-valitut ((if valittu? conj disj) valitut arvo)]
                                  (when-not (empty? uudet-valitut)
                                    uudet-valitut)))]
    (assoc-in app [:valinnat avain] uudet-valitut-tai-nil)))

(extend-protocol tuck/Event

  ValitseHoitokausi
  (process-event [{uusi-alkuvuosi :hoitokauden-alkuvuosi} app]
    (assoc-in app [:valinnat :hoitokauden-alkuvuosi] uusi-alkuvuosi))

  ValitseHoitovuodenKuukausi
  (process-event [{hoitovuoden-kuukausi :hoitovuoden-kuukausi} app]
    (do
      (assoc-in app [:valinnat :hoitovuoden-kuukausi] hoitovuoden-kuukausi)))

  ValitseKohdeluokka
  (process-event [{:keys [kohdeluokka valittu?]} app]
    (reset! varustetyypit nil)
    (as-> app app
      (pavittaa-valitut app :kohdeluokat kohdeluokka valittu?)
      (assoc app :varustetyyppihaku (tee-varustetyyppihaku (:valinnat app) (:kohdeluokat-nimikkeisto app)))))

  ValitseKuntoluokka
  (process-event [{:keys [kuntoluokka valittu?]} app]
    (pavittaa-valitut app :kuntoluokat kuntoluokka valittu?))

  ValitseTR-osoite
  (process-event [{arvo :arvo avain :avain} app]
    (do
      (if (empty? arvo)
        (assoc-in app [:valinnat avain] nil)
        (assoc-in app [:valinnat avain] (int arvo)))))

  ValitseToteuma
  (process-event [{toteuma :toteuma} app]
    (do
      (assoc-in app [:valinnat :toteuma] toteuma)))

  ValitseToimenpide
  (process-event [{toimenpide :toimenpide} app]
    (assoc-in app [:valinnat :toimenpide] toimenpide))

  HaeVarusteet
  (process-event [_ {:keys [haku-paalla] :as app}]
    (if haku-paalla
      app
      (do
        (reset! varusteet-kartalla/karttataso-varusteet [])
        (-> app
          (assoc :haku-paalla true :varusteet [])
          (tuck-apurit/post! :hae-urakan-varustetoteumat
            (hakuparametrit app)
            {:onnistui ->HaeVarusteetOnnistui
             :epaonnistui ->HaeVarusteetEpaonnistui})))))

  HaeVarusteetOnnistui
  (process-event [{:keys [vastaus]} app]
    (reset! varusteet-kartalla/karttataso-varusteet
      (map (fn [t]
             (-> t
               (assoc :tr-osoite (muodosta-tr-osoite t))
               (assoc :toimenpide-id (:id (first (filter #(= (:otsikko %) (:toimenpide t)) (:toimenpiteet app)))))))
        (:toteumat vastaus)))
    (-> app
      (assoc :haku-paalla false)
      (assoc :varusteet (:toteumat vastaus))))

  HaeVarusteetEpaonnistui
  (process-event [_ app]
    (reset! varusteet-kartalla/karttataso-varusteet nil)
    (viesti/nayta! "Varusteiden haku epäonnistui!" :varoitus)
    (-> app
      (assoc :haku-paalla false)
      (assoc :varusteet [])))

  HaeVarusteenHistoria
  (process-event [{{:keys [kohdeluokka ulkoinen-oid]} :varuste} app]
    (tuck-apurit/post! app :hae-varusteen-historia
      {:urakka-id @nav/valittu-urakka-id
       :kohdeluokka kohdeluokka
       :ulkoinen-oid ulkoinen-oid}
      {:onnistui ->HaeVarusteenHistoriaOnnistui
       :epaonnistui ->HaeVarusteenHistoriaEpaonnistui}))

  HaeVarusteenHistoriaOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc-in app [:valittu-varuste :historia] vastaus))

  HaeVarusteenHistoriaEpaonnistui
  (process-event [{:keys [_]} app]
    (viesti/nayta! "Varusteen historian haku epäonnistui!" :varoitus)
    (assoc-in app [:valittu-varuste :historia] :haku-epaonnistui))

  JarjestaVarusteet
  (process-event [{jarjestys :jarjestys} app]
    (let [vanha-jarjestys (get-in app [:jarjestys :nimi])
          kaanteinen? (if (= jarjestys vanha-jarjestys)
                        (not (get-in app [:jarjestys :kaanteinen?]))
                        false)
          kaikki-jarjestys-kentat (into [jarjestys] [:tr-osoite :tr-alkuosa :tr-alkuetaisyys
                                                     :tr-loppuosa :tr-loppuetaisyys])
          avain-kentat (fn [x] ((apply juxt kaikki-jarjestys-kentat) x))]
      (-> app
        (assoc-in [:jarjestys :nimi] jarjestys)
        (assoc-in [:jarjestys :kaanteinen?] kaanteinen?)
        (assoc :varusteet (sort-by avain-kentat (if kaanteinen? kaanteinen-jarjestaja compare) (:varusteet app))))))

  AvaaVarusteLomake
  (process-event [{:keys [varuste]} app]
    (assoc app :valittu-varuste varuste))

  SuljeVarusteLomake
  (process-event [_ app]
    (assoc app :valittu-varuste nil))

  TyhjennaSuodattimet
  (process-event [{:keys [hoitokauden-alkuvuosi]} app]
    (assoc app :valinnat {:hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))

  TaydennaTR-osoite-suodatin
  (process-event [{:keys [tie aosa aeta losa leta]} {:keys [valinnat] :as app}]
    (let [aosa (or aosa 1)
          aeta (or aeta 0)
          losa (or losa 99999)
          leta (or leta 99999)]
      (if tie
        (assoc app :valinnat (merge valinnat {:aosa aosa :aeta aeta :losa losa :leta leta}))
        (assoc app :valinnat (merge valinnat {:aosa nil :aeta nil :losa nil :leta nil})))))

  HaeNimikkeisto
  (process-event [_ app]
    (tuck-apurit/post! app :hae-varustetoteuma-nimikkeistot
      {}
      {:onnistui ->HaeNimikkeistoOnnistui
       :epaonnistui ->HaeNimikkeistoEpaonnistui}))

  HaeNimikkeistoOnnistui
  (process-event [{:keys [vastaus]} {:keys [valinnat] :as app}]
    (assoc app
      :kohdeluokat-nimikkeisto (dissoc (group-by :kohdeluokka vastaus) "")
      :kuntoluokat-nimikkeisto (filter #(= "kuntoluokka" (:nimiavaruus %)) vastaus)
      :toimenpiteet-nimikkeisto (filter #(= "varustetoimenpide" (:nimiavaruus %)) vastaus)
      :varustetyyppihaku (tee-varustetyyppihaku valinnat (group-by :kohdeluokka vastaus))))

  HaeNimikkeistoEpaonnistui
  (process-event [{:keys [_]} app]
    (viesti/nayta! "Kohdeluokkien haku epäonnistui!" :varoitus)
    app))
