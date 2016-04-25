(ns harja.palvelin.palvelut.paallystys-ja-paikkaus
  "Sisältää päällystys- ja paikkausurakoiden palvelut"
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.kommentit :as kommentit]
            [harja.domain.paallystysilmoitus :as paallystysilmoitus-domain]
            [harja.domain.paikkausilmoitus :as paikkausilmoitus-domain]

            [harja.kyselyt.paallystys-ja-paikkaus :as q]

            [cheshire.core :as cheshire]
            [harja.domain.skeema :as skeema]
            [harja.geo :as geo]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json]))

(defn tyot-tyyppi-string->avain [json avainpolku]
  (-> json
      (assoc-in avainpolku
                (when-let [tyot (some-> json (get-in avainpolku))]
                  (map #(assoc % :tyyppi (keyword (:tyyppi %))) tyot)))))

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

(defn hae-urakan-paallystystoteumat [db user {:keys [urakka-id sopimus-id]}]
  (log/debug "Haetaan urakan päällystystoteumat. Urakka-id " urakka-id ", sopimus-id: " sopimus-id)
  (oikeudet/lue oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (let [vastaus (into []
                      (comp
                        (map #(konv/string-polusta->keyword % [:paatos_taloudellinen_osa]))
                        (map #(konv/string-polusta->keyword % [:paatos_tekninen_osa]))
                        (map #(konv/string-polusta->keyword % [:tila]))
                        (map #(assoc % :kohdeosat
                                       (into []
                                             kohdeosa-xf
                                             (q/hae-urakan-yllapitokohteen-yllapitokohdeosat
                                               db urakka-id sopimus-id (:paallystyskohde_id %))))))
                      (q/hae-urakan-paallystystoteumat db urakka-id sopimus-id))]
    (log/debug "Päällystystoteumat saatu: " (pr-str vastaus))
    vastaus))

(defn hae-urakan-paallystysilmoitus-paallystyskohteella [db user {:keys [urakka-id sopimus-id paallystyskohde-id]}]
  (log/debug "Haetaan urakan päällystysilmoitus, jonka päällystyskohde-id " paallystyskohde-id ". Urakka-id " urakka-id ", sopimus-id: " sopimus-id)
  (oikeudet/lue oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (let [kohdetiedot (first (q/hae-urakan-yllapitokohde db urakka-id paallystyskohde-id))
        kokonaishinta (reduce + (keep kohdetiedot [:sopimuksen_mukaiset_tyot
                                                   :arvonvahennykset
                                                   :bitumi_indeksi
                                                   :kaasuindeksi]))
        paallystysilmoitus (first (into []
                                        (comp (map #(konv/jsonb->clojuremap % :ilmoitustiedot))
                                              (map #(tyot-tyyppi-string->avain % [:ilmoitustiedot :tyot]))
                                              (map #(konv/string-polusta->keyword % [:tila]))
                                              (map #(konv/string-polusta->keyword % [:paatos_tekninen_osa]))
                                              (map #(konv/string-polusta->keyword % [:paatos_taloudellinen_osa])))
                                        (q/hae-urakan-paallystysilmoitus-paallystyskohteella db urakka-id sopimus-id paallystyskohde-id)))]
    (log/debug "Päällystysilmoitus saatu: " (pr-str paallystysilmoitus))
    (if-not paallystysilmoitus
      ;; Uusi päällystysilmoitus
      ^{:uusi true}
      {:kohdenumero (:kohdenumero kohdetiedot)
       :kohdenimi (:nimi kohdetiedot)
       :paallystyskohde-id paallystyskohde-id
       :kokonaishinta kokonaishinta
       :kommentit []}

      (do
        (log/debug "Haetaan kommentit...")
        (log/info "KOHDETIEDOT: " kohdetiedot)
        (let [kommentit (into []
                              (comp (map konv/alaviiva->rakenne)
                                    (map (fn [{:keys [liite] :as kommentti}]
                                           (if (:id
                                                 liite)
                                             kommentti
                                             (dissoc kommentti :liite)))))
                              (q/hae-paallystysilmoituksen-kommentit db (:id paallystysilmoitus)))]
          (log/debug "Kommentit saatu: " kommentit)
          (assoc paallystysilmoitus
            :kokonaishinta kokonaishinta
            :paallystyskohde-id paallystyskohde-id
            :kommentit kommentit))))))

(defn paivita-paallystysilmoitus [db user {:keys [id ilmoitustiedot aloituspvm valmispvm_kohde valmispvm_paallystys takuupvm paallystyskohde-id paatos_tekninen_osa paatos_taloudellinen_osa perustelu_tekninen_osa perustelu_taloudellinen_osa kasittelyaika_tekninen_osa kasittelyaika_taloudellinen_osa]}]
  (log/debug "Päivitetään vanha päällystysilmoitus, jonka id: " paallystyskohde-id)
  (let [muutoshinta (paallystysilmoitus-domain/laske-muutokset-kokonaishintaan (:tyot ilmoitustiedot))
        tila (if (and (= paatos_tekninen_osa :hyvaksytty)
                      (= paatos_taloudellinen_osa :hyvaksytty))
               "lukittu"
               (if (and valmispvm_kohde valmispvm_paallystys) "valmis" "aloitettu"))
        encoodattu-ilmoitustiedot (cheshire/encode ilmoitustiedot)]
    (log/debug "Encoodattu ilmoitustiedot: " (pr-str encoodattu-ilmoitustiedot))
    (log/debug "Asetetaan ilmoituksen tilaksi " tila)
    (q/paivita-paallystysilmoitus! db
                                   tila
                                   encoodattu-ilmoitustiedot
                                   (konv/sql-date aloituspvm)
                                   (konv/sql-date valmispvm_kohde)
                                   (konv/sql-date valmispvm_paallystys)
                                   (konv/sql-date takuupvm)
                                   muutoshinta
                                   (if paatos_tekninen_osa (name paatos_tekninen_osa))
                                   (if paatos_taloudellinen_osa (name paatos_taloudellinen_osa))
                                   perustelu_tekninen_osa
                                   perustelu_taloudellinen_osa
                                   (konv/sql-date kasittelyaika_tekninen_osa)
                                   (konv/sql-date kasittelyaika_taloudellinen_osa)
                                   (:id user)
                                   paallystyskohde-id))
  id)

(defn luo-paallystysilmoitus [db user {:keys [ilmoitustiedot aloituspvm valmispvm_kohde valmispvm_paallystys takuupvm paallystyskohde-id]}]
  (log/debug "Luodaan uusi päällystysilmoitus.")
  (log/debug "valmispvm_kohde: " (pr-str valmispvm_kohde))
  (log/debug "valmispvm_paallystys: " (pr-str valmispvm_paallystys))
  (let [muutoshinta (paallystysilmoitus-domain/laske-muutokset-kokonaishintaan (:tyot ilmoitustiedot))
        tila (if (and valmispvm_kohde valmispvm_paallystys) "valmis" "aloitettu")
        encoodattu-ilmoitustiedot (cheshire/encode ilmoitustiedot)]
    (log/debug "Asetetaan ilmoituksen tilaksi " tila)
    (:id (q/luo-paallystysilmoitus<! db
                                     paallystyskohde-id
                                     tila
                                     encoodattu-ilmoitustiedot
                                     (konv/sql-date aloituspvm)
                                     (konv/sql-date valmispvm_kohde)
                                     (konv/sql-date valmispvm_paallystys)
                                     (konv/sql-date takuupvm)
                                     muutoshinta
                                     (:id user)))))

(defn luo-tai-paivita-paallystysilmoitus [db user lomakedata paallystysilmoitus-kannassa]
  (if paallystysilmoitus-kannassa
    (paivita-paallystysilmoitus db user lomakedata)
    (luo-paallystysilmoitus db user lomakedata)))

(defn tallenna-paallystysilmoitus [db user {:keys [urakka-id sopimus-id paallystysilmoitus]}]
  (log/debug "Käsitellään päällystysilmoitus: " paallystysilmoitus
             ". Urakka-id " urakka-id
             ", sopimus-id: " sopimus-id
             ", päällystyskohde-id:" (:paallystyskohde-id paallystysilmoitus))
  (oikeudet/kirjoita oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (skeema/validoi paallystysilmoitus-domain/+paallystysilmoitus+ (:ilmoitustiedot paallystysilmoitus))

  (jdbc/with-db-transaction [c db]
                            (let [paallystysilmoitus-kannassa (hae-urakan-paallystysilmoitus-paallystyskohteella
                                                                c user {:urakka-id urakka-id
                                                                        :sopimus-id sopimus-id
                                                                        :paallystyskohde-id (:paallystyskohde-id paallystysilmoitus)})
                                  paallystysilmoitus-kannassa (when-not (:uusi (meta paallystysilmoitus-kannassa))
                                                                ;; Tunnistetaan uuden tallentaminen
                                                                paallystysilmoitus-kannassa)]
                              (log/debug "POT kannassa: " paallystysilmoitus-kannassa)

                              ;; Päätöstiedot lähetetään aina lomakkeen mukana, mutta vain urakanvalvoja saa muuttaa tehtyä päätöstä.
                              ;; Eli jos päätöstiedot ovat muuttuneet, vaadi rooli urakanvalvoja.
                              (if (or
                                    (not (= (:paatos_tekninen_osa paallystysilmoitus-kannassa)
                                            (or (:paatos_tekninen_osa paallystysilmoitus) nil)))
                                    (not (= (:paatos_taloudellinen_osa paallystysilmoitus-kannassa)
                                            (or (:paatos_taloudellinen_osa paallystysilmoitus) nil))))
                                ;; FIXME Pitää varmistaa ja testata, ettei myöskään selitystä voi muuttaa ilman oikeuksia
                                (oikeudet/vaadi-oikeus "päätös" oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                                                       user urakka-id))

                              ;; Käyttöliittymässä on estetty lukitun päällystysilmoituksen muokkaaminen,
                              ;; mutta tehdään silti tarkistus
                              (log/debug "Tarkistetaan onko POT lukittu...")
                              (if (= :lukittu (:tila paallystysilmoitus-kannassa))
                                (do (log/debug "POT on lukittu, ei voi päivittää!")
                                    (throw (RuntimeException. "Päällystysilmoitus on lukittu, ei voi päivittää!")))
                                (log/debug "POT ei ole lukittu, vaan " (:tila paallystysilmoitus-kannassa)))

                              (let [paallystysilmoitus-id (luo-tai-paivita-paallystysilmoitus c user paallystysilmoitus
                                                                                              paallystysilmoitus-kannassa)]

                                ;; Luodaan uusi kommentti
                                (when-let [uusi-kommentti (:uusi-kommentti paallystysilmoitus)]
                                  (log/info "Uusi kommentti: " uusi-kommentti)
                                  (let [kommentti (kommentit/luo-kommentti<! c
                                                                             nil
                                                                             (:kommentti uusi-kommentti)
                                                                             nil
                                                                             (:id user))]
                                    ;; Liitä kommentti päällystysilmoitukseen
                                    (q/liita-kommentti<! c paallystysilmoitus-id (:id kommentti))))

                                (hae-urakan-paallystystoteumat c user {:urakka-id urakka-id
                                                                       :sopimus-id sopimus-id})))))

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

(defn hae-urakan-paikkaustoteumat [db user {:keys [urakka-id sopimus-id]}]
  (oikeudet/lue oikeudet/urakat-kohdeluettelo-paikkauskohteet user urakka-id)
  (let [vastaus (into []
                      (comp
                        (map #(konv/string-polusta->keyword % [:paatos]))
                        (map #(konv/string-polusta->keyword % [:tila]))
                        (map #(assoc % :kohdeosat
                                       (into []
                                             kohdeosa-xf
                                             (q/hae-urakan-yllapitokohteen-yllapitokohdeosat
                                               db urakka-id sopimus-id (:paikkauskohde_id %))))))
                      (q/hae-urakan-paikkaustoteumat db urakka-id sopimus-id))]
    (log/debug "Paikkaustoteumat saatu: " (pr-str (map :nimi vastaus)))
    vastaus))


(defn hae-urakan-paikkausilmoitus-paikkauskohteella [db user {:keys [urakka-id sopimus-id paikkauskohde-id]}]
  (log/debug "Haetaan urakan paikkausilmoitus, jonka paikkauskohde-id " paikkauskohde-id ". Urakka-id " urakka-id ", sopimus-id: " sopimus-id)
  (oikeudet/lue oikeudet/urakat-kohdeluettelo-paikkausilmoitukset user urakka-id)
  (let [kohdetiedot (first (q/hae-urakan-yllapitokohde db urakka-id paikkauskohde-id))
        _ (log/debug (pr-str kohdetiedot))
        kokonaishinta (reduce + (keep kohdetiedot [:sopimuksen_mukaiset_tyot
                                                   :arvonvahennykset
                                                   :bitumi_indeksi
                                                   :kaasuindeksi]))
        paikkausilmoitus (first (into []
                                      (comp (map #(konv/jsonb->clojuremap % :ilmoitustiedot))
                                            (map #(json/parsi-json-pvm-vectorista % [:ilmoitustiedot :toteumat] :takuupvm))
                                            (map #(konv/string-polusta->keyword % [:tila]))
                                            (map #(konv/string-polusta->keyword % [:paatos])))
                                      (q/hae-urakan-paikkausilmoitus-paikkauskohteella db urakka-id sopimus-id paikkauskohde-id)))]
    (log/debug "Paikkausilmoitus saatu: " (pr-str paikkausilmoitus))
    ;; Uusi paikkausilmoitus
    (if-not paikkausilmoitus
      ^{:uusi true}
      {:kohdenumero (:kohdenumero kohdetiedot)
       :kohdenimi (:nimi kohdetiedot)
       :paikkauskohde-id paikkauskohde-id
       :kokonaishinta kokonaishinta
       :kommentit []}
      (do
        (log/debug "Haetaan kommentit...")
        (let [kommentit (into []
                              (comp (map konv/alaviiva->rakenne)
                                    (map (fn [{:keys [liite] :as kommentti}]
                                           (if (:id
                                                 liite)
                                             kommentti
                                             (dissoc kommentti :liite)))))
                              (q/hae-paikkausilmoituksen-kommentit db (:id paikkausilmoitus)))]
          (log/debug "Kommentit saatu: " kommentit)
          (assoc paikkausilmoitus
            :kokonaishinta kokonaishinta
            :paikkauskohde-id paikkauskohde-id
            :kommentit kommentit))))))


(defn paivita-paikkausilmoitus [db user {:keys [id ilmoitustiedot aloituspvm valmispvm_kohde valmispvm_paikkaus paikkauskohde-id paatos perustelu kasittelyaika]}]
  (log/debug "Päivitetään vanha paikkaussilmoitus, jonka id: " paikkauskohde-id)
  (let [tila (if (= paatos :hyvaksytty)
               "lukittu"
               (if (and valmispvm_kohde valmispvm_paikkaus) "valmis" "aloitettu"))
        toteutunut-hinta (paikkausilmoitus-domain/laske-kokonaishinta (:toteumat ilmoitustiedot))
        encoodattu-ilmoitustiedot (cheshire/encode ilmoitustiedot)]
    (log/debug "Encoodattu ilmoitustiedot: " (pr-str encoodattu-ilmoitustiedot))
    (log/debug "Asetetaan ilmoituksen tilaksi " tila)
    (log/debug "Asetetaan ilmoituksen toteutuneeksi hinnaksi " toteutunut-hinta)
    (q/paivita-paikkausilmoitus! db
                                 tila
                                 encoodattu-ilmoitustiedot
                                 toteutunut-hinta
                                 (konv/sql-date aloituspvm)
                                 (konv/sql-date valmispvm_kohde)
                                 (konv/sql-date valmispvm_paikkaus)
                                 (if paatos (name paatos))
                                 perustelu
                                 (konv/sql-date kasittelyaika)
                                 (:id user)
                                 paikkauskohde-id))
  id)

(defn luo-paikkausilmoitus [db user {:keys [ilmoitustiedot aloituspvm valmispvm_kohde valmispvm_paikkaus paikkauskohde-id]}]
  (log/debug "Luodaan uusi paikkausilmoitus.")
  (log/debug "valmispvm_kohde: " (pr-str valmispvm_kohde))
  (log/debug "valmispvm_paikkaus: " (pr-str valmispvm_paikkaus))
  (let [tila (if (and valmispvm_kohde valmispvm_paikkaus) "valmis" "aloitettu")
        toteutunut-hinta (paikkausilmoitus-domain/laske-kokonaishinta (:toteumat ilmoitustiedot))
        encoodattu-ilmoitustiedot (cheshire/encode ilmoitustiedot)]
    (log/debug "Encoodattu ilmoitustiedot: " (pr-str encoodattu-ilmoitustiedot))
    (log/debug "Asetetaan ilmoituksen tilaksi " tila)
    (log/debug "Asetetaan ilmoituksen toteutuneeksi hinnaksi " toteutunut-hinta)
    (:id (q/luo-paikkausilmoitus<! db
                                   paikkauskohde-id
                                   tila
                                   encoodattu-ilmoitustiedot
                                   toteutunut-hinta
                                   (konv/sql-date aloituspvm)
                                   (konv/sql-date valmispvm_kohde)
                                   (konv/sql-date valmispvm_paikkaus)
                                   (:id user)))))

(defn luo-tai-paivita-paikkausilmoitus [db user lomakedata paikkausilmoitus-kannassa]
  (if paikkausilmoitus-kannassa
    (paivita-paikkausilmoitus db user lomakedata)
    (luo-paikkausilmoitus db user lomakedata)))

(defn tallenna-paikkausilmoitus [db user {:keys [urakka-id sopimus-id paikkausilmoitus]}]
  (log/debug "Käsitellään paikkausilmoitus: " paikkausilmoitus
             ". Urakka-id " urakka-id
             ", sopimus-id: " sopimus-id
             ", paikkauskohde-id:" (:paikkauskohde-id paikkausilmoitus))
  (oikeudet/kirjoita oikeudet/urakat-kohdeluettelo-paikkausilmoitukset user urakka-id)
  (skeema/validoi paikkausilmoitus-domain/+paikkausilmoitus+ (:ilmoitustiedot paikkausilmoitus))

  (jdbc/with-db-transaction [c db]
                            (let [paikkausilmoitus-kannassa (hae-urakan-paikkausilmoitus-paikkauskohteella
                                                              c user {:urakka-id urakka-id
                                                                      :sopimus-id sopimus-id
                                                                      :paikkauskohde-id (:paikkauskohde-id paikkausilmoitus)})
                                  paikkausilmoitus-kannassa (when-not (:uusi (meta paikkausilmoitus-kannassa))
                                                              ;; Tunnistetaan uuden tallentaminen
                                                              paikkausilmoitus-kannassa)]
                              (log/debug "MINIPOT kannassa: " paikkausilmoitus-kannassa)

                              ;; Päätöstiedot lähetetään aina lomakkeen mukana, mutta vain urakanvalvoja saa muuttaa tehtyä
                              ;; päätöstä. Eli jos päätöstiedot ovat muuttuneet, vaadi rooli urakanvalvoja.
                              (if (or
                                    (not (= (:paatos_tekninen_osa paikkausilmoitus-kannassa)
                                            (or (:paatos_tekninen_osa paikkausilmoitus) nil)))
                                    (not (= (:perustelu paikkausilmoitus-kannassa)
                                            (or (:perustelu paikkausilmoitus) nil))))
                                (oikeudet/vaadi-oikeus "päätös" oikeudet/urakat-kohdeluettelo-paikkausilmoitukset
                                                       user urakka-id))

                              ;; Käyttöliittymässä on estetty lukitun päällystysilmoituksen muokkaaminen,
                              ;; mutta tehdään silti tarkistus
                              (log/debug "Tarkistetaan onko MINIPOT lukittu...")
                              (if (= :lukittu (:tila paikkausilmoitus-kannassa))
                                (do (log/debug "MINIPOT on lukittu, ei voi päivittää!")
                                    (throw (RuntimeException. "Paikkausilmoitus on lukittu, ei voi päivittää!")))
                                (log/debug "MINIPOT ei ole lukittu, vaan " (:tila paikkausilmoitus-kannassa)))

                              (let [paikkausilmoitus-id (luo-tai-paivita-paikkausilmoitus c user paikkausilmoitus
                                                                                          paikkausilmoitus-kannassa)]

                                ;; Luodaan uusi kommentti
                                (when-let [uusi-kommentti (:uusi-kommentti paikkausilmoitus)]
                                  (log/info "Uusi kommentti: " uusi-kommentti)
                                  (let [kommentti (kommentit/luo-kommentti<! c
                                                                             nil
                                                                             (:kommentti uusi-kommentti)
                                                                             nil
                                                                             (:id user))]
                                    ;; Liitä kommentti paikkausilmoitukseen
                                    (q/liita-kommentti<! c paikkausilmoitus-id (:id kommentti))))

                                (hae-urakan-paikkaustoteumat c user {:urakka-id urakka-id
                                                                     :sopimus-id sopimus-id})))))

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
        (let [paallystysilmoitus (hae-urakan-paallystysilmoitus-paallystyskohteella
                                   db user {:urakka-id urakka-id
                                            :sopimus-id sopimus-id
                                            :yllapitokohde-id id})
              paikkausilmoitus (hae-urakan-paikkausilmoitus-paikkauskohteella
                                 db user {:urakka-id urakka-id
                                          :sopimus-id sopimus-id
                                          :yllapitokohde-id id})]
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


(defrecord PaallystysJaPaikkaus []
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
      (julkaise-palvelu http :urakan-paallystystoteumat
                        (fn [user tiedot]
                          (hae-urakan-paallystystoteumat db user tiedot)))
      (julkaise-palvelu http :urakan-paallystysilmoitus-paallystyskohteella
                        (fn [user tiedot]
                          (hae-urakan-paallystysilmoitus-paallystyskohteella db user tiedot)))
      (julkaise-palvelu http :tallenna-paallystysilmoitus
                        (fn [user tiedot]
                          (tallenna-paallystysilmoitus db user tiedot)))
      (julkaise-palvelu http :tallenna-paallystyskohteet
                        (fn [user tiedot]
                          (tallenna-yllapitokohteet db user tiedot)))
      (julkaise-palvelu http :tallenna-yllapitokohdeosat
                        (fn [user tiedot]
                          (tallenna-yllapitokohdeosat db user tiedot)))
      (julkaise-palvelu http :hae-aikataulut
                        (fn [user tiedot]
                          (hae-urakan-aikataulu db user tiedot)))
      (julkaise-palvelu http :tallenna-paallystyskohteiden-aikataulu
                        (fn [user tiedot]
                          (tallenna-yllapitokohteiden-aikataulu db user tiedot)))
      (julkaise-palvelu http :urakan-paikkaustoteumat
                        (fn [user tiedot]
                          (hae-urakan-paikkaustoteumat db user tiedot)))
      (julkaise-palvelu http :urakan-paikkausilmoitus-paikkauskohteella
                        (fn [user tiedot]
                          (hae-urakan-paikkausilmoitus-paikkauskohteella db user tiedot)))
      (julkaise-palvelu http :tallenna-paikkausilmoitus
                        (fn [user tiedot]
                          (tallenna-paikkausilmoitus db user tiedot)))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :urakan-yllapitokohteet
      :urakan-yllapitokohdeosat
      :urakan-paallystystoteumat
      :urakan-paallystysilmoitus-paallystyskohteella
      :tallenna-paallystysilmoitus
      :tallenna-paallystyskohteet
      :tallenna-yllapitokohdeosat
      :hae-aikataulut
      :tallenna-paallystyskohteiden-aikataulu
      :urakan-paikkaustoteumat
      :urakan-paikkausilmoitus-paikkauskohteella
      :tallenna-paikkaussilmoitus)
    this))
