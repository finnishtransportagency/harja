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
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-vesivaylat user)
  (q/hae-kanavat-ja-kohteet db))

(defn lisaa-kanavalle-kohteita [db user kohteet]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-vesivaylat user)
  (q/lisaa-kanavalle-kohteet! db user kohteet)
  (hae-kanavat-ja-kohteet db user))

(defn liita-kohde-urakkaan! [db user {:keys [kohde-id urakka-id poistettu?]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-vesivaylat user)
  (q/liita-kohde-urakkaan! db user kohde-id urakka-id poistettu?))

(defn poista-kohde! [db user {:keys [kohde-id]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-vesivaylat user)
  (q/merkitse-kohde-poistetuksi! db user kohde-id))

(defrecord Kanavat []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelu
      http
      :hae-kanavat-ja-kohteet
      (fn [user]
        (hae-kanavat-ja-kohteet db user))
      {:vastaus-spec ::kan/hae-kanavat-ja-kohteet-vastaus})
    (julkaise-palvelu
      http
      :lisaa-kanavalle-kohteita
      (fn [user kohteet]
        (lisaa-kanavalle-kohteita db user kohteet))
      {:kysely-spec ::kan/lisaa-kanavalle-kohteita-kysely
       :vastaus-spec ::kan/lisaa-kanavalle-kohteita-vastaus})
    (julkaise-palvelu
      http
      :liita-kohde-urakkaan
      (fn [user tiedot]
        (liita-kohde-urakkaan! db user tiedot))
      {:kysely-spec ::kan/liita-kohde-urakkaan-kysely})
    (julkaise-palvelu
      http
      :poista-kohde
      (fn [user tiedot]
        (poista-kohde! db user tiedot))
      {:kysely-spec ::kan/poista-kohde-kysely})
    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-kanavat-ja-kohteet)
    this))
