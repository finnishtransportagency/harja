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

(defn varustetoteuma [{:keys [tietue]} toiminto kuntoluokitus lisatieto]
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
     :loppupvm (:loppupvm tietue)
     :kuntoluokitus kuntoluokitus
     :lisatieto lisatieto}))

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
(defrecord AloitaVarusteenTarkastus [varuste tunniste tietolaji])
(defrecord PeruutaVarusteenTarkastus [])
(defrecord AsetaVarusteTarkastuksenTiedot [tiedot])
(defrecord TallennaVarustetarkastus [varuste tarkastus])
(defrecord ToimintoEpaonnistui [toiminto virhe])
(defrecord ToimintoOnnistui [vastaus])

(defrecord VarusteToteumatMuuttuneet [varustetoteumat])
(defrecord MuokkaaVarustetta [varuste])

(defn laheta-viivastyneesti [async-fn]
  ;; hackish ratkaisu, jolla varmistetaan, että tämän funktion käsittely päättyy ennen kuin send-async menee läpi.
  (.setTimeout js/window (async-fn) 1))

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
          (log "[TR] Varustehaun vastaus: " (pr-str vastaus))
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
    (viesti/nayta! "Virhe haettaessa varusteita Tierekisteristä" :warning)
    (assoc-in app [:hakuehdot :haku-kaynnissa?] false))

  ToimintoEpaonnistui
  (process-event [{{:keys [viesti vastaus]} :toiminto virhe :virhe :as tiedot} app]
    (log "[TR] Virhe suoritettaessa toimintoa. Virhe:" (pr-str virhe) ". Vastaus: " (pr-str vastaus) ".")
    (viesti/nayta! viesti :warning)
    (laheta-viivastyneesti #(t/send-async! (partial ->VarusteToteumatMuuttuneet vastaus)))
    ;; todo: mieti miten tehdä haku tierekisteriin uudestaan
    (hakutulokset app nil nil))

  ToimintoOnnistui
  (process-event [{{:keys [vastaus]} :toiminto :as tiedot} app]
    (laheta-viivastyneesti #(t/send-async! (partial ->VarusteToteumatMuuttuneet vastaus)))
    ;; todo: mieti miten tehdä haku tierekisteriin uudestaan
    (hakutulokset app nil nil))

  PoistaVaruste
  (process-event [{varuste :varuste} app]
    (let [tulos! (t/send-async! map->ToimintoOnnistui)
          virhe! (t/send-async! ->ToimintoEpaonnistui)]
      (go
        (let [varustetoteuma (varustetoteuma varuste :poistettu nil nil)
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
  (process-event [{varuste :varuste {lisatieto :lisatietoja kuntoluokitus :kuntoluokitus} :tarkastus :as data} app]
    (let [tulos! (t/send-async! map->ToimintoOnnistui)
          virhe! (t/send-async! ->ToimintoEpaonnistui)]
      (go
        (let [varuste (assoc-in varuste [:tietue :tietolaji :arvot "kuntoluokitus"] kuntoluokitus)
              varustetoteuma (varustetoteuma varuste :tarkastus kuntoluokitus lisatieto)
              hakuehdot {:urakka-id (:id @nav/valittu-urakka)
                         :sopimus-id (first @urakka/valittu-sopimusnumero)
                         :aikavali @urakka/valittu-aikavali}
              vastaus (<! (varuste-tiedot/tallenna-varustetoteuma hakuehdot varustetoteuma))]
          (if (or (k/virhe? vastaus) (not (:onnistunut vastaus)))
            (virhe! {:vastaus vastaus :viesti "Varusteen tarkastuksen kirjauksessa tapahtui virhe."})
            (tulos! {:vastaus vastaus :viesti "Varustetarkastus kirjattu onnistuneesti."})))))
    (dissoc app :tarkastus))

  AsetaVarusteTarkastuksenTiedot
  (process-event [{uudet-tiedot :tiedot} {tarkastus :tarkastus :as app}]
    (assoc app :tarkastus (assoc tarkastus :tiedot uudet-tiedot)))


  ;; Hook-upit päänäkymään (harja.tiedot.urakka.toteumat.varusteet)

  VarusteToteumatMuuttuneet
  (process-event [_ app]
    app)

  MuokkaaVarustetta
  (process-event [_ app]
    app))
