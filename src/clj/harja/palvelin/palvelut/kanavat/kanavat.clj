(ns harja.palvelin.palvelut.kanavat.kanavat
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.kanavat.kanavat :as q]

            [harja.domain.kanavat.kanava :as kan]
            [harja.domain.kanavat.kanavan-kohde :as kohde]
            [harja.domain.kanavat.kanavan-huoltokohde :as huoltokohde]
            [harja.domain.urakka :as ur]))


(defn hae-kanavat-ja-kohteet [db user]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-vesivaylat user)
  (q/hae-kanavat-ja-kohteet db))

(defn hae-urakan-kohteet [db user tiedot]
  (let [urakka-id (::ur/id tiedot)]
    (assert urakka-id "Ei voida hakea urakan kohteita, urakka-id puuttuu")
    ;; TODO Tämä on vähän hassu oikeustarkastus, koska pitää vaan varmistaa, että käyttäjällä
    ;; on oikeus nähdä, mitkä kohteet kuuluvat kyseiseen urakkaan. Tälle ei vaan ole suoraa omaa oikeutta
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kanavat-kokonaishintaiset user)

    (q/hae-urakan-kohteet db urakka-id)))

(defn lisaa-kanavalle-kohteita [db user kohteet]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-vesivaylat user)
  (q/lisaa-kanavalle-kohteet! db user kohteet)
  (hae-kanavat-ja-kohteet db user))

(defn liita-kohde-urakkaan! [db user {:keys [kohde-id urakka-id poistettu?]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-vesivaylat user)
  (q/liita-kohde-urakkaan! db user kohde-id urakka-id poistettu?))

(defn poista-kohde! [db user {:keys [kohde-id]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-vesivaylat user)
  (jdbc/with-db-transaction [db db]
                            (let [kohteella-urakoita? (not (empty? (q/hae-kohteen-urakat db kohde-id)))]
                              (if kohteella-urakoita?
                                {:virhe :kohteella-on-urakoita}
                                (q/merkitse-kohde-poistetuksi! db user kohde-id)))))

(defn hae-huoltokohteet [db user]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kanavat-kokonaishintaiset user)
  (q/hae-huoltokohteet db))

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
      :hae-urakan-kohteet
      (fn [user tiedot]
        (hae-urakan-kohteet db user tiedot))
      {:kysely-spec ::kan/hae-urakan-kohteet-kysely
       :vastaus-spec ::kan/hae-urakan-kohteet-vastaus})
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
    (julkaise-palvelu
      http
      :hae-huoltokohteet
      (fn [user]
        (hae-huoltokohteet db user))
      {:vastaus-spec ::huoltokohde/hae-huoltokohteet-kysely})

    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-kanavat-ja-kohteet
      :hae-urakan-kohteet
      :lisaa-kanavalle-kohteita
      :liita-kohde-urakkaan
      :poista-kohde)
    this))
