(ns harja.palvelin.komponentit.komponenttien-tila
  (:require [com.stuartsierra.component :as component]
            [clj-time.coerce :as tc]
            [harja.palvelin.tyokalut.event-apurit :as event-apurit]
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

(defn- tallenna-sonjan-tila-cacheen [jms-tila komponenttien-tila]
  (swap! komponenttien-tila
         (fn [kt]
           (-> kt
               (assoc :sonja jms-tila)
               (assoc-in [:sonja :kaikki-ok?] (sonjayhteys-ok? (:olioiden-tilat jms-tila)))))))

(defn- aloita-sonjan-tarkkailu! [komponentti-event komponenttien-tila]
  (event-apurit/kuuntele-eventtia komponentti-event :sonja :tila tallenna-sonjan-tila-cacheen komponenttien-tila))

(defn- tallenna-dbn-tila-cacheen [kanta-ok? komponenttien-tila]
  (swap! komponenttien-tila
         (fn [kt]
           (assoc-in kt [:db :kaikki-ok?] kanta-ok?))))

(defn- aloita-db-tarkkailu! [komponentti-event komponenttien-tila]
  (event-apurit/kuuntele-eventtia komponentti-event :db :tila tallenna-dbn-tila-cacheen komponenttien-tila))

(defn- tallenna-db-replikan-tila-cacheen [replica-ok? komponenttien-tila]
  (swap! komponenttien-tila
         (fn [kt]
           (assoc-in kt [:db-replica :kaikki-ok?] replica-ok?))))

(defn- aloita-db-replican-tarrkailu! [komponentti-event komponenttien-tila]
  (event-apurit/kuuntele-eventtia komponentti-event :db-replica :tila tallenna-db-replikan-tila-cacheen komponenttien-tila))


(defn- tyhjenna-cache! [komponenttien-tila]
  (reset! komponenttien-tila nil))

(defrecord KomponentinTila [komponenttien-tila]
  component/Lifecycle
  (start [{:keys [komponentti-event] :as this}]
    (assoc this
           ::sonja-kuuntelija (aloita-sonjan-tarkkailu! komponentti-event komponenttien-tila)
           ::db-kuuntelija (aloita-db-tarkkailu! komponentti-event komponenttien-tila)
           ::db-replica-kuuntelija (aloita-db-replican-tarrkailu! komponentti-event komponenttien-tila)))
  (stop [this]
    (event-apurit/lopeta-eventin-kuuntelu (::sonja-kuuntelija this))
    (event-apurit/lopeta-eventin-kuuntelu (::db-kuuntelija this))
    (event-apurit/lopeta-eventin-kuuntelu (::db-replica-kuuntelija this))
    (tyhjenna-cache! komponenttien-tila)
    this))

(defn komponentin-tila []
  (->KomponentinTila (atom {})))
