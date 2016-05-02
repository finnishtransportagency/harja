(ns harja.palvelin.palvelut.yha
  "Paikallisen kannan YHA-tietojenkäsittelyn logiikka"
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.yha :as yha-q]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.integraatiot.yha.yha-komponentti :as yha]
            [harja.kyselyt.konversio :as konversio]
            [harja.domain.oikeudet :as oikeudet]))

(defn- lisaa-urakalle-yha-tiedot [db user urakka-id {:keys [yhatunnus yhaid yhanimi elyt vuodet] :as yha-tiedot}]
  (log/debug "Lisätään YHA-tiedot urakalle " urakka-id)
  (yha-q/lisaa-urakalle-yha-tiedot<! db {:urakka urakka-id
                                         :yhatunnus yhatunnus
                                         :yhaid yhaid
                                         :yhanimi yhanimi
                                         :elyt (konv/seq->array elyt)
                                         :vuodet (konv/seq->array (map str vuodet))
                                         :kayttaja (:id user)}))

(defn- poista-urakan-yha-tiedot [db urakka-id]
  (log/debug "Poistetaan urakan " urakka-id " vanhat YHA-tiedot")
  (yha-q/poista-urakan-yha-tiedot! db {:urakka urakka-id}))

(defn- poista-urakan-yllapitokohteet [db urakka-id]
  (log/debug "Poistetaan urakan " urakka-id " ylläpitokohteet")
  (yha-q/poista-urakan-yllapitokohdeosat! db {:urakka urakka-id})
  (yha-q/poista-urakan-yllapitokohteet! db {:urakka urakka-id}))

(defn sido-yha-urakka-harja-urakkaan [db user {:keys [harja-urakka-id yha-tiedot]}]
  (oikeudet/on-muu-oikeus? "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet harja-urakka-id user)
  (log/debug "Käsitellään pyyntö lisätä Harja-urakalle " harja-urakka-id " yha-tiedot: " yha-tiedot)
  (jdbc/with-db-transaction [db db]
    (poista-urakan-yha-tiedot db harja-urakka-id)
    (poista-urakan-yllapitokohteet db harja-urakka-id)
    (lisaa-urakalle-yha-tiedot db user harja-urakka-id yha-tiedot)
    (log/debug "YHA-tiedot sidottu")
    ;; TODO Hae ja käsittele YHA:n kohdeluettelo
    (log/debug "Palautetaan urakan YHA-tiedot")
    (first (into []
                 (comp
                   (map #(konv/array->vec % :vuodet))
                   (map #(konv/array->vec % :elyt)))
                 (yha-q/hae-urakan-yhatiedot db {:urakka harja-urakka-id})))))


(defn hae-urakat-yhasta [db yha user {:keys [yhatunniste sampotunniste vuosi harja-urakka-id]}]
  (oikeudet/on-muu-oikeus? "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet harja-urakka-id user)
  (let [urakat (yha/hae-urakat yha yhatunniste sampotunniste vuosi)
        yhaidt (mapv :yhaid urakat)
        sidontatiedot (yha-q/hae-urakoiden-sidontatiedot db {:yhaidt yhaidt})
        urakat (mapv second
                     (merge-with merge
                                 (into {} (map (juxt :yhaid identity) urakat))
                                 (into {} (map (juxt :yhaid identity) sidontatiedot))))]
    urakat))

(defrecord Yha []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)
          yha (:yha-integraatio this)]
      (julkaise-palvelu http :sido-yha-urakka-harja-urakkaan
                        (fn [user tiedot]
                          (sido-yha-urakka-harja-urakkaan db user tiedot)))
      (julkaise-palvelu http :hae-urakat-yhasta
                        (fn [user tiedot]
                          (hae-urakat-yhasta db yha user tiedot)))))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :sido-yha-urakka-harja-urakkaan
      this)))