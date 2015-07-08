(ns harja.palvelin.palvelut.paallystys
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [harja.domain.roolit :as roolit]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.kommentit :as kommentit]
            [harja.domain.paallystys.pot :as pot]

            [harja.kyselyt.paallystys :as q]
            [harja.kyselyt.materiaalit :as materiaalit-q]

            [harja.palvelin.palvelut.materiaalit :as materiaalipalvelut]
            [cheshire.core :as cheshire]
            [harja.domain.skeema :as skeema]
            [clj-time.format :as format]
            [clj-time.coerce :as coerce]))

(defn tyot-tyyppi-string->avain [json avainpolku]
  (-> json
      (assoc-in avainpolku
                (when-let [tyot (some-> json (get-in avainpolku))]
                  (map #(assoc % :tyyppi (keyword (:tyyppi %))) tyot)))))

(defn hae-urakan-paallystyskohteet [db user {:keys [urakka-id sopimus-id]}]
  (log/debug "Haetaan urakan päällystyskohteet. Urakka-id " urakka-id ", sopimus-id: " sopimus-id)
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [vastaus (into [] (q/hae-urakan-paallystyskohteet db urakka-id sopimus-id))]
    (log/debug "Päällystyskohteet saatu: " (pr-str vastaus))
    vastaus))

(defn hae-urakan-paallystyskohdeosat [db user {:keys [urakka-id sopimus-id paallystyskohde-id]}]
  (log/debug "Haetaan urakan päällystyskohdeosat. Urakka-id " urakka-id ", sopimus-id: " sopimus-id ", paallystyskohde-id: " paallystyskohde-id)
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [vastaus (into [] (q/hae-urakan-paallystyskohteen-paallystyskohdeosat db urakka-id sopimus-id paallystyskohde-id))]
    (log/debug "Päällystyskohdeosat saatu: " (pr-str vastaus))
    vastaus))

(defn hae-urakan-paallystystoteumat [db user {:keys [urakka-id sopimus-id]}]
  (log/debug "Haetaan urakan päällystystoteumat. Urakka-id " urakka-id ", sopimus-id: " sopimus-id)
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [vastaus (into []
                      (comp
                        (map #(konv/string->avain % [:paatos_taloudellinen_osa]))
                        (map #(konv/string->avain % [:paatos_tekninen_osa]))
                        (map #(konv/string->avain % [:tila])))
                      (q/hae-urakan-paallystystoteumat db urakka-id sopimus-id))]
    (log/debug "Päällystystoteumat saatu: " (pr-str vastaus))
    vastaus))

(defn
  hae-urakan-paallystysilmoitus-paallystyskohteella [db user {:keys [urakka-id sopimus-id paallystyskohde-id]}]
  (log/debug "Haetaan urakan päällystysilmoitus, jonka päällystyskohde-id " paallystyskohde-id ". Urakka-id " urakka-id ", sopimus-id: " sopimus-id)
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [paallystysilmoitus (first (into []
                                        (comp (map #(konv/jsonb->clojuremap % :ilmoitustiedot))
                                              (map #(tyot-tyyppi-string->avain % [:ilmoitustiedot :tyot]))
                                              (map #(konv/string->avain % [:tila]))
                                              (map #(konv/string->avain % [:paatos_tekninen_osa]))
                                              (map #(konv/string->avain % [:paatos_taloudellinen_osa])))
                                        (q/hae-urakan-paallystysilmoitus-paallystyskohteella db urakka-id sopimus-id paallystyskohde-id)))]
    (log/debug "Päällystysilmoitus saatu: " (pr-str paallystysilmoitus))
    (when paallystysilmoitus
      (log/debug "Haetaan kommentit...")
      (let [kommentit (into []
                            (comp (map konv/alaviiva->rakenne)
                                  (map (fn [{:keys [liite] :as kommentti}]
                                         (if (:id
                                               liite)
                                           kommentti
                                           (dissoc kommentti :liite)))))
                            (q/hae-paallystysilmoituksen-kommentit db (:id paallystysilmoitus)))]
        (log/debug "Kommentit saatu: " kommentit)
        (assoc paallystysilmoitus :kommentit kommentit)))))

(defn laske-muutoshinta [ilmoitustiedot]
  (reduce + (map
              (fn [rivi] (* (- (:toteutunut-maara rivi) (:tilattu-maara rivi)) (:yksikkohinta rivi)))
              (:tyot ilmoitustiedot))))

(defn paivita-paallystysilmoitus [db user {:keys [id ilmoitustiedot aloituspvm valmistumispvm takuupvm paallystyskohde-id paatos_tekninen_osa paatos_taloudellinen_osa perustelu kasittelyaika]}]
  (log/debug "Päivitetään vanha päällystysilmoitus, jonka id: " paallystyskohde-id)
  (let [muutoshinta (laske-muutoshinta ilmoitustiedot)
        tila (if (and (= paatos_tekninen_osa :hyvaksytty)
                      (= paatos_taloudellinen_osa :hyvaksytty))
               "lukittu"
               (if valmistumispvm "valmis" "aloitettu"))
        encoodattu-ilmoitustiedot (cheshire/encode ilmoitustiedot)]
    (log/debug "Encoodattu ilmoitustiedot: " (pr-str encoodattu-ilmoitustiedot))
    (log/debug "Asetetaan ilmoituksen tilaksi " tila)
    (q/paivita-paallystysilmoitus! db tila encoodattu-ilmoitustiedot
                                   (konv/sql-date aloituspvm)
                                   (konv/sql-date valmistumispvm)
                                   (konv/sql-date takuupvm)
                                   muutoshinta
                                   (if paatos_tekninen_osa (name paatos_tekninen_osa))
                                   (if paatos_taloudellinen_osa (name paatos_taloudellinen_osa))
                                   perustelu
                                   (konv/sql-date kasittelyaika)
                                   (:id user)
                                   paallystyskohde-id))
  id)

(defn luo-paallystysilmoitus [db user {:keys [ilmoitustiedot aloituspvm valmistumispvm takuupvm paallystyskohde-id]}]
  (log/debug "Luodaan uusi päällystysilmoitus.")
  (let [muutoshinta (laske-muutoshinta ilmoitustiedot)
        tila (if valmistumispvm "valmis" "aloitettu")
        encoodattu-ilmoitustiedot (cheshire/encode ilmoitustiedot)]
    (log/debug "Ilmoituksen valmistumispvm on " valmistumispvm ", joten asetetaan ilmoituksen tilaksi " tila)
    (:id (q/luo-paallystysilmoitus<! db
                                     paallystyskohde-id
                                     tila
                                     encoodattu-ilmoitustiedot
                                     (konv/sql-date aloituspvm)
                                     (konv/sql-date valmistumispvm)
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
  (roolit/vaadi-rooli-urakassa user roolit/toteumien-kirjaus urakka-id)
  (skeema/validoi pot/+paallystysilmoitus+ (:ilmoitustiedot paallystysilmoitus))

  (jdbc/with-db-transaction [c db]
    (let [paallystysilmoitus-kannassa (hae-urakan-paallystysilmoitus-paallystyskohteella c user {:urakka-id          urakka-id
                                                                                                 :sopimus-id         sopimus-id
                                                                                                 :paallystyskohde-id (:paallystyskohde-id paallystysilmoitus)})]
      (log/debug "POT kannassa: " paallystysilmoitus-kannassa)

      ; Päätöstiedot lähetetään aina lomakkeen mukana, mutta vain urakanvalvoja saa muuttaa tehtyä päätöstä.
      ; Eli jos päätöstiedot ovat muuttuneet, vaadi rooli urakanvalvoja.
      (if (or
            (not (= (:paatos_tekninen_osa paallystysilmoitus-kannassa) (or (:paatos_tekninen_osa paallystysilmoitus) nil)))
            (not (= (:paatos_taloudellinen_osa paallystysilmoitus-kannassa) (or (:paatos_taloudellinen_osa paallystysilmoitus) nil))))
        (roolit/vaadi-rooli-urakassa user roolit/urakanvalvoja urakka-id))

      ; Käyttöliittymässä on estetty lukitun päällystysilmoituksen muokkaaminen, mutta tehdään silti tarkistus
      (if (= :lukittu (:tila paallystysilmoitus-kannassa))
        (do (log/debug "POT on lukittu, ei voi päivittää!")
            (throw (RuntimeException. "Päällystysilmoitus on lukittu, ei voi päivittää!"))))

      (let [paallystysilmoitus-id (luo-tai-paivita-paallystysilmoitus c user paallystysilmoitus paallystysilmoitus-kannassa)]

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

        (hae-urakan-paallystystoteumat c user {:urakka-id  urakka-id
                                               :sopimus-id sopimus-id})))))

(defn luo-uusi-paallystyskohde [db user urakka-id sopimus-id {:keys [kohdenumero nimi sopimuksen_mukaiset_tyot lisatyot arvonvahennykset bitumi_indeksi kaasuindeksi poistettu]}]
  (log/debug "Luodaan uusi päällystyskohde")
  (when-not poistettu
    (q/luo-paallystyskohde<! db
                             urakka-id
                             sopimus-id
                             kohdenumero
                             nimi
                             (or sopimuksen_mukaiset_tyot 0)
                             (or lisatyot 0)
                             (or arvonvahennykset 0)
                             (or bitumi_indeksi 0)
                             (or kaasuindeksi 0))))

(defn paivita-paallystyskohde [db user {:keys [id kohdenumero nimi sopimuksen_mukaiset_tyot lisatyot arvonvahennykset bitumi_indeksi kaasuindeksi poistettu]}]
  (if poistettu
    (do (log/debug "Poistetaan päällystyskohde")
        (q/poista-paallystyskohde! db id))                  ; TODO Varmista että jos löytyy POT, ei saa poistaa.
    (do (log/debug "Päivitetään päällystyskohde")
        (q/paivita-paallystyskohde! db
                                    kohdenumero
                                    nimi
                                    (or sopimuksen_mukaiset_tyot 0)
                                    (or lisatyot 0)
                                    (or arvonvahennykset 0)
                                    (or bitumi_indeksi 0)
                                    (or kaasuindeksi 0)
                                    id))))

(defn tallenna-paallystyskohteet [db user {:keys [urakka-id sopimus-id kohteet]}]
  (jdbc/with-db-transaction [c db]
    (log/debug "Tallennetaan päällystyskohteet: " (pr-str kohteet))
    (doseq [kohde kohteet]
      (log/debug (str "Käsitellään saapunut päällystyskohde: " kohde))
      (if (and (:id kohde) (not (neg? (:id kohde))))
        (paivita-paallystyskohde c user kohde)
        (luo-uusi-paallystyskohde c user urakka-id sopimus-id kohde)))
    (let [paallystyskohteet (hae-urakan-paallystyskohteet c user {:urakka-id  urakka-id
                                                                  :sopimus-id sopimus-id})]
      (log/debug "Tallennus suoritettu. Tuoreet päällystyskohteet: " (pr-str paallystyskohteet))
      paallystyskohteet)))

(defn luo-uusi-paallystyskohdeosa [db user paallystyskohde-id {:keys [nimi tr_numero tr_alkuosa tr_alkuetaisyys tr_loppuosa tr_loppuetaisyys kvl nykyinen_paallyste toimenpide poistettu]}]
  (log/debug "Luodaan uusi päällystyskohdeosa, jonka päällystyskohde-id: " paallystyskohde-id)
  (when-not poistettu
    (q/luo-paallystyskohdeosa<! db
                                paallystyskohde-id
                                nimi
                                (or tr_numero 0)
                                (or tr_alkuosa 0)
                                (or tr_alkuetaisyys 0)
                                (or tr_loppuosa 0)
                                (or tr_loppuetaisyys 0)
                                (or kvl 0)
                                nykyinen_paallyste
                                toimenpide)))

(defn paivita-paallystyskohdeosa [db user {:keys [id nimi tr_numero tr_alkuosa tr_alkuetaisyys tr_loppuosa tr_loppuetaisyys kvl nykyinen_paallyste toimenpide poistettu]}]
  (if poistettu
    (do (log/debug "Poistetaan päällystyskohdeosa")
        (q/poista-paallystyskohdeosa! db id))
    (do (log/debug "Päivitetään päällystyskohdeosa")
        (q/paivita-paallystyskohdeosa! db
                                       nimi
                                       (or tr_numero 0)
                                       (or tr_alkuosa 0)
                                       (or tr_alkuetaisyys 0)
                                       (or tr_loppuosa 0)
                                       (or tr_loppuetaisyys 0)
                                       (or kvl 0)
                                       nykyinen_paallyste
                                       toimenpide
                                       id))))

(defn tallenna-paallystyskohdeosat [db user {:keys [urakka-id sopimus-id paallystyskohde-id osat]}]
  (jdbc/with-db-transaction [c db]
    (log/debug "Tallennetaan päällystyskohdeosat " (pr-str osat) ". Päällystyskohde-id: " paallystyskohde-id)
    (doseq [osa osat]
      (log/debug (str "Käsitellään saapunut päällystyskohdeosa: " osa))
      (if (and (:id osa) (not (neg? (:id osa))))
        (paivita-paallystyskohdeosa c user osa)
        (luo-uusi-paallystyskohdeosa c user paallystyskohde-id osa)))
    (let [paallystyskohdeosat (hae-urakan-paallystyskohdeosat c user {:urakka-id  urakka-id
                                                                    :sopimus-id sopimus-id
                                                                    :paallystyskohde-id paallystyskohde-id})]
      (log/debug "Tallennus suoritettu. Tuoreet päällystyskohdeosat: " (pr-str paallystyskohdeosat))
      paallystyskohdeosat)))

(defrecord Paallystys []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)]
      (julkaise-palvelu http :urakan-paallystyskohteet
                        (fn [user tiedot]
                          (hae-urakan-paallystyskohteet db user tiedot)))
      (julkaise-palvelu http :urakan-paallystyskohdeosat
                        (fn [user tiedot]
                          (hae-urakan-paallystyskohdeosat db user tiedot)))
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
                          (tallenna-paallystyskohteet db user tiedot)))
      (julkaise-palvelu http :tallenna-paallystyskohdeosat
                        (fn [user tiedot]
                          (tallenna-paallystyskohdeosat db user tiedot)))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :urakan-paallystyskohteet)
    this))