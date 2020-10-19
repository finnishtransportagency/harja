(ns harja.palvelin.komponentit.komponenttien-tila
  (:require [com.stuartsierra.component :as component]
            [clj-time.coerce :as tc]
            [harja.palvelin.tyokalut.tapahtuma-apurit :as tapahtuma-apurit]
            [harja.pvm :as pvm]))

(defn olion-tila-aktiivinen? [tila]
  (= tila "ACTIVE"))

(defn jono-ok? [jonon-tiedot]
  (let [{:keys [tuottaja vastaanottaja]} (first (vals jonon-tiedot))
        tuottajan-tila-ok? (when tuottaja
                             (olion-tila-aktiivinen? (:tuottajan-tila tuottaja)))
        vastaanottajan-tila-ok? (when vastaanottaja
                                  (olion-tila-aktiivinen? (:vastaanottajan-tila vastaanottaja)))]
    (every? #(not (false? %))
            [tuottajan-tila-ok? vastaanottajan-tila-ok?])))

(defn istunto-ok? [{:keys [jonot istunnon-tila]}]
  (and (olion-tila-aktiivinen? istunnon-tila)
       (not (empty? jonot))
       (every? jono-ok?
               jonot)))

(defn sonjayhteys-ok?
  [{:keys [istunnot yhteyden-tila]}]
  (boolean
    (and (olion-tila-aktiivinen? yhteyden-tila)
         (not (empty? istunnot))
         (every? istunto-ok?
                 istunnot))))

(defn sonjayhteydet-kannasta-ok? [tilat]
  (and (not (empty? tilat))
       (every? (fn [{:keys [tila paivitetty]}]
                 (and (sonjayhteys-ok? (:olioiden-tilat tila))
                      (pvm/ennen? (tc/to-local-date-time (pvm/sekunttia-sitten 20))
                                  (tc/to-local-date-time paivitetty))))
               tilat)))

(defn- tallenna-sonjan-tila-cacheen [{:keys [palvelin payload]} komponenttien-tila]
  (swap! komponenttien-tila
         (fn [kt]
           (-> kt
               (assoc-in [palvelin :sonja] payload)
               (assoc-in [palvelin :sonja :kaikki-ok?] (sonjayhteys-ok? (:olioiden-tilat payload)))))))

(defn- aloita-sonjan-tarkkailu! [komponenttien-tila]
  (tapahtuma-apurit/tarkkaile-tapahtumaa :sonja-tila :viimeisin tallenna-sonjan-tila-cacheen komponenttien-tila))

(defn- tallenna-dbn-tila-cacheen [{:keys [palvelin payload]} komponenttien-tila]
  (swap! komponenttien-tila
         (fn [kt]
           (assoc-in kt [palvelin :db :kaikki-ok?] payload))))

(defn- aloita-db-tarkkailu! [komponenttien-tila]
  (tapahtuma-apurit/tarkkaile-tapahtumaa :db-tila :viimeisin tallenna-dbn-tila-cacheen komponenttien-tila))

(defn- tallenna-db-replikan-tila-cacheen [{:keys [palvelin payload]} komponenttien-tila]
  (swap! komponenttien-tila
         (fn [kt]
           (assoc-in kt [palvelin :db-replica :kaikki-ok?] payload))))

(defn- aloita-db-replican-tarrkailu! [komponenttien-tila]
  (tapahtuma-apurit/tarkkaile-tapahtumaa :db-replica-tila :viimeisin tallenna-db-replikan-tila-cacheen komponenttien-tila))

(defn- tallenna-harjan-tila-cacheen [{:keys [palvelin payload]} komponenttien-tila]
  (swap! komponenttien-tila
         (fn [kt]
           (assoc-in kt [palvelin :harja] payload))))

(defn- aloita-harjan-tarkkailu! [komponenttien-tila]
  (tapahtuma-apurit/tarkkaile-tapahtumaa :harja-tila :viimeisin tallenna-harjan-tila-cacheen komponenttien-tila))


(defn- tyhjenna-cache! [komponenttien-tila]
  (reset! komponenttien-tila nil))

(defrecord KomponentinTila [komponenttien-tila]
  component/Lifecycle
  (start [this]
    (assoc this
           ::harja-kuuntelija (aloita-harjan-tarkkailu! komponenttien-tila)
           ::sonja-kuuntelija (aloita-sonjan-tarkkailu! komponenttien-tila)
           ::db-kuuntelija (aloita-db-tarkkailu! komponenttien-tila)
           ::db-replica-kuuntelija (aloita-db-replican-tarrkailu! komponenttien-tila)))
  (stop [this]
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu (::sonja-kuuntelija this))
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu (::db-kuuntelija this))
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu (::db-replica-kuuntelija this))
    (tyhjenna-cache! komponenttien-tila)
    this))

(defn komponentin-tila []
  (->KomponentinTila (atom {})))
