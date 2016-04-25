(ns harja.palvelin.palvelut.yllapitokohteet
  "Ylläpitokohteiden palvelut"
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.yllapitokohteet :as q]
            [harja.geo :as geo]
            [harja.domain.oikeudet :as oikeudet]))

(def kohdeosa-xf (geo/muunna-pg-tulokset :sijainti))

(defn hae-urakan-yllapitokohteet [db user {:keys [urakka-id sopimus-id]}]
  (oikeudet/lue oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (jdbc/with-db-transaction [db db]
                            (let [vastaus (into []
                                                (comp (map #(konv/string-polusta->keyword % [:paallystysilmoitus_tila]))
                                                      (map #(konv/string-polusta->keyword % [:paikkausilmoitus_tila]))
                                                      (map #(assoc % :kohdeosat
                                                                     (into []
                                                                           kohdeosa-xf
                                                                           (q/hae-urakan-yllapitokohteen-yllapitokohdeosat
                                                                             db urakka-id sopimus-id (:id %))))))
                                                (q/hae-urakan-yllapitokohteet db urakka-id sopimus-id))]
                              (log/debug "Päällystyskohteet saatu: " (pr-str (map :nimi vastaus)))
                              vastaus)))

(defn hae-urakan-yllapitokohdeosat [db user {:keys [urakka-id sopimus-id paallystyskohde-id]}]
  (log/debug "Haetaan urakan ylläpitokohdeosat. Urakka-id " urakka-id ", sopimus-id: " sopimus-id ", paallystyskohde-id: " paallystyskohde-id)
  (oikeudet/lue oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (let [vastaus (into []
                      kohdeosa-xf
                      (q/hae-urakan-yllapitokohteen-yllapitokohdeosat db urakka-id sopimus-id paallystyskohde-id))]
    (log/debug "Päällystyskohdeosat saatu: " (pr-str vastaus))
    vastaus))

(defn hae-urakan-aikataulu [db user {:keys [urakka-id sopimus-id]}]
  (assert (and urakka-id sopimus-id) "anna urakka-id ja sopimus-id")
  (oikeudet/lue oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
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

(defn luo-uusi-yllapitokohde [db user urakka-id sopimus-id
                                {:keys [kohdenumero nimi sopimuksen_mukaiset_tyot muu_tyo
                                        arvonvahennykset bitumi_indeksi kaasuindeksi poistettu]}]
  (log/debug "Luodaan uusi ylläpitokohde")
  (when-not poistettu
    (q/luo-yllapitokohde<! db
                           urakka-id
                           sopimus-id
                           kohdenumero
                           nimi
                           (or sopimuksen_mukaiset_tyot 0)
                           (or muu_tyo false)
                           (or arvonvahennykset 0)
                           (or bitumi_indeksi 0)
                           (or kaasuindeksi 0))))

(defn paivita-yllapitokohde [db user urakka-id sopimus-id
                             {:keys [id kohdenumero nimi sopimuksen_mukaiset_tyot muu_tyo
                                     arvonvahennykset bitumi_indeksi kaasuindeksi poistettu]}]
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
                                  (or sopimuksen_mukaiset_tyot 0)
                                  (or muu_tyo false)
                                  (or arvonvahennykset 0)
                                  (or bitumi_indeksi 0)
                                  (or kaasuindeksi 0)
                                  id))))

(defn tallenna-yllapitokohteet [db user {:keys [urakka-id sopimus-id kohteet]}]
  (jdbc/with-db-transaction [c db]
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

(defn luo-uusi-yllapitokohdeosa [db user yllapitokohde-id {:keys [nimi tr_numero tr_alkuosa tr_alkuetaisyys tr_loppuosa tr_loppuetaisyys kvl nykyinen_paallyste toimenpide poistettu sijainti]}]
  (log/debug "Luodaan uusi ylläpitokohdeosa, jonka ylläpitokohde-id: " yllapitokohde-id)
  (when-not poistettu
    (q/luo-yllapitokohdeosa<! db
                                yllapitokohde-id
                                nimi
                                (or tr_numero 0)
                                (or tr_alkuosa 0)
                                (or tr_alkuetaisyys 0)
                                (or tr_loppuosa 0)
                                (or tr_loppuetaisyys 0)
                                (geo/geometry (geo/clj->pg sijainti))
                                (or kvl 0)
                                nykyinen_paallyste
                                toimenpide)))

(defn paivita-yllapitokohdeosa [db user {:keys [id nimi tr_numero tr_alkuosa tr_alkuetaisyys tr_loppuosa tr_loppuetaisyys kvl nykyinen_paallyste toimenpide poistettu sijainti]}]
  (if poistettu
    (do (log/debug "Poistetaan ylläpitokohdeosa")
        (q/poista-yllapitokohdeosa! db id))
    (do (log/debug "Päivitetään ylläpitokohdeosa")
        (q/paivita-yllapitokohdeosa! db
                                     nimi
                                     (or tr_numero 0)
                                     (or tr_alkuosa 0)
                                     (or tr_alkuetaisyys 0)
                                     (or tr_loppuosa 0)
                                     (or tr_loppuetaisyys 0)
                                     (when-not (empty? sijainti)
                                       (geo/geometry (geo/clj->pg sijainti)))
                                     (or kvl 0)
                                     nykyinen_paallyste
                                     toimenpide
                                     id))))

(defn tallenna-yllapitokohdeosat [db user {:keys [urakka-id sopimus-id yllapitokohde-id osat]}]
  (jdbc/with-db-transaction [c db]
                            (log/debug "Tallennetaan ylläpitokohdeosat " (pr-str osat) ". Ylläpitokohde-id: " yllapitokohde-id)
                            (doseq [osa osat]
                              (log/debug (str "Käsitellään saapunut ylläpitokohdeosa: " osa))
                              (if (and (:id osa) (not (neg? (:id osa))))
                                (paivita-yllapitokohdeosa c user osa)
                                (luo-uusi-yllapitokohdeosa c user yllapitokohde-id osa)))
                            (q/paivita-paallystys-tai-paikkausurakan-geometria c urakka-id)
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
                          (tallenna-yllapitokohteiden-aikataulu db user tiedot)))))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :urakan-yllapitokohteet
      :urakan-yllapitokohdeosat
      :tallenna-yllapitokohteet
      :tallenna-yllapitokohdeosat
      :hae-aikataulut
      :tallenna-yllapitokohteiden-aikataulu
    this)))