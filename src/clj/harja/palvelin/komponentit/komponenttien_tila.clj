(ns harja.palvelin.komponentit.komponenttien-tila
  (:require [com.stuartsierra.component :as component]
            [clj-time.coerce :as tc]
            [harja.palvelin.tyokalut.tapahtuma-apurit :as tapahtuma-apurit]
            [harja.palvelin.tyokalut.tapahtuma-tulkkaus :as tapahtuma-tulkkaus]
            [harja.pvm :as pvm]))

(defn- tallenna-sonjan-tila-cacheen [{:keys [palvelin payload]} timeout? komponenttien-tila]
  (swap! komponenttien-tila
         (fn [kt]
           (-> kt
               (assoc-in [palvelin :sonja] payload)
               (assoc-in [palvelin :sonja :kaikki-ok?] (if timeout? false (tapahtuma-tulkkaus/sonjayhteys-ok? (:olioiden-tilat payload))))))))

(defn- aloita-sonjan-tarkkailu! [komponenttien-tila timeout]
  (tapahtuma-apurit/tarkkaile-tapahtumaa :sonja-tila {:tyyppi :viimeisin-per-palvelin :timeout timeout} tallenna-sonjan-tila-cacheen komponenttien-tila))

(defn- tallenna-dbn-tila-cacheen [{:keys [palvelin payload]} timeout? komponenttien-tila]
  (swap! komponenttien-tila
         (fn [kt]
           (assoc-in kt [palvelin :db :kaikki-ok?] (if timeout? false payload)))))

(defn- aloita-db-tarkkailu! [komponenttien-tila timeout]
  (tapahtuma-apurit/tarkkaile-tapahtumaa :db-tila {:tyyppi :viimeisin :timeout timeout} tallenna-dbn-tila-cacheen komponenttien-tila))

(defn- tallenna-db-replikan-tila-cacheen [{:keys [palvelin payload]} timeout? komponenttien-tila]
  (swap! komponenttien-tila
         (fn [kt]
           (assoc-in kt [palvelin :db-replica :kaikki-ok?] (if timeout? false payload)))))

(defn- aloita-db-replican-tarrkailu! [komponenttien-tila timeout]
  (tapahtuma-apurit/tarkkaile-tapahtumaa :db-replica-tila {:tyyppi :viimeisin :timeout timeout} tallenna-db-replikan-tila-cacheen komponenttien-tila))

(defn- tallenna-harjan-tila-cacheen [{:keys [palvelin payload]} komponenttien-tila]
  (swap! komponenttien-tila
         (fn [kt]
           (assoc-in kt [palvelin :harja] payload))))

(defn- aloita-harjan-tarkkailu! [komponenttien-tila]
  (tapahtuma-apurit/tarkkaile-tapahtumaa :harja-tila {:tyyppi :viimeisin-per-palvelin} tallenna-harjan-tila-cacheen komponenttien-tila))


(defn- tyhjenna-cache! [komponenttien-tila]
  (reset! komponenttien-tila nil))

(defrecord KomponentinTila [timeout-asetukset komponenttien-tila]
  component/Lifecycle
  (start [this]
    (let [{sonja-asetukset :sonja
           db-asetukset :db
           db-replica-asetukset :db-replica} timeout-asetukset
          varoaika (* 5 1000)]
      (assoc this
        ::harja-kuuntelija (aloita-harjan-tarkkailu! komponenttien-tila)
        ::sonja-kuuntelija (aloita-sonjan-tarkkailu! komponenttien-tila (+ (:paivitystiheys-ms sonja-asetukset) varoaika))
        ::db-kuuntelija (aloita-db-tarkkailu! komponenttien-tila (+ (:paivitystiheys-ms db-asetukset) varoaika))
        ::db-replica-kuuntelija (aloita-db-replican-tarrkailu! komponenttien-tila (+ (:paivitystiheys-ms db-replica-asetukset) varoaika)))))
  (stop [this]
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::harja-kuuntelija this))
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::sonja-kuuntelija this))
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::db-kuuntelija this))
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::db-replica-kuuntelija this))
    (tyhjenna-cache! komponenttien-tila)
    this))

(defn komponentin-tila [timeout-asetukset]
  (->KomponentinTila timeout-asetukset (atom {})))
