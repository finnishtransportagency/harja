(ns harja.tiedot.tierekisteri.varusteet
  "Tierekisterin varusteisiin ja niiden hakuun liittyvät tiedot"
  (:require [tuck.core :as t]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log]]
            [harja.domain.tierekisteri.varusteet :as varusteet])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; Määritellään varustehaun UI tapahtumat
;;

;; Päivittää varustehaun hakuehdot
(defrecord AsetaVarusteidenHakuehdot [hakuehdot])
;; Suorittaa haun
(defrecord HaeVarusteita [])
(defrecord VarusteHakuTulos [tietolaji varusteet])
(defrecord VarusteHakuEpaonnistui [virhe])
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
    (-> app
        (assoc-in [:varusteet] varusteet)
        (assoc-in [:tietolaji] tietolaji)
        (assoc-in [:listaus-skeema]
                  (into [varusteet/varusteen-osoite-skeema]
                        (comp (filter varusteet/kiinnostaa-listauksessa?)
                              (map varusteet/varusteominaisuus->skeema))
                        (:ominaisuudet tietolaji)))
        (assoc-in [:hakuehdot :haku-kaynnissa?] false)))

  VarusteHakuEpaonnistui
  (process-event [{virhe :virhe} app]
    (log "VIRHE HAETTAESSA: " (pr-str virhe))
    app)

  MuokkausTierekisteriinOnnistui
  (process-event [{toiminto :toiminto viesti :viesti} app]
    (log "---> toiminto: " (pr-str toiminto) ", viesti: " (pr-str viesti))
    ;; todo: nayta virhe
    app)

  MuokkausTierekisteriinEpaonnistui
  (process-event [{toiminto :toiminto virhe :virhe vastaus :vastaus} app]
    (log "---> toiminto: " (pr-str toiminto) ", virhe: " (pr-str virhe) ", vastaus: " (pr-str vastaus))
    ;; todo: nayta viesti
    ;; todo: laukaise haku uudestaan samoilla parametreillä
    app)

  PoistaVaruste
  (process-event [{varuste :varuste} app]
    (let [tulos! (t/send-async! map->MuokkausTierekisteriinOnnistui)
          virhe! (t/send-async! ->MuokkausTierekisteriinEpaonnistui)]
      (go
        (log "---> varuste: " (pr-str varuste))
        (let [vastaus (<! (k/post! :poista-varuste varuste))]
          (log "VASTAUS: " (pr-str vastaus))
          (if (or (k/virhe? vastaus)
                  (not (:onnistunut vastaus)))
            (virhe! vastaus)
            (tulos! vastaus)))))
    ;; todo: assoccaa appiin palautetut varusteet ja varustetoteumat näytettäväksi
    app))
