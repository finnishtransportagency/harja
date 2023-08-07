(ns harja.palvelin.palvelut.tyomaapaivakirja
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.tyomaapaivakirja :as tyomaapaivakirja-kyselyt]
            [harja.kyselyt.konversio :as konversio]
            [harja.domain.oikeudet :as oikeudet]))

(defn hae-tyomaapaivakirjat [db user tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/raportit-tyomaapaivakirja user (:urakka-id tiedot))
  (let [_ (log/debug "hae-tyomaapaivakirjat :: tiedot" (pr-str tiedot))
        ;; Päiväkirjalistaukseen generoidaan rivit, vaikka päiväkirjoja ei olisi
        ;; Mutta tulevaisuuden päiville rivejä ei tarvitse generoida
        ;; Joten rajoitetaan loppupäivä tähän päivään
        loppuaika-sql (if (pvm/jalkeen? (:loppuaika tiedot) (pvm/nyt))
                        (konversio/sql-date (pvm/nyt))
                        (konversio/sql-date (:loppuaika tiedot)))
        paivakirjat (tyomaapaivakirja-kyselyt/hae-paivakirjalistaus db {:urakka-id (:urakka-id tiedot)
                                                                        :alkuaika (konversio/sql-date (:alkuaika tiedot))
                                                                         :loppuaika loppuaika-sql})]
    paivakirjat))

(defn- hae-kommentit [db tiedot]
  (tyomaapaivakirja-kyselyt/hae-paivakirjan-kommentit db {:urakka_id (:urakka-id tiedot)
                                                          :tyomaapaivakirja_id (:tyomaapaivakirja_id tiedot)
                                                          :versio (:versio tiedot)}))

(defn- hae-tyomaapaivakirjan-kommentit [db user tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/raportit-tyomaapaivakirja user (:urakka-id tiedot))
  (hae-kommentit db tiedot))

(defn- tallenna-kommentti [db user tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/raportit-tyomaapaivakirja user (:urakka-id tiedot))
  (let [_ (log/debug "tallenna-kommentti :: tiedot" (pr-str tiedot))
        _ (tyomaapaivakirja-kyselyt/lisaa-kommentti<! db {:urakka_id (:urakka-id tiedot)
                                                          :tyomaapaivakirja_id (:tyomaapaivakirja_id tiedot)
                                                          :versio (:versio tiedot)
                                                          :kommentti (:kommentti tiedot)
                                                          :tunnit 0
                                                          :luoja (:id user)})]
    (hae-kommentit db tiedot)))

(defrecord Tyomaapaivakirja []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    
    (julkaise-palvelu http-palvelin
      :tyomaapaivakirja-hae
      (fn [user tiedot]
        (hae-tyomaapaivakirjat db user tiedot)))
    
    (julkaise-palvelu http-palvelin
      :tyomaapaivakirja-tallenna-kommentti
      (fn [user tiedot]
        (tallenna-kommentti db user tiedot)))
    
    (julkaise-palvelu http-palvelin
      :tyomaapaivakirja-hae-kommentit
      (fn [user tiedot]
        (hae-tyomaapaivakirjan-kommentit db user tiedot)))
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin 
      :tyomaapaivakirja-hae 
      :tyomaapaivakirja-tallenna-kommentti
      :tyomaapaivakirja-hae-kommentit)
    this))
