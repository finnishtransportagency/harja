(ns harja.palvelin.palvelut.info
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.info :as q]
            [harja.domain.oikeudet :as oikeudet]))

(defn hae-koulutusvideot [db _]
  (oikeudet/ei-oikeustarkistusta!)
  (into []
    (q/hae-koulutusvideot db)))

(defn syotetaan-uusi-rivi?
  "Palauttaa true/false jos halutaan inserttaa uusi rivi"
  [poistetaan indeksi]
  (and
    (nil? poistetaan)
    (< indeksi 0)))

(defn paivita-koulutusvideot [db user {:keys [tiedot]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-koulutusvideot user)

  (doseq [x tiedot]
    (if (syotetaan-uusi-rivi? (:poistettu x) (:id x))
      ; Käyttäjä lisäsi uuden rivin
      (q/lisaa-video! db {:otsikko (:otsikko x)
                          :linkki (:linkki x)
                          :pvm (:pvm x)})
      ; Käyttäjä muokkaa rivejä
      (if (and (not= (:id x) -1) (:poistettu x))
        ; Poistettu true => Käyttäjä poistaa rivin
        (q/poista-video! db {:id (:id x)})
        ; Poistettu False => Käyttäjä päivittää tiedot
        (q/paivita-video! db {:otsikko (:otsikko x)
                              :linkki (:linkki x)
                              :pvm (:pvm x)
                              :id (:id x)}))))
  ; Palauta päivitetty lista
  (into []
    (q/hae-koulutusvideot db)))

(defrecord Info []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
      :hae-koulutusvideot
      (fn [user _]
        (hae-koulutusvideot (:db this) user)))
    (julkaise-palvelu (:http-palvelin this)
      :paivita-koulutusvideot
      (fn [user tiedot]
        (paivita-koulutusvideot (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
      :hae-koulutusvideot
      :paivita-koulutusvideot)
    this))
