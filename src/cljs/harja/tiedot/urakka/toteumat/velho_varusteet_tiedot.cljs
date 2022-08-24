(ns harja.tiedot.urakka.toteumat.velho-varusteet-tiedot
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]
            [tuck.core :refer [process-event] :as tuck]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.domain.tierekisteri.varusteet :as v]
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

(def toteumat [{:tallennusmuoto "lisatty" :esitysmuoto "Lisätty"}
               {:tallennusmuoto "paivitetty" :esitysmuoto "Päivitetty"}
               {:tallennusmuoto "poistettu" :esitysmuoto "Poistettu"}])

(defn hakuparametrit [{:keys [valinnat urakka]}]
  (merge
    (select-keys valinnat [:tie :aosa :aeta :losa :leta :hoitokauden-alkuvuosi :hoitovuoden-kuukausi :kuntoluokat :toteuma])
    {:urakka-id (:id urakka)
     :tietolajit (map varustetyyppi->tietolaji (:varustetyypit valinnat))}))

(defn hae-kentta
  "Hakee `joukko` taulukosta alkion, jonka `kentta-avain` kentällä on haettu `arvo`
  ja palauttaa sen alkion `kentta-tulos` arvon.

  (hae-kentta :a :b [{:a 1 :b \"K\"} {:a 2 :b \"E\"}] 1)
  => \"K\""
  [kentta-avain kentta-tulos joukko arvo]
  (->> joukko
       (filter #(= (kentta-avain %) arvo))
       first
       kentta-tulos))

(defn toteuma->toimenpide [toteuma]
  (hae-kentta :tallennusmuoto :esitysmuoto toteumat toteuma))

(defn toimenpide->toteuma [toimenpide]
  (hae-kentta :esitysmuoto :tallennusmuoto toteumat toimenpide))

(defn muodosta-tr-osoite [{:keys [tr-numero tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys] :as rivi}]
  (if tr-loppuosa
    (str tr-numero "/" tr-alkuosa "/" tr-alkuetaisyys "/" tr-loppuosa "/" tr-loppuetaisyys)
    (str tr-numero "/" tr-alkuosa "/" tr-alkuetaisyys)))

(def +max-toteumat+ 1000)

(defrecord ValitseHoitokausi [hoitokauden-alkuvuosi])
(defrecord ValitseHoitokaudenKuukausi [hoitokauden-kuukausi])
(defrecord ValitseTR-osoite [arvo avain])
(defrecord ValitseVarustetyyppi [varustetyyppi valittu?])
(defrecord ValitseKuntoluokka [kuntoluokka valittu?])
(defrecord ValitseToteuma [toteuma])
(defrecord HaeVarusteet [])
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

  ValitseHoitokaudenKuukausi
  (process-event [{hoitokauden-kuukausi :hoitokauden-kuukausi} app]
    (do
      (assoc-in app [:valinnat :hoitokauden-kuukausi] hoitokauden-kuukausi)))

  ValitseVarustetyyppi
  (process-event [{:keys [varustetyyppi valittu?]} app]
    (pavittaa-valitut app :varustetyypit varustetyyppi valittu?))

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

  HaeVarusteet
  (process-event [_ {:keys [haku-paalla] :as app}]
    (if haku-paalla
      app
      (do
        (reset! varusteet-kartalla/karttataso-varusteet [])
        (-> app
          (assoc :haku-paalla true :varusteet [])
          (tuck-apurit/post! :hae-urakan-varustetoteuma-ulkoiset
            (hakuparametrit app)
            {:onnistui ->HaeVarusteetOnnistui
             :epaonnistui ->HaeVarusteetEpaonnistui})))))

  HaeVarusteetOnnistui
  (process-event [{:keys [vastaus]} app]
    (reset! varusteet-kartalla/karttataso-varusteet
      (map (fn [t]
             (assoc t :tr-osoite (muodosta-tr-osoite t)
                      :toimenpide (toteuma->toimenpide (:toteuma t))
                      :varustetyyppi (tietolaji->varustetyyppi (:tietolaji t))))
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
        (assoc app :valinnat (merge valinnat {:aosa nil :aeta nil :losa nil :leta nil}))))))

