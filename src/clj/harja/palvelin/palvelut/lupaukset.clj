(ns harja.palvelin.palvelut.lupaukset
  "Palvelu välitavoitteiden hakemiseksi ja tallentamiseksi."
  (:require [com.stuartsierra.component :as component]
            [harja.id :refer [id-olemassa?]]
            [harja.kyselyt
             [lupaukset :as lupaukset-q]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.konversio :as konv]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]))


(defn- sitoutumistiedot [lupausrivit]
  {:pisteet (:sitoutuminen-pisteet (first lupausrivit))
   :id (:sitoutuminen-id (first lupausrivit))})

(defn- numero->kirjain [numero]
  (case numero
    1 "A"
    2 "B"
    3 "C"
    4 "D"
    5 "E"
    nil))

(defn- liita-lupausryhmien-max-pisteet [lupausrivit]
  (let [ryhmat (group-by :lupausryhma-id lupausrivit)]
    (into {}
          (map (fn [[avain rivit]]
                 {avain {:pisteet (reduce + 0 (map :pisteet rivit))
                         :kyselypisteet (reduce + 0 (map :kyselypisteet rivit))}})
               ryhmat))))

(defn- lupausryhman-max-pisteet [max-pisteet ryhma-id]
  (get max-pisteet ryhma-id))

(defn- lupausryhman-tiedot [lupausrivit]
  (let [ryhmat (map first (vals (group-by :lupausryhma-id lupausrivit)))
        max-pisteet (liita-lupausryhmien-max-pisteet lupausrivit)]
    (->> ryhmat
         (map #(select-keys % [:lupausryhma-id :lupausryhma-otsikko
                               :lupausryhma-jarjestys :lupausryhma-alkuvuosi]))
         (map #(set/rename-keys % {:lupausryhma-id :id
                                   :lupausryhma-otsikko :otsikko
                                   :lupausryhma-jarjestys :jarjestys
                                   :lupausryhma-alkuvuosi :alkuvuosi}))
         (map #(assoc % :kirjain (numero->kirjain (:jarjestys %))))
         (map #(merge % (lupausryhman-max-pisteet max-pisteet (:id %)))))))

(defn- hae-urakan-lupaustiedot [db user {:keys [urakka-id urakan-alkuvuosi] :as tiedot}]
  {:pre [(number? urakka-id) (number? urakan-alkuvuosi)]}
  (println "hae-urakan-lupaustiedot " tiedot)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-valitavoitteet user urakka-id)
  (let [[hk-alkupvm hk-loppupvm] (:valittu-hoitokausi tiedot)
        vastaus (into []
                      (map #(update % :kirjaus-kkt konv/pgarray->vector))
                      (lupaukset-q/hae-urakan-lupaustiedot db {:urakka urakka-id
                                                               :alkuvuosi urakan-alkuvuosi
                                                               :alkupvm hk-alkupvm
                                                               :loppupvm hk-loppupvm}))]
    {:lupaus-sitoutuminen (sitoutumistiedot vastaus)
     :lupausryhmat (lupausryhman-tiedot vastaus)
     :lupaukset vastaus}))

(defn vaadi-lupaus-kuuluu-urakkaan
  "Tarkistaa, että lupaus kuuluu annettuun urakkaan"
  [db urakka-id lupaus-id]
  (when (id-olemassa? lupaus-id)
    (let [lupauksen-urakka (:urakka-id (first (lupaukset-q/hae-lupauksen-urakkatieto db {:id lupaus-id})))]
      (when-not (= lupauksen-urakka urakka-id)
        (throw (SecurityException. (str "Lupaus " lupaus-id " ei kuulu valittuun urakkaan "
                                        urakka-id " vaan urakkaan " lupauksen-urakka)))))))

(defn- tallenna-urakan-luvatut-pisteet
  [db user tiedot]
  (println "tallenna-urakan-luvatut-pisteet tiedot " tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-valitavoitteet user (:urakka-id tiedot))
  (vaadi-lupaus-kuuluu-urakkaan db (:urakka-id tiedot) (:id tiedot))
  (jdbc/with-db-transaction [db db]
    (let [params {:id (:id tiedot)
                  :urakka-id (:urakka-id tiedot)
                  :pisteet (:pisteet tiedot)
                  :kayttaja (:id user)}
          vastaus (if (:id tiedot)
                    (lupaukset-q/paivita-urakan-luvatut-pisteet<! db params)
                    (lupaukset-q/lisaa-urakan-luvatut-pisteet<! db params))]
      (hae-urakan-lupaustiedot db user tiedot))))

(defn- paivita-lupaus-vastaus [db user-id {:keys [id vastaus lupaus-vaihtoehto-id]}]
  {:pre [db user-id id]}
  (assert (or (boolean? vastaus) (number? lupaus-vaihtoehto-id)))
  (assert (not (and vastaus lupaus-vaihtoehto-id)))
  (let [paivitetyt-rivit (lupaukset-q/paivita-lupaus-vastaus!
                           db
                           {:vastaus vastaus
                            :lupaus-vaihtoehto-id lupaus-vaihtoehto-id
                            :muokkaaja user-id
                            :id id})]
    (assert (pos? paivitetyt-rivit) (str "lupaus_vastaus id " id " ei löytynyt"))
    (first (lupaukset-q/hae-lupaus-vastaus db {:id id}))))

(defn- lisaa-lupaus-vastaus [db user-id {:keys [lupaus-id urakka-id kuukausi vuosi paatos vastaus lupaus-vaihtoehto-id]}]
  {:pre [db user-id lupaus-id urakka-id kuukausi vuosi (boolean? paatos)]}
  (assert (or (boolean? vastaus) (number? lupaus-vaihtoehto-id)))
  (lupaukset-q/lisaa-lupaus-vastaus<!
    db
    {:lupaus-id lupaus-id
     :urakka-id urakka-id
     :kuukausi kuukausi
     :vuosi vuosi
     :paatos paatos
     :vastaus vastaus
     :lupaus-vaihtoehto-id lupaus-vaihtoehto-id
     :luoja user-id}))

(defn- vastaa-lupaukseen
  [db user {:keys [id lupaus-id urakka-id _kuukausi _vuosi _paatos _vastaus _lupaus-vaihtoehto-id] :as tiedot}]
  {:pre [db user]}
  (println "vastaa-lupaukseen " tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-valitavoitteet user urakka-id)
  (vaadi-lupaus-kuuluu-urakkaan db urakka-id lupaus-id)
  ;; TODO: jos paatos = true, käyttäjän pitää olla tilaaja
  ;; TODO: validoi kirjaus-kkt ja paatos-kk
  ;; TODO: validoi vastaus / lupaus-vaihtoehto-id lupauksen tyypin mukaisesti
  ;; HUOM: paatos-arvoa ei voi muuttaa jälkeenpäin
  ;; - oletetaan, että samalle kuukaudelle tulee joko päätös tai kirjaus - ei molempia
  ;; - tämä täytyy olla siis conffattu oikein lupaus-taulussa
  ;; - TODO: lisää kommentti
  (jdbc/with-db-transaction [db db]
    (if id
      (paivita-lupaus-vastaus db (:id user) tiedot)
      (lisaa-lupaus-vastaus db (:id user) tiedot))))

(defrecord Lupaukset []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-urakan-lupaustiedot
                      (fn [user tiedot]
                        (hae-urakan-lupaustiedot (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
                      :tallenna-luvatut-pisteet
                      (fn [user tiedot]
                        (tallenna-urakan-luvatut-pisteet (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
                      :vastaa-lupaukseen
                      (fn [user tiedot]
                        (vastaa-lupaukseen (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-urakan-lupaustiedot
                     :tallenna-luvatut-pisteet)
    this))