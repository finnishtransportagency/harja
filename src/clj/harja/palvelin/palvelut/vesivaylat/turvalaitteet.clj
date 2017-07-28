(ns harja.palvelin.palvelut.vesivaylat.turvalaitteet
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.core.async :as async]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :refer [ominaisuus-kaytossa?]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]
            [harja.geo :as geo]
            [harja.transit :as transit]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.vesivaylat.turvalaite :as tu]
            [harja.kyselyt.vesivaylat.turvalaitteet :as tu-q]))

(defn hae-turvalaitteet [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    ;; Turvalaitteita haetaan väylän tai turvalaitenumeroiden perusteella,
    ;; eli oikeustarkastusta ei tarvita. Jos lisätään esim urakan perusteella
    ;; haku, täytyy oikeustarkastusta harkita uudelleen.
    (oikeudet/ei-oikeustarkistusta!)
    (tu-q/hae-turvalaitteet-kartalle db tiedot)))

(defrecord Turvalaitteet []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelu
      http
      :hae-turvalaitteet-kartalle
      (fn [user tiedot]
        (hae-turvalaitteet db user tiedot))
      {:kysely-spec ::tu/hae-turvalaitteet-kysely
       :vastaus-spec ::tu/hae-turvalaitteet-vastaus})
    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-turvalaitteet-kartalle)
    this))