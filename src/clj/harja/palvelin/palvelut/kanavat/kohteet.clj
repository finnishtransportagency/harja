(ns harja.palvelin.palvelut.kanavat.kohteet
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.kanavat.kohteet :as q]
            
            [harja.domain.kanavat.kohdekokonaisuus :as kok]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.kanavat.kanavan-huoltokohde :as huoltokohde]
            [harja.domain.urakka :as ur]))


(defn hae-kohdekokonaisuudet-ja-kohteet [db user]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-vesivaylat user)
  (q/hae-kokonaisuudet-ja-kohteet db user))

(defn hae-urakan-kohteet [db user tiedot]
  (let [urakka-id (::ur/id tiedot)]
    (assert urakka-id "Ei voida hakea urakan kohteita, urakka-id puuttuu")
    ;; TODO Tämä on vähän hassu oikeustarkastus, koska pitää vaan varmistaa, että käyttäjällä
    ;; on oikeus nähdä, mitkä kohteet kuuluvat kyseiseen urakkaan. Tälle ei vaan ole suoraa omaa oikeutta
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kanavat-kokonaishintaiset user)
    (q/hae-urakan-kohteet db user urakka-id)))

(defn lisaa-kohdekokonaisuudelle-kohteita [db user kohteet]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-vesivaylat user)
  (q/lisaa-kokonaisuudelle-kohteet! db user kohteet)
  (hae-kohdekokonaisuudet-ja-kohteet db user))

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

(defrecord Kohteet []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelu
      http
      :hae-kohdekokonaisuudet-ja-kohteet
      (fn [user]
        (hae-kohdekokonaisuudet-ja-kohteet db user))
      {:vastaus-spec ::kok/hae-kohdekokonaisuudet-ja-kohteet-vastaus})
    (julkaise-palvelu
      http
      :hae-urakan-kohteet
      (fn [user tiedot]
        (hae-urakan-kohteet db user tiedot))
      {:kysely-spec ::kok/hae-urakan-kohteet-kysely
       :vastaus-spec ::kok/hae-urakan-kohteet-vastaus})
    (julkaise-palvelu
      http
      :lisaa-kohdekokonaisuudelle-kohteita
      (fn [user kohteet]
        (lisaa-kohdekokonaisuudelle-kohteita db user kohteet))
      {:kysely-spec ::kok/lisaa-kohdekokonaisuudelle-kohteita-kysely
       :vastaus-spec ::kok/lisaa-kohdekokonaisuudelle-kohteita-vastaus})
    (julkaise-palvelu
      http
      :liita-kohde-urakkaan
      (fn [user tiedot]
        (liita-kohde-urakkaan! db user tiedot))
      {:kysely-spec ::kok/liita-kohde-urakkaan-kysely})
    ;; Poistamista ei tueta UI:lla tällä hetkellä
    #_(julkaise-palvelu
      http
      :poista-kohde
      (fn [user tiedot]
        (poista-kohde! db user tiedot))
      {:kysely-spec ::kok/poista-kohde-kysely})
    (julkaise-palvelu
      http
      :hae-kanavien-huoltokohteet
      (fn [user]
        (hae-huoltokohteet db user))
      {:vastaus-spec ::huoltokohde/hae-huoltokohteet-kysely})

    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-kohdekokonaisuudet-ja-kohteet
      :hae-urakan-kohteet
      :lisaa-kohdekokonaisuudelle-kohteita
      :liita-kohde-urakkaan
      :poista-kohde
      :hae-kanavien-huoltokohteet)
    this))
