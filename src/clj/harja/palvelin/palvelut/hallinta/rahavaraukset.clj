(ns harja.palvelin.palvelut.hallinta.rahavaraukset
  (:require [com.stuartsierra.component :as component]
            [clojure.spec.alpha :as s]
            [harja.kyselyt.konversio :as konversio]
            [taoensso.timbre :as log]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.rahavaraukset :as q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]))

(s/def ::urakka-id #(and (string? %) (not (nil? (konversio/konvertoi->int %))) (pos? (konversio/konvertoi->int %))))
(s/def ::urakka-id #(and (string? %) (not (nil? (konversio/konvertoi->int %))) (pos? (konversio/konvertoi->int %))))

(defn onko-urakka-olemassa?
  "Tarkistaa, että urakka löytyy Harjan tietokannasta"
  [db urakka-id]
  (when urakka-id
    (when-not (urakat-q/onko-olemassa? db urakka-id)
      (throw (SecurityException. (str "Urakkaa " urakka-id " ei ole olemassa."))))))

(defn onko-rahavaraus-olemassa?
  "Tarkistaa, että rahavaraus löytyy Harjan tietokannasta"
  [db rahavaraus-id]
  (when rahavaraus-id
    (when-not (q/onko-rahavaraus-olemassa? db {:rahavaraus-id rahavaraus-id})
      (throw (SecurityException. (str "Rahavarausta " rahavaraus-id " ei ole olemassa."))))))

(defn hae-rahavaraukset [db kayttaja]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-rahavaraukset kayttaja)
  (q/hae-rahavaraukset db))

(defn hae-rahavaraukset-tehtavineen [db kayttaja]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-rahavaraukset kayttaja)
  (let [rahavaraukset-tehtavineen (q/hae-rahavaraukset-tehtavineen db)
        ;; Muokataan array_agg tehtävät vectoriksi
        rahavaraukset-tehtavineen (mapv (fn [rivi]
                                          (let [tehtavat (mapv
                                                           #(konv/pgobject->map % :id :long :nimi :string)
                                                           (konv/pgarray->vector (:tehtavat rivi)))
                                                rivi (assoc rivi :tehtavat tehtavat)]
                                            rivi))
                                    rahavaraukset-tehtavineen)]
    rahavaraukset-tehtavineen))

(defn hae-rahavaraukselle-mahdolliset-tehtavat [db kayttaja]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-rahavaraukset kayttaja)
  (q/hae-rahavaraukselle-mahdolliset-tehtavat db))

(defn hae-urakoiden-rahavaraukset [db kayttaja]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-rahavaraukset kayttaja)
  (q/hae-urakoiden-rahavaraukset db))

(defn paivita-urakan-rahavaraus [db kayttaja {:keys [urakka rahavaraus valittu?]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-rahavaraukset kayttaja urakka)
  (if valittu?
    (q/lisaa-urakan-rahavaraus<! db {:urakka urakka
                                     :rahavaraus rahavaraus
                                     :kayttaja (:id kayttaja)})
    (q/poista-urakan-rahavaraus<! db {:urakka urakka
                                      :rahavaraus rahavaraus}))
  (q/hae-urakoiden-rahavaraukset db))

(defn tallenna-rahavarauksen-tehtava [db kayttaja {:keys [rahavaraus-id vanha-tehtava-id uusi-tehtava]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-rahavaraukset kayttaja)
  (log/debug "tallenna-rahavarauksen-tehtava :: rahavaraus-id:" rahavaraus-id
    "Vanha tehtva-id: " vanha-tehtava-id
    " Uusi tehtava-id: " uusi-tehtava)
  ;; Jos vanha-tehtava-id on nolla, niin silloin lisätään kokonaan uusi tehtävä rahavaraukselle. Muuten
  ;; Vanha tehtävä poistetaan ja korvataan uudella
  (if (= 0 vanha-tehtava-id)
    ;; Lisätään kokonaan uusi tehtävä rahavaraukselle
    (q/lisaa-rahavaraukselle-tehtava<! db {:rahavaraus-id rahavaraus-id
                                           :tehtava-id (:id uusi-tehtava)
                                           :kayttaja (:id kayttaja)})
    ;; Poistetaan vanha ja lisätään uusi
    (do
      (q/poista-rahavaraukselta-tehtava! db {:rahavaraus-id rahavaraus-id
                                             :tehtava-id vanha-tehtava-id})
      (q/lisaa-rahavaraukselle-tehtava<! db {:rahavaraus-id rahavaraus-id
                                             :tehtava-id (:id uusi-tehtava)
                                             :kayttaja (:id kayttaja)})))

  ;; Palautetaan rahavaraukset tehtävineen, kun ne on nyt päivittyneet
  (hae-rahavaraukset-tehtavineen db kayttaja))

(defn poista-rahavarauksen-tehtava [db kayttaja {:keys [rahavaraus-id tehtava-id]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-rahavaraukset kayttaja)
  (log/debug "poista-rahavarauksen-tehtava :: rahavaraus-id: " rahavaraus-id "Tehtävä-id: " tehtava-id)
  (q/poista-rahavaraukselta-tehtava! db {:rahavaraus-id rahavaraus-id
                                         :tehtava-id tehtava-id})

  ;; Palautetaan rahavaraukset tehtävineen, kun ne on nyt päivittyneet
  (hae-rahavaraukset-tehtavineen db kayttaja))

(defrecord RahavarauksetHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin :hae-rahavaraukset
      (fn [kayttaja _]
        (hae-rahavaraukset db kayttaja)))
    (julkaise-palvelu http-palvelin :hae-rahavaraukset-tehtavineen
      (fn [kayttaja _]
        (hae-rahavaraukset-tehtavineen db kayttaja)))
    (julkaise-palvelu http-palvelin :hae-urakoiden-rahavaraukset
      (fn [kayttaja _]
        (hae-urakoiden-rahavaraukset db kayttaja)))
    (julkaise-palvelu http-palvelin :hae-rahavaraukselle-mahdolliset-tehtavat
      (fn [kayttaja _]
        (hae-rahavaraukselle-mahdolliset-tehtavat db kayttaja)))
    (julkaise-palvelu http-palvelin :paivita-urakan-rahavaraus
      (fn [kayttaja tiedot]
        (paivita-urakan-rahavaraus db kayttaja tiedot)))
    (julkaise-palvelu http-palvelin :tallenna-rahavarauksen-tehtava
      (fn [kayttaja tiedot]
        (tallenna-rahavarauksen-tehtava db kayttaja tiedot)))
    (julkaise-palvelu http-palvelin :poista-rahavarauksen-tehtava
      (fn [kayttaja tiedot]
        (poista-rahavarauksen-tehtava db kayttaja tiedot)))
    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-rahavaraukset
      :hae-rahavaraukset-tehtavineen
      :hae-urakoiden-rahavaraukset
      :hae-rahavaraukselle-mahdolliset-tehtavat
      :paivita-urakan-rahavaraus
      :tallenna-rahavarauksen-tehtava
      :poista-rahavarauksen-tehtava)
    this))
