(ns harja.palvelin.palvelut.tyomaapaivakirja
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.tyomaapaivakirja :as tyomaapaivakirja-kyselyt]
            [harja.kyselyt.konversio :as konversio]
            [harja.domain.oikeudet :as oikeudet]))

(defn kasittele-tyomaapaivakirjan-tila
  "Käsitellään päiväkirjan tila
   Palauttaa passatun päiväkirjan mutta lisätyllä :tila avaimella
   Myöhästyneeksi merkataan ne työmaapäiväkirjat, jotka eivät ole tuleet seuraavan arkipäivän klo 12.00 mennessä. 
   Eli perjantaina tehdyn työmaapäiväkirjan suhteen ajatellaan niin, että se ei ole myöhässä lauantaina klo 15.00 
   eikä sunnuntaina klo 15.00 vaan vasta maanantaina klo 12.01. Eli arkipäivä on se merkitsevä tekijä. Otetaan huomioon myös arkipyhät.

   Tilat: 'puuttuu' / 'ok' / 'myohassa'
   paivakirja: Päiväkirjan data
   paivamaara: Työmaapäiväkirjan päivämäärä
   luotu: Koska päiväkirja on lähetetty kyseiselle päivälle"
  [{:keys [luotu paivamaara] :as paivakirja}]
  (if (and luotu paivamaara)
    (let [;; Korjaa palvelimen ja tietokannan aika-eron (3 tuntia, 10800s)
          ;; Tietokannan 24:00 == Palvelimen 21:00
          ;; (tuotannossa ei varmaan tarvi..............??????¤#%&#d4te5yuy354erfdw3425tyrt53te4rdfbc) 
          luotu (pvm/dateksi (pvm/ajan-muokkaus luotu true 10800))
          paivamaara (pvm/dateksi (pvm/ajan-muokkaus paivamaara true 10800))

          seuraava-arkipaiva (pvm/seuraava-arkipaiva paivamaara)
          seur-akipaiva-klo-12 (pvm/dateksi (pvm/aikana seuraava-arkipaiva 12 0 0 0))
          aikaa-valissa (try
                          ;; Jäikö aikaa jäljelle lähettää päiväkirja
                          (pvm/aikavali-sekuntteina luotu seur-akipaiva-klo-12)
                          (catch Exception e
                            (if (= "The end instant must be greater than the start instant" (.getMessage e))
                              ;; Alkuaika isompi kuin seuraavan arkipäivän klo 12
                              ;; -> Päiväkirja lähetetty myöhässä -> Palautetaan vaan false
                              false
                              ;; Jos sattuu jokin muu virhe, heitetään se 
                              (throw (Exception. (.getMessage e))))))]
      (if aikaa-valissa
        ;; Aikaa jäi jäljelle, eli päiväkirja lähetetty OK
        (assoc paivakirja :tila "ok")
        ;; Aikaa ei jäänyt jäljelle, päiväkirja on myöhässä
        (assoc paivakirja :tila "myohassa")))
    ;; Else -> lähetetty aikaa ei ole -> päiväkirja puuttuu 
    (assoc paivakirja :tila "puuttuu")))

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
    (map #(kasittele-tyomaapaivakirjan-tila %) paivakirjat)))

(defn- hae-kommentit [db tiedot]
  (tyomaapaivakirja-kyselyt/hae-paivakirjan-kommentit db {:urakka_id (:urakka-id tiedot)
                                                          :tyomaapaivakirja_id (:tyomaapaivakirja_id tiedot)}))

(defn- hae-tyomaapaivakirjan-kommentit [db user tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/raportit-tyomaapaivakirja user (:urakka-id tiedot))
  (hae-kommentit db tiedot))

(defn- tallenna-kommentti [db user tiedot]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/raportit-kommentit user (:urakka-id tiedot))
  (tyomaapaivakirja-kyselyt/lisaa-kommentti<! db {:urakka_id (:urakka-id tiedot)
                                                  :tyomaapaivakirja_id (:tyomaapaivakirja_id tiedot)
                                                  :versio (:versio tiedot)
                                                  :kommentti (:kommentti tiedot)
                                                  :luoja (:id user)})
  (hae-kommentit db tiedot))

(defn- poista-tyomaapaivakirjan-kommentti [db user tiedot]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/raportit-kommentit user (:urakka-id tiedot))
  ;; Poistaa ainoastaan kommentin jos kommentti on poistajan itse tekemä
  (tyomaapaivakirja-kyselyt/poista-tyomaapaivakirjan-kommentti<! db {:id (:id tiedot)
                                                                     :kayttaja (:kayttaja tiedot)
                                                                     :tyomaapaivakirja_id (:tyomaapaivakirja_id tiedot)
                                                                     :muokkaaja (:id user)})
  (hae-kommentit db tiedot))

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
    
    (julkaise-palvelu http-palvelin
      :tyomaapaivakirja-poista-kommentti
      (fn [user tiedot]
        (poista-tyomaapaivakirjan-kommentti db user tiedot)))
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin 
      :tyomaapaivakirja-hae 
      :tyomaapaivakirja-tallenna-kommentti
      :tyomaapaivakirja-hae-kommentit
      :tyomaapaivakirja-poista-kommentti)
    this))
