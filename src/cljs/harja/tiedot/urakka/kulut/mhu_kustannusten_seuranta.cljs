(ns harja.tiedot.urakka.kulut.mhu-kustannusten-seuranta
  "UI controlleri kustannusten seurantaan"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]
            [tuck.core :refer [process-event] :as tuck]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.domain.kulut.kustannusten-seuranta :as kustannusten-seuranta]
            [harja.pvm :as pvm]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.toteumat.maarien-toteumat-kartalla :as maarien-toteumat-kartalla])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def fin-hk-alkupvm "01.10.")
(def fin-hk-loppupvm  "30.09.")
(declare hae-kustannukset)

(defrecord HaeKustannukset [hoitokauden-alkuvuosi aikavali-alkupvm aikavali-loppupvm])
(defrecord KustannustenHakuOnnistui [vastaus])
(defrecord KustannustenHakuEpaonnistui [vastaus])
(defrecord HaeBudjettitavoite [])
(defrecord HaeBudjettitavoiteHakuOnnistui [vastaus])
(defrecord HaeBudjettitavoiteHakuEpaonnistui [vastaus])
(defrecord AvaaRivi [avain])
(defrecord ValitseHoitokausi [urakka vuosi])
(defrecord ValitseKuukausi [urakka kuukausi vuosi])

(defn hae-kustannukset [urakka-id hoitokauden-alkuvuosi aikavali-alkupvm aikavali-loppupvm]
  (let [alkupvm (if (and
                      hoitokauden-alkuvuosi
                      (not aikavali-alkupvm))
                  (str hoitokauden-alkuvuosi "-10-01")
                  (str (pvm/vuosi aikavali-alkupvm) "-" (pvm/kuukausi aikavali-alkupvm) "-" (pvm/paiva aikavali-alkupvm)))
        loppupvm (if (and
                       hoitokauden-alkuvuosi
                       (not aikavali-loppupvm))
                   (str (inc hoitokauden-alkuvuosi) "-09-30")
                   (str (pvm/vuosi aikavali-loppupvm) "-" (pvm/kuukausi aikavali-loppupvm) "-" (pvm/paiva aikavali-loppupvm)))]
    (tuck-apurit/post! :urakan-kustannusten-seuranta-paaryhmittain
                       {:urakka-id urakka-id
                        :hoitokauden-alkuvuosi hoitokauden-alkuvuosi
                        :alkupvm alkupvm
                        :loppupvm loppupvm}
                       {:onnistui ->KustannustenHakuOnnistui
                        :epaonnistui ->KustannustenHakuEpaonnistui
                        :paasta-virhe-lapi? true})))

(extend-protocol tuck/Event

  HaeKustannukset
  (process-event [{hoitokauden-alkuvuosi :hoitokauden-alkuvuosi
                   aikavali-alkupvm :aikavali-alkupvm aikavali-loppupvm :aikavali-loppupvm} app]
    (let [alkupvm (when aikavali-alkupvm
                    (pvm/iso8601 aikavali-alkupvm))
          loppupvm (when aikavali-loppupvm
                     (pvm/iso8601 aikavali-loppupvm))
          urakka-id (-> @tila/yleiset :urakka :id)]
      (hae-kustannukset urakka-id hoitokauden-alkuvuosi alkupvm loppupvm))
    app)

  KustannustenHakuOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [data (kustannusten-seuranta/jarjesta-tehtavat vastaus)]
      (-> app
          (assoc-in [:kustannukset-yhteensa] (:yhteensa data))
          (assoc-in [:kustannukset] (:taulukon-rivit data)))))

  KustannustenHakuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Haku epäonnistui!" :danger)
    app)

  HaeBudjettitavoite
  (process-event [_ app]
    (tuck-apurit/post! :budjettitavoite
                       {:urakka-id (-> @tila/yleiset :urakka :id)}
                       {:onnistui ->HaeBudjettitavoiteHakuOnnistui
                        :epaonnistui ->HaeBudjettitavoiteHakuEpaonnistui
                        :paasta-virhe-lapi? true})
    app)

  HaeBudjettitavoiteHakuOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :budjettitavoite vastaus))

  HaeBudjettitavoiteHakuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Kattohinnan ja tavoitteen haku epäonnistui!" :danger)
    app)

  ;; Monta riviä voi olla avattuna kerrallaan
  AvaaRivi
  (process-event [{avain :avain} app]
    (let [app (if (nil? (:avatut-rivit app))
                (assoc app :avatut-rivit #{})
                app)]
      (if (contains? (:avatut-rivit app) avain)
        (assoc app :avatut-rivit (disj (:avatut-rivit app) avain))
        (assoc app :avatut-rivit (merge (:avatut-rivit app) avain)))))

  ValitseHoitokausi
  (process-event [{urakka :urakka vuosi :vuosi} app]
    (do
      ;; Päivitetään myös Tavoitehinta ja kattohinta kaiken varalta
      (tuck/action!
        (fn [e!]
          (e! (->HaeBudjettitavoite))))
      (hae-kustannukset urakka vuosi nil nil)
      (-> app
          (assoc :valittu-kuukausi nil)
          (assoc :hoitokauden-alkuvuosi vuosi))))

  ValitseKuukausi
  (process-event [{urakka :urakka kuukausi :kuukausi vuosi :vuosi} app]
    (let [valittu-kuukausi (if (= "Kaikki" kuukausi)
                     [(pvm/hoitokauden-alkupvm vuosi)
                      (pvm/hoitokauden-loppupvm (inc vuosi))]
                     kuukausi)]
      (do
        ;; Päivitetään myös Tavoitehinta ja kattohinta kaiken varalta
        (tuck/action!
          (fn [e!]
            (e! (->HaeBudjettitavoite))))
        (hae-kustannukset urakka vuosi (first valittu-kuukausi) (second valittu-kuukausi))
        (-> app
            (assoc-in [:valittu-kuukausi] kuukausi))))))

(defn- muuta-hoitokausivuosi-jarjestysnumeroksi
  "Otetaan urakan loppupäivämäärän vuosi (esim 2025) ja vähennetään siitä saatu vuosi (esim 2021) ja muutetaan
  se järjestysnumeroksi sillä oletuksella, että hoitokausia voi olla maksimissaan viisi (5). Joten laskutoimituksesta tulee
  perin yksinkertainen. Saaduilla arvoilla laskuksi tulee 5 - 4 -> 1. Koska kuluva vuosi on aina ensimmäinen (1) eikä nollas vuosi (0)
   lisätään järjestysnumeroon yksi. Eli Tulos on tässä tilanteessa 2."
  [vuosi]
  (inc (- 5
          (- (pvm/vuosi (-> @tila/yleiset :urakka :loppupvm)) vuosi))))

(defn hoitokauden-jarjestysnumero [valittu-hoitokausivuosi]
  (muuta-hoitokausivuosi-jarjestysnumeroksi valittu-hoitokausivuosi))

(defn kuluva-hoitokausi-nro [paivamaara]
  (let [vuosi (pvm/vuosi paivamaara)
        kuukausi (pvm/kuukausi paivamaara)
        kuluva-hoitokausivuosi (if (< kuukausi 10)
                                 (dec vuosi)
                                 vuosi)]
    (muuta-hoitokausivuosi-jarjestysnumeroksi kuluva-hoitokausivuosi)))

(defn hoitokauden-tavoitehinta [hoitokauden-nro app]
  (let [tavoitehinta (some #(when (= hoitokauden-nro (:hoitokausi %))
                              (:tavoitehinta %))
                           (:budjettitavoite app))]
    tavoitehinta))

(defn hoitokauden-kattohinta [hoitokauden-nro app]
  (let [kattohinta (some #(when (= hoitokauden-nro (:hoitokausi %))
                            (:kattohinta %))
                         (:budjettitavoite app))]
    kattohinta))