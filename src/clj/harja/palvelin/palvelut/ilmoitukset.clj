(ns harja.palvelin.palvelut.ilmoitukset
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.oikeudet :as oik]
            [harja.kyselyt.konversio :as konv]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]

            [harja.kyselyt.ilmoitukset :as q]))

(defn hae-ilmoitukset
  [db user hallintayksikko urakka tilat tyypit aikavali hakuehto]
  (into []
        (comp
          (map konv/alaviiva->rakenne)
          (map #(assoc % :urakkatyyppi (keyword (:urakkatyyppi %))))
          (map #(assoc % :ilmoituksenselite (keyword (:ilmoituksenselite %))))
          (map #(assoc % :kuittaustyyppi (keyword (:kuittaustyyppi %))))
          (map #(assoc % :ilmoitustyyppi (keyword (:ilmoitustyyppi %))))
          (map #(assoc % :ilmoittajatyyppi (keyword (:ilmoittajatyyppi %)))))
    (q/hae-ilmoitukset db hallintayksikko urakka tilat tyypit aikavali hakuehto)))

(defrecord Ilmoitukset []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-ilmoitukset
                      (fn [user [{:keys [hallintayksikko urakka tilat tyypit aikavali hakuehto]}]]
                        (hae-ilmoitukset (:db this) user hallintayksikko urakka tilat tyypit aikavali hakuehto)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-ilmoitukset)

    this))
