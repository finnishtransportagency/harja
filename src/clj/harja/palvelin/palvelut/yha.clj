(ns harja.palvelin.palvelut.yha
  "Paikallisen kannan YHA-tietojenkäsittelyn logiikka"
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.yha :as yha-q]
            [harja.kyselyt.konversio :as konv]))

(defn- lisaa-urakalle-yha-tiedot [db user urakka-id {:keys [yhatunnus yhaid yhanimi elyt vuodet] :as yha-tiedot}]
  (yha-q/lisaa-urakalle-yha-tiedot<! db {:urakka urakka-id
                                         :yhatunnus yhatunnus
                                         :yhaid yhaid
                                         :yhanimi yhanimi
                                         :elyt (konv/seq->array elyt)
                                         :vuodet (konv/seq->array vuodet)
                                         :kayttaja (:id user)}))

(defn- poista-urakan-yha-tiedot [db urakka-id]
  (yha-q/poista-urakan-yha-tiedot! db {:urakka urakka-id}))

(defn sido-yha-urakka-harja-urakkaan [db user {:keys [harja-urakka-id yha-tiedot]}]
  ; FIXME Oikeustarkistus!
  (log/debug "Käsitellään pyyntö lisätä Harja-urakalle " harja-urakka-id " yha-tiedot: " yha-tiedot)
  (jdbc/with-db-transaction [db db]
    (log/debug "Poistetaan urakan vanhat YHA-tiedot")
    (poista-urakan-yha-tiedot db harja-urakka-id)
    (log/debug "Lisätään YHA-tiedot")
    (lisaa-urakalle-yha-tiedot db user harja-urakka-id yha-tiedot)
    (log/debug "YHA-tiedot sidottu")
    (yha-q/hae-urakka-yhatietoineen db {:urakka harja-urakka-id})))

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