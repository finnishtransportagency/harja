(ns harja.tiedot.urakka.kulut.mhu-kustannusten-seuranta
  "UI controlleri kustannusten seurantaan"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [tuck.core :refer [process-event] :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.domain.kulut.kustannusten-seuranta :as kustannusten-seuranta]
            [harja.pvm :as pvm]
            [harja.ui.lomake :as ui-lomake]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.toteumat.maarien-toteumat-kartalla :as maarien-toteumat-kartalla])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(declare hae-kustannukset)

(defrecord HaeKustannukset [hoitokauden-alkuvuosi aikavali-alkupvm aikavali-loppupvm])
(defrecord KustannustenHakuOnnistui [vastaus])
(defrecord KustannustenHakuEpaonnistui [vastaus])
(defrecord HaeBudjettitavoite [])
(defrecord HaeBudjettitavoiteHakuOnnistui [vastaus])
(defrecord HaeBudjettitavoiteHakuEpaonnistui [vastaus])
(defrecord AvaaRivi [tyyppi avain])
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

  ;; Vain yksi rivi voi olla avattuna kerralla, joten tallennetaan avain app-stateen tai poistetaan se, jos se oli jo valittuna
  AvaaRivi
  (process-event [{tyyppi :tyyppi avain :avain} app]
    (if (= avain (get-in app [:valittu-rivi tyyppi]))
      (assoc-in app [:valittu-rivi tyyppi] nil)
      (assoc-in app [:valittu-rivi tyyppi] avain)))

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
    (do
      ;; Päivitetään myös Tavoitehinta ja kattohinta kaiken varalta
      (tuck/action!
        (fn [e!]
          (e! (->HaeBudjettitavoite))))
      (hae-kustannukset urakka vuosi (first kuukausi) (second kuukausi))
      (-> app
          (assoc-in [:valittu-kuukausi] kuukausi)))))

(defn hoitokauden-jarjestysnumero [valittu-hoitokausivuosi]
  (let [urakka-loppupvm (-> @tila/yleiset :urakka :loppupvm)
        hoitokauden-nro (- 6 (- (pvm/vuosi urakka-loppupvm) valittu-hoitokausivuosi))]
    hoitokauden-nro))

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