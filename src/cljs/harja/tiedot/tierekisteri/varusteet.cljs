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
            [harja.ui.viesti :as viesti]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn varustetoteuma
  ([tiedot toiminto]
   (varustetoteuma tiedot toiminto nil nil nil))
  ([tiedot toiminto kuntoluokitus lisatieto]
   (varustetoteuma tiedot toiminto kuntoluokitus lisatieto nil))
  ([{:keys [tietue tunniste]} toiminto kuntoluokitus lisatieto uusi-liite]
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
      :lisatieto lisatieto
      :uusi-liite uusi-liite
      :tunniste tunniste})))

(defonce varusteen-tietiedot (comp :tie :sijainti :tietue :varuste))

(defn hakutulokset
  ([app] (hakutulokset app nil nil))
  ([app tietolajit varusteet]
   (let [perustieto-skeema varusteet/varusteen-perustiedot-skeema
         tietolaji (first tietolajit)
         skeema (if (= (get-in tietolaji [:tietolaji :tunniste]) "tl506")
                  (conj perustieto-skeema (varusteet/varusteen-liikennemerkki-skeema tietolaji))
                  perustieto-skeema)
         palauta-varusteen-tietieto #((comp %1 varusteen-tietiedot) %2)
         varusteet-jarjestetty (sort-by (fn [varuste]
                                          (mapv #(palauta-varusteen-tietieto % varuste) [:tie :aosa :aet :puoli]))
                                        varusteet)]
     (varuste-tiedot/kartalle
       (-> app
           (assoc-in [:tierekisterin-varusteet :varusteet] varusteet-jarjestetty)
           (assoc-in [:tierekisterin-varusteet :tietolajit] tietolajit)
           (assoc-in [:tierekisterin-varusteet :listaus-skeema]
                     (into skeema
                           (comp (filter varusteet/kiinnostaa-listauksessa?)
                                 (map varusteet/varusteominaisuus->skeema))
                           (:ominaisuudet tietolaji)))
           (assoc-in [:tierekisterin-varusteet :hakuehdot :haku-kaynnissa?] false))))))

(defn hae-varuste [app tunniste tie]
  (let [arvot (first (filter #(and (= tunniste (get-in % [:varuste :tunniste]))
                                   (= tie (get-in % [:varuste :tietue :sijainti :tie])))
                             (get-in app [:tierekisterin-varusteet :varusteet])))]
    (assoc (:varuste arvot) :sijainti (:sijainti arvot))))

;; Määritellään varustehaun UI tapahtumat

;; Päivittää varustehaun hakuehdot
(defrecord AsetaVarusteidenHakuehdot [hakuehdot])
;; Suorittaa haun
(defrecord HaeVarusteita [])
(defrecord TyhjennaHakutulokset [])
(defrecord VarusteHakuTulos [tietolaji varusteet])
(defrecord VarusteHakuEpaonnistui [virhe])
(defrecord LisaaLiitetiedosto [liite])
(defrecord PoistaUusiLiitetiedosto [liite])

;; Toimenpiteet Tierekisteriin
(defrecord PoistaVaruste [tunniste tie])
(defrecord AloitaVarusteenTarkastus [tunniste tietolaji tie])
(defrecord PeruutaVarusteenTarkastus [])
(defrecord AsetaVarusteTarkastuksenTiedot [tiedot])
(defrecord TallennaVarustetarkastus [varuste tarkastus])
(defrecord AloitaVarusteenMuokkaus [tunniste tie])
(defrecord AvaaVaruste [tunniste tie])
(defrecord ToimintoEpaonnistui [toiminto virhe])
(defrecord ToimintoOnnistui [vastaus viesti])

(extend-protocol t/Event
  AsetaVarusteidenHakuehdot
  (process-event [{ehdot :hakuehdot} app]
    (assoc-in app [:tierekisterin-varusteet :hakuehdot] ehdot))

  HaeVarusteita
  (process-event [_ {{hakuehdot :hakuehdot} :tierekisterin-varusteet :as app}]
    (let [tulos! (t/send-async! map->VarusteHakuTulos)
          virhe! (t/send-async! ->VarusteHakuEpaonnistui)]
      (go
        (let [hakuehdot (dissoc hakuehdot (if (= (:varusteiden-haun-tila hakuehdot) :sijainnilla)
                                            :tunniste
                                            :tierekisteriosoite)
                                :varusteiden-haun-tila)
              vastaus (<! (k/post! :hae-varusteita hakuehdot))]
          (log "[TR] Varustehaun vastaus: " (pr-str vastaus))
          (if (or (k/virhe? vastaus) (false? (:onnistunut vastaus)))
            (virhe! vastaus)
            (tulos! vastaus hakuehdot))))
      (-> app
          (assoc-in [:tierekisterin-varusteet :varusteet] nil)
          (assoc-in [:tierekisterin-varusteet :hakuehdot :haku-kaynnissa?] true))))

  TyhjennaHakutulokset
  (process-event [_ app]
    (-> app
        (assoc-in [:tierekisterin-varusteet :varusteet] nil)
        (update :tierekisterin-varusteet #(dissoc % :listaus-skeema))))

  VarusteHakuTulos
  (process-event [{tietolajit :tietolajit varusteet :varusteet} app]
    (hakutulokset app tietolajit varusteet))

  VarusteHakuEpaonnistui
  (process-event [{virhe :virhe :as vastaus} app]
    (log "[TR] Virhe haettaessa varusteita: " (pr-str virhe))
    (let [virheet (str/join ", " (mapv :viesti (:virheet virhe)))]
      (viesti/nayta! (str "Virhe haettaessa varusteita Tierekisteristä: " virheet) :warning))
    (assoc-in app [:tierekisterin-varusteet :hakuehdot :haku-kaynnissa?] false))

  ToimintoEpaonnistui
  (process-event [{{:keys [viesti vastaus]} :toiminto virhe :virhe :as tiedot} app]
    (log "[TR] Virhe suoritettaessa toimintoa. Virhe:" (pr-str virhe) ". Vastaus: " (pr-str vastaus) ".")
    (viesti/nayta! viesti :warning)
    ;; todo: mieti miten tehdä haku tierekisteriin uudestaan
    (-> app
        (hakutulokset)
        (assoc :uudet-varustetoteumat vastaus)))

  ToimintoOnnistui
  (process-event [{:keys [viesti vastaus]} app]
    (when viesti
      (viesti/nayta! viesti :success))
    ;; todo: mieti miten tehdä haku tierekisteriin uudestaan
    (-> app
        (hakutulokset)
        (assoc :uudet-varustetoteumat vastaus)))

  PoistaVaruste
  (process-event [{:keys [tunniste tie]} app]
    (let [varuste (hae-varuste app tunniste tie)
          tulos! (t/send-async! map->ToimintoOnnistui)
          virhe! (t/send-async! ->ToimintoEpaonnistui)]
      (go
        (let [varustetoteuma (varustetoteuma varuste :poistettu)
              hakuehdot {:urakka-id (:id @nav/valittu-urakka)
                         :sopimus-id (first @urakka/valittu-sopimusnumero)
                         :aikavali @urakka/valittu-aikavali}
              vastaus (<! (varuste-tiedot/tallenna-varustetoteuma hakuehdot varustetoteuma))]
          (if (or (k/virhe? vastaus) (false? (:onnistunut vastaus)))
            (virhe! {:vastaus vastaus :viesti "Varusteen poistossa tapahtui virhe."})
            (tulos! {:vastaus vastaus :viesti "Varuste poistettu onnistuneesti."})))))
    app)

  AloitaVarusteenTarkastus
  (process-event [{:keys [tunniste tietolaji tie]} app]
    (let [varuste (hae-varuste app tunniste tie)]
      (assoc-in app [:tierekisterin-varusteet :tarkastus] {:varuste varuste :tunniste tunniste :tietolajit tietolaji})))

  PeruutaVarusteenTarkastus
  (process-event [_ app]
    (assoc-in app [:tierekisterin-varusteet :tarkastus] nil))

  TallennaVarustetarkastus
  (process-event [{varuste :varuste
                   {lisatieto :lisatietoja kuntoluokitus :kuntoluokitus uusi-liite :uusi-liite} :tarkastus
                   :as data} app]
    (let [tulos! (t/send-async! map->ToimintoOnnistui)
          virhe! (t/send-async! ->ToimintoEpaonnistui)]
      (go
        (let [varuste (if (not= kuntoluokitus nil)
                        (assoc-in varuste [:tietue :tietolaji :arvot "kuntoluokitus"] kuntoluokitus) varuste)
              varustetoteuma (varustetoteuma varuste :tarkastus kuntoluokitus lisatieto uusi-liite)
              hakuehdot {:urakka-id (:id @nav/valittu-urakka)
                         :sopimus-id (first @urakka/valittu-sopimusnumero)
                         :aikavali @urakka/valittu-aikavali}
              vastaus (<! (varuste-tiedot/tallenna-varustetoteuma hakuehdot varustetoteuma))]
          (if (or (k/virhe? vastaus) (false? (:onnistunut vastaus)))
            (virhe! {:vastaus vastaus :viesti "Varusteen tarkastuksen kirjauksessa tapahtui virhe."})
            (tulos! {:vastaus vastaus :viesti "Varustetarkastus kirjattu onnistuneesti."})))))
    (assoc-in app [:tierekisterin-varusteet :tarkastus] nil))

  AsetaVarusteTarkastuksenTiedot
  (process-event [{uudet-tiedot :tiedot} {{tarkastus :tarkastus} :tierekisterin-varusteet :as app}]
    (assoc-in app [:tierekisterin-varusteet :tarkastus] (assoc tarkastus :tiedot uudet-tiedot)))

  AloitaVarusteenMuokkaus
  (process-event [{:keys [tunniste tie]} app]
    (let [varuste (hae-varuste app tunniste tie)]
      (assoc app :muokattava-varuste varuste)))

  AvaaVaruste
  (process-event [{:keys [tunniste tie]} app]
    (let [varuste (hae-varuste app tunniste tie)]
      (assoc app :naytettava-varuste varuste)))

  LisaaLiitetiedosto
  (process-event [{liite :liite} app]
    (assoc-in app [:tierekisterin-varusteet :tarkastus :uusi-liite] liite))

  PoistaUusiLiitetiedosto
  (process-event [_ app]
    (assoc-in app [:tierekisterin-varusteet :tarkastus :uusi-liite] nil)))
