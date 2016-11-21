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


(extend-protocol t/Event
  AsetaVarusteidenHakuehdot
  (process-event [{ehdot :hakuehdot} app]
    (assoc-in app [:hakuehdot] ehdot))

  HaeVarusteita
  (process-event [_ {hakuehdot :hakuehdot :as app}]
    (let [tulos! (t/send-async! map->VarusteHakuTulos)
          virhe! (t/send-async! ->VarusteHakuEpaonnistui)]
      (log "HAETAAN EHDOILLA: " (pr-str hakuehdot))
      (go
        (let [vastaus (<! (k/post! :hae-varusteita hakuehdot))]
          (log "VASTAUS: " (pr-str vastaus))
          (if (or (k/virhe? vastaus)
                  (not (:onnistunut vastaus)))
            (virhe! vastaus)
            (tulos! vastaus))))
      (-> app
          (assoc-in [:varusteet] nil)
          (assoc-in [:hakuehdot :haku-kaynnissa?] false))))

  VarusteHakuTulos
  (process-event [{tietolaji :tietolaji varusteet :varusteet} app]
    (log "VarusteHakuTulos: " (pr-str varusteet) ", app: " (pr-str app))

    (-> app
        (assoc-in [:varusteet] varusteet)
        (assoc-in [:tietolaji] tietolaji)
        (assoc-in [:listaus-skeema]
                  (mapv varusteet/varusteominaisuus->skeema
                        (filter varusteet/kiinnostaa-listauksessa?
                                (:ominaisuudet tietolaji))))
        (assoc-in [:hakuehdot :haku-kaynnissa?] false)))

  VarusteHakuEpaonnistui
  (process-event [{virhe :virhe} app]
    (log "VIRHE HAETTAESSA: " (pr-str virhe))
    app))
