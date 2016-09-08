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
            [harja.kyselyt.paallystys :as q]
            [cheshire.core :as cheshire]
            [harja.palvelin.palvelut.yha :as yha]
            [harja.domain.skeema :as skeema]
            [harja.domain.tierekisteri :as tierekisteri-domain]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.palvelut.yllapitokohteet :as yllapitokohteet]))

(defn tyot-tyyppi-string->avain [json avainpolku]
  (-> json
      (assoc-in avainpolku
                (when-let [tyot (some-> json (get-in avainpolku))]
                  (map #(assoc % :tyyppi (keyword (:tyyppi %))) tyot)))))

(defn hae-urakan-paallystysilmoitukset [db user {:keys [urakka-id sopimus-id]}]
  (log/debug "Haetaan urakan päällystysilmoitukset. Urakka-id " urakka-id ", sopimus-id: " sopimus-id)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (let [vastaus (q/hae-urakan-paallystysilmoitukset-kohteineen db urakka-id sopimus-id)]
    (log/debug "Päällystysilmoitukset saatu: " (count vastaus) "kpl")
    vastaus))

(defn hae-urakan-paallystysilmoitus-paallystyskohteella
  "Hakee päällystysilmoituksen ja kohteen tiedot.

   Päällystysilmoituksen kohdeosien tiedot haetaan yllapitokohteet-taulusta ja liitetään mukaan ilmoitukseen.
   Jos kohdeosalle löytyy myös toimenpidetiedot päällystysilmoituksesta, myös ne liitetään mukaan.

   Huomaa, että vaikka päällystysilmoitusta ei olisi tehty, tämä kysely palauttaa joka tapauksessa
   kohteen tiedot ja esitäytetyn ilmoituksen, jossa kohdeosat on syötetty valmiiksi."
  [db user {:keys [urakka-id paallystyskohde-id]}]
  (log/debug "Haetaan urakan päällystysilmoitus, jonka päällystyskohde-id " paallystyskohde-id)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (let [paallystysilmoitus (into []
                                 (comp (map konv/alaviiva->rakenne)
                                       (map #(konv/jsonb->clojuremap % :ilmoitustiedot))
                                       (map #(tyot-tyyppi-string->avain % [:ilmoitustiedot :tyot]))
                                       (map #(konv/string-poluista->keyword
                                              %
                                              [[:taloudellinen-osa :paatos]
                                               [:tekninen-osa :paatos]
                                               [:tila]])))
                                 (q/hae-urakan-paallystysilmoitus-paallystyskohteella
                                   db
                                   {:paallystyskohde paallystyskohde-id}))
        paallystysilmoitus (first (konv/sarakkeet-vektoriin
                                    paallystysilmoitus
                                    {:kohdeosa :kohdeosat}
                                    :id))
        _ (log/debug "Kohdeosat: " (get-in paallystysilmoitus [:ilmoitustiedot :osoitteet]))
        ;; Tyhjälle ilmoitukselle esitäytetään kohdeosat. Jos ilmoituksessa on tehty toimenpiteitä
        ;; kohdeosille, niihin liitetään kohdeosan tiedot, jotta voidaan muokata frontissa.
        paallystysilmoitus
        (-> paallystysilmoitus
            (assoc-in [:ilmoitustiedot :osoitteet]
                      (->> paallystysilmoitus
                           :kohdeosat
                           (map (fn [kohdeosa]
                                  ;; Lisää kohdeosan tietoihin päällystystoimenpiteen tiedot
                                  (merge (clojure.set/rename-keys kohdeosa {:id :kohdeosa-id})
                                         nil)))
                           (sort-by tierekisteri-domain/tiekohteiden-jarjestys)
                           vec))
            (dissoc :kohdeosat))

        kokonaishinta (reduce + (keep paallystysilmoitus [:sopimuksen-mukaiset-tyot
                                                          :arvonvahennykset
                                                          :bitumi-indeksi
                                                          :kaasuindeksi]))]
    (log/debug "Päällystysilmoitus kasattu: " (pr-str paallystysilmoitus))
    (log/debug "Haetaan kommentit...")
    (let [kommentit (into []
                          (comp (map konv/alaviiva->rakenne)
                                (map (fn [{:keys [liite] :as kommentti}]
                                       (if (:id
                                             liite)
                                         kommentti
                                         (dissoc kommentti :liite)))))
                          (q/hae-paallystysilmoituksen-kommentit db {:id (:id paallystysilmoitus)}))]
      (log/debug "Kommentit saatu: " kommentit)
      (assoc paallystysilmoitus
        :kokonaishinta kokonaishinta
        :paallystyskohde-id paallystyskohde-id
        :kommentit kommentit))))

(defn- paattele-ilmoituksen-tila
  [{:keys [tekninen-osa taloudellinen-osa valmispvm-kohde valmispvm-paallystys]}]
  (cond
    (and (= (:paatos tekninen-osa) :hyvaksytty)
         (= (:paatos taloudellinen-osa) :hyvaksytty))
    "lukittu"

    (and valmispvm-kohde valmispvm-paallystys)
    "valmis"

    :default
    "aloitettu"))

(defn- paivita-paallystysilmoituksen-perustiedot
  [db user urakka-id sopimus-id
   {:keys [id paallystyskohde-id ilmoitustiedot aloituspvm valmispvm-kohde
           valmispvm-paallystys takuupvm
           tekninen-osa taloudellinen-osa] :as paallystysilmoitus}]
  (if (oikeudet/voi-kirjoittaa?
        oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
        urakka-id
        user)
    (do (log/debug "Päivitetään päällystysilmoituksen perustiedot")
        (let [muutoshinta (paallystysilmoitus-domain/laske-muutokset-kokonaishintaan
                           (:tyot ilmoitustiedot))
              tila (paattele-ilmoituksen-tila paallystysilmoitus)
              encoodattu-ilmoitustiedot (cheshire/encode ilmoitustiedot)]
          (log/debug "Encoodattu ilmoitustiedot: " (pr-str encoodattu-ilmoitustiedot))
          (log/debug "Asetetaan ilmoituksen tilaksi " tila)
          (log/debug "POT muutoshinta: " muutoshinta)
          (q/paivita-paallystysilmoitus<!
            db
            {:tila tila
             :ilmoitustiedot encoodattu-ilmoitustiedot
             :aloituspvm (konv/sql-date aloituspvm)
             :valmispvm_kohde (konv/sql-date valmispvm-kohde)
             :valmispvm_paallystys (konv/sql-date valmispvm-paallystys)
             :takuupvm (konv/sql-date takuupvm)
             :muutoshinta muutoshinta
             :muokkaaja (:id user)
             :id paallystyskohde-id
             :urakka urakka-id}))
        id)
    (log/debug "Ei oikeutta päivittää perustietoja.")))

(defn- luo-paallystysilmoitus [db user urakka-id sopimus-id
                               {:keys [paallystyskohde-id ilmoitustiedot aloituspvm
                                       valmispvm-kohde valmispvm-paallystys
                                       takuupvm] :as paallystysilmoitus}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (log/debug "Luodaan uusi päällystysilmoitus.")
  (let [muutoshinta (paallystysilmoitus-domain/laske-muutokset-kokonaishintaan (:tyot ilmoitustiedot))
        tila (if (and valmispvm-kohde valmispvm-paallystys) "valmis" "aloitettu")
        encoodattu-ilmoitustiedot (cheshire/encode ilmoitustiedot)]
    (log/debug "Asetetaan ilmoituksen tilaksi " tila)
    (log/debug "POT muutoshinta: " muutoshinta)
    (:id (q/luo-paallystysilmoitus<!
           db
           {:paallystyskohde paallystyskohde-id
            :tila tila
            :ilmoitustiedot encoodattu-ilmoitustiedot
            :aloituspvm (konv/sql-date aloituspvm)
            :valmispvm_kohde (konv/sql-date valmispvm-kohde)
            :valmispvm_paallystys (konv/sql-date valmispvm-paallystys)
            :takuupvm (konv/sql-date takuupvm)
            :muutoshinta muutoshinta
            :kayttaja (:id user)}))))

(defn- tarkista-paallystysilmoituksen-lukinta [paallystysilmoitus-kannassa]
  (log/debug "Tarkistetaan onko POT lukittu...")
  (if (= :lukittu (:tila paallystysilmoitus-kannassa))
    (do (log/debug "POT on lukittu, ei voi päivittää!")
        (throw (SecurityException. "Päällystysilmoitus on lukittu, ei voi päivittää!")))
    (log/debug "POT ei ole lukittu, vaan " (pr-str (:tila paallystysilmoitus-kannassa)))))

(defn- paivita-kasittelytiedot [db user urakka-id
                                {:keys [paallystyskohde-id
                                        tekninen-osa taloudellinen-osa]}]
  (if (oikeudet/on-muu-oikeus? "päätös" oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                               urakka-id user)
    (do
      (log/debug "Päivitetään päällystysilmoituksen käsittelytiedot")
      (q/paivita-paallystysilmoituksen-kasittelytiedot<!
        db
        {:paatos_tekninen_osa (some-> tekninen-osa :paatos name)
         :paatos_taloudellinen_osa (some-> taloudellinen-osa :paatos name)
         :perustelu_tekninen_osa (:perustelu tekninen-osa)
         :perustelu_taloudellinen_osa (:perustelu taloudellinen-osa)
         :kasittelyaika_tekninen_osa (konv/sql-date (:kasittelyaika tekninen-osa))
         :kasittelyaika_taloudellinen_osa (konv/sql-date (:kasittelyaika taloudellinen-osa))
         :muokkaaja (:id user)
         :id paallystyskohde-id
         :urakka urakka-id}))
    (log/debug "Ei oikeutta päivittää päätöstä.")))

(defn- paivita-asiatarkastus [db user urakka-id
                              {:keys [paallystyskohde-id asiatarkastus]}]
  (let [{:keys [tarkastusaika tarkastaja tekninen-osa taloudellinen-osa lisatiedot]} asiatarkastus]
    (if (oikeudet/on-muu-oikeus? "asiatarkastus" oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                                 urakka-id user)
      (do (log/debug "Päivitetään päällystysilmoituksen asiatarkastus: " asiatarkastus)
          (q/paivita-paallystysilmoituksen-asiatarkastus<!
           db
           {:asiatarkastus_pvm (konv/sql-date tarkastusaika)
            :asiatarkastus_tarkastaja tarkastaja
            :asiatarkastus_tekninen_osa tekninen-osa
            :asiatarkastus_taloudellinen_osa taloudellinen-osa
            :asiatarkastus_lisatiedot lisatiedot
            :muokkaaja (:id user)
            :id paallystyskohde-id
            :urakka urakka-id}))
      (log/debug "Ei oikeutta päivittää asiatarkastusta."))))

(defn- paivita-paallystysilmoitus [db user urakka-id sopimus-id
                                   uusi-paallystysilmoitus paallystysilmoitus-kannassa]
  ;; Ilmoituksen kaikki tiedot lähetetään aina tallennettavaksi, vaikka käyttäjällä olisi oikeus
  ;; muokata vain tiettyä osaa ilmoituksesta. Frontissa on estettyä muokkaamasta sellaisia asioita, joita
  ;; käyttäjä ei saa muokata. Täällä ilmoitus päivitetään osa kerrallaan niin, että jokaista
  ;; osaa vasten tarkistetaan tallennusoikeus.
  (log/debug "Päivitetään olemassa oleva päällystysilmoitus")
  (tarkista-paallystysilmoituksen-lukinta paallystysilmoitus-kannassa)
  (paivita-kasittelytiedot db user urakka-id uusi-paallystysilmoitus)
  (paivita-asiatarkastus db user urakka-id uusi-paallystysilmoitus)
  (paivita-paallystysilmoituksen-perustiedot db user urakka-id sopimus-id uusi-paallystysilmoitus)
  (log/debug "Päällystysilmoitus päivitetty!")
  (:id paallystysilmoitus-kannassa))

(defn tallenna-paallystysilmoituksen-kommentti [db user uusi-paallystysilmoitus paallystysilmoitus-id]
  (when-let [uusi-kommentti (:uusi-kommentti uusi-paallystysilmoitus)]
    (log/info "Tallennetaan uusi kommentti: " uusi-kommentti)
    (let [kommentti (kommentit/luo-kommentti<! db
                                               nil
                                               (:kommentti uusi-kommentti)
                                               nil
                                               (:id user))]
      (q/liita-kommentti<! db {:paallystysilmoitus paallystysilmoitus-id
                               :kommentti (:id kommentti)}))))

(defn tallenna-paallystysilmoitus
  "Tallentaa päällystysilmoituksen tiedot kantaan.

  Päällystysilmoituksen kohdeosien tietoja ei tallenneta itse ilmoitukseen, vaan ne päivitetään
  yllapitokohdeosa-tauluun. Tästä syystä kohdeosien tiedot poistetaan ilmoituksesta ennen tallennusta."
  [db user {:keys [urakka-id sopimus-id paallystysilmoitus]}]
  (log/debug "Tallennetaan päällystysilmoitus: " paallystysilmoitus
             ". Urakka-id " urakka-id
             ", sopimus-id: " sopimus-id
             ", päällystyskohde-id:" (:paallystyskohde-id paallystysilmoitus))

  (log/debug "Aloitetaan päällystysilmoituksen tallennus")
  (jdbc/with-db-transaction [c db]
    (yha/lukitse-urakan-yha-sidonta db urakka-id)
    (skeema/validoi paallystysilmoitus-domain/+paallystysilmoitus+
                    (:ilmoitustiedot paallystysilmoitus))

    (let [paallystyskohde-id (:paallystyskohde-id paallystysilmoitus)
          kohdeosat (yllapitokohteet/tallenna-yllapitokohdeosat
                     db user {:urakka-id urakka-id :sopimus-id sopimus-id
                              :yllapitokohde-id paallystyskohde-id
                              :osat (map #(assoc % :id (:kohdeosa-id %))
                                         (->> paallystysilmoitus
                                              :ilmoitustiedot
                                              :osoitteet
                                              (filter (comp not :poistettu))))})
          paallystysilmoitus-kannassa
          (first (into []
                       (comp (map #(konv/jsonb->clojuremap % :ilmoitustiedot))
                             (map #(tyot-tyyppi-string->avain % [:ilmoitustiedot :tyot]))
                             (map #(konv/string-poluista->keyword %
                                                                  [[:paatos :taloudellinen-osa]
                                                                   [:paatos :tekninen-osa]
                                                                   [:tila]])))
                       (q/hae-paallystysilmoitus-paallystyskohteella
                        db
                        {:paallystyskohde paallystyskohde-id})))]
      (let [paallystysilmoitus-id
            (if paallystysilmoitus-kannassa
              (paivita-paallystysilmoitus db user urakka-id sopimus-id paallystysilmoitus
                                          paallystysilmoitus-kannassa)
              (luo-paallystysilmoitus db user urakka-id sopimus-id paallystysilmoitus))]

        (tallenna-paallystysilmoituksen-kommentti db user paallystysilmoitus paallystysilmoitus-id)

        ;; FIXME: haun voisi irrottaa erilleen
        (let [uudet-ilmoitukset (hae-urakan-paallystysilmoitukset c user {:urakka-id urakka-id
                                                                          :sopimus-id sopimus-id})]
          (log/debug "Tallennus tehty, palautetaan uudet päällystysilmoitukset: "
                     (count uudet-ilmoitukset) " kpl")
          uudet-ilmoitukset)))))

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
