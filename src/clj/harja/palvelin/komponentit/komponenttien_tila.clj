(ns harja.palvelin.komponentit.komponenttien-tila
  (:require [com.stuartsierra.component :as component]
            [clj-time.coerce :as tc]
            [harja.palvelin.tyokalut.tapahtuma-apurit :as tapahtuma-apurit]
            [harja.palvelin.tyokalut.tapahtuma-tulkkaus :as tapahtuma-tulkkaus]
            [harja.pvm :as pvm]))

(defn- tallenna-sonjan-tila-cacheen [komponenttien-tila {:keys [palvelin payload]} timeout?]
  (swap! komponenttien-tila
         (fn [kt]
           (let [tilan-tarkastaminen-onnistui? (and (map? payload)
                                                    (contains? payload :olioiden-tilat))
                 tilan-tarkastaminen-epaonnistui? (contains? payload :virhe)
                 harjaa-kaynnistetaan-uudestaan? (get-in kt [palvelin :harja :kaynnistetaan-uudestaan?])]
             (cond
               harjaa-kaynnistetaan-uudestaan?
               (-> kt
                   (assoc-in [palvelin :sonja] {:payload :harjaa-kaynnistetaan-uudestaan})
                   (assoc-in [palvelin :sonja :kaikki-ok?] true))
               tilan-tarkastaminen-onnistui?
               (-> kt
                   (assoc-in [palvelin :sonja] payload)
                   (assoc-in [palvelin :sonja :kaikki-ok?] (if timeout?
                                                             false
                                                             (tapahtuma-tulkkaus/sonjayhteys-ok? (:olioiden-tilat payload)))))
               tilan-tarkastaminen-epaonnistui?
               (-> kt
                   (assoc-in [palvelin :sonja] {:payload payload})
                   (assoc-in [palvelin :sonja :kaikki-ok?] false)))))))

(defn- harjajarjestelman-restart [komponentin-tila tapahtuma {:keys [palvelin payload]}]
  (case tapahtuma
    :aloitus (swap! komponentin-tila
                    update-in
                    [palvelin :harja]
                    assoc
                    :kaynnistetaan-uudestaan? true
                    :kaynnistettavat-komponentit payload)
    :onnistui (swap! komponentin-tila
                     update-in
                     [palvelin :harja]
                     assoc
                     :kaynnistetaan-uudestaan? false
                     :kaynnistettavat-komponentit nil)
    :epaonnistui (swap! komponentin-tila
                        update-in
                        [palvelin :harja]
                        assoc
                        :kaynnistetaan-uudestaan? false
                        :kaynnistettavat-komponentit nil
                        :kaikki-ok? false
                        :viesti "Harjan uudelleen käynnistys epäonnistui")))

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
             (zipmap [::harja-kuuntelija ::harjajarjestelman-restart ::harjajarjestelman-restart-onnistui ::harjajarjestelman-restart-epaonnistui ::sonja-kuuntelija ::sonjan-uudelleenkaynnistys-epaonnistui ::db-kuuntelija ::db-replica-kuuntelija]
                     (tapahtuma-apurit/tarkkaile-tapahtumia :harja-tila {:tyyppi :viimeisin-per-palvelin} (partial tallenna-harjan-tila-cacheen komponenttien-tila)
                                                            :harjajarjestelman-restart {:tyyppi :perus} (partial harjajarjestelman-restart komponenttien-tila :aloitus)
                                                            :harjajarjestelman-restart-onnistui {:tyyppi :perus} (partial harjajarjestelman-restart komponenttien-tila :onnistui)
                                                            :harjajarjestelman-restart-epaonnistui {:tyyppi :perus} (partial harjajarjestelman-restart komponenttien-tila :epaonnistui)
                                                            :sonja-tila {:tyyppi :viimeisin-per-palvelin :timeout (+ (:paivitystiheys-ms sonja-asetukset) varoaika)} (partial tallenna-sonjan-tila-cacheen komponenttien-tila)
                                                            :sonjan-uudelleenkaynnistys-epaonnistui {:tyyppi :perus} (partial tallenna-sonjan-uudelleen-kaynnistamisen-tila-cacheen komponenttien-tila)
                                                            :db-tila {:tyyppi :viimeisin :timeout (+ (:paivitystiheys-ms db-asetukset) varoaika)} (partial tallenna-dbn-tila-cacheen komponenttien-tila)
                                                            :db-replica-tila {:tyyppi :viimeisin :timeout (+ (:paivitystiheys-ms db-replica-asetukset) varoaika)} (partial tallenna-db-replikan-tila-cacheen komponenttien-tila))))))
  (stop [this]
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::harja-kuuntelija this))
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::harjajarjestelman-restart this))
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::harjajarjestelman-restart-onnistui this))
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::harjajarjestelman-restart-epaonnistui this))
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::sonja-kuuntelija this))
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::sonjan-uudelleenkaynnistys-epaonnistui this))
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::db-kuuntelija this))
    (tapahtuma-apurit/lopeta-tapahtuman-kuuntelu @(::db-replica-kuuntelija this))
    (tyhjenna-cache! komponenttien-tila)
    (dissoc this ::harja-kuuntelija ::sonja-kuuntelija ::sonjan-uudelleenkaynnistys-epaonnistui ::db-kuuntelija ::db-replica-kuuntelija)))

(defn komponentin-tila [timeout-asetukset]
  (->KomponentinTila timeout-asetukset (atom {})))
