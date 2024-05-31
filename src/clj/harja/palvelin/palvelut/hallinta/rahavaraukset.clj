(ns harja.palvelin.palvelut.hallinta.rahavaraukset
  (:require [com.stuartsierra.component :as component]
            [clojure.spec.alpha :as s]
            [taoensso.timbre :as log]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.rahavaraukset :as q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]))

(s/def ::urakka-id #(and (not (nil? %)) (pos? %)))
(s/def ::rahavaraus-id #(and (not (nil? %)) (pos? %)))
(s/def ::tehtava-id #(and (not (nil? %)) (pos? %)))

(defn onko-urakka-olemassa?
  "Tarkistaa, että urakka löytyy Harjan tietokannasta"
  [db urakka-id]
  (when urakka-id
    (if-not (urakat-q/onko-olemassa? db urakka-id)
      (throw (SecurityException. (str "Urakkaa " urakka-id " ei ole olemassa.")))
      urakka-id)))

(defn onko-rahavaraus-olemassa?
  "Tarkistaa, että rahavaraus löytyy Harjan tietokannasta"
  [db rahavaraus-id]
  (when rahavaraus-id
    (if-not (q/onko-rahavaraus-olemassa? db {:rahavaraus-id rahavaraus-id})
      (throw (SecurityException. (str "Rahavarausta " rahavaraus-id " ei ole olemassa.")))
      rahavaraus-id)))

(defn onko-tehtava-olemassa?
  "Tarkistaa, että tehtävä löytyy Harjan tietokannasta"
  [db tehtava-id]
  (when tehtava-id
    (if-not (q/onko-tehtava-olemassa? db {:tehtava-id tehtava-id})
      (throw (SecurityException. (str "Tehtävää: " tehtava-id " ei löydy! ")))
      tehtava-id)))

(defn kuuluuko-tehtava-rahavaraukselle?
  "Tarkistaa, että tehtävä löytyy Harjan tietokannasta"
  [db rahavaraus-id tehtava-id]
  (when (and rahavaraus-id tehtava-id)
    (if-not (q/kuuluuko-tehtava-rahavaraukselle? db {:rahavaraus-id rahavaraus-id
                                                     :tehtava-id tehtava-id})
      (throw (SecurityException. (str "Tehtävää: " tehtava-id " ei löydy rahavaraukselta: " rahavaraus-id ".")))
      tehtava-id)))

(defn hae-rahavaraukset [db kayttaja]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-rahavaraukset kayttaja)
  {:rahavaraukset (q/hae-rahavaraukset db)
   :urakoiden-rahavaraukset (q/hae-urakoiden-rahavaraukset db)})

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

(defn paivita-urakan-rahavaraus [db kayttaja {:keys [id nimi urakkakohtainen-nimi urakka valittu?] :as tiedot}]
  (log/info "paivita-urakan-rahavaraus :: tiedot:" tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-rahavaraukset kayttaja urakka)
  (let [;validoidaan urakka ja rahavaraus
        urakka-valid? (and
                        (s/valid? ::urakka-id urakka)
                        (onko-urakka-olemassa? db urakka))
        rahavaraus-valid? (and
                            (s/valid? ::rahavaraus-id id)
                            (onko-rahavaraus-olemassa? db id))
        ;; Uuden rivin syötössä id on -1
        uusi-rivi (and (not (nil? id)) (= -1 id))
        ;; Haetaan urakan rahavaraus
        urakan-rahavaraus (first (q/hae-urakan-rahavaraus db {:urakka-id urakka
                                                              :rahavaraus-id id}))
        ;; Käyttöliittymän yksinkertaistamiseksi logiikkamuutos tänne bäackendiin.
        ;; Jos käyttäjä lisää urakkakohtainen-nimi arvon, niin se on päätös ottaa rahavaraus käyttöön.
        valittu? (cond
                   ;; Jos valittu? on nil, mutta urakkakohtainen-nimi on annettu, niin tulkitaan, että se haluttiin valita.
                   (and (nil? valittu?) (not (nil? urakkakohtainen-nimi)))
                   true
                   ;; Jos valittu on false ja urakkokohtainen nimi on annettu, niin sitä ei ole valittu, vaan käyttöliittymästä on poistettu valinta
                   (false? valittu?)
                   false
                   :else
                   valittu?)]
    ;; Muokataan rahavarausta tai lisätään se urakalle
    (if (and urakka-valid? rahavaraus-valid? (not uusi-rivi))

      (cond
        ;; Jos rahavaraus löytyy id:llä ja edelleen käyttäjä on valinnut sen, niin päivitetään.
        (and urakan-rahavaraus valittu?)
        (q/paivita-urakan-rahavaraus<! db {:urakkaid urakka
                                           :rahavarausid id
                                           :urakkakohtainen-nimi urakkakohtainen-nimi
                                           :kayttajaid (:id kayttaja)})
        ;; Jos rahavaraussta ei ole kannassa, mutta käyttäjä on valinnut sen, niin lisätään.
        (and (not urakan-rahavaraus) valittu?)
        (q/lisaa-urakan-rahavaraus<! db {:urakkaid urakka
                                         :rahavarausid id
                                         :urakkakohtainen-nimi urakkakohtainen-nimi
                                         :kayttaja (:id kayttaja)})
        ;; Muussa tapauksessa se poistetaan
        :else
        (q/poista-urakan-rahavaraus<! db {:urakkaid urakka
                                          :rahavarausid id}))
      ;; Lisätään kokonaan uusi rahavaraus, mutta ei merkitä sitä vielä käyttöön urakalle. Jos urakkakohtainen-nimi on syötetty, niin sitä ei hyödynnetä
      (q/lisaa-uusi-rahavaraus<! db {:nimi nimi
                                     :kayttajaid (:id kayttaja)}))

    ;; Palauta sen jälkeen tietokannasta tuoreet urakan rahavaraukset
    (hae-rahavaraukset db kayttaja)))

(defn tallenna-rahavarauksen-tehtava [db kayttaja {:keys [rahavaraus-id vanha-tehtava-id uusi-tehtava]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-rahavaraukset kayttaja)
  (log/debug "tallenna-rahavarauksen-tehtava :: rahavaraus-id:" rahavaraus-id
    "Vanha tehtva-id: " vanha-tehtava-id
    "Uusi tehtava: " uusi-tehtava)
  (let [rahavaraus-valid? (and
                            (s/valid? ::rahavaraus-id rahavaraus-id)
                            (onko-rahavaraus-olemassa? db rahavaraus-id))
        tehtava-valid? (and
                         (s/valid? ::tehtava-id (:id uusi-tehtava))
                         (onko-tehtava-olemassa? db (:id uusi-tehtava)))]
    (when (and rahavaraus-valid? tehtava-valid?)
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
                                                 :kayttaja (:id kayttaja)})))))

  ;; Palautetaan rahavaraukset tehtävineen, kun ne on nyt päivittyneet
  (hae-rahavaraukset-tehtavineen db kayttaja))

(defn poista-rahavarauksen-tehtava [db kayttaja {:keys [rahavaraus-id tehtava-id]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-rahavaraukset kayttaja)
  (log/debug "poista-rahavarauksen-tehtava :: rahavaraus-id: " rahavaraus-id "Tehtävä-id: " tehtava-id)
  (let [rahavaraus-valid? (and
                            (s/valid? ::rahavaraus-id rahavaraus-id)
                            (onko-rahavaraus-olemassa? db rahavaraus-id))
        tehtava-valid? (and
                         (s/valid? ::tehtava-id rahavaraus-id)
                         (kuuluuko-tehtava-rahavaraukselle? db rahavaraus-id tehtava-id))]
    (when (and rahavaraus-valid? tehtava-valid?)
      (q/poista-rahavaraukselta-tehtava! db {:rahavaraus-id rahavaraus-id
                                             :tehtava-id tehtava-id})))

  ;; Palautetaan rahavaraukset tehtävineen, kun ne on nyt päivittyneet
  (hae-rahavaraukset-tehtavineen db kayttaja))

(defn poista-rahavaraus [db kayttaja {:keys [id]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-rahavaraukset kayttaja)
  (log/debug "poista-rahavaraus :: rahavaraus-id: " id)
  (let [rahavaraus-valid? (and
                            (s/valid? ::rahavaraus-id id)
                            (onko-rahavaraus-olemassa? db id))
        ;; Tarkistetaan, onko kustannusarvioituo_tyo tai kulu_kohdistus tauluissa tällä rahavaraus id:llä merkintöjä
        ;; Jos on, niin poistoa ei voi tehdä
        onko-kaytossa? (q/onko-rahavaraus-kaytossa? db id)]
    (if (and rahavaraus-valid? (not onko-kaytossa?))
      (do ;; Poista rahavaraus kaikilta urakoilta
        (q/poista-rahavaraus-urakoilta! db id)
        ;; Poista rahavarauksen tehtävät
        (q/poista-rahavarauksen-tehtavat! db id)
        ;; Poista rahavaraus lopullisesti tietokannasta
        (q/poista-rahavaraus! db id)
        (log/info "Rahavaraus poistettu onnistuneesti."))
      (throw (SecurityException. (str "Rahavaraus on käytössä. Eikä sitä voi poistaa"))))

    ;; Välitä ui:lle muuttunut tilanne
    (hae-rahavaraukset db kayttaja)))

(defrecord RahavarauksetHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin :hae-rahavaraukset
      (fn [kayttaja _]
        (hae-rahavaraukset db kayttaja)))
    (julkaise-palvelu http-palvelin :hae-rahavaraukset-tehtavineen
      (fn [kayttaja _]
        (hae-rahavaraukset-tehtavineen db kayttaja)))
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
    (julkaise-palvelu http-palvelin :poista-rahavaraus
      (fn [kayttaja tiedot]
        (poista-rahavaraus db kayttaja tiedot)))
    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-rahavaraukset
      :hae-rahavaraukset-tehtavineen
      :hae-rahavaraukselle-mahdolliset-tehtavat
      :paivita-urakan-rahavaraus
      :tallenna-rahavarauksen-tehtava
      :poista-rahavarauksen-tehtava
      :poista-rahavaraus)
    this))
