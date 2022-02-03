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

(defrecord ValitseHoitokausi [urakka-id hoitokauden-alkuvuosi])
(defrecord ValitseHoitokaudenKuukausi [urakka-id hoitokauden-kuukausi])
(defrecord ValitseKuntoluokka [urakka-id kuntoluokka])
(defrecord ValitseToteuma [urakka-id toteuma])
(defrecord HaeVarusteet [])
(defrecord HaeVarusteetOnnistui [vastaus])
(defrecord HaeVarusteetEpaonnistui [vastaus])
(defrecord AvaaVarusteLomake [varuste])
(defrecord SuljeVarusteLomake [])

(def fin-hk-alkupvm "01.10.")
(def fin-hk-loppupvm "30.09.")


(extend-protocol tuck/Event

  ValitseHoitokausi
  (process-event [{urakka-id :urakka-id hoitokauden-alkuvuosi :hoitokauden-alkuvuosi} app]
    (-> app
        (assoc-in [:valinnat :hoitokauden-alkuvuosi] hoitokauden-alkuvuosi)
        (assoc-in [:valinnat :hoitokauden-kuukausi] (hoitokausi-rajat hoitokauden-alkuvuosi))))

  ValitseHoitokaudenKuukausi
  (process-event [{urakka-id :urakka-id hoitokauden-kuukausi :hoitokauden-kuukausi} app]
    (do
      (assoc-in app [:valinnat :hoitokauden-kuukausi] hoitokauden-kuukausi)))

  ValitseKuntoluokka
  (process-event [{urakka-id :urakka-id kuntoluokka :kuntoluokka} app]
    (do
      (assoc-in app [:valinnat :kuntoluokka] kuntoluokka)))

  ValitseToteuma
  (process-event [{urakka-id :urakka-id toteuma :toteuma} app]
    (do
      (assoc-in app [:valinnat :toteuma] toteuma)))

  HaeVarusteet
  (process-event [_ app]
    (-> app
        (tuck-apurit/post! :hae-urakan-varustetoteuma-ulkoiset
                           {:urakka-id (get-in app [:urakka :id])
                            :kuntoluokka (get-in app [:valinnat :kuntoluokka])
                            :toteuma (get-in app [:valinnat :toteuma])}
                           {:onnistui ->HaeVarusteetOnnistui
                            :epaonnistui ->HaeVarusteetEpaonnistui})))

  HaeVarusteetOnnistui
  (process-event [{:keys [vastaus] :as jotain} app]
    (println "petrisi1225: jotain onnistui: " jotain)
    (assoc app :varusteet (:toteumat vastaus)))

  HaeVarusteetEpaonnistui
  (process-event [{:keys [vastaus] :as jotain-muuta} app]
    (println "petrisi1226: jotain-muuta epäonnistui: " jotain-muuta)
    (viesti/nayta! "Varusteiden haku epäonnistui!" :danger)
    app)

  AvaaVarusteLomake
  (process-event [{:keys [varuste]} app]
    (assoc app :valittu-varuste varuste))

  SuljeVarusteLomake
  (process-event [_ app]
    (assoc app :valittu-varuste nil)))

