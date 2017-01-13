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
(defrecord AsetaVarusteTarkastuksenTiedot [tarkastus])
;; Suorittaa haun
(defrecord HaeVarusteita [])
(defrecord VarusteHakuTulos [tietolaji varusteet])
(defrecord VarusteHakuEpaonnistui [virhe])

;; Toimenpiteet Tierekisteriin
(defrecord PoistaVaruste [varuste])
(defrecord AloitaVarusteenTarkastus [varuste tunniste tietolaji])
(defrecord PeruutaVarusteenTarkastus [])
(defrecord AsetaVarusteTarkastuksenTiedot [tarkastus])
(defrecord TallennaVarustetarkastus [varuste tarkastus])
(defrecord ToimintoEpaonnistui [toiminto virhe])
(defrecord ToimintoOnnistui [vastaus])

(defrecord VarusteToteumatMuuttuneet [varustetoteumat])

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

  ToimintoEpaonnistui
  (process-event [{{:keys [viesti vastaus]} :toiminto virhe :virhe :as tiedot} app]
    (log "[TR] Virhe suoritettaessa toimintoa. Virhe:" (pr-str virhe) ". Vastaus: " (pr-str vastaus) ".")
    (viesti/nayta! viesti :warning)
    ((t/send-async! (partial ->VarusteToteumatMuuttuneet vastaus)))

    ;; todo: mieti miten tehdä haku tierekisteriin uudestaan
    (hakutulokset app nil nil))

  ToimintoOnnistui
  (process-event [{{:keys [vastaus]} :toiminto :as tiedot} app]
    ((t/send-async! (partial ->VarusteToteumatMuuttuneet vastaus)))
    ;; todo: mieti miten tehdä haku tierekisteriin uudestaan
    (hakutulokset app nil nil))

  ;; Hook-up harja.tiedot.urakka.toteumat.varusteet -namespaceen, jossa varsinainen käsittely
  VarusteToteumatMuuttuneet
  (process-event [_ app]
    app)

  PoistaVaruste
  (process-event [{varuste :varuste} app]
    (let [tulos! (t/send-async! map->ToimintoOnnistui)
          virhe! (t/send-async! ->ToimintoEpaonnistui)]
      (go
        (let [varustetoteuma (varustetoteuma varuste :poistettu)
              hakuehdot {:urakka-id (:id @nav/valittu-urakka)
                         :sopimus-id (first @urakka/valittu-sopimusnumero)
                         :aikavali @urakka/valittu-aikavali}
              vastaus (<! (varuste-tiedot/tallenna-varustetoteuma hakuehdot varustetoteuma))]
          (if (or (k/virhe? vastaus) (not (:onnistunut vastaus)))
            (virhe! {:vastaus vastaus :viesti "Varusteen poistossa tapahtui virhe."})
            (tulos! {:vastaus vastaus :viesti "Varuste poistettu onnistuneesti."})))))
    app)

  AloitaVarusteenTarkastus
  (process-event [{varuste :varuste tunniste :tunniste tietolaji :tietolaji :as data} app]
    (assoc app :tarkastus {:varuste varuste :tunniste tunniste :tietolaji tietolaji}))

  PeruutaVarusteenTarkastus
  (process-event [_ app]
    (dissoc app :tarkastus))

  TallennaVarustetarkastus
  (process-event [{varuste :varuste tarkastus :tarkastus :as data} app]
    (log "---> varuste:" (pr-str varuste))
    (log "---> tarkastus:" (pr-str tarkastus))
    ;; todo: rakenna varustetoteuma ja lähetä backendille
    (dissoc app :tarkastus))

  AsetaVarusteTarkastuksenTiedot
  (process-event [{tarkastus :tarkastus} app]
    (log "--> tarkastus 3:" (pr-str tarkastus))
    (assoc app :tarkastus tarkastus)))
