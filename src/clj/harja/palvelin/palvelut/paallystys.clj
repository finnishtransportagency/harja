(ns harja.palvelin.palvelut.paallystys
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [harja.domain.roolit :as roolit]
            [clojure.java.jdbc :as jdbc]
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
                      (comp (map #(konv/string->avain % [:paatos]))
                            (map #(konv/string->avain % [:tila])))
                      (q/hae-urakan-paallystystoteumat db urakka-id sopimus-id))]
    (log/debug "Päällystystoteumat saatu: " (pr-str vastaus))
    vastaus))

(defn hae-urakan-paallystysilmoitus-paallystyskohteella [db user {:keys [urakka-id sopimus-id paallystyskohde-id]}]
  (log/debug "Haetaan urakan päällystysilmoitus, jonka päällystyskohde-id " paallystyskohde-id ". Urakka-id " urakka-id ", sopimus-id: " sopimus-id)
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [paallystysilmoitus (first (into []
                                        (comp (map #(konv/jsonb->clojuremap % :ilmoitustiedot))
                                              (map #(tyot-tyyppi-string->avain % [:ilmoitustiedot :tyot]))
                                              (map #(konv/string->avain % [:tila]))
                                              (map #(konv/string->avain % [:paatos])))
                                        (q/hae-urakan-paallystysilmoitus-paallystyskohteella db urakka-id sopimus-id paallystyskohde-id)))]
    (log/debug "Päällystysilmoitus saatu: " (pr-str paallystysilmoitus))
    (log/debug "Haetaan kommentit...")
    ; TODO
    paallystysilmoitus))

(defn laske-muutoshinta [lomakedata]
  (reduce + (map (fn [rivi] (* (- (:toteutunut-maara rivi) (:tilattu-maara rivi)) (:yksikkohinta rivi))) (:tyot lomakedata))))

(defn paivita-paallystysilmoitus [db user {:keys [ilmoitustiedot aloituspvm valmistumispvm takuupvm paallystyskohde-id paatos perustelu kasittelyaika]}]
  (log/debug "Päivitetään vanha päällystysilmoitus, jonka id: " paallystyskohde-id)
  (let [muutoshinta (laske-muutoshinta ilmoitustiedot)
        tila (if (= paatos :hyvaksytty)
               "lukittu"
               (if valmistumispvm "valmis" "aloitettu"))
        encoodattu-ilmoitustiedot (cheshire/encode ilmoitustiedot)]
    (log/debug "Asetetaan ilmoituksen tilaksi " tila)
    (q/paivita-paallystysilmoitus! db tila encoodattu-ilmoitustiedot
                                   (konv/sql-date aloituspvm)
                                   (konv/sql-date valmistumispvm)
                                   (konv/sql-date takuupvm)
                                   muutoshinta
                                   (if paatos (name paatos))
                                   perustelu
                                   (konv/sql-date kasittelyaika)
                                   (:id user)
                                   paallystyskohde-id)))

(defn luo-paallystysilmoitus [db user {:keys [ilmoitustiedot aloituspvm valmistumispvm takuupvm paallystyskohde-id]}]
  (log/debug "Luodaan uusi päällystysilmoitus.")
  (let [muutoshinta (laske-muutoshinta ilmoitustiedot)
        tila (if valmistumispvm "valmis" "aloitettu")
        encoodattu-ilmoitustiedot (cheshire/encode ilmoitustiedot)]
    (log/debug "Ilmoituksen valmistumispvm on " valmistumispvm ", joten asetetaan ilmoituksen tilaksi " tila)
    (q/luo-paallystysilmoitus<! db
                                paallystyskohde-id
                                tila
                                encoodattu-ilmoitustiedot
                                (konv/sql-date aloituspvm)
                                (konv/sql-date valmistumispvm)
                                (konv/sql-date takuupvm)
                                muutoshinta
                                (:id user))))

(defn tallenna-paallystysilmoitus [db user {:keys [urakka-id sopimus-id lomakedata]}]
  (log/debug "Käsitellään päällystysilmoitus: " lomakedata
             ". Urakka-id " urakka-id
             ", sopimus-id: " sopimus-id
             ", päällystyskohde-id:" (:paallystyskohde-id lomakedata))
  (roolit/vaadi-rooli-urakassa user roolit/toteumien-kirjaus urakka-id)
  ;(skeema/validoi pot/+paallystysilmoitus+ (:ilmoitustiedot lomakedata)) FIXME Validoi kantaan menevä JSON

  (jdbc/with-db-transaction [c db]
    (let [paallystysilmoitus-kannassa (hae-urakan-paallystysilmoitus-paallystyskohteella c user {:urakka-id          urakka-id
                                                                                                 :sopimus-id         sopimus-id
                                                                                                 :paallystyskohde-id (:paallystyskohde-id lomakedata)})]
      (log/debug "POT kannassa: " paallystysilmoitus-kannassa)

      ; Päätöstiedot lähetetään aina lomakkeen mukana, mutta vain urakanvalvoja saa muuttaa tehtyä päätöstä.
      ; Eli jos päätöstiedot ovat muuttuneet, vaadi rooli urakanvalvoja.
      (if (not (= (:paatos paallystysilmoitus-kannassa) (or (:paatos lomakedata) nil)))
        (roolit/vaadi-rooli-urakassa user roolit/urakanvalvoja urakka-id))


      (if paallystysilmoitus-kannassa
        (if (not (= :lukittu (:tila paallystysilmoitus-kannassa)))
          (paivita-paallystysilmoitus c user lomakedata)
          (log/debug "POT on lukittu, ei voi päivittää!"))
        (luo-paallystysilmoitus c user lomakedata))

      (hae-urakan-paallystystoteumat c user {:urakka-id  urakka-id
                                             :sopimus-id sopimus-id}))))

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
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :urakan-paallystyskohteet)
    this))
