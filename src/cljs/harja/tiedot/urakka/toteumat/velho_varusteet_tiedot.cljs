(ns harja.tiedot.urakka.toteumat.velho-varusteet-tiedot
  (:require [clojure.string :as str]
            [harja.ui.protokollat :as protokollat]
            [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]
            [tuck.core :refer [process-event] :as tuck]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.domain.tierekisteri.varusteet :as v]
            [harja.domain.varuste-ulkoiset :as varuste-ulkoiset]
            [harja.pvm :as pvm]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.varusteet-kartalla :as varusteet-kartalla]
            [harja.tiedot.urakka.kulut.yhteiset :as t-yhteiset]
            [harja.tiedot.urakka.toteumat.maarien-toteumat-kartalla :as maarien-toteumat-kartalla]
            [clojure.set :as s])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))



(defn hoitokausi-rajat [alkuvuosi]
  [(pvm/hoitokauden-alkupvm alkuvuosi)
   (pvm/hoitokauden-loppupvm (inc alkuvuosi))])

(def tietolaji->varustetyyppi-map {"tl501" "Kaiteet"
                                   "tl503" "Levähdysalueiden varusteet"
                                   "tl504" "WC"
                                   "tl505" "Jätehuolto"
                                   "tl506" "Liikennemerkit"
                                   "tl507" "Bussipysäkin varusteet"
                                   "tl508" "Bussipysäkin katos"
                                   "tl516" "Hiekkalaatikot"
                                   "tl509" "Rummut"
                                   "tl512" "Viemärit"
                                   "tl513" "Reunapaalut"
                                   "tl514" "Melurakenteet"
                                   "tl515" "Aidat"
                                   "tl517" "Portaat"
                                   "tl518" "Kivetyt alueet"
                                   "tl520" "Puomit"
                                   "tl522" "Reunakivet"
                                   "tl524" "Viherkuviot"})

(defn tietolaji->varustetyyppi [tietolaji]
  (or (get tietolaji->varustetyyppi-map tietolaji)
      (str "tuntematon: " tietolaji)))

(defn varustetyyppi->tietolaji [varustetyyppi]
  (get (s/map-invert tietolaji->varustetyyppi-map) varustetyyppi))

(def kuntoluokat [{:id 1 :nimi "Erittäin hyvä" :css-luokka "kl-erittain-hyva"}
                  {:id 2 :nimi "Hyvä" :css-luokka "kl-hyva"}
                  {:id 3 :nimi "Tyydyttävä" :css-luokka "kl-tyydyttava"}
                  {:id 4 :nimi "Huono" :css-luokka "kl-huono"}
                  {:id 5 :nimi "Erittäin huono" :css-luokka "kl-erittain-huono"}
                  {:id 6 :nimi "Puuttuu" :css-luokka "kl-puuttuu"}
                  {:id 7 :nimi "Ei voitu tarkastaa" :css-luokka "kl-ei-voitu-tarkistaa"}])

(defn hakuparametrit [{:keys [valinnat urakka]}]
  (merge
    (select-keys valinnat [:tie :aosa :aeta :losa :leta :hoitokauden-alkuvuosi :hoitovuoden-kuukausi :kuntoluokat :toteuma])
    {:urakka-id (:id urakka)
     :tietolajit (map varustetyyppi->tietolaji (:varustetyypit valinnat))}))

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
                         (filter #(not= (.indexOf (.toLowerCase (:otsikko %))
                                          (.toLowerCase teksti)) -1)
                           varustetyypit))]
            (vec (sort-by :otsikko itemit)))))))

(defn muodosta-varustetyypin-hakuparametri [varustetyyppi]
  {:kohdeluokka (:kohdeluokka varustetyyppi)
   :nimiavaruus (:nimiavaruus varustetyyppi)
   :tyyppi ((comp #(str/join "/" %) (juxt :tyyppi_avain :nimi)) varustetyyppi)})

(defn muodosta-ns-nimi-hakuparametri [param]
  (when param
    (str/join "/" [(:nimiavaruus param) (:nimi param)])))

(def +max-toteumat+ 1000)

(defrecord ValitseHoitokausi [hoitokauden-alkuvuosi])
(defrecord ValitseHoitovuodenKuukausi [hoitovuoden-kuukausi])
(defrecord ValitseTR-osoite [arvo avain])
(defrecord ValitseVarustetyyppi [varustetyyppi valittu?])
(defrecord ValitseKohdeluokka [kohdeluokka valittu? varustetyypit-atom])
(defrecord ValitseVarustetyyppi2 [varustetyyppi])
(defrecord ValitseKuntoluokka [kuntoluokka valittu?])
(defrecord ValitseToteuma [toteuma])
(defrecord ValitseToimenpide [toimenpide])
(defrecord HaeVarusteet [lahde varustetyypit])
(defrecord HaeVarusteetOnnistui [vastaus])
(defrecord HaeVarusteetEpaonnistui [vastaus])
(defrecord HaeToteumat [])
(defrecord HaeToteumatOnnistui [vastaus])
(defrecord HaeToteumatEpaonnistui [vastaus])
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

  ValitseVarustetyyppi
  (process-event [{:keys [varustetyyppi valittu?]} app]
    (pavittaa-valitut app :varustetyypit varustetyyppi valittu?))

  ValitseKohdeluokka
  (process-event [{:keys [kohdeluokka valittu? varustetyypit-atom]} app]
    (reset! varustetyypit-atom nil)
    (as-> app app
      (pavittaa-valitut app :kohdeluokat kohdeluokka valittu?)
      (assoc app :varustetyyppihaku (tee-varustetyyppihaku (:valinnat app) (:nimikkeisto app)))
      (assoc-in app [:valinnat :varustetyypit2] nil)))

  ValitseVarustetyyppi2
  (process-event [{:keys [varustetyyppi]} app]
    (assoc-in app [:valinnat :varustetyypit2] varustetyyppi))

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
  (process-event [{lahde :lahde varustetyypit :varustetyypit} {:keys [haku-paalla valinnat] :as app}]
    (if haku-paalla
      app
      (let [varustetyypit (map muodosta-varustetyypin-hakuparametri varustetyypit)
            kuntoluokat (map muodosta-ns-nimi-hakuparametri (:kuntoluokat valinnat))
            toimenpide (muodosta-ns-nimi-hakuparametri (:toimenpide valinnat))]
        (do
          (reset! varusteet-kartalla/karttataso-varusteet [])
          (-> app
            (assoc :haku-paalla true :varusteet [])
            (tuck-apurit/post! (case lahde
                                 :velho
                                 :hae-urakan-varustetoteumat
                                 :harja
                                 :hae-urakan-varustetoteuma-ulkoiset)
              (case lahde
                :velho
                (merge valinnat
                  {:urakka-id @harja.tiedot.navigaatio/valittu-urakka-id
                   :varustetyypit varustetyypit
                   :kuntoluokat kuntoluokat
                   :toimenpide toimenpide})

                :harja
                (hakuparametrit app))
              {:onnistui ->HaeVarusteetOnnistui
               :epaonnistui ->HaeVarusteetEpaonnistui}))))))

  HaeVarusteetOnnistui
  (process-event [{:keys [vastaus]} app]
    (reset! varusteet-kartalla/karttataso-varusteet
      (map (fn [t]
             (assoc t :tr-osoite (muodosta-tr-osoite t)))
        (:toteumat vastaus)))
    (-> app
      (assoc :haku-paalla false)
      (assoc :varusteet (:toteumat vastaus))))

  HaeVarusteetEpaonnistui
  (process-event [_ app]
    (reset! varusteet-kartalla/karttataso-varusteet nil)
    (viesti/nayta! "Varusteiden haku epäonnistui!" :danger)
    (-> app
        (assoc :haku-paalla false)
        (assoc :varusteet [])))

  HaeToteumat
  (process-event [_ {:keys [valinnat] :as app}]
    (-> app
        (tuck-apurit/post! :hae-varustetoteumat-ulkoiset
                           {:urakka-id (get-in app [:urakka :id])
                            :ulkoinen-oid (get-in app [:valittu-varuste :ulkoinen-oid])}
                           {:onnistui ->HaeToteumatOnnistui
                            :epaonnistui ->HaeToteumatEpaonnistui})))

  HaeToteumatOnnistui
  (process-event [{:keys [vastaus] :as jotain} app]
    (assoc app :valittu-toteumat (:toteumat vastaus)))

  HaeToteumatEpaonnistui
  (process-event [{:keys [vastaus] :as jotain-muuta} app]
    (viesti/nayta! "Varusteiden toteuman haku epäonnistui!" :danger)
    app)

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
      :kohdeluokat (dissoc (group-by :kohdeluokka vastaus) "")
      :kuntoluokat (filter #(= "kuntoluokka" (:nimiavaruus %)) vastaus)
      :toimenpiteet (filter #(= "varustetoimenpide" (:nimiavaruus %)) vastaus)
      :varustetyyppihaku (tee-varustetyyppihaku valinnat (group-by :kohdeluokka vastaus))))

  HaeNimikkeistoEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta! "Kohdeluokkien haku epäonnistui!" :varoitus)
    app))
