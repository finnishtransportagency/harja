(ns harja.tiedot.tierekisteri.varusteet
  "Tierekisterin varusteisiin ja niiden hakuun liittyvät tiedot"
  (:require [tuck.core :as t]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log]]
            [harja.domain.tierekisteri.varusteet :as varusteet]
            [harja.tiedot.urakka.toteumat.varusteet :as varuste-tiedot]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]
            [clojure.walk :as walk]
            [harja.ui.viesti :as viesti])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn varustetoteuma [{:keys [tietue]} toiminto]
  (let [tr-osoite (get-in tietue [:sijainti :tie])]
    {:arvot (walk/keywordize-keys (get-in tietue [:tietolaji :arvot]))
     :puoli (:puoli tr-osoite)
     :ajorata (:ajr tr-osoite)
     :tierekisteriosoite {:numero (:numero tr-osoite)
                          :alkuosa (:aosa tr-osoite)
                          :alkuetaisyys (:aet tr-osoite)
                          :loppuosa (:losa tr-osoite)
                          :loppuetaisyys (:let tr-osoite)}
     :tietolaji (get-in tietue [:tietolaji :tunniste])
     :toiminto toiminto
     :urakka-id @nav/valittu-urakka-id
     :alkupvm (:alkupvm tietue)
     :loppupvm (:loppupvm tietue)}))

(defn hakutulokset [app tietolaji varusteet]
  (-> app
      (assoc-in [:varusteet] varusteet)
      (assoc-in [:tietolaji] tietolaji)
      (assoc-in [:listaus-skeema]
                (into [varusteet/varusteen-osoite-skeema]
                      (comp (filter varusteet/kiinnostaa-listauksessa?)
                            (map varusteet/varusteominaisuus->skeema))
                      (:ominaisuudet tietolaji)))
      (assoc-in [:hakuehdot :haku-kaynnissa?] false)))

;; Määritellään varustehaun UI tapahtumat

;; Päivittää varustehaun hakuehdot
(defrecord AsetaVarusteidenHakuehdot [hakuehdot])
;; Suorittaa haun
(defrecord HaeVarusteita [])
(defrecord VarusteHakuTulos [tietolaji varusteet])
(defrecord VarusteHakuEpaonnistui [virhe])

;; Toimenpiteet Tierekisteriin
(defrecord PoistaVaruste [varuste])
(defrecord MuokkausTierekisteriinOnnistui [toiminto viesti])
(defrecord MuokkausTierekisteriinEpaonnistui [toiminto virhe])



(extend-protocol t/Event
  AsetaVarusteidenHakuehdot
  (process-event [{ehdot :hakuehdot} app]
    (assoc-in app [:hakuehdot] ehdot))

  HaeVarusteita
  (process-event [_ {hakuehdot :hakuehdot :as app}]
    (let [tulos! (t/send-async! map->VarusteHakuTulos)
          virhe! (t/send-async! ->VarusteHakuEpaonnistui)]
      (go
        (let [vastaus (<! (k/post! :hae-varusteita hakuehdot))]
          (log "VASTAUS: " (pr-str vastaus))
          (if (or (k/virhe? vastaus)
                  (not (:onnistunut vastaus)))
            (virhe! vastaus)
            (tulos! vastaus))))
      (-> app
          (assoc-in [:varusteet] nil)
          (assoc-in [:hakuehdot :haku-kaynnissa?] true))))

  VarusteHakuTulos
  (process-event [{tietolaji :tietolaji varusteet :varusteet} app]
    (hakutulokset app tietolaji varusteet))

  VarusteHakuEpaonnistui
  (process-event [{virhe :virhe} app]
    (log "[TR] Virhe haettaessa varusteita: " (pr-str virhe))
    (viesti/nayta! "Virhe haettaessa varusteita Tierekisteristä" :error)
    app)

  MuokkausTierekisteriinOnnistui
  (process-event [{viesti :viesti tietolaji :tietolaji varusteet :varusteet} app]
    (viesti/nayta! viesti :success)
    (hakutulokset app tietolaji varusteet))

  MuokkausTierekisteriinEpaonnistui
  (process-event [{virhe :virhe viesti :viesti vastaus :vastaus} app]
    (log "[TR] Virhe suoritettaessa toimintoa. Virhe:" (pr-str virhe) ". Vastaus: " (pr-str vastaus) ".")
    (viesti/nayta! viesti :error)
    (hakutulokset app tietolaji varusteet))

  PoistaVaruste
  (process-event [{varuste :varuste} app]
    (let [tulos! (t/send-async! map->MuokkausTierekisteriinOnnistui)
          virhe! (t/send-async! ->MuokkausTierekisteriinEpaonnistui)]
      (go
        (let [varustetoteuma (varustetoteuma varuste :poistettu)
              vastaus (<! (varuste-tiedot/tallenna-varustetoteuma
                            {:urakka-id (:id @nav/valittu-urakka)
                             :sopimus-id (first @urakka/valittu-sopimusnumero)
                             :aikavali @urakka/valittu-aikavali}
                            varustetoteuma))]
          (if (or (k/virhe? vastaus)
                  (not (:onnistunut vastaus)))
            (virhe! {:vastaus vastaus :viesti "Varusteen poistossa tapahtui virhe."})
            (tulos! {:viesti "Varuste poistettu onnistuneesti."})))))
    app))
