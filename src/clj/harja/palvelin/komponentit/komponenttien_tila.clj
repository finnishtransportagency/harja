(ns harja.palvelin.komponentit.komponenttien-tila
  (:require [com.stuartsierra.component :as component]
            [clj-time.coerce :as tc]
            [harja.palvelin.tyokalut.tapahtuma-apurit :as tapahtuma-apurit]
            [harja.palvelin.tyokalut.tapahtuma-tulkkaus :as tapahtuma-tulkkaus]
            [harja.pvm :as pvm]))

(defn- tallenna-sonjan-tila-cacheen [komponenttien-tila {:keys [palvelin payload]} timeout?]
  (swap! komponenttien-tila
         (fn [kt]
           (if (and (map? payload)
                    (contains? payload :olioiden-tilat))
             (-> kt
                 (assoc-in [palvelin :sonja] payload)
                 (assoc-in [palvelin :sonja :kaikki-ok?] (if timeout? false (tapahtuma-tulkkaus/sonjayhteys-ok? (:olioiden-tilat payload)))))
             (-> kt
                 (assoc-in [palvelin :sonja] {:payload payload})
                 (assoc-in [palvelin :sonja :kaikki-ok?] (if timeout? false (tapahtuma-tulkkaus/sonjayhteys-ok? (:olioiden-tilat payload)))))))))

(defn- tallenna-sonjan-uudelleen-kaynnistamisen-tila-cacheen [komponenttien-tila {:keys [palvelin payload]}]
  (swap! komponenttien-tila
         (fn [kt]
           (-> kt
               (assoc-in [palvelin :sonja] {:payload payload})
               (assoc-in [palvelin :sonja :kaikki-ok?] false)))))

(defn- tallenna-dbn-tila-cacheen [komponenttien-tila {:keys [palvelin payload]} timeout?]
  (swap! komponenttien-tila
         (fn [kt]
           (assoc-in kt [palvelin :db :kaikki-ok?] (if timeout? false payload)))))

(defn- tallenna-db-replikan-tila-cacheen [komponenttien-tila {:keys [palvelin payload]} timeout?]
  (swap! komponenttien-tila
         (fn [kt]
           (assoc-in kt [palvelin :db-replica :kaikki-ok?] (if timeout? false payload)))))

(defn- tallenna-harjan-tila-cacheen [komponenttien-tila {:keys [palvelin payload]}]
  (swap! komponenttien-tila
         (fn [kt]
           (assoc-in kt [palvelin :harja] payload))))


(defn- tyhjenna-cache! [komponenttien-tila]
  (reset! komponenttien-tila {}))

(defrecord KomponentinTila [timeout-asetukset komponenttien-tila]
  component/Lifecycle
  (start [this]
    (let [{sonja-asetukset :sonja
           db-asetukset :db
           db-replica-asetukset :db-replica} timeout-asetukset
          varoaika (* 5 1000)]
      (merge this
             (zipmap [::harja-kuuntelija ::sonja-kuuntelija ::sonjan-uudelleenkaynnistys-epaonnistui ::db-kuuntelija ::db-replica-kuuntelija]
                     (tapahtuma-apurit/tarkkaile-tapahtumia :harja-tila {:tyyppi :viimeisin-per-palvelin} (partial tallenna-harjan-tila-cacheen komponenttien-tila)
                                                            :sonja-tila {:tyyppi :viimeisin-per-palvelin :timeout (+ (:paivitystiheys-ms sonja-asetukset) varoaika)} (partial tallenna-sonjan-tila-cacheen komponenttien-tila)
                                                            :sonjan-uudelleenkaynnistys-epaonnistui {:tyyppi :perus} (partial tallenna-sonjan-uudelleen-kaynnistamisen-tila-cacheen komponenttien-tila)
                                                            :db-tila {:tyyppi :viimeisin :timeout (+ (:paivitystiheys-ms db-asetukset) varoaika)} (partial tallenna-dbn-tila-cacheen komponenttien-tila)
                                                            :db-replica-tila {:tyyppi :viimeisin :timeout (+ (:paivitystiheys-ms db-replica-asetukset) varoaika)} (partial tallenna-db-replikan-tila-cacheen komponenttien-tila))))))
  (stop [this]
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::harja-kuuntelija this))
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::sonja-kuuntelija this))
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::sonjan-uudelleenkaynnistys-epaonnistui this))
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::db-kuuntelija this))
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::db-replica-kuuntelija this))
    (tyhjenna-cache! komponenttien-tila)
    (dissoc this ::harja-kuuntelija ::sonja-kuuntelija ::sonjan-uudelleenkaynnistys-epaonnistui ::db-kuuntelija ::db-replica-kuuntelija)))

(defn komponentin-tila [timeout-asetukset]
  (->KomponentinTila timeout-asetukset (atom {})))
