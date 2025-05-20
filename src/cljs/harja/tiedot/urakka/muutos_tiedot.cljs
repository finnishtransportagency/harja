(ns harja.tiedot.urakka.muutos-tiedot
  "Urakan muutosten tiedot."
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.viesti :as viesti]
            [harja.domain.muutos-domain :as muutos-domain])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

;; Hae muutostiedot
(defrecord HaeUrakanMuutostiedot [urakka])
(defrecord HaeUrakanMuutostiedotOnnnistui [vastaus])
(defrecord HaeUrakanMuutostiedotEpaonnistui [vastaus])

(defrecord MuokkaaMuutosta [rivi])
;; Vaihda hoitokausi
(defrecord HoitokausiVaihdettu [urakka hoitokausi])


;; Päänäkymä ja listaus
(defrecord ValitseUrakka [urakka])
(defrecord NakymastaPoistuttiin [])


(defn valitse-urakka [app urakka]
  (let [hoitokaudet (u/hoito-tai-sopimuskaudet urakka)
        vanha-hoitokausi (:valittu-hoitokausi app)
        uusi-hoitokausi (if (contains? (set hoitokaudet) vanha-hoitokausi)
                          vanha-hoitokausi
                          (u/paattele-valittu-hoitokausi hoitokaudet))]
    ;; Tyhjennä muu app state kun urakka vaihtuu
    (-> {}
        (assoc :urakan-hoitokaudet hoitokaudet)
        (assoc :valittu-hoitokausi uusi-hoitokausi))))

(defn hae-urakan-muutostiedot
  "Vuonna 2021 alkaville urakoille haetaan muutostiedot. Sitä vanhemmille ei haeta."
  ([app] (hae-urakan-muutostiedot app (:urakka @tila/yleiset)))
  ([app urakka]
   (tuck-apurit/post! :hae-urakan-muutostiedot
     {:urakka-id (:id urakka)
      :valittu-hoitokausi (:valittu-hoitokausi app)}
     {:onnistui ->HaeUrakanMuutostiedotOnnnistui
      :epaonnistui ->HaeUrakanMuutostiedotEpaonnistui})))


(extend-protocol tuck/Event
  HoitokausiVaihdettu
  (process-event [{urakka :urakka hoitokausi :hoitokausi} app]
    (let [app (-> app
                  (assoc :valittu-hoitokausi hoitokausi))]
      (hae-urakan-muutostiedot app urakka)
      app))

  HaeUrakanMuutostiedot
  (process-event [{urakka :urakka} app]
    (let [;; Lupauksia voidaan hakea myös välikatselmuksesta, niin tarkistetaan hoitokauden tila sitä ennen
          app (if (:valittu-hoitokausi app)
                app
                (assoc app :valittu-hoitokausi [(pvm/hoitokauden-alkupvm (:hoitokauden-alkuvuosi app))
                                                (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc (:hoitokauden-alkuvuosi app))))]))]
      (do
        (hae-urakan-muutostiedot app urakka)
        app)))

  HaeUrakanMuutostiedotOnnnistui
  (process-event [{vastaus :vastaus} app]
    (prn "Jarno HaeUrakanMuutostiedotOnnnistui vastaus: " vastaus)
    (assoc app :muutokset vastaus))
  
  HaeUrakanMuutostiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Muutostietojen hakeminen epäonnistui!" :varoitus)
    app)

  MuokkaaMuutosta
  (process-event [{rivi :rivi} app]
    (assoc app :muokattava-muutos rivi))

  ValitseUrakka
  (process-event [{urakka :urakka} app]
    (valitse-urakka app urakka))

  NakymastaPoistuttiin
  (process-event [_ app]
    app))
