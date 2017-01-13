(ns harja.palvelin.palvelut.yllapitokohteet.paallystys
  "Tässä namespacessa on esitelty palvelut, jotka liittyvät erityisesti päällystysurakkaan
   Tärkeimmät palvelut ovat päällystysilmoituksen haku/tallennus sekä määrämuutosten haku/tallennus

   Päällystysurakka on ylläpidon urakka ja siihen liittyy keskeisenä osana ylläpitokohteet.
   Ylläpitokohteiden hallintaan on olemassa oma palvelu."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.kommentit :as kommentit]
            [harja.domain.paallystysilmoitus :as pot-domain]
            [harja.kyselyt.paallystys :as q]
            [cheshire.core :as cheshire]
            [harja.palvelin.palvelut.yha :as yha]
            [harja.domain.skeema :as skeema]
            [harja.domain.tierekisteri :as tierekisteri-domain]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.palvelut.yllapitokohteet.yllapitokohteet :as yllapitokohteet]
            [harja.palvelin.palvelut.yllapitokohteet.maaramuutokset :as maaramuutokset]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]))

(defn hae-urakan-paallystysilmoitukset [db user {:keys [urakka-id sopimus-id vuosi]}]
  (log/debug "Haetaan urakan päällystysilmoitukset. Urakka-id " urakka-id ", sopimus-id: " sopimus-id)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (let [vastaus (into []
                      (comp
                        (map #(konv/string-polusta->keyword % [:yllapitokohdetyotyyppi])))
                      (q/hae-urakan-paallystysilmoitukset-kohteineen db urakka-id sopimus-id vuosi))]
    (log/debug "Päällystysilmoitukset saatu: " (count vastaus) "kpl")
    vastaus))

(defn- lisaa-paallystysilmoitukseen-kohdeosien-tiedot [paallystysilmoitus]
  (-> paallystysilmoitus
      (assoc-in [:ilmoitustiedot :osoitteet]
                (->> paallystysilmoitus
                     :kohdeosat
                     (map (fn [kohdeosa]
                            ;; Lisää kohdeosan tietoihin päällystystoimenpiteen tiedot
                            (merge (clojure.set/rename-keys kohdeosa {:id :kohdeosa-id})
                                   (some
                                     (fn [paallystystoimenpide]
                                       (when (= (:id kohdeosa)
                                                (:kohdeosa-id paallystystoimenpide))
                                         paallystystoimenpide))
                                     (get-in paallystysilmoitus
                                             [:ilmoitustiedot :osoitteet])))))
                     (sort-by tierekisteri-domain/tiekohteiden-jarjestys)
                     vec))
      (dissoc :kohdeosat)))

(defn hae-urakan-paallystysilmoitus-paallystyskohteella
  "Hakee päällystysilmoituksen ja kohteen tiedot.

   Päällystysilmoituksen kohdeosien tiedot haetaan yllapitokohdeosa-taulusta ja liitetään mukaan ilmoitukseen.

   Huomaa, että vaikka päällystysilmoitusta ei olisi tehty, tämä kysely palauttaa joka tapauksessa
   kohteen tiedot ja esitäytetyn ilmoituksen, jossa kohdeosat on syötetty valmiiksi."
  [db user {:keys [urakka-id paallystyskohde-id]}]
  (assert (and urakka-id paallystyskohde-id) "Virheelliset hakuparametrit!")
  (log/debug "Haetaan urakan päällystysilmoitus, jonka päällystyskohde-id " paallystyskohde-id)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (let [paallystysilmoitus (into []
                                 (comp (map konv/alaviiva->rakenne)
                                       (map #(konv/jsonb->clojuremap % :ilmoitustiedot))
                                       (map #(konv/string-poluista->keyword
                                               %
                                               [[:tekninen-osa :paatos]
                                                [:tila]])))
                                 (q/hae-paallystysilmoitus-kohdetietoineen-paallystyskohteella
                                   db
                                   {:paallystyskohde paallystyskohde-id}))
        paallystysilmoitus (first (konv/sarakkeet-vektoriin
                                    paallystysilmoitus
                                    {:kohdeosa :kohdeosat}
                                    :id))
        _ (when-let [ilmoitustiedot (:ilmoitustiedot paallystysilmoitus)]
            (skeema/validoi pot-domain/+paallystysilmoitus+
                            ilmoitustiedot))
        ;; Tyhjälle ilmoitukselle esitäytetään kohdeosat. Jos ilmoituksessa on tehty toimenpiteitä
        ;; kohdeosille, niihin liitetään kohdeosan tiedot, jotta voidaan muokata frontissa.
        paallystysilmoitus (lisaa-paallystysilmoitukseen-kohdeosien-tiedot paallystysilmoitus)
        kokonaishinta (reduce + (keep paallystysilmoitus [:sopimuksen-mukaiset-tyot
                                                          :arvonvahennykset
                                                          :bitumi-indeksi
                                                          :kaasuindeksi]))
        kommentit (into []
                        (comp (map konv/alaviiva->rakenne)
                              (map (fn [{:keys [liite] :as kommentti}]
                                     (if (:id
                                           liite)
                                       kommentti
                                       (dissoc kommentti :liite)))))
                        (q/hae-paallystysilmoituksen-kommentit db {:id (:id paallystysilmoitus)}))
        maaramuutokset (let [maaramuutokset (maaramuutokset/hae-maaramuutokset
                                              db user {:yllapitokohde-id paallystyskohde-id
                                                       :urakka-id urakka-id})]
                         (paallystys-ja-paikkaus/summaa-maaramuutokset maaramuutokset))
        paallystysilmoitus (assoc paallystysilmoitus
                             :kokonaishinta kokonaishinta
                             :maaramuutokset maaramuutokset
                             :paallystyskohde-id paallystyskohde-id
                             :kommentit kommentit)]
    (log/debug "Päällystysilmoitus kasattu: " (pr-str paallystysilmoitus))
    paallystysilmoitus))


(defn- poista-ilmoitustiedoista-tieosoitteet
  "Poistaa päällystysilmoituksen ilmoitustiedoista sellaiset tiedot, jotka tallennetaan
   ylläpitokohdeosa-tauluun."
  [ilmoitustiedot]
  (let [paivitetyt-osoitteet (mapv
                               (fn [osoite]
                                 (-> osoite
                                     (dissoc :tr-kaista
                                             :tr-ajorata
                                             :tr-loppuosa
                                             :tunnus
                                             :tr-alkuosa
                                             :tr-loppuetaisyys
                                             :nimi
                                             :tr-alkuetaisyys
                                             :tr-numero
                                             :toimenpide)))
                               (:osoitteet ilmoitustiedot))]
    (assoc ilmoitustiedot :osoitteet paivitetyt-osoitteet)))

(defn- luo-paallystysilmoitus [db user urakka-id sopimus-id
                               {:keys [paallystyskohde-id ilmoitustiedot
                                       takuupvm]
                                :as paallystysilmoitus}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (log/debug "Luodaan uusi päällystysilmoitus.")
  (let [tila (pot-domain/paattele-ilmoituksen-tila
               (:valmis-kasiteltavaksi paallystysilmoitus)
               (= (get-in paallystysilmoitus [:tekninen-osa :paatos]) :hyvaksytty))
        ilmoitustiedot (-> ilmoitustiedot
                           (poista-ilmoitustiedoista-tieosoitteet))
        _ (skeema/validoi pot-domain/+paallystysilmoitus+
                          ilmoitustiedot)
        encoodattu-ilmoitustiedot (cheshire/encode ilmoitustiedot)]
    (log/debug "Asetetaan ilmoituksen tilaksi " tila)
    (:id (q/luo-paallystysilmoitus<!
           db
           {:paallystyskohde paallystyskohde-id
            :tila tila
            :ilmoitustiedot encoodattu-ilmoitustiedot
            :takuupvm (konv/sql-date takuupvm)
            :kayttaja (:id user)}))))

(defn- tarkista-paallystysilmoituksen-lukinta [paallystysilmoitus-kannassa]
  (log/debug "Tarkistetaan onko POT lukittu...")
  (if (= :lukittu (:tila paallystysilmoitus-kannassa))
    (do (log/debug "POT on lukittu, ei voi päivittää!")
        (throw (SecurityException. "Päällystysilmoitus on lukittu, ei voi päivittää!")))
    (log/debug "POT ei ole lukittu, vaan " (pr-str (:tila paallystysilmoitus-kannassa)))))

(defn- paivita-kasittelytiedot [db user urakka-id
                                {:keys [paallystyskohde-id tekninen-osa]}]
  (if (oikeudet/on-muu-oikeus? "päätös" oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                               urakka-id user)
    (do
      (log/debug "Päivitetään päällystysilmoituksen käsittelytiedot")
      (q/paivita-paallystysilmoituksen-kasittelytiedot<!
        db
        {:paatos_tekninen_osa (some-> tekninen-osa :paatos name)
         :perustelu_tekninen_osa (:perustelu tekninen-osa)
         :kasittelyaika_tekninen_osa (konv/sql-date (:kasittelyaika tekninen-osa))
         :muokkaaja (:id user)
         :id paallystyskohde-id
         :urakka urakka-id}))
    (log/debug "Ei oikeutta päivittää päätöstä.")))

(defn- paivita-asiatarkastus [db user urakka-id
                              {:keys [paallystyskohde-id asiatarkastus]}]
  (let [{:keys [tarkastusaika tarkastaja tekninen-osa lisatiedot]} asiatarkastus]
    (if (oikeudet/on-muu-oikeus? "asiatarkastus" oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                                 urakka-id user)
      (do (log/debug "Päivitetään päällystysilmoituksen asiatarkastus: " asiatarkastus)
          (q/paivita-paallystysilmoituksen-asiatarkastus<!
            db
            {:asiatarkastus_pvm (konv/sql-date tarkastusaika)
             :asiatarkastus_tarkastaja tarkastaja
             :asiatarkastus_tekninen_osa tekninen-osa
             :asiatarkastus_lisatiedot lisatiedot
             :muokkaaja (:id user)
             :id paallystyskohde-id
             :urakka urakka-id}))
      (log/debug "Ei oikeutta päivittää asiatarkastusta."))))

(defn- paivita-paallystysilmoituksen-perustiedot
  [db user urakka-id sopimus-id
   {:keys [id paallystyskohde-id ilmoitustiedot
           takuupvm tekninen-osa] :as paallystysilmoitus}]
  (if (oikeudet/voi-kirjoittaa?
        oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
        urakka-id
        user)
    (do (log/debug "Päivitetään päällystysilmoituksen perustiedot")
        (let [tila (pot-domain/paattele-ilmoituksen-tila
                     (:valmis-kasiteltavaksi paallystysilmoitus)
                     (= (get-in paallystysilmoitus [:tekninen-osa :paatos]) :hyvaksytty))
              ilmoitustiedot (-> ilmoitustiedot
                                 (poista-ilmoitustiedoista-tieosoitteet))
              _ (skeema/validoi pot-domain/+paallystysilmoitus+
                                ilmoitustiedot)
              encoodattu-ilmoitustiedot (cheshire/encode ilmoitustiedot)]
          (log/debug "Encoodattu ilmoitustiedot: " (pr-str encoodattu-ilmoitustiedot))
          (log/debug "Asetetaan ilmoituksen tilaksi " tila)
          (q/paivita-paallystysilmoitus<!
            db
            {:tila tila
             :ilmoitustiedot encoodattu-ilmoitustiedot
             :takuupvm (konv/sql-date takuupvm)
             :muokkaaja (:id user)
             :id paallystyskohde-id
             :urakka urakka-id}))
        id)
    (log/debug "Ei oikeutta päivittää perustietoja.")))

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

(defn- lisaa-paallystysilmoitukseen-kohdeosien-idt
  "Liittää päällystysilmoituksen osoitetietoihin vastaavan
   ylläpitokohdeosan id:n."
  [paallystysilmoitus paivitetyt-kohdeosat]
  (assert (not (empty? paivitetyt-kohdeosat)) "Ei voida liittää päällystysilmoitukseen tyhjiä kohdeosia")
  (-> paallystysilmoitus
      (assoc-in [:ilmoitustiedot :osoitteet]
                (into []
                      (keep
                        (fn [osoite]
                          (let [vastaava-kohdeosa
                                (first
                                  (filter #(and
                                             (= (:tr-numero %) (:tr-numero osoite))
                                             (= (:tr-alkuosa %) (:tr-alkuosa osoite))
                                             (= (:tr-alkuetaisyys %) (:tr-alkuetaisyys osoite))
                                             (= (:tr-loppuosa %) (:tr-loppuosa osoite))
                                             (= (:tr-loppuetaisyys %) (:tr-loppuetaisyys osoite)))
                                          paivitetyt-kohdeosat))]
                            ;; Jos osoitteelle ei ole kohdeosaa, se on poistettu
                            (when vastaava-kohdeosa
                              (assoc osoite :kohdeosa-id (:id vastaava-kohdeosa)))))
                        (get-in paallystysilmoitus [:ilmoitustiedot :osoitteet]))))))

(defn tallenna-paallystysilmoitus
  "Tallentaa päällystysilmoituksen tiedot kantaan.

  Päällystysilmoituksen kohdeosien tietoja ei tallenneta itse ilmoitukseen, vaan ne tallennetaan
  yllapitokohdeosa-tauluun.

  Lopuksi palauttaa päällystysilmoitukset ja ylläpitokohteet kannasta."
  [db user {:keys [urakka-id sopimus-id vuosi paallystysilmoitus]}]
  (log/debug "Tallennetaan päällystysilmoitus: " paallystysilmoitus
             ". Urakka-id " urakka-id
             ", sopimus-id: " sopimus-id
             ", päällystyskohde-id:" (:paallystyskohde-id paallystysilmoitus))

  (log/debug "Aloitetaan päällystysilmoituksen tallennus")
  (jdbc/with-db-transaction [c db]
    (yha/lukitse-urakan-yha-sidonta db urakka-id)
    (let [paallystyskohde-id (:paallystyskohde-id paallystysilmoitus)
          paivitetyt-kohdeosat (yllapitokohteet/tallenna-yllapitokohdeosat
                                 db user {:urakka-id urakka-id :sopimus-id sopimus-id
                                          :yllapitokohde-id paallystyskohde-id
                                          :osat (map #(assoc % :id (:kohdeosa-id %))
                                                     (->> paallystysilmoitus
                                                          :ilmoitustiedot
                                                          :osoitteet
                                                          (filter (comp not :poistettu))))})
          paallystysilmoitus (lisaa-paallystysilmoitukseen-kohdeosien-idt paallystysilmoitus paivitetyt-kohdeosat)
          paallystysilmoitus-kannassa
          (first (into []
                       (comp (map #(konv/jsonb->clojuremap % :ilmoitustiedot))
                             (map #(konv/string-poluista->keyword %
                                                                  [[:paatos :tekninen-osa]
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

        (let [yllapitokohteet (yllapitokohteet/hae-urakan-yllapitokohteet db user {:urakka-id urakka-id
                                                                                   :sopimus-id sopimus-id
                                                                                   :vuosi vuosi})
              uudet-ilmoitukset (hae-urakan-paallystysilmoitukset c user {:urakka-id urakka-id
                                                                          :sopimus-id sopimus-id})]
          {:yllapitokohteet yllapitokohteet
           :paallystysilmoitukset uudet-ilmoitukset})))))

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
