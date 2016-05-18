(ns harja.palvelin.palvelut.paallystys
  "Päällystyksen palvelut"
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.kommentit :as kommentit]
            [harja.domain.paallystysilmoitus :as paallystysilmoitus-domain]
            [harja.kyselyt.yllapitokohteet :as yllapitokohteet-q]
            [harja.kyselyt.paallystys :as q]
            [cheshire.core :as cheshire]
            [harja.palvelin.palvelut.yha :as yha]
            [harja.domain.skeema :as skeema]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.palvelut.yllapitokohteet :as yllapitokohteet]))

(defn tyot-tyyppi-string->avain [json avainpolku]
  (-> json
      (assoc-in avainpolku
                (when-let [tyot (some-> json (get-in avainpolku))]
                  (map #(assoc % :tyyppi (keyword (:tyyppi %))) tyot)))))

(defn hae-urakan-paallystysilmoitukset [db user {:keys [urakka-id sopimus-id]}]
  (log/debug "Haetaan urakan päällystystoteumat. Urakka-id " urakka-id ", sopimus-id: " sopimus-id)
  (oikeudet/lue oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (let [vastaus (into []
                      (comp
                        (map #(konv/string-polusta->keyword % [:paatos-taloudellinen-osa]))
                        (map #(konv/string-polusta->keyword % [:paatos-tekninen-osa]))
                        (map #(konv/string-polusta->keyword % [:tila]))
                        (map #(assoc % :kohdeosat
                                       (into []
                                             yllapitokohteet/kohdeosa-xf
                                             (yllapitokohteet-q/hae-urakan-yllapitokohteen-yllapitokohdeosat
                                               db urakka-id sopimus-id (:paallystyskohde-id %))))))
                      (q/hae-urakan-paallystysilmoitukset db urakka-id sopimus-id))]
    (log/debug "Päällystystoteumat saatu: " (pr-str vastaus))
    vastaus))

(defn hae-urakan-paallystysilmoitus-paallystyskohteella
  [db user {:keys [urakka-id sopimus-id paallystyskohde-id]}]
  (log/debug "Haetaan urakan päällystysilmoitus, jonka päällystyskohde-id " paallystyskohde-id ". Urakka-id " urakka-id ", sopimus-id: " sopimus-id)
  (oikeudet/lue oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (let [kohdetiedot (first (yllapitokohteet-q/hae-urakan-yllapitokohde db urakka-id paallystyskohde-id))
        kokonaishinta (reduce + (keep kohdetiedot [:sopimuksen-mukaiset-tyot
                                                   :arvonvahennykset
                                                   :bitumi-indeksi
                                                   :kaasuindeksi]))
        ;; Hae päällystysilmoitus kannasta
        paallystysilmoitus (into []
                                 (comp (map konv/alaviiva->rakenne)
                                       (map #(konv/jsonb->clojuremap % :ilmoitustiedot))
                                       (map #(tyot-tyyppi-string->avain % [:ilmoitustiedot :tyot]))
                                       (map #(konv/string-polusta->keyword % [:tila]))
                                       (map #(konv/string-polusta->keyword % [:paatos-tekninen-osa]))
                                       (map #(konv/string-polusta->keyword % [:paatos-taloudellinen-osa])))
                                 (q/hae-urakan-paallystysilmoitus-paallystyskohteella db paallystyskohde-id))
        ;; Yhdistä kohdeosat samalle riville
        paallystysilmoitus (first (konv/sarakkeet-vektoriin
                                    paallystysilmoitus
                                    {:kohdeosa :kohdeosat}
                                    :id))
        ;; Lisää kohdeosat ilmoitustietoihin
        paallystysilmoitus (-> paallystysilmoitus
                               (assoc-in
                                 [:ilmoitustiedot :osoitteet]
                                 (mapv
                                   (fn [kohdeosa]
                                     ;; Lisää kohdeosan tietoihin päällystystoimenpiteen tiedot
                                     (merge (clojure.set/rename-keys kohdeosa {:id :kohdeosa-id})
                                            (first (filter
                                                     (fn [paallystystoimenpide]
                                                       (= (:id kohdeosa) (:kohdeosa-id paallystystoimenpide)))
                                                     (get-in paallystysilmoitus [:ilmoitustiedot :osoitteet])))))
                                   (:kohdeosat paallystysilmoitus)))
                               (dissoc :kohdeosat))]
    (log/debug "Päällystysilmoitus kasattu: " (pr-str paallystysilmoitus))
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

(defn- paivita-paallystysilmoitus [db user
                                   {:keys [id ilmoitustiedot aloituspvm valmispvm-kohde
                                           valmispvm-paallystys takuupvm paallystyskohde-id
                                           paatos-tekninen-osa paatos-taloudellinen-osa perustelu-tekninen-osa
                                           perustelu-taloudellinen-osa kasittelyaika-tekninen-osa
                                           kasittelyaika-taloudellinen-osa]}]
  (log/debug "Päivitetään vanha päällystysilmoitus, jonka id: " paallystyskohde-id)
  (let [muutoshinta (paallystysilmoitus-domain/laske-muutokset-kokonaishintaan (:tyot ilmoitustiedot))
        tila (if (and (= paatos-tekninen-osa :hyvaksytty)
                      (= paatos-taloudellinen-osa :hyvaksytty))
               "lukittu"
               (if (and valmispvm-kohde valmispvm-paallystys) "valmis" "aloitettu"))
        encoodattu-ilmoitustiedot (cheshire/encode ilmoitustiedot)]
    (log/debug "Encoodattu ilmoitustiedot: " (pr-str encoodattu-ilmoitustiedot))
    (log/debug "Asetetaan ilmoituksen tilaksi " tila)
    (q/paivita-paallystysilmoitus! db
                                   tila
                                   encoodattu-ilmoitustiedot
                                   (konv/sql-date aloituspvm)
                                   (konv/sql-date valmispvm-kohde)
                                   (konv/sql-date valmispvm-paallystys)
                                   (konv/sql-date takuupvm)
                                   muutoshinta
                                   (if paatos-tekninen-osa (name paatos-tekninen-osa))
                                   (if paatos-taloudellinen-osa (name paatos-taloudellinen-osa))
                                   perustelu-tekninen-osa
                                   perustelu-taloudellinen-osa
                                   (konv/sql-date kasittelyaika-tekninen-osa)
                                   (konv/sql-date kasittelyaika-taloudellinen-osa)
                                   (:id user)
                                   paallystyskohde-id))
  id)

(defn- luo-paallystysilmoitus [db user
                               {:keys [ilmoitustiedot aloituspvm valmispvm-kohde valmispvm-paallystys
                                       takuupvm paallystyskohde-id]}]
  (log/debug "Luodaan uusi päällystysilmoitus.")
  (log/debug "valmispvm-kohde: " (pr-str valmispvm-kohde))
  (log/debug "valmispvm-paallystys: " (pr-str valmispvm-paallystys))
  (let [muutoshinta (paallystysilmoitus-domain/laske-muutokset-kokonaishintaan (:tyot ilmoitustiedot))
        tila (if (and valmispvm-kohde valmispvm-paallystys) "valmis" "aloitettu")
        encoodattu-ilmoitustiedot (cheshire/encode ilmoitustiedot)]
    (log/debug "Asetetaan ilmoituksen tilaksi " tila)
    (:id (q/luo-paallystysilmoitus<! db
                                     paallystyskohde-id
                                     tila
                                     encoodattu-ilmoitustiedot
                                     (konv/sql-date aloituspvm)
                                     (konv/sql-date valmispvm-kohde)
                                     (konv/sql-date valmispvm-paallystys)
                                     (konv/sql-date takuupvm)
                                     muutoshinta
                                     (:id user)))))

(defn- kasittele-paallystysilmoituksen-tierekisterikohteet
  "Ottaa päällystysilmoituksen ilmoitustiedot.
   Päivittää päällystyskohteen alikohteet niin, että niiden tiedot ovat samat kuin päällystysilmoituslomakkeessa.
   Palauttaa ilmoitustiedot, jossa päällystystoimenpiteiltä on riisuttu tieosoitteet."
  [db user urakka-id sopimus-id yllapitokohde-id ilmoitustiedot]
  (yllapitokohteet/tallenna-yllapitokohdeosat db
                                              user
                                              {:urakka-id urakka-id
                                               :sopimus-id sopimus-id
                                               :yllapitokohde-id yllapitokohde-id
                                               :osat (mapv
                                                       (fn [osoite]
                                                         {:id (:kohdeosa-id osoite)
                                                          :nimi (:nimi osoite)
                                                          :tr-numero (:tie osoite)
                                                          :tr-alkuosa (:aosa osoite)
                                                          :tr-alkuetaisyys (:aet osoite)
                                                          :tr-loppuosa (:losa osoite)
                                                          :tr-loppuetaisyys (:let osoite)
                                                          :poistettu (:poistettu osoite)
                                                          :sijainti (:sijainti osoite)})
                                                       (:osoitteet ilmoitustiedot))})
  (assoc ilmoitustiedot :osoitteet (mapv
                                     #(dissoc % :nimi :tie :aosa :aet :losa :let :pituus :poistettu)
                                     (:osoitteet ilmoitustiedot))))

(defn- luo-tai-paivita-paallystysilmoitus [db user urakka-id sopimus-id lomakedata paallystysilmoitus-kannassa]
  (let [lomakedata (assoc lomakedata
                     :ilmoitustiedot
                     (kasittele-paallystysilmoituksen-tierekisterikohteet db
                                                                          user
                                                                          urakka-id
                                                                          sopimus-id
                                                                          (:paallystyskohde-id lomakedata)
                                                                          (:ilmoitustiedot lomakedata)))]
    (if paallystysilmoitus-kannassa
      (paivita-paallystysilmoitus db user lomakedata)
      (luo-paallystysilmoitus db user lomakedata))))

(defn- tarkista-paallystysilmoituksen-tallentamisoikeudet [user urakka-id
                                                           paallystysilmoitus paallystysilmoitus-kannassa]
  (let [kasittelytiedot-muuttuneet? (fn [uudet-tiedot tiedot-kannassa]
                                      (let [vertailtavat
                                            [:paatos-tekninen-osa :paatos-taloudellinen-osa
                                             :perustelu-tekninen-osa :perustelu-taloudellinen-osa
                                             :kasittelyaika-tekninen-osa :kasittelyaika-taloudellinen-osa]]
                                        (not= ((apply juxt vertailtavat) uudet-tiedot)
                                              ((apply juxt vertailtavat) tiedot-kannassa))))]
    ;; Päätöstiedot lähetetään aina lomakkeen mukana, mutta vain urakanvalvoja saa muuttaa tehtyä päätöstä.
    ;; Eli jos päätöstiedot ovat muuttuneet, vaadi rooli urakanvalvoja.
    (if (kasittelytiedot-muuttuneet? paallystysilmoitus paallystysilmoitus-kannassa)
      (oikeudet/vaadi-oikeus "päätös" oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                             user urakka-id))

    ;; Käyttöliittymässä on estetty lukitun päällystysilmoituksen muokkaaminen,
    ;; mutta tehdään silti tarkistus
    (log/debug "Tarkistetaan onko POT lukittu...")
    (if (= :lukittu (:tila paallystysilmoitus-kannassa))
      (do (log/debug "POT on lukittu, ei voi päivittää!")
          (throw (RuntimeException. "Päällystysilmoitus oyhan lukittu, ei voi päivittää!")))
      (log/debug "POT ei ole lukittu, vaan " (:tila paallystysilmoitus-kannassa)))))

(defn tallenna-paallystysilmoitus [db user {:keys [urakka-id sopimus-id paallystysilmoitus]}]
  (log/debug "Käsitellään päällystysilmoitus: " paallystysilmoitus
             ". Urakka-id " urakka-id
             ", sopimus-id: " sopimus-id
             ", päällystyskohde-id:" (:paallystyskohde-id paallystysilmoitus))
  (oikeudet/kirjoita oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (skeema/validoi paallystysilmoitus-domain/+paallystysilmoitus+ (:ilmoitustiedot paallystysilmoitus))

  (jdbc/with-db-transaction [c db]
    (yha/lukitse-urakan-yha-sidonta db urakka-id)
    (let [paallystysilmoitus-kannassa (hae-urakan-paallystysilmoitus-paallystyskohteella
                                        c user {:urakka-id urakka-id
                                                :sopimus-id sopimus-id
                                                :paallystyskohde-id (:paallystyskohde-id paallystysilmoitus)})
          ;; Tunnistetaan uuden tallentaminen
          paallystysilmoitus-kannassa (when-not (:uusi (meta paallystysilmoitus-kannassa))
                                        paallystysilmoitus-kannassa)]
      (log/debug "POT kannassa: " paallystysilmoitus-kannassa)
      (tarkista-paallystysilmoituksen-tallentamisoikeudet user urakka-id
                                                          paallystysilmoitus paallystysilmoitus-kannassa)
      (let [paallystysilmoitus-id (luo-tai-paivita-paallystysilmoitus c
                                                                      user
                                                                      urakka-id
                                                                      sopimus-id
                                                                      paallystysilmoitus
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

        (hae-urakan-paallystysilmoitukset c user {:urakka-id urakka-id
                                                  :sopimus-id sopimus-id})))))

(defrecord Paallystys []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)]
      (julkaise-palvelu http :urakan-paallystysilmoitukset
                        (fn [user tiedot]
                          (hae-urakan-paallystysilmoitukset db user tiedot)))
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
      :urakan-paallystysilmoitukset
      :urakan-paallystysilmoitus-paallystyskohteella
      :tallenna-paallystysilmoitus
      :tallenna-paallystyskohteet)
    this))
