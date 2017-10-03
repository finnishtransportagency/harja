(ns harja.palvelin.palvelut.vesivaylat.alukset
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

(defn hae-urakan-alukset [db user tiedot]
  ;; TODO Toteuta
  ;; TODO Testi
  ;; TODO Oikeustarkistus
  (log/debug "TODO"))

(defn hae-urakoitsijan-alukset [db user tiedot]
  ;; TODO Toteuta
  ;; TODO Testi
  ;; TODO Oikeustarkistus
  (log/debug "TODO"))

(defn hae-kaikki-alukset [db user tiedot]
  ;; TODO Toteuta
  ;; TODO Testi
  ;; TODO Oikeustarkistus
  (log/debug "TODO"))

(defrecord Alukset []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelu
      http
      :hae-urakan-alukset
      (fn [user tiedot]
        (hae-urakan-alukset db user tiedot))
      ;; TODO spec
      )
    (julkaise-palvelu
      http
      :hae-urakoitsijan-alukset
      (fn [user tiedot]
        (hae-urakoitsijan-alukset db user tiedot))
      ;; TODO spec
      )
    (julkaise-palvelu
      http
      :hae-kaikki-alukset
      (fn [user tiedot]
        (hae-kaikki-alukset db user tiedot))
      ;; TODO spec
      )
    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-urakan-alukset
      :hae-urakoitsijan-alukset
      :hae-kaikki-alukset)
    this))