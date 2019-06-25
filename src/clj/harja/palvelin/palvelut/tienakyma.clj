(ns harja.palvelin.palvelut.tienakyma
  "Tienäkymän backend palvelu.

  Tienäkymässä haetaan TR osoitevälillä tietyllä aikavälillä tapahtuneita
  asioita. Palvelu on vain tilaajan käyttäjille, eikä hauissa ole mitään
  urakkarajauksia näkyvyyteen.

  Tienäkymän kaikki löydökset renderöidään frontilla, koska tietomäärä on rajattu
  yhteen tiehen ja aikaväliin. Tällä mallilla ei tarvitse tehdä erikseen enää
  karttakuvan klikkauksesta hakua vaan kaikki tienäkymän tieto on jo frontilla.

  Kaikki hakufunktiot ottavat samat parametrit: tietokantayhteyden ja parametrimäpin,
  jossa on seuraavat tiedot:
  - hakualueen extent: :x1, :y1, :x2 ja :y2
  - tierekisteriosoitteen geometria: :sijainti
  - tierekisteriosoite: :numero, :alkuosa, :alkuetaisyys, :loppuosa ja :loppuetaisyys
  - haettava aikaväli: :alku ja :loppu

  Hakufunktiot ovat tienakyma-haut mäpissä määritelty.
  "
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin
             :refer [julkaise-palvelu poista-palvelut]
             :as http-palvelin]
            [harja.geo :as geo]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]
            [slingshot.slingshot :refer [throw+]]
            [harja.tyokalut.functor :refer [fmap]]
            [taoensso.timbre :as log]
            [harja.kyselyt.tienakyma :as q]
            [harja.kyselyt.tietyoilmoitukset :as tietyoilmoitukset-q]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.palvelut.tilannekuva :as tilannekuva]
            [harja.palvelin.palvelut.toteumat :as toteumat]
            [clojure.core.async :as async]
            [harja.kyselyt.kursori :as kursori]
            [harja.domain.tienakyma :as d]))

(defonce debug-hakuparametrit (atom nil))

(defn- hakuparametrit
  "Tekee käyttäjän antamista hakuehdoista jeesql hakuparametrit"
  [{:keys [sijainti alku loppu tierekisteriosoite] :as valinnat}]
  (let [extent (geo/extent sijainti)]
    (merge
     ;; TR-osoitteen geometrinen alue envelope
     (zipmap  [:x1 :y1 :x2 :y2] extent)

     ;; Tierekisteriosoitteen geometria
     {:sijainti (geo/geometry (geo/clj->pg sijainti))}

     ;; Tierekisteriosoite
     tierekisteriosoite

     ;; Aikarajaus, jonka sisällä tarkastellaan
     {:alku alku
      :loppu loppu})))

(defn- hae-toteumat [db parametrit]
  (kursori/hae-kanavaan
   (async/chan 32 (comp
                    ;; Tässä ei haluta palauttaa varustetoteumia.
                    ;; Tehokkain tapa estää varustetoteumien palautuminen on tehdä filtteröinti
                    ;; täällä, koska kommentin kirjoittamisen hetkellä suodattaminen vaatisi SQL-puolella
                    ;; raskaan EXISTS tarkastuksen.
                    (filter #(not (empty? (:tehtavat %))))
                    (map #(assoc % :tyyppi-kartalla :toteuma))))
   db q/hae-toteumat parametrit))

(defn- hae-varustetoteumat [db parametrit]
  (kursori/hae-kanavaan
   (async/chan 32 (toteumat/varustetoteuma-xf))
   db q/hae-varustetoteumat parametrit))

(defn- hae-tarkastukset [db parametrit]
  (kursori/hae-kanavaan (async/chan 32 (comp (map konv/alaviiva->rakenne)
                                             (map #(assoc % :tyyppi-kartalla :tarkastus))
                                             (geo/muunna-pg-tulokset :sijainti)
                                             (map #(konv/array->vec % :vakiohavainnot))
                                             (map #(konv/string->keyword % :tyyppi))))
                        db q/hae-tarkastukset parametrit))

(defn- hae-turvallisuuspoikkeamat [db parametrit]
  (kursori/hae-kanavaan (async/chan 32 (comp (geo/muunna-pg-tulokset :sijainti)
                                             (map #(assoc % :tyyppi-kartalla :turvallisuuspoikkeama))
                                             (map #(konv/array->keyword-set % :tyyppi))))
                        db q/hae-turvallisuuspoikkeamat parametrit))

(defn- hae-laatupoikkeamat [db parametrit]
  (kursori/hae-kanavaan (async/chan 32 (comp (map konv/alaviiva->rakenne)
                                             (geo/muunna-pg-tulokset :sijainti)
                                             (map #(if (nil? (get-in % [:yllapitokohde :numero]))
                                                     (dissoc % :yllapitokohde)
                                                     %))
                                             (map #(assoc % :tyyppi-kartalla :laatupoikkeama))))
                        db q/hae-laatupoikkeamat parametrit))

(defn- hae-ilmoitukset [db parametrit]
  (let [ch (async/chan 32)]
    (async/thread
      (let [tulokset (konv/sarakkeet-vektoriin
                      (into []
                            (comp tilannekuva/ilmoitus-xf
                                  (map #(assoc % :tyyppi-kartalla (:ilmoitustyyppi %))))
                            (q/hae-ilmoitukset db parametrit))
                      {:kuittaus :kuittaukset})]
        (loop [[t & tulokset] tulokset]
          (when (and t (async/>!! ch t))
            (recur tulokset)))
        (async/close! ch)))
    ch))

(defn- hae-tietyoilmoitukset [db parametrit]
  (let [ch (async/chan 32)]
    (async/thread
      (let [tulokset (into []
                           (map #(assoc % :tyyppi-kartalla :tietyoilmoitus))
                           (tietyoilmoitukset-q/hae-ilmoitukset-tienakymaan db parametrit))]
        (loop [[t & tulokset] tulokset]
          (when (and t (async/>!! ch t))
            (recur tulokset)))
        (async/close! ch)))
    ch))

(def ^{:private true
       :doc "Määrittelee kaikki kyselyt mitä tienäkymään voi hakea"}
  tienakyma-haut
  {:toteumat #'hae-toteumat
   :ilmoitukset #'hae-ilmoitukset
   :tarkastukset #'hae-tarkastukset
   :turvallisuuspoikkeamat #'hae-turvallisuuspoikkeamat
   :laatupoikkeamat #'hae-laatupoikkeamat
   :tietyoilmoitukset #'hae-tietyoilmoitukset
   :varustetoteumat #'hae-varustetoteumat})

(def +haun-max-kesto+ 20000)

(defn- hae-tienakymaan [db valinnat]
  (let [parametrit (hakuparametrit valinnat)]
    (reset! debug-hakuparametrit parametrit)

    (let [timeout (async/timeout +haun-max-kesto+)
          kanavat (vals (fmap (fn [haku-fn]
                                (haku-fn db parametrit))
                              tienakyma-haut))
          tulos-ch (async/merge kanavat)
          lue! #(async/alts!! [timeout tulos-ch])]
      (try
        (loop [acc []
               [v ch] (lue!)]
          (if (= ch timeout)
            (do (log/warn "Aika loppui haettaessa tienäkymän tietoja")
                ;; Paluatetaan virheviestin kera se, mitä kerettiin löytää
                {:timeout? true
                 :tulos acc})
            (if v
              (recur (conj acc v) (lue!))
              {:tulos acc})))
        (finally
          (doseq [k kanavat]
            (async/close! k)))))))

(defn- hae-reittipisteet [db {:keys [toteuma-id]}]
  (q/hae-reittipisteet db {:toteuma-id toteuma-id}))

(defn vain-tilaajalle! [user]
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (when-not (roolit/tilaajan-kayttaja? user)
    (throw+ (roolit/->EiOikeutta "vain tilaajan käyttäjille"))))



(defrecord Tienakyma []
  component/Lifecycle
  (start [{db :db http :http-palvelin
           :as this}]
    (julkaise-palvelu
     http
     :hae-tienakymaan
     (fn [user valinnat]
       (vain-tilaajalle! user)
       (hae-tienakymaan db valinnat))
     {:kysely-spec ::d/hakuehdot
      :vastaus-spec ::d/tulokset})

    (julkaise-palvelu
     http
     :hae-reittipisteet-tienakymaan
     (fn [user valinnat]
       (vain-tilaajalle! user)
       (hae-reittipisteet db valinnat)))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :hae-tienakymaan :hae-reittipisteet-tienakymaan)
    this))
