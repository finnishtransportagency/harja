(ns harja.palvelin.palvelut.yllapitokohteet
  "Ylläpitokohteiden palvelut"
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.palvelut.yha :as yha]
            [harja.kyselyt.yllapitokohteet :as q]
            [harja.geo :as geo]
            [harja.domain.oikeudet :as oikeudet]))

(def kohdeosa-xf (geo/muunna-pg-tulokset :sijainti))

(defn hae-urakan-yllapitokohteet [db user {:keys [urakka-id sopimus-id]}]
  (oikeudet/lue oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (oikeudet/lue oikeudet/urakat-kohdeluettelo-paikkauskohteet user urakka-id)
  (log/debug "Haetaan urakan ylläpitokohteet.")
  (jdbc/with-db-transaction [db db]
    (let [vastaus (into []
                        (comp (map #(konv/string-polusta->keyword % [:paallystysilmoitus-tila]))
                              (map #(konv/string-polusta->keyword % [:paikkausilmoitus-tila]))
                              (map #(assoc % :kohdeosat
                                             (into []
                                                   kohdeosa-xf
                                                   (q/hae-urakan-yllapitokohteen-yllapitokohdeosat
                                                     db urakka-id sopimus-id (:id %))))))
                        (q/hae-urakan-yllapitokohteet db urakka-id sopimus-id))]
      (log/debug "Ylläpitokohteet saatu: " (count vastaus) " kpl")
      vastaus)))

(defn hae-urakan-yllapitokohdeosat [db user {:keys [urakka-id sopimus-id yllapitokohde-id]}]
  (log/debug "Haetaan urakan ylläpitokohdeosat. Urakka-id " urakka-id ", sopimus-id: " sopimus-id ", yllapitokohde-id: " yllapitokohde-id)
  (oikeudet/lue oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (oikeudet/lue oikeudet/urakat-kohdeluettelo-paikkauskohteet user urakka-id)
  (let [vastaus (into []
                      kohdeosa-xf
                      (q/hae-urakan-yllapitokohteen-yllapitokohdeosat db urakka-id sopimus-id yllapitokohde-id))]
    (log/debug "Ylläpitokohdeosat saatu: " (pr-str vastaus))
    vastaus))

(defn hae-urakan-aikataulu [db user {:keys [urakka-id sopimus-id]}]
  ;; FIXME Refactoroi alaviivat pois SQL-kyselyssä
  (assert (and urakka-id sopimus-id) "anna urakka-id ja sopimus-id")
  (oikeudet/lue oikeudet/urakat-aikataulu user urakka-id)
  (log/debug "Haetaan urakan aikataulutiedot.")
  (q/hae-urakan-aikataulu db urakka-id sopimus-id))

(defn tallenna-yllapitokohteiden-aikataulu [db user {:keys [urakka-id sopimus-id kohteet]}]
  (assert (and urakka-id sopimus-id kohteet) "anna urakka-id ja sopimus-id ja kohteet")
  (oikeudet/kirjoita oikeudet/urakat-aikataulu user urakka-id)
  (log/debug "Tallennetaan urakan " urakka-id " ylläpitokohteiden aikataulutiedot: " kohteet)
  (jdbc/with-db-transaction [db db]
    (doseq [rivi kohteet]
      (q/tallenna-yllapitokohteen-aikataulu!
        db
        (:aikataulu_paallystys_alku rivi)
        (:aikataulu_paallystys_loppu rivi)
        (:aikataulu_tiemerkinta_alku rivi)
        (:aikataulu_tiemerkinta_loppu rivi)
        (:aikataulu_kohde_valmis rivi)
        (:id user)
        (:id rivi)))
    (hae-urakan-aikataulu db user {:urakka-id urakka-id
                                   :sopimus-id sopimus-id})))

(defn- luo-uusi-yllapitokohde [db user urakka-id sopimus-id
                               {:keys [kohdenumero nimi
                                       tr-numero tr-alkuosa tr-alkuetaisyys
                                       tr-loppuosa tr-loppuetaisyys tr-ajorata tr-kaista
                                       tr-keskimaarainen-vuorokausiliikenne yllapitoluokka
                                       sopimuksen-mukaiset-tyot arvonvahennykset bitumi-indeksi
                                       kaasuindeksi poistettu nykyinen-paallyste
                                       keskimaarainen-vuorokausiliikenne]}]
  (log/debug "Luodaan uusi ylläpitokohde")
  (when-not poistettu
    (q/luo-yllapitokohde<! db
                           urakka-id
                           sopimus-id
                           kohdenumero
                           nimi
                           tr-numero
                           tr-alkuosa
                           tr-alkuetaisyys
                           tr-loppuosa
                           tr-loppuetaisyys
                           tr-ajorata
                           tr-kaista
                           keskimaarainen-vuorokausiliikenne
                           yllapitoluokka,
                           nykyinen-paallyste,
                           sopimuksen-mukaiset-tyot
                           arvonvahennykset
                           bitumi-indeksi
                           kaasuindeksi
                           nykyinen-paallyste)))

(defn- paivita-yllapitokohde [db user urakka-id sopimus-id
                              {:keys [id kohdenumero nimi
                                      tr-numero tr-alkuosa tr-alkuetaisyys
                                      tr-loppuosa tr-loppuetaisyys tr-ajorata tr-kaista
                                      tr-keskimaarainen-vuorokausiliikenne yllapitoluokka
                                      sopimuksen-mukaiset-tyot
                                      arvonvahennykset bitumi-indeksi kaasuindeksi
                                      nykyinen-paallyste keskimaarainen-vuorokausiliikenne poistettu]}]
  (if poistettu
    (do (log/debug "Tarkistetaan onko ylläpitokohteella ilmoituksia")
        (let [paallystysilmoitus (q/onko-olemassa-paallystysilmoitus? db id)
              paikkausilmoitus (q/onko-olemassa-paikkausilmioitus? db id)]
          (log/debug "Vastaus päällystysilmoitus: " paallystysilmoitus)
          (log/debug "Vastaus paikkausilmoitus: " paikkausilmoitus)
          (if (and (nil? paallystysilmoitus)
                   (nil? paikkausilmoitus))
            (do
              (log/debug "Ilmoituksia ei löytynyt, poistetaan ylläpitokohde")
              (q/poista-yllapitokohde! db id))
            (log/debug "Ei voi poistaa, ylläpitokohteelle on kirjattu ilmoituksia!"))))
    (do (log/debug "Päivitetään ylläpitokohde")
        (q/paivita-yllapitokohde! db
                                  kohdenumero
                                  nimi
                                  tr-numero
                                  tr-alkuosa
                                  tr-alkuetaisyys
                                  tr-loppuosa
                                  tr-loppuetaisyys
                                  tr-ajorata
                                  tr-kaista
                                  keskimaarainen-vuorokausiliikenne
                                  yllapitoluokka,
                                  nykyinen-paallyste,
                                  sopimuksen-mukaiset-tyot
                                  arvonvahennykset
                                  bitumi-indeksi
                                  kaasuindeksi
                                  id))))

(defn tallenna-yllapitokohteet [db user {:keys [urakka-id sopimus-id kohteet]}]
  (oikeudet/kirjoita oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (oikeudet/kirjoita oikeudet/urakat-kohdeluettelo-paikkauskohteet user urakka-id)
  (jdbc/with-db-transaction [c db]
    (yha/lukitse-urakan-yha-sidonta db urakka-id)
    (log/debug "Tallennetaan ylläpitokohteet: " (pr-str kohteet))
    (doseq [kohde kohteet]
      (log/debug (str "Käsitellään saapunut ylläpitokohde: " kohde))
      (if (and (:id kohde) (not (neg? (:id kohde))))
        (paivita-yllapitokohde c user urakka-id sopimus-id kohde)
        (luo-uusi-yllapitokohde c user urakka-id sopimus-id kohde)))
    (let [paallystyskohteet (hae-urakan-yllapitokohteet c user {:urakka-id urakka-id
                                                                :sopimus-id sopimus-id})]
      (log/debug "Tallennus suoritettu. Tuoreet ylläpitokohteet: " (pr-str paallystyskohteet))
      paallystyskohteet)))

(defn- luo-uusi-yllapitokohdeosa [db user yllapitokohde-id
                                  {:keys [nimi tr-numero tr-alkuosa tr-alkuetaisyys tr-loppuosa
                                          tr-loppuetaisyys tr-ajorata tr-kaista poistettu sijainti]}]
  (log/debug "Luodaan uusi ylläpitokohdeosa, jonka ylläpitokohde-id: " yllapitokohde-id)
  (when-not poistettu
    (q/luo-yllapitokohdeosa<! db
                              yllapitokohde-id
                              nimi
                              tr-numero
                              tr-alkuosa
                              tr-alkuetaisyys
                              tr-loppuosa
                              tr-loppuetaisyys
                              tr-ajorata
                              tr-kaista
                              (geo/geometry (geo/clj->pg sijainti)))))

(defn- paivita-yllapitokohdeosa [db user {:keys [id nimi tr-numero tr-alkuosa tr-alkuetaisyys
                                                 tr-loppuosa tr-loppuetaisyys tr-ajorata
                                                 tr-kaista poistettu sijainti]}]

  (if poistettu
    (do (log/debug "Poistetaan ylläpitokohdeosa")
        (q/poista-yllapitokohdeosa! db id))
    (do (log/debug "Päivitetään ylläpitokohdeosa")
        (q/paivita-yllapitokohdeosa! db
                                     nimi
                                     tr-numero
                                     tr-alkuosa
                                     tr-alkuetaisyys
                                     tr-loppuosa
                                     tr-loppuetaisyys
                                     tr-ajorata
                                     tr-kaista
                                     (when-not (empty? sijainti)
                                       (geo/geometry (geo/clj->pg sijainti)))
                                     id))))

(defn tallenna-yllapitokohdeosat [db user {:keys [urakka-id sopimus-id yllapitokohde-id osat]}]
  (oikeudet/kirjoita oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (oikeudet/kirjoita oikeudet/urakat-kohdeluettelo-paikkauskohteet user urakka-id)
  (jdbc/with-db-transaction [c db]
    (yha/lukitse-urakan-yha-sidonta db urakka-id)
    (log/debug "Tallennetaan ylläpitokohdeosat. Ylläpitokohde-id: " yllapitokohde-id)
    (doseq [osa osat]
      (log/debug (str "Käsitellään saapunut ylläpitokohdeosa"))
      (if (and (:id osa) (not (neg? (:id osa))))
        (paivita-yllapitokohdeosa c user osa)
        (luo-uusi-yllapitokohdeosa c user yllapitokohde-id osa)))
    (yha/paivita-yllapitourakan-geometriat c urakka-id)
    (let [yllapitokohdeosat (hae-urakan-yllapitokohdeosat c user {:urakka-id urakka-id
                                                                  :sopimus-id sopimus-id
                                                                  :yllapitokohde-id yllapitokohde-id})]
      (log/debug "Tallennus suoritettu. Tuoreet ylläpitokohdeosat: " (pr-str yllapitokohdeosat))
      yllapitokohdeosat)))


(defrecord Yllapitokohteet []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)]
      (julkaise-palvelu http :urakan-yllapitokohteet
                        (fn [user tiedot]
                          (hae-urakan-yllapitokohteet db user tiedot)))
      (julkaise-palvelu http :urakan-yllapitokohdeosat
                        (fn [user tiedot]
                          (hae-urakan-yllapitokohdeosat db user tiedot)))
      (julkaise-palvelu http :tallenna-yllapitokohteet
                        (fn [user tiedot]
                          (tallenna-yllapitokohteet db user tiedot)))
      (julkaise-palvelu http :tallenna-yllapitokohdeosat
                        (fn [user tiedot]
                          (tallenna-yllapitokohdeosat db user tiedot)))
      (julkaise-palvelu http :hae-aikataulut
                        (fn [user tiedot]
                          (hae-urakan-aikataulu db user tiedot)))
      (julkaise-palvelu http :tallenna-yllapitokohteiden-aikataulu
                        (fn [user tiedot]
                          (tallenna-yllapitokohteiden-aikataulu db user tiedot)))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :urakan-yllapitokohteet
      :urakan-yllapitokohdeosat
      :tallenna-yllapitokohteet
      :tallenna-yllapitokohdeosat
      :hae-aikataulut
      :tallenna-yllapitokohteiden-aikataulu)
    this))
