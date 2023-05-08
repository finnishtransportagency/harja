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
            [harja.domain.kanavat.kohteenosa :as kohteenosa]
            [harja.domain.kanavat.kanavan-huoltokohde :as huoltokohde]
            [harja.domain.urakka :as ur]))


(defn hae-kohdekokonaisuudet-ja-kohteet [db user]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-kanavat user)
  (q/hae-kokonaisuudet-ja-kohteet db user))

(defn hae-urakan-kohteet [db user tiedot]
  (let [urakka-id (::ur/id tiedot)]
    (assert urakka-id "Ei voida hakea urakan kohteita, urakka-id puuttuu")
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kanavat-kanavakohteet user)
    (q/hae-urakan-kohteet db user urakka-id)))

;; TODO: omaa
(defn hae-urakan-kohteet-mukaanlukien-poistetut
  [db user urakka-id]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kanavat-kanavakohteet user)
  (into []
        (q/hae-urakan-kohteet-mukaanlukien-poistetut db user urakka-id)))


(defn liita-kohteet-urakkaan! [db user {:keys [liitokset] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-kanavat user)
  (doseq [linkki (keys liitokset)]
    (let [[kohde-id urakka-id] linkki
          linkitetty? (get liitokset linkki)]
      (q/liita-kohde-urakkaan! db user kohde-id urakka-id (not linkitetty?))))
  (hae-kohdekokonaisuudet-ja-kohteet db user))

(defn poista-kohde! [db user {:keys [kohde-id]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-kanavat user)
  (jdbc/with-db-transaction [db db]
                            (let [kohteella-urakoita? (not (empty? (q/hae-kohteen-urakat db kohde-id)))]
                              (if kohteella-urakoita?
                                {:virhe :kohteella-on-urakoita}
                                (q/merkitse-kohde-poistetuksi! db user kohde-id)))))

(defn hae-huoltokohteet [db _]
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (q/hae-huoltokohteet db))

(defn tallenna-kohdekokonaisuudet [db user tiedot]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-kanavat user)
  (q/tallenna-kohdekokonaisuudet! db user tiedot)
  (q/paivita-jarjestys! db)
  (hae-kohdekokonaisuudet-ja-kohteet db user))

(defn hae-kohteenosat [db user]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-kanavat user)
  (q/hae-kohteenosat db))

(defn tallenna-kohde! [db user tiedot]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-kanavat user)
  (q/tallenna-kohde! db user tiedot)
  (q/paivita-jarjestys! db)
  (hae-kohdekokonaisuudet-ja-kohteet db user))

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
      :hae-kaikki-urakan-kohteet
      (fn [user urakka-id]
        (hae-urakan-kohteet-mukaanlukien-poistetut db user urakka-id)))
    (julkaise-palvelu
      http
      :tallenna-kohdekokonaisuudet
      (fn [user kokonaisuudet]
        (tallenna-kohdekokonaisuudet db user kokonaisuudet))
      {:kysely-spec ::kok/tallenna-kohdekokonaisuudet-kysely
       :vastaus-spec ::kok/tallenna-kohdekokonaisuudet-vastaus})
    (julkaise-palvelu
      http
      :tallenna-kohde
      (fn [user kokonaisuudet]
        (tallenna-kohde! db user kokonaisuudet))
      {:kysely-spec ::kohde/tallenna-kohde-kysely
       :vastaus-spec ::kohde/tallenna-kohde-vastaus})
    (julkaise-palvelu
      http
      :liita-kohteet-urakkaan
      (fn [user tiedot]
        (liita-kohteet-urakkaan! db user tiedot))
      {:vastaus-spec ::kok/hae-kohdekokonaisuudet-ja-kohteet-vastaus})
    (julkaise-palvelu
      http
      :hae-kanavien-huoltokohteet
      (fn [user]
        (hae-huoltokohteet db user))
      {:vastaus-spec ::huoltokohde/hae-huoltokohteet-vastaus})
    (julkaise-palvelu
      http
      :hae-kohteenosat
      (fn [user]
        (hae-kohteenosat db user))
      {:vastaus-spec ::kohteenosa/hae-kohteenosat-vastaus})

    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-kohdekokonaisuudet-ja-kohteet
      :hae-urakan-kohteet
      :lisaa-kohdekokonaisuudelle-kohteita
      :liita-kohteet-urakkaan
      :poista-kohde
      :hae-kanavien-huoltokohteet)
    this))
