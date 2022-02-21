(ns harja.tiedot.urakka.toteumat.velho-varusteet-tiedot
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]
            [tuck.core :refer [process-event] :as tuck]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.domain.kulut.kustannusten-seuranta :as kustannusten-seuranta]
            [harja.domain.urakka :as urakka]
            [harja.pvm :as pvm]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.kulut.yhteiset :as t-yhteiset]
            [harja.tiedot.urakka.toteumat.maarien-toteumat-kartalla :as maarien-toteumat-kartalla])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))



(defn hoitokausi-rajat [alkuvuosi]
  [(pvm/hoitokauden-alkupvm alkuvuosi)
   (pvm/hoitokauden-loppupvm (inc alkuvuosi))])

(defn tietolaji->varustetyyppi [tietolaji]
  (case tietolaji
    "tl501" "Kaiteet"
    "tl503" "Levähdysalueiden varusteet"
    "tl504" "WC"
    "tl505" "Jätehuolto"
    "tl506" "Liikennemerkki"
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
    "tl524" "Viherkuviot"
    (str "tuntematon: " tietolaji)))

(def kuntoluokat [{:nimi "Erittäin hyvä" :css-luokka "kl-erittain-hyva"}
                  {:nimi "Hyvä" :css-luokka "kl-hyva"}
                  {:nimi "Tyydyttävä" :css-luokka "kl-tyydyttava"}
                  {:nimi "Huono" :css-luokka "kl-huono"}
                  {:nimi "Erittäin huono" :css-luokka "kl-erittain-huono"}
                  {:nimi "Puuttuu" :css-luokka "kl-puuttuu"}
                  {:nimi "Ei voitu tarkastaa" :css-luokka "kl-ei-voitu-tarkistaa"}])

(def toteumat [{:tallennusmuoto "lisatty" :esitysmuoto "Lisätty"}
               {:tallennusmuoto "paivitetty" :esitysmuoto "Päivitetty"}
               {:tallennusmuoto "poistettu" :esitysmuoto "Poistettu"}])

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

(defrecord ValitseHoitokausi [urakka-id hoitokauden-alkuvuosi])
(defrecord ValitseHoitokaudenKuukausi [urakka-id hoitokauden-kuukausi])
(defrecord ValitseTR-osoite [urakka-id arvo avain])
(defrecord ValitseKuntoluokka [urakka-id kuntoluokka])
(defrecord ValitseToteuma [urakka-id toteuma])
(defrecord HaeVarusteet [])
(defrecord HaeVarusteetOnnistui [vastaus])
(defrecord HaeVarusteetEpaonnistui [vastaus])
(defrecord HaeToteumat [])
(defrecord HaeToteumatOnnistui [vastaus])
(defrecord HaeToteumatEpaonnistui [vastaus])
(defrecord JarjestaVarusteet [jarjestys])
(defrecord AvaaVarusteLomake [varuste])
(defrecord SuljeVarusteLomake [])

(def fin-hk-alkupvm "01.10.")
(def fin-hk-loppupvm "30.09.")

(defn- kaanteinen-jarjestaja [a b]
  (compare b a))

(extend-protocol tuck/Event

  ValitseHoitokausi
  (process-event [{urakka-id :urakka-id hoitokauden-alkuvuosi :hoitokauden-alkuvuosi} app]
    (-> app
        (assoc-in [:valinnat :hoitokauden-alkuvuosi] hoitokauden-alkuvuosi)
        (assoc-in [:valinnat :hoitokauden-kuukausi] nil)))

  ValitseHoitokaudenKuukausi
  (process-event [{urakka-id :urakka-id hoitokauden-kuukausi :hoitokauden-kuukausi} app]
    (do
      (assoc-in app [:valinnat :hoitokauden-kuukausi] hoitokauden-kuukausi)))

  ValitseKuntoluokka
  (process-event [{urakka-id :urakka-id kuntoluokka :kuntoluokka} app]
    (do
      (assoc-in app [:valinnat :kuntoluokka] kuntoluokka)))

  ValitseTR-osoite
  (process-event [{urakka-id :urakka-id arvo :arvo avain :avain} app]
    (do
      (assoc-in app [:valinnat avain] (int arvo))))

  ValitseToteuma
  (process-event [{urakka-id :urakka-id toteuma :toteuma} app]
    (do
      (assoc-in app [:valinnat :toteuma] toteuma)))

  HaeVarusteet
  (process-event [_ {:keys [valinnat] :as app}]
    (do
      (println "petrisi1504: " (:valinnat app))
      (if (get-in app [:valinnat :haku-paalla])
        app
        (do
          (-> app
              (assoc-in [:valinnat :haku-paalla] true)
              (tuck-apurit/post! :hae-urakan-varustetoteuma-ulkoiset
                                 {:urakka-id (get-in app [:urakka :id])
                                  :hoitovuosi (:hoitokauden-alkuvuosi valinnat)
                                  :kuukausi (:hoitokauden-kuukausi valinnat)
                                  :tie (:tie valinnat)
                                  :aosa (:aosa valinnat)
                                  :aeta (:aeta valinnat)
                                  :losa (:losa valinnat)
                                  :leta (:leta valinnat)
                                  :kuntoluokka (:kuntoluokka valinnat)
                                  :toteuma (:toteuma valinnat)}
                                 {:onnistui ->HaeVarusteetOnnistui
                                  :epaonnistui ->HaeVarusteetEpaonnistui}))))))

  HaeVarusteetOnnistui
  (process-event [{:keys [vastaus] :as jotain} app]
    (-> app
        (assoc-in [:valinnat :haku-paalla] false)
        (assoc :varusteet (:toteumat vastaus))))

  HaeVarusteetEpaonnistui
  (process-event [{:keys [vastaus] :as jotain-muuta} app]
    ; TODO jos TR-osoite haku epäonnistui, muuta vain puuttuvat kentät punaiseksi
    (viesti/nayta! "Varusteiden haku epäonnistui!" :danger)
    (assoc-in app [:valinnat :haku-paalla] false))

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
    (assoc app :valittu-varuste nil)))

