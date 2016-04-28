(ns harja.palvelin.palvelut.yha
  "Paikallisen kannan YHA-tietojenkäsittelyn logiikka"
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.yha :as q]
            [harja.geo :as geo]
            [harja.domain.oikeudet :as oikeudet]))

(defn- lisaa-urakalle-yha-tiedot [db user urakka-id {:keys [yhatunnus yhaid yhanimi elyt vuodet] :as yha-tiedot}]
  (q/lisaa-urakalle-yha-tiedot db {:urakka urakka-id
                                   :yhatunnus yhatunnus
                                   :yhaid yhaid
                                   :yhanimi yhanimi
                                   :elyt elyt
                                   :vuodet vuodet
                                   :kayttaja (:id user)}))

(defn- poista-urakan-yha-tiedot [db urakka-id]
  (q/poista-urakan-yha-tiedot db {:urakka urakka-id}))

(defn sido-yha-urakka-harja-urakkaan [db user {:keys [harja-urakka-id yha-tiedot]}]
  ; FIXME Oikeustarkistus!
  (log/debug (format "Lisätään Harja-urakalle " harja-urakka-id " yha-tiedot: " yha-tiedot))
  (jdbc/with-db-transaction [db db]
    (poista-urakan-yha-tiedot db harja-urakka-id)
    (lisaa-urakalle-yha-tiedot db user harja-urakka-id yha-tiedot)
    (log/debug "YHA-tiedot sidottu!")
    (q/hae-urakka-yhatietoineen db {:urakka harja-urakka-id})))

(defrecord Yha []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)]
      (julkaise-palvelu http :sido-yha-urakka-harja-urakkaan
                        (fn [user tiedot]
                          (sido-yha-urakka-harja-urakkaan db user tiedot)))))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :sido-yha-urakka-harja-urakkaan
      this)))