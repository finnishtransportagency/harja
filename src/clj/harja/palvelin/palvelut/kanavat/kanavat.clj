(ns harja.palvelin.palvelut.kanavat.kanavat
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.kanavat.kanavat :as q]

            [harja.domain.kanavat.kanava :as kan]))


(defn hae-kanavat-ja-kohteet [db user]
  (q/hae-kanavat-ja-kohteet db))

(defrecord Kanavat []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelu
      http
      :hae-kanavat-ja-kohteet
      (fn [user]
        (hae-kanavat-ja-kohteet db user))
      {:kysely-spec ::kan/hae-kanavat-ja-kohteet-kysely
       :vastaus-spec ::kan/hae-kanavat-ja-kohteet-vastaus})
    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-kanavat-ja-kohteet)
    this))
