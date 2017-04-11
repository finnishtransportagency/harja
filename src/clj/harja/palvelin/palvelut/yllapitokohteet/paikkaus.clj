(ns harja.palvelin.palvelut.yllapitokohteet.paikkaus
  "Paikkauksen palvelut"
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.kommentit :as kommentit]
            [harja.domain.paikkausilmoitus :as paikkausilmoitus-domain]
            [harja.kyselyt.paikkaus :as q]
            [harja.palvelin.palvelut.yha-apurit :as yha-apurit]
            [harja.kyselyt.yllapitokohteet :as yllapitokohteet-q]
            [harja.kyselyt.paallystys :as paallystys-q]
            [cheshire.core :as cheshire]
            [harja.domain.skeema :as skeema]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]))

(defn hae-urakan-paikkausilmoitukset [db user {:keys [urakka-id sopimus-id vuosi]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paikkausilmoitukset user urakka-id)
  (let [vastaus (into []
                      (comp
                        (map #(konv/string-poluista->keyword % [[:tila] [:paatos]])))
                      (q/hae-urakan-paikkausilmoitukset db urakka-id sopimus-id vuosi))
        vastaus (yllapitokohteet-q/liita-kohdeosat-kohteisiin
                  db vastaus :paikkauskohde-id)]
    (log/debug "Paikkaustoteumat saatu: " (pr-str (map :nimi vastaus)))
    vastaus))


(defn hae-urakan-paikkausilmoitus-paikkauskohteella [db user {:keys [urakka-id sopimus-id paikkauskohde-id]}]
  (log/debug "Haetaan urakan paikkausilmoitus, jonka paikkauskohde-id " paikkauskohde-id ". Urakka-id " urakka-id ", sopimus-id: " sopimus-id)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paikkausilmoitukset user urakka-id)
  (let [;; FIXME Voisi refactoroida käyttämään pitkälti samaa mallia kuin mitä päällystyspuolella on tehty.
        kohdetiedot (first (q/hae-urakan-yllapitokohde db urakka-id paikkauskohde-id))
        _ (log/debug (pr-str kohdetiedot))
        kokonaishinta (reduce + (keep kohdetiedot [:sopimuksen-mukaiset-tyot
                                                   :arvonvahennykset
                                                   :bitumi-indeksi
                                                   :kaasuindeksi]))
        paikkausilmoitus (first (into []
                                      (comp (map #(konv/jsonb->clojuremap % :ilmoitustiedot))
                                            (map #(json/parsi-json-pvm-vectorista % [:ilmoitustiedot :toteumat] :takuupvm))
                                            (map #(konv/string-poluista->keyword % [[:tila] [:paatos]])))
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

(defn- paivita-paikkausilmoitus [db user {:keys [id ilmoitustiedot aloituspvm valmispvm-kohde valmispvm-paikkaus paikkauskohde-id paatos perustelu kasittelyaika]}]
  (log/debug "Päivitetään vanha paikkaussilmoitus, jonka id: " paikkauskohde-id)
  (let [tila (if (= paatos :hyvaksytty)
               "lukittu"
               (if (and valmispvm-kohde valmispvm-paikkaus) "valmis" "aloitettu"))
        toteutunut-hinta (paikkausilmoitus-domain/laske-kokonaishinta (:toteumat ilmoitustiedot))
        encoodattu-ilmoitustiedot (cheshire/encode ilmoitustiedot)]
    (log/debug "Encoodattu ilmoitustiedot: " (pr-str encoodattu-ilmoitustiedot))
    (log/debug "Asetetaan ilmoituksen tilaksi " tila)
    (log/debug "Asetetaan ilmoituksen toteutuneeksi hinnaksi " toteutunut-hinta)
    (q/paivita-paikkausilmoitus! db
                                 tila
                                 encoodattu-ilmoitustiedot
                                 (konv/sql-date aloituspvm)
                                 (konv/sql-date valmispvm-kohde)
                                 (konv/sql-date valmispvm-paikkaus)
                                 (if paatos (name paatos))
                                 perustelu
                                 (konv/sql-date kasittelyaika)
                                 (:id user)
                                 paikkauskohde-id))
  id)

(defn- luo-paikkausilmoitus [db user {:keys [ilmoitustiedot aloituspvm valmispvm-kohde valmispvm-paikkaus paikkauskohde-id]}]
  (log/debug "Luodaan uusi paikkausilmoitus: " ilmoitustiedot)
  (jdbc/with-db-transaction [db db]
    (let [tila (if (and valmispvm-kohde valmispvm-paikkaus) "valmis" "aloitettu")
          toteutunut-hinta (paikkausilmoitus-domain/laske-kokonaishinta (:toteumat ilmoitustiedot))
          encoodattu-ilmoitustiedot (cheshire/encode ilmoitustiedot)
          paikkauskohteen-id (:id (q/luo-paikkausilmoitus<! db
                                                            paikkauskohde-id
                                                            tila
                                                            encoodattu-ilmoitustiedot
                                                            (konv/sql-date aloituspvm)
                                                            (konv/sql-date valmispvm-kohde)
                                                            (konv/sql-date valmispvm-paikkaus)
                                                            (:id user)))]
      (log/debug "Asetetaan ilmoituksen toteutuneeksi hinnaksi " toteutunut-hinta)
      (when paikkauskohteen-id
        (q/paivita-paikkauskohteen-toteutunut-hinta! db {:toteutunut_hinta toteutunut-hinta
                                                         :id paikkauskohde-id})
        paikkauskohteen-id))))

(defn- luo-tai-paivita-paikkausilmoitus [db user lomakedata paikkausilmoitus-kannassa]
  (if paikkausilmoitus-kannassa
    (paivita-paikkausilmoitus db user lomakedata)
    (luo-paikkausilmoitus db user lomakedata)))

(defn- tarkista-paikkausilmoituksen-tallentamisoikeudet [user urakka-id
                                                         uusi-paikkausilmoitus
                                                         paikkausilmoitus-kannassa]
  (let [kasittelytiedot-muuttuneet?
        (fn [uudet-tiedot tiedot-kannassa]
          (let [vertailtavat
                [:paatos :perustelu :kasittelyaika]]
            (not= (select-keys uudet-tiedot vertailtavat)
                  (select-keys tiedot-kannassa vertailtavat))))]
    ;; Päätöstiedot lähetetään aina lomakkeen mukana, mutta vain urakanvalvoja saa muuttaa tehtyä päätöstä.
    ;; Eli jos päätöstiedot ovat muuttuneet, vaadi rooli urakanvalvoja.
    (if (kasittelytiedot-muuttuneet? uusi-paikkausilmoitus paikkausilmoitus-kannassa)
      (oikeudet/vaadi-oikeus "päätös" oikeudet/urakat-kohdeluettelo-paikkausilmoitukset
                             user urakka-id))

    ;; Käyttöliittymässä on estetty lukitun paikkausilmoituksen muokkaaminen,
    ;; mutta tehdään silti tarkistus
    (log/debug "Tarkistetaan onko MINIPOT lukittu...")
    (if (= :lukittu (:tila paikkausilmoitus-kannassa))
      (do (log/debug "MINIPOT on lukittu, ei voi päivittää!")
          (throw (RuntimeException. "Paikkausilmoitus on lukittu, ei voi päivittää!")))
      (log/debug "MINIPOT ei ole lukittu, vaan " (:tila paikkausilmoitus-kannassa)))))

(defn tallenna-paikkausilmoitus [db user {:keys [urakka-id sopimus-id paikkausilmoitus]}]
  (log/debug "Tallennetaan paikkausilmoitus: " paikkausilmoitus
             ". Urakka-id " urakka-id
             ", sopimus-id: " sopimus-id
             ", paikkauskohde-id:" (:paikkauskohde-id paikkausilmoitus))
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paikkausilmoitukset user urakka-id)
  (skeema/validoi paikkausilmoitus-domain/+paikkausilmoitus+ (:ilmoitustiedot paikkausilmoitus))

  (jdbc/with-db-transaction [c db]
    (yha-apurit/lukitse-urakan-yha-sidonta db urakka-id)
    (let [paikkausilmoitus-kannassa (hae-urakan-paikkausilmoitus-paikkauskohteella
                                      c user {:urakka-id urakka-id
                                              :sopimus-id sopimus-id
                                              :paikkauskohde-id (:paikkauskohde-id paikkausilmoitus)})
          paikkausilmoitus-kannassa (when-not (:uusi (meta paikkausilmoitus-kannassa))
                                      ;; Tunnistetaan uuden tallentaminen
                                      paikkausilmoitus-kannassa)]

      (log/debug "MINIPOT kannassa: " paikkausilmoitus-kannassa)
      (tarkista-paikkausilmoituksen-tallentamisoikeudet user
                                                        urakka-id
                                                        paikkausilmoitus
                                                        paikkausilmoitus-kannassa)

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

        (hae-urakan-paikkausilmoitukset c user {:urakka-id urakka-id
                                                :sopimus-id sopimus-id})))))
(defrecord Paikkaus []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)]
      (julkaise-palvelu http :urakan-paikkausilmoitukset
                        (fn [user tiedot]
                          (hae-urakan-paikkausilmoitukset db user tiedot)))
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
      :urakan-paikkausilmoitukset
      :urakan-paikkausilmoitus-paikkauskohteella
      :tallenna-paikkaussilmoitus)
    this))
