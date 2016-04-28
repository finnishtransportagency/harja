(ns harja.palvelin.palvelut.yha
  "Paikallisen kannan YHA-tietojenkäsittelyn logiikka"
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.yllapitokohteet :as q]
            [harja.geo :as geo]
            [harja.domain.oikeudet :as oikeudet]))

(defn sido-yha-urakka-harja-urakkaan [db user {:keys [urakka-id sopimus-id]}]
  #_(oikeudet/lue oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  #_(log/debug "Haetaan urakan ylläpitokohteet.")
  #_(jdbc/with-db-transaction [db db]
    (let [vastaus (into []
                        (comp (map #(konv/string-polusta->keyword % [:paallystysilmoitus_tila]))
                              (map #(konv/string-polusta->keyword % [:paikkausilmoitus_tila]))
                              (map #(assoc % :kohdeosat
                                             (into []
                                                   kohdeosa-xf
                                                   (q/hae-urakan-yllapitokohteen-yllapitokohdeosat
                                                     db urakka-id sopimus-id (:id %))))))
                        (q/hae-urakan-yllapitokohteet db urakka-id sopimus-id))]
      (log/debug "Päällystyskohteet saatu: " (pr-str (map :nimi vastaus)))
      vastaus))) ;; TODO!

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