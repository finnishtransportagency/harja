(ns harja.tiedot.urakka.lupaukset
  "Urakan lupausten tiedot."
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [cljs.core.async :refer [<! >! chan]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [harja.loki :refer [log tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.viesti :as viesti])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defrecord HaeUrakanLupaustiedot [urakka])
(defrecord HaeUrakanLupaustiedotOnnnistui [vastaus])
(defrecord HaeUrakanLupaustiedotEpaonnistui [vastaus])
(defrecord HaeLupauksenVastausvaihtoehdot [vastaus])
(defrecord HaeLupauksenVastausvaihtoehdotOnnistui [vastaus])
(defrecord HaeLupauksenVastausvaihtoehdotEpaonnistui [vastaus])


(defrecord HoitokausiVaihdettu [urakka hoitokausi])

(defrecord VaihdaLuvattujenPisteidenMuokkausTila [])
(defrecord LuvattujaPisteitaMuokattu [pisteet])
(defrecord TallennaLupausSitoutuminen [urakka])
(defrecord TallennaLupausSitoutuminenOnnnistui [vastaus])
(defrecord TallennaLupausSitoutuminenEpaonnistui [vastaus])

(defrecord AvaaLupausvastaus [vastaus])
(defrecord SuljeLupausvastaus [vastaus])

(defrecord AlustaNakyma [urakka])
(defrecord NakymastaPoistuttiin [])

(defn- lupausten-hakuparametrit [urakka hoitokausi]
  {:urakka-id (:id urakka)
   :urakan-alkuvuosi (pvm/vuosi (:alkupvm urakka))
   :valittu-hoitokausi hoitokausi})

(extend-protocol tuck/Event

  HoitokausiVaihdettu
  (process-event [{urakka :urakka hoitokausi :hoitokausi} app]
    (-> app
        (assoc :valittu-hoitokausi hoitokausi)
        (tuck-apurit/post! :hae-urakan-lupaustiedot
                           (lupausten-hakuparametrit urakka hoitokausi)
                           {:onnistui ->HaeUrakanLupaustiedotOnnnistui
                            :epaonnistui ->HaeUrakanLupaustiedotEpaonnistui})))

  HaeUrakanLupaustiedot
  (process-event [{urakka :urakka} app]
    (-> app
        (tuck-apurit/post! :hae-urakan-lupaustiedot
                           (lupausten-hakuparametrit urakka (:valittu-hoitokausi app))
                           {:onnistui ->HaeUrakanLupaustiedotOnnnistui
                            :epaonnistui ->HaeUrakanLupaustiedotEpaonnistui})))

  HaeUrakanLupaustiedotOnnnistui
  (process-event [{vastaus :vastaus} app]
    (println "HaeUrakanLupaustiedotOnnnistui ")
    (merge app vastaus))

  HaeUrakanLupaustiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Lupaustietojen hakeminen epäonnistui!" :varoitus)
    app)

  HaeLupauksenVastausvaihtoehdot
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.log "HaeLupauksenVastausvaihtoehdot :: vastaus" (pr-str vastaus))
      (tuck-apurit/post! :lupauksen-vastausvaihtoehdot
                         {:lupaus-id (:lupaus-id vastaus)}
                         {:onnistui ->HaeLupauksenVastausvaihtoehdotOnnistui
                          :epaonnistui ->HaeLupauksenVastausvaihtoehdotEpaonnistui})
      app))

  HaeLupauksenVastausvaihtoehdotOnnistui
  (process-event [{vastaus :vastaus} app]
    (println "Vastausvaihtoehtojen haku onnistui :: vastaus " (pr-str vastaus))
    (assoc app :lomake-lupauksen-vaihtoehdot vastaus))

  HaeLupauksenVastausvaihtoehdotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Vastausvaihtoehtojen hakeminen epäonnistui!" :varoitus)
    (dissoc app :lomake-lupauksen-vaihtoehdot))

  VaihdaLuvattujenPisteidenMuokkausTila
  (process-event [_ app]
    (let [arvo-nyt (:muokkaa-luvattuja-pisteita? app)]
     (assoc app :muokkaa-luvattuja-pisteita? (not arvo-nyt))))

  LuvattujaPisteitaMuokattu
  (process-event [{pisteet :pisteet} app]
    (assoc-in app [:lupaus-sitoutuminen :pisteet] pisteet))

  TallennaLupausSitoutuminen
  (process-event [{urakka :urakka} app]
    (let [parametrit (merge (lupausten-hakuparametrit urakka (:valittu-hoitokausi app))
                            {:id (get-in app [:lupaus-sitoutuminen :id])
                             :pisteet (get-in app [:lupaus-sitoutuminen :pisteet])})]
      (-> app
         (tuck-apurit/post! :tallenna-luvatut-pisteet
                            parametrit
                            {:onnistui ->TallennaLupausSitoutuminenOnnnistui
                             :epaonnistui ->TallennaLupausSitoutuminenEpaonnistui}))))

  TallennaLupausSitoutuminenOnnnistui
  (process-event [{vastaus :vastaus} app]
    (println "TallennaLupausSitoutuminenOnnnistui " vastaus)
    (-> app
        (merge vastaus)
        (assoc :muokkaa-luvattuja-pisteita? false)))

  TallennaLupausSitoutuminenEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast!
      "TallennaLupausSitoutuminenOnnnistui tallennus epäonnistui"
      :varoitus
      viesti/viestin-nayttoaika-aareton)
    app)

  AvaaLupausvastaus
  (process-event [{vastaus :vastaus} app]
    ;; Avataansivupaneeli
    (do
      (js/console.log "Avataan sivupaneeli :: vastaus" (pr-str vastaus))
      (tuck-apurit/post! :lupauksen-vastausvaihtoehdot
                         {:lupaus-id (:lupaus-id vastaus)}
                         {:onnistui ->HaeLupauksenVastausvaihtoehdotOnnistui
                          :epaonnistui ->HaeLupauksenVastausvaihtoehdotEpaonnistui})
      (assoc app :vastaus-lomake vastaus)))

  SuljeLupausvastaus
  (process-event [_ app]
    ;; Suljetaan sivupaneeli
    (do
      (js/console.log "Suljetaan sivupaneeli")
      (dissoc app :vastaus-lomake)))

  AlustaNakyma
  (process-event [{urakka :urakka} app]
    (let [hoitokaudet (u/hoito-tai-sopimuskaudet urakka)]
      (assoc app :urakan-hoitokaudet hoitokaudet
                 :valittu-hoitokausi (u/paattele-valittu-hoitokausi hoitokaudet))))

  NakymastaPoistuttiin
  (process-event [_ app]
    (println "NakymastaPoistuttiin ")
    app))