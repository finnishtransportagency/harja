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
  (log/debug (pr-str hallintayksikko))
  (log/debug (class hallintayksikko))
  (log/debug (pr-str urakka))
  (log/debug (pr-str aikavali))
  (log/debug (pr-str (map name tyypit)))
  (log/debug (pr-str hakuehto))
  (log/debug (pr-str tilat))
  (let [mankeloitava (into []
                           (comp
                             (map konv/alaviiva->rakenne)
                             (map #(assoc % :urakkatyyppi (keyword (:urakkatyyppi %))))
                             (map #(assoc % :ilmoituksenselite (keyword (:ilmoituksenselite %))))
                             (map #(assoc % :kuittaustyyppi (keyword (:kuittaustyyppi %))))
                             (map #(assoc % :ilmoitustyyppi (keyword (:ilmoitustyyppi %))))
                             (map #(assoc % :ilmoittajatyyppi (keyword (:ilmoittajatyyppi %)))))
                           (q/hae-ilmoitukset db
                                              hallintayksikko
                                              urakka
                                              (when (first aikavali)
                                                (konv/sql-timestamp (first aikavali)))
                                              (when (second aikavali)
                                                (konv/sql-timestamp (second aikavali)))
                                              (mapv name tyypit)
                                              hakuehto
                                              (:suljetut tilat)
                                              (:avoimet tilat)
                                              ))]

    (log/debug mankeloitava)))

(defrecord Ilmoitukset []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-ilmoitukset
                      (fn [user tiedot #_[{:keys [hallintayksikko urakka tilat tyypit aikavali hakuehto]} tiedot]]
                        (log/debug ":hae-ilmoitukset")
                        (hae-ilmoitukset (:db this) user (:hallintayksikko tiedot)
                                         (:urakka tiedot) (:tilat tiedot) (:tyypit tiedot) (:aikavali tiedot)
                                         (:hakuehto tiedot))))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-ilmoitukset)

    this))
