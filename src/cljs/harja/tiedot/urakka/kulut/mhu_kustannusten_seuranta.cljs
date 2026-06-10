(ns harja.tiedot.urakka.kulut.mhu-kustannusten-seuranta
  "UI controlleri kustannusten seurantaan"
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
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.tiedot.urakka.kulut.yhteiset :as t-yhteiset]
            [harja.tiedot.urakka.toteumat.maarien-toteumat-kartalla :as maarien-toteumat-kartalla])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def fin-hk-alkupvm "01.10.")
(def fin-hk-loppupvm "30.09.")
(declare hae-kustannukset)

(defrecord HaeKustannukset [hoitokauden-alkuvuosi aikavali-alkupvm aikavali-loppupvm])
(defrecord KustannustenHakuOnnistui [vastaus])
(defrecord KustannustenHakuEpaonnistui [vastaus])
(defrecord HaeBudjettitavoite [])
(defrecord HaeBudjettitavoiteHakuOnnistui [vastaus])
(defrecord HaeBudjettitavoiteHakuEpaonnistui [vastaus])
(defrecord HaeTavoitehintojenOikaisut [urakka])
(defrecord HaeTavoitehintojenOikaisutOnnistui [vastaus])
(defrecord HaeTavoitehintojenOikaisutEpaonnistui [vastaus])
(defrecord HaeKattohintojenOikaisut [urakka])
(defrecord HaeKattohintojenOikaisutOnnistui [vastaus])
(defrecord HaeKattohintojenOikaisutEpaonnistui [vastaus])
(defrecord AvaaRivi [avain])
(defrecord ValitseHoitokausi [urakka vuosi])
(defrecord ValitseKuukausi [urakka kuukausi vuosi])
(defrecord AvaaValikatselmusLomake [])
(defrecord SuljeValikatselmusLomake [])
(defrecord HaeOnkoPaatoksiaTekematta [urakkaid kuluva-hoitovuosi])
(defrecord HaeOnkoPaatoksiaTekemattaOnnistui [vastaus])
(defrecord HaeOnkoPaatoksiaTekemattaEpaonnistui [vastaus])

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
    (do
      (hae-kustannukset (-> @tila/yleiset :urakka :id) hoitokauden-alkuvuosi aikavali-alkupvm aikavali-loppupvm)
      (assoc app :haku-kaynnissa? true)))

  KustannustenHakuOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [sopimustyyppi (-> @tila/yleiset :urakka :sopimustyyppi)
          data (kustannusten-seuranta/jarjesta-tehtavat vastaus sopimustyyppi)]
      (-> app
        (assoc-in [:kustannukset-yhteensa] (:yhteensa data))
        (assoc-in [:kustannukset] (:taulukon-rivit data))
        (assoc :haku-kaynnissa? false))))

  KustannustenHakuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Haku epäonnistui!" :danger)
    (assoc app :haku-kaynnissa? false))

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
    (assoc app
      :budjettitavoite vastaus
      :valittu-urakka (-> @tila/yleiset :urakka :id)))

  HaeBudjettitavoiteHakuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Kattohinnan ja tavoitteen haku epäonnistui!" :danger)
    app)

  HaeOnkoPaatoksiaTekematta
  (process-event [{urakkaid :urakkaid kuluva-hoitovuosi :kuluva-hoitovuosi} app]
    (tuck-apurit/post! :onko-paatoksia-tekematta
      {:urakkaid urakkaid
       :kuluva-hoitovuosi kuluva-hoitovuosi}
      {:onnistui ->HaeOnkoPaatoksiaTekemattaOnnistui
       :epaonnistui ->HaeOnkoPaatoksiaTekemattaEpaonnistui
       :paasta-virhe-lapi? true})
    app)

  HaeOnkoPaatoksiaTekemattaOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :onko-paatoksia-tekematta vastaus))

  HaeOnkoPaatoksiaTekemattaEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Päätöksien haku epäonnistui!" :danger)
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
        ;; Lupaukset on kiinteässä linkissä kustannusten seurannan kanssa joten tarvitaan hoitokaudellekin sama avain
        (assoc :valittu-hoitokausi [(pvm/hoitokauden-alkupvm vuosi)
                                    (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))])
        (assoc :haku-kaynnissa? true)
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
          (assoc :haku-kaynnissa? true)
          (assoc-in [:valittu-kuukausi] kuukausi)))))

  AvaaValikatselmusLomake
  (process-event [_ app]
    (assoc app :valikatselmus-auki? true))

  SuljeValikatselmusLomake
  (process-event [_ app]
    (assoc app :valikatselmus-auki? false)))
