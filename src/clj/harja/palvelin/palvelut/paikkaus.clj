(ns harja.palvelin.palvelut.paikkaus
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

            [harja.kyselyt.paikkaus :as q]
            [harja.kyselyt.materiaalit :as materiaalit-q]

            [harja.palvelin.palvelut.materiaalit :as materiaalipalvelut]
            [cheshire.core :as cheshire]
            [harja.domain.skeema :as skeema]
            [clj-time.format :as format]
            [clj-time.coerce :as coerce]))

(defn hae-urakan-paikkaustoteumat [db user {:keys [urakka-id sopimus-id]}]
  (log/debug "Haetaan urakan paikkaustoteumat. Urakka-id " urakka-id ", sopimus-id: " sopimus-id)
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [vastaus (into []
                      (comp
                        (map #(konv/string->avain % [:paatos]))
                        (map #(konv/string->avain % [:tila])))
                      (q/hae-urakan-paikkaustoteumat db urakka-id sopimus-id))]
    (log/debug "Paikkaustoteumat saatu: " (pr-str vastaus))
    vastaus))

(defn
  hae-urakan-paikkausilmoitus-paikkauskohteella [db user {:keys [urakka-id sopimus-id paikkauskohde-id]}]
  (log/debug "Haetaan urakan paikkausilmoitus, jonka paikkauskohde-id " paikkauskohde-id ". Urakka-id " urakka-id ", sopimus-id: " sopimus-id)
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [paikkausilmoitus (first (into []
                                      (comp (map #(konv/jsonb->clojuremap % :ilmoitustiedot))
                                            (map #(konv/string->avain % [:tila]))
                                            (map #(konv/string->avain % [:paatos])))
                                      (q/hae-urakan-paikkausilmoitus-paikkauskohteella db urakka-id sopimus-id paikkauskohde-id)))]
    (log/debug "Paikkausilmoitus saatu: " (pr-str paikkausilmoitus))
    (when paikkausilmoitus
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
        (assoc paikkausilmoitus :kommentit kommentit))
      (assoc paikkausilmoitus :kommentit []))))


(defn paivita-paikkausilmoitus [db user {:keys [id ilmoitustiedot aloituspvm valmispvm_kohde valmispvm_paikkaus paikkauskohde-id paatos perustelu kasittelyaika]}]
  (log/debug "Päivitetään vanha paikkaussilmoitus, jonka id: " paikkauskohde-id)
  (let [tila (if (= paatos :hyvaksytty)
               "lukittu"
               (if (and valmispvm_kohde valmispvm_paikkaus) "valmis" "aloitettu"))
        encoodattu-ilmoitustiedot (cheshire/encode ilmoitustiedot)]
    (log/debug "Encoodattu ilmoitustiedot: " (pr-str encoodattu-ilmoitustiedot))
    (log/debug "Asetetaan ilmoituksen tilaksi " tila)
    (q/paivita-paikkausilmoitus! db
                                 tila
                                 encoodattu-ilmoitustiedot
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
        encoodattu-ilmoitustiedot (cheshire/encode ilmoitustiedot)]
    (log/debug "Asetetaan ilmoituksen tilaksi " tila)
    (:id (q/luo-paikkausilmoitus<! db
                                   paikkauskohde-id
                                   tila
                                   encoodattu-ilmoitustiedot
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
  (roolit/vaadi-rooli-urakassa user roolit/toteumien-kirjaus urakka-id)
  #_(skeema/validoi pot/+paikkausilmoitus+ (:ilmoitustiedot paikkausilmoitus)) ; FIXME Skeemavalidointi puuttuu

  (jdbc/with-db-transaction [c db]
    (let [paikkausilmoitus-kannassa (hae-urakan-paikkausilmoitus-paikkauskohteella c user {:urakka-id        urakka-id
                                                                                           :sopimus-id       sopimus-id
                                                                                           :paikkauskohde-id (:paikkauskohde-id paikkausilmoitus)})]
      (log/debug "MINIPOT kannassa: " paikkausilmoitus-kannassa)

      ; Päätöstiedot lähetetään aina lomakkeen mukana, mutta vain urakanvalvoja saa muuttaa tehtyä päätöstä.
      ; Eli jos päätöstiedot ovat muuttuneet, vaadi rooli urakanvalvoja.
      (if (or
            (not (= (:paatos_tekninen_osa paikkausilmoitus-kannassa) (or (:paatos_tekninen_osa paikkausilmoitus) nil)))
            (not (= (:perustelu paikkausilmoitus-kannassa) (or (:perustelu paikkausilmoitus) nil))))
        (roolit/vaadi-rooli-urakassa user roolit/urakanvalvoja urakka-id))

      ; Käyttöliittymässä on estetty lukitun päällystysilmoituksen muokkaaminen, mutta tehdään silti tarkistus
      (log/debug "Tarkistetaan onko MINIPOT lukittu...")
      (if (= :lukittu (:tila paikkausilmoitus-kannassa))
        (do (log/debug "MINIPOT on lukittu, ei voi päivittää!")
            (throw (RuntimeException. "Paikkausilmoitus on lukittu, ei voi päivittää!")))
        (log/debug "MINIPOT ei ole lukittu, vaan " (:tila paikkausilmoitus-kannassa)))

      (let [paikkausilmoitus-id (luo-tai-paivita-paikkausilmoitus c user paikkausilmoitus paikkausilmoitus-kannassa)]

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

        (hae-urakan-paikkaustoteumat c user {:urakka-id  urakka-id
                                             :sopimus-id sopimus-id})))))


(defrecord Paikkaus []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)]
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
      :urakan-paikkaustoteumat
      :urakan-paikkausilmoitus-paikkauskohteella
      :tallenna-paikkaussilmoitus)
    this))