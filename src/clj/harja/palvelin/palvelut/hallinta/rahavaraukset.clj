(ns harja.palvelin.palvelut.hallinta.rahavaraukset
  (:require [com.stuartsierra.component :as component]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.rahavaraukset :as q]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]))

(defn hae-rahavaraukset [db kayttaja]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-rahavaraukset kayttaja)
  (let [rahavaraukset (q/hae-rahavaraukset db)
        _ (println "hae-rahavaraukset: " rahavaraukset)]
    rahavaraukset))

(defn hae-rahavaraukset-tehtavineen [db kayttaja]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-rahavaraukset kayttaja)
  (let [rahavaraukset-tehtavineen (q/hae-rahavaraukset-tehtavineen db)
        _ (println "rahavaraukset-tehtavineen1: " rahavaraukset-tehtavineen)
        ;; Muokataan array_agg tehtävät vectoriksi
        rahavaraukset-tehtavineen (mapv (fn [rivi]
                                          (let [_ (println "rivi1: " rivi)
                                                tehtavat (mapv
                                                       #(konv/pgobject->map % :id :long :nimi :string)
                                                       (konv/pgarray->vector (:tehtavat rivi)))
                                                rivi (assoc rivi :tehtavat tehtavat)
                                                _ (println "rivi2: " rivi)]
                                            rivi))
                                        rahavaraukset-tehtavineen)
        _ (println "rahavaraukset-tehtavineen2: " rahavaraukset-tehtavineen)]
    rahavaraukset-tehtavineen))

(defn hae-rahavaraukselle-mahdolliset-tehtavat [db kayttaja]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-rahavaraukset kayttaja)
  (let [tehtavat (q/hae-rahavaraukselle-mahdolliset-tehtavat db)
        _ (println "hae-rahavaraukselle-mahdolliset-tehtavat :: tehtavat:" tehtavat)]
    tehtavat))

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
    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-rahavaraukset
      :hae-rahavaraukset-tehtavineen
      :hae-urakoiden-rahavaraukset
      :hae-rahavaraukselle-mahdolliset-tehtavat
      :paivita-urakan-rahavaraus)
    this))
