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
            [harja.palvelin.palvelut.yha-apurit :as yha-apurit]
            [harja.kyselyt.urakat :as urakat-q]
            [hiccup.core :refer [html]]
            [harja.tyokalut.html :refer [sanitoi]]
            [harja.domain.urakka :as urakka-domain]
            [harja.domain.sopimus :as sopimus-domain]
            [harja.domain.skeema :as skeema]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.palvelut.yllapitokohteet :as yllapitokohteet]
            [harja.palvelin.palvelut.yllapitokohteet.maaramuutokset :as maaramuutokset]
            [harja.domain.paallystyksen-maksuerat :as paallystyksen-maksuerat]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [harja.domain.tierekisteri :as tr-domain]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yy]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.yllapitokohteet :as yllapitokohteet-q]
            [harja.palvelin.palvelut.viestinta :as viestinta]))

(defn hae-urakan-paallystysilmoitukset [db user {:keys [urakka-id sopimus-id vuosi]}]
  (log/debug "Haetaan urakan päällystysilmoitukset. Urakka-id " urakka-id ", sopimus-id: " sopimus-id)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (let [vastaus (into []
                      (comp
                        (map #(konv/string-polusta->keyword % [:yllapitokohdetyotyyppi])))
                      (q/hae-urakan-paallystysilmoitukset-kohteineen db urakka-id sopimus-id vuosi))]
    (log/debug "Päällystysilmoitukset saatu: " (count vastaus) "kpl")
    vastaus))

(defn hae-urakan-maksuerat
  "Hakee hakuparametreihin osuvien ylläpitokohteiden maksuerät.
   Palauttaa myös sellaiset ylläpitokohteet, joilla ei ole maksuerää."
  [db user hakuparametrit]
  (log/debug "Haetaan päällystyksen maksuerät")
  (let [urakka-id (::urakka-domain/id hakuparametrit)
        sopimus-id (::sopimus-domain/id hakuparametrit)
        vuosi (::urakka-domain/vuosi hakuparametrit)
        _ (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-maksuerat user urakka-id)
        yllapitokohteet (into []
                              (comp
                                (map konversio/alaviiva->rakenne))
                              (q/hae-urakan-maksuerat db {:urakka urakka-id :sopimus sopimus-id :vuosi vuosi}))
        yllapitokohteet (as-> yllapitokohteet ypk
                              (konv/sarakkeet-vektoriin
                                ypk
                                {:maksuera :maksuerat}
                                :id)
                              (map
                                #(assoc % :maaramuutokset (:tulos (maaramuutokset/hae-ja-summaa-maaramuutokset
                                                                    db
                                                                    {:urakka-id urakka-id
                                                                     :yllapitokohde-id (:id %)})))
                                ypk)
                              (map
                                #(assoc % :kokonaishinta (yllapitokohteet-domain/yllapitokohteen-kokonaishinta %))
                                ypk)
                              (map
                                #(assoc % :maksuerat (vec (sort-by :maksueranumero (:maksuerat %))))
                                ypk))]
    (vec yllapitokohteet)))

(defn- tallenna-maksuerat [db yllapitokohteet]
  (let [maksuerat (mapcat (fn [yllapitokohde]
                            (let [kohteen-maksuerat (:maksuerat yllapitokohde)]
                              (map #(assoc % :yllapitokohde-id (:id yllapitokohde))
                                   kohteen-maksuerat)))
                          yllapitokohteet)]
    (doseq [maksuera maksuerat]
      (let [nykyinen-maksuera-kannassa (first (q/hae-yllapitokohteen-maksuera
                                                db
                                                {:yllapitokohde (:yllapitokohde-id maksuera)
                                                 :maksueranumero (:maksueranumero maksuera)}))
            maksuera-params {:sisalto (:sisalto maksuera)
                             :yllapitokohde (:yllapitokohde-id maksuera)
                             :maksueranumero (:maksueranumero maksuera)}]
        (if nykyinen-maksuera-kannassa
          (q/paivita-maksuera<! db maksuera-params)
          (q/luo-maksuera<! db maksuera-params))))))

(defn- tallenna-maksueratunnus [db yllapitokohteet]
  (doseq [yllapitokohde yllapitokohteet]
    (let [nykyinen-maksueratunnus-kannassa (first (q/hae-yllapitokohteen-maksueratunnus
                                                    db
                                                    {:yllapitokohde (:id yllapitokohde)}))
          maksueratunnus-params {:maksueratunnus (:maksueratunnus yllapitokohde)
                                 :yllapitokohde (:id yllapitokohde)}]
      (if nykyinen-maksueratunnus-kannassa
        (q/paivita-maksueratunnus<! db maksueratunnus-params)
        (q/luo-maksueratunnus<! db maksueratunnus-params)))))

(defn tallenna-urakan-maksuerat
  [db user {:keys [yllapitokohteet] :as hakuparametrit}]
  (log/debug "Tallennetaan päällystyksen maksuerät")
  (let [urakka-id (::urakka-domain/id hakuparametrit)
        sopimus-id (::sopimus-domain/id hakuparametrit)
        vuosi (::urakka-domain/vuosi hakuparametrit)]
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-maksuerat user urakka-id)
    (jdbc/with-db-transaction [db db]
      (doseq [yllapitokohde yllapitokohteet]
        (yy/vaadi-yllapitokohde-kuuluu-urakkaan db urakka-id (:id yllapitokohde)))

      (let [voi-tayttaa-maksuerat?
            (oikeudet/on-muu-oikeus? "maksuerät" oikeudet/urakat-kohdeluettelo-maksuerat urakka-id user)
            voi-tayttaa-maksueratunnuksen?
            (oikeudet/on-muu-oikeus? "maksuerätunnus" oikeudet/urakat-kohdeluettelo-maksuerat urakka-id user)]

        (when voi-tayttaa-maksuerat?
          (tallenna-maksuerat db yllapitokohteet))

        (when voi-tayttaa-maksueratunnuksen?
          (tallenna-maksueratunnus db yllapitokohteet)))

      (hae-urakan-maksuerat db user {::urakka-domain/id urakka-id
                                     ::sopimus-domain/id sopimus-id
                                     ::urakka-domain/vuosi vuosi}))))

(defn- taydenna-paallystysilmoituksen-kohdeosien-tiedot
  "Ottaa päällystysilmoituksen, jolla on siihen liittyvän ylläpitokohteen kohdeosien tiedot.
   Lisää päällystysilmoitukselle ilmoitustiedot, jossa on kohdeosien tiedot ja niiden vastaavat ilmoitustiedot.
   Poistaa päällystysilmoitukselta kohdeosien tiedot."
  [paallystysilmoitus]
  (-> paallystysilmoitus
      (assoc-in [:ilmoitustiedot :osoitteet]
                (->> paallystysilmoitus
                     :kohdeosat
                     (map (fn [kohdeosa]
                            ;; Lisää kohdeosan tietoihin päällystystoimenpiteen tiedot
                            ;; On mahdollista, ettei kohdeosalle ole lisätty mitään tietoja
                            (let [kohdeosan-ilmoitustiedot (first (filter
                                                                    #(= (:id kohdeosa)
                                                                        (:kohdeosa-id %))
                                                                    (get-in paallystysilmoitus
                                                                            [:ilmoitustiedot :osoitteet])))]
                              (merge (clojure.set/rename-keys kohdeosa {:id :kohdeosa-id})
                                     kohdeosan-ilmoitustiedot))))
                     (tr-domain/jarjesta-tiet)
                     vec))
      (dissoc :kohdeosat)))

(defn- pyorista-kasittelypaksuus
  "Käsittelypaksuus täytyy pyöristää johtuen siitä, että aiemmassa mallissa, käsittelypaksuudelle sallittiin desimaalit.
   Myöhemmin YHA:ssa sallittiin vain kokonaisluvut ja olemassa olevien päällystysilmoitusten migrointi ei ole enää
   mahdollista. Sen takia vanhat arvot pyöristetään vaikka kaikki uudet arvot voi tallentaa vain kokonaislukuina."
  [paallystysilmoitus]
  (let [alustatoimet (get-in paallystysilmoitus [:ilmoitustiedot :alustatoimet])]
    (if (empty? alustatoimet)
      paallystysilmoitus
      (assoc-in paallystysilmoitus
                [:ilmoitustiedot :alustatoimet]
                (map #(assoc % :paksuus (int (:paksuus %)))
                     alustatoimet)))))

(defn- muunna-ilmoitustiedot-tallennusmuotoon
  "Muuntaa päällystysilmoitus-lomakkeen päällystystoimenpiteen tiedot sellaiseen muotoon,
  josta se voidaan tallennetaan kantaan JSON-muunnoksen jälkeen."
  [ilmoitustiedot]
  (let [paivitetyt-osoitteet (mapv
                               (fn [osoite]
                                 (-> osoite (clojure.set/rename-keys
                                              {:toimenpide-paallystetyyppi :paallystetyyppi
                                               :toimenpide-raekoko :raekoko
                                               :toimenpide-tyomenetelma :tyomenetelma})))
                               (:osoitteet ilmoitustiedot))]
    (assoc ilmoitustiedot :osoitteet paivitetyt-osoitteet)))

(defn- poista-ilmoitustiedoista-alikohteen-tiedot
  "Poistaa päällystysilmoituksen ilmoitustiedoista sellaiset tiedot, jotka tallennetaan
   ylläpitokohdeosa-tauluun."
  [ilmoitustiedot]
  (let [paivitetyt-osoitteet (mapv
                               (fn [osoite]
                                 (-> osoite
                                     (dissoc :tr-kaista :tr-ajorata :tr-loppuosa
                                             :tunnus :tr-alkuosa :tr-loppuetaisyys
                                             :nimi :tr-alkuetaisyys :tr-numero
                                             :toimenpide :massamaara :raekoko
                                             :paallystetyyppi :tyomenetelma)))
                               (:osoitteet ilmoitustiedot))]
    (assoc ilmoitustiedot :osoitteet paivitetyt-osoitteet)))

(defn- muunna-tallennetut-ilmoitustiedot-lomakemuotoon
  "Muuntaa päällystysilmoituksen ilmoitustiedot lomakkeessa esitettävään muotoon."
  [ilmoitustiedot]
  (let [paivitetyt-osoitteet (mapv
                               (fn [osoite]
                                 (clojure.set/rename-keys osoite
                                                          {:paallystetyyppi :toimenpide-paallystetyyppi
                                                           :raekoko :toimenpide-raekoko
                                                           :tyomenetelma :toimenpide-tyomenetelma}))
                               (:osoitteet ilmoitustiedot))]
    (assoc ilmoitustiedot :osoitteet paivitetyt-osoitteet)))


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
        paallystysilmoitus (pyorista-kasittelypaksuus paallystysilmoitus)
        _ (when-let [ilmoitustiedot (:ilmoitustiedot paallystysilmoitus)]
            (skeema/validoi pot-domain/+paallystysilmoitus+ ilmoitustiedot))
        ;; Tyhjälle ilmoitukselle esitäytetään kohdeosat. Jos ilmoituksessa on tehty toimenpiteitä
        ;; kohdeosille, niihin liitetään kohdeosan tiedot, jotta voidaan muokata frontissa.
        paallystysilmoitus (as-> paallystysilmoitus p
                                 (assoc p :ilmoitustiedot
                                          (muunna-tallennetut-ilmoitustiedot-lomakemuotoon (:ilmoitustiedot p)))
                                 (taydenna-paallystysilmoituksen-kohdeosien-tiedot p))
        kokonaishinta-ilman-maaramuutoksia (yllapitokohteet-domain/yllapitokohteen-kokonaishinta paallystysilmoitus)
        kommentit (into []
                        (comp (map konv/alaviiva->rakenne)
                              (map (fn [{:keys [liite] :as kommentti}]
                                     (if (:id
                                           liite)
                                       kommentti
                                       (dissoc kommentti :liite)))))
                        (q/hae-paallystysilmoituksen-kommentit db {:id (:id paallystysilmoitus)}))
        maaramuutokset (maaramuutokset/hae-ja-summaa-maaramuutokset
                         db {:urakka-id urakka-id :yllapitokohde-id paallystyskohde-id})
        paallystysilmoitus (assoc paallystysilmoitus
                             :kokonaishinta-ilman-maaramuutoksia kokonaishinta-ilman-maaramuutoksia
                             :maaramuutokset (:tulos maaramuutokset)
                             :maaramuutokset-ennustettu? (:ennustettu? maaramuutokset)
                             :paallystyskohde-id paallystyskohde-id
                             :kommentit kommentit)]
    (log/debug "Päällystysilmoitus kasattu: " (pr-str paallystysilmoitus))
    paallystysilmoitus))

(defn- luo-paallystysilmoitus [db user urakka-id
                               {:keys [paallystyskohde-id ilmoitustiedot takuupvm]
                                :as paallystysilmoitus}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (log/debug "Luodaan uusi päällystysilmoitus.")
  (let [tila (pot-domain/paattele-ilmoituksen-tila
               (:valmis-kasiteltavaksi paallystysilmoitus)
               (= (get-in paallystysilmoitus [:tekninen-osa :paatos]) :hyvaksytty))
        ilmoitustiedot (-> ilmoitustiedot
                           (poista-ilmoitustiedoista-alikohteen-tiedot)
                           (muunna-ilmoitustiedot-tallennusmuotoon))
        _ (skeema/validoi pot-domain/+paallystysilmoitus+ ilmoitustiedot)
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
  (let [{:keys [tarkastusaika tarkastaja hyvaksytty lisatiedot]} asiatarkastus]
    (if (oikeudet/on-muu-oikeus? "asiatarkastus" oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                                 urakka-id user)
      (do (log/debug "Päivitetään päällystysilmoituksen asiatarkastus: " asiatarkastus)
          (q/paivita-paallystysilmoituksen-asiatarkastus<!
            db
            {:asiatarkastus_pvm (konv/sql-date tarkastusaika)
             :asiatarkastus_tarkastaja tarkastaja
             :asiatarkastus_hyvaksytty hyvaksytty
             :asiatarkastus_lisatiedot lisatiedot
             :muokkaaja (:id user)
             :id paallystyskohde-id
             :urakka urakka-id}))
      (log/debug "Ei oikeutta päivittää asiatarkastusta."))))

(defn- paivita-paallystysilmoituksen-perustiedot
  [db user urakka-id
   {:keys [id paallystyskohde-id ilmoitustiedot takuupvm] :as paallystysilmoitus}]
  (if (oikeudet/voi-kirjoittaa?
        oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
        urakka-id
        user)
    (do (log/debug "Päivitetään päällystysilmoituksen perustiedot")
        (let [tila (pot-domain/paattele-ilmoituksen-tila
                     (:valmis-kasiteltavaksi paallystysilmoitus)
                     (= (get-in paallystysilmoitus [:tekninen-osa :paatos]) :hyvaksytty))
              ilmoitustiedot (-> ilmoitustiedot
                                 (poista-ilmoitustiedoista-alikohteen-tiedot)
                                 (muunna-ilmoitustiedot-tallennusmuotoon))
              _ (log/debug "PÄIVITETTÄVÄT ILMOITUSTIEDOT: " (pr-str ilmoitustiedot))
              _ (skeema/validoi pot-domain/+paallystysilmoitus+ ilmoitustiedot)
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

(defn- paivita-paallystysilmoitus [db user urakka-id
                                   uusi-paallystysilmoitus paallystysilmoitus-kannassa]
  ;; Ilmoituksen kaikki tiedot lähetetään aina tallennettavaksi, vaikka käyttäjällä olisi oikeus
  ;; muokata vain tiettyä osaa ilmoituksesta. Frontissa on estettyä muokkaamasta sellaisia asioita, joita
  ;; käyttäjä ei saa muokata. Täällä ilmoitus päivitetään osa kerrallaan niin, että jokaista
  ;; osaa vasten tarkistetaan tallennusoikeus.
  (log/debug "Päivitetään olemassa oleva päällystysilmoitus")
  (tarkista-paallystysilmoituksen-lukinta paallystysilmoitus-kannassa)
  (paivita-kasittelytiedot db user urakka-id uusi-paallystysilmoitus)
  (paivita-asiatarkastus db user urakka-id uusi-paallystysilmoitus)
  (paivita-paallystysilmoituksen-perustiedot db user urakka-id uusi-paallystysilmoitus)
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

(defn laheta-paallystysilmoituksesta-sahkoposti-urakan-valvojalle?
  [uusi-tila vanha-tila]
  (and (= uusi-tila :valmis)
       (not= vanha-tila :valmis)))

(defn laheta-paallystysilmoituksesta-sahkoposti? [{:keys [uusi-tila vanha-tila uusi-paatos vanha-paatos]}]
  (or
    ;; viesti urakan valvojalle?
    (laheta-paallystysilmoituksesta-sahkoposti-urakan-valvojalle? uusi-tila vanha-tila)
    ;; viesti urakoitsijalle?
    (not= vanha-paatos uusi-paatos)))

(defn laheta-paallystysilmoitussahkoposti-tarvittaessa [{:keys [db fim email urakka-id paallystyskohde-id uusi-tila
                                                                vanha-tila uusi-paatos vanha-paatos] :as s-posti-params}]
  (when (laheta-paallystysilmoituksesta-sahkoposti? s-posti-params)
    (let [sposti-urakan-valvojalle? (laheta-paallystysilmoituksesta-sahkoposti-urakan-valvojalle? uusi-tila vanha-tila)
          paallystyskohde (first (yllapitokohteet-q/hae-yllapitokohde db {:id paallystyskohde-id}))
          urakka-nimi (:nimi (first (urakat-q/hae-urakka db urakka-id)))
          urakka-sampoid (urakat-q/hae-urakan-sampo-id db urakka-id)
          hallintayksikko-id (:id (first (urakat-q/hae-urakan-ely db urakka-id)))
          viesti (sanitoi
                   (format "Urakan %s kohteen %s päällystysilmoitus %s"
                           urakka-nimi
                           (:nimi paallystyskohde)
                           (if sposti-urakan-valvojalle?
                             "valmis käsiteltäväksi"
                             (str "on " (if (= "hylatty" uusi-paatos) "hylätty" "hyväksytty")))))
          url (str "https://extranet.liikennevirasto.fi/harja#urakat/kohdeluettelo-paallystys/paallystysilmoitukset?"
                   "&hy=" hallintayksikko-id
                   "&u=" urakka-id)
            kayttajaroolit (if sposti-urakan-valvojalle? #{"ely urakanvalvoja"} #{"urakan vastuuhenkilö"})
          spostin-parametrit {:fim fim
                              :email email
                              :urakka-sampoid urakka-sampoid
                              :fim-kayttajaroolit kayttajaroolit
                              :viesti-otsikko viesti
                              :viesti-body (html
                                             [:div
                                              [:p (str viesti ".")]
                                              [:p "Päällystysilmoitukset Harjassa: "
                                               [:a {:href url} url]]])}]
      (viestinta/laheta-sposti-fim-kayttajarooleille spostin-parametrit))))

(defn tallenna-paallystysilmoitus
  "Tallentaa päällystysilmoituksen tiedot kantaan.

  Päällystysilmoituksen kohdeosien tietoja ei tallenneta itse ilmoitukseen, vaan ne tallennetaan
  yllapitokohdeosa-tauluun.

  Lopuksi palauttaa päällystysilmoitukset ja ylläpitokohteet kannasta."
  [db user fim email {:keys [urakka-id sopimus-id vuosi paallystysilmoitus]}]
  (log/debug "Tallennetaan päällystysilmoitus: " paallystysilmoitus
             ". Urakka-id " urakka-id
             ", sopimus-id: " sopimus-id
             ", päällystyskohde-id:" (:paallystyskohde-id paallystysilmoitus))

  (log/debug "Aloitetaan päällystysilmoituksen tallennus")
  (jdbc/with-db-transaction [db db]
    ;; Kirjoitusoikeudet tarkistetaan syvemällä, päivitetään vain ne osat, jotka saa
    (yy/vaadi-yllapitokohde-kuuluu-urakkaan db urakka-id (:paallystyskohde-id paallystysilmoitus))
    (yha-apurit/lukitse-urakan-yha-sidonta db urakka-id)
    (let [hae-paallystysilmoitus (fn [paallystyskohde-id]
                                   (first (into []
                                                (comp (map #(konv/jsonb->clojuremap % :ilmoitustiedot))
                                                      (map #(konv/string-poluista->keyword %
                                                                                           [[:tila]])))
                                                (q/hae-paallystysilmoitus-paallystyskohteella
                                                  db
                                                  {:paallystyskohde paallystyskohde-id}))))
          paallystyskohde-id (:paallystyskohde-id paallystysilmoitus)
          paivitetyt-kohdeosat (yllapitokohteet/tallenna-yllapitokohdeosat
                                 db user {:urakka-id urakka-id :sopimus-id sopimus-id
                                          :yllapitokohde-id paallystyskohde-id
                                          :osat (map #(assoc % :id (:kohdeosa-id %))
                                                     (->> paallystysilmoitus
                                                          :ilmoitustiedot
                                                          :osoitteet
                                                          (filter (comp not :poistettu))))})
          paallystysilmoitus (lisaa-paallystysilmoitukseen-kohdeosien-idt paallystysilmoitus paivitetyt-kohdeosat)
          vanha-paallystysilmoitus (hae-paallystysilmoitus paallystyskohde-id)
          paallystysilmoitus-id (if vanha-paallystysilmoitus
                                  (paivita-paallystysilmoitus db user urakka-id paallystysilmoitus
                                                              vanha-paallystysilmoitus)
                                  (luo-paallystysilmoitus db user urakka-id paallystysilmoitus))
          tuore-paallystysilmoitus (hae-paallystysilmoitus paallystyskohde-id)]

      (tallenna-paallystysilmoituksen-kommentti db user paallystysilmoitus paallystysilmoitus-id)
      (laheta-paallystysilmoitussahkoposti-tarvittaessa {:db db :fim fim :email email :urakka-id urakka-id
                                                         :paallystyskohde-id paallystyskohde-id
                                                         :uusi-tila (:tila tuore-paallystysilmoitus)
                                                         :vanha-tila (:tila vanha-paallystysilmoitus)
                                                         :uusi-paatos (:tekninen-osa_paatos tuore-paallystysilmoitus)
                                                         :vanha-paatos (:tekninen-osa_paatos vanha-paallystysilmoitus)})

      ;; Rakennetaan vastaus
      (let [yllapitokohteet (yllapitokohteet/hae-urakan-yllapitokohteet db user {:urakka-id urakka-id
                                                                                 :sopimus-id sopimus-id
                                                                                 :vuosi vuosi})
            uudet-ilmoitukset (hae-urakan-paallystysilmoitukset db user {:urakka-id urakka-id
                                                                         :sopimus-id sopimus-id})]
        {:yllapitokohteet yllapitokohteet
         :paallystysilmoitukset uudet-ilmoitukset}))))

(defn tallenna-paallystysilmoitusten-takuupvmt [db user {urakka-id ::urakka-domain/id
                                                         takuupvmt ::pot-domain/tallennettavat-paallystysilmoitusten-takuupvmt}]
  (log/debug "Tallennetaan päällystysilmoitusten takuupäivämäärät")
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)

  (doseq [kohde-id (distinct (mapv ::pot-domain/paallystyskohde-id takuupvmt))]
    (yy/vaadi-yllapitokohde-kuuluu-urakkaan db urakka-id kohde-id))

  (jdbc/with-db-transaction [db db]
    (doseq [takuupvm takuupvmt]
      ; Tallennetaan takuupvm vain sellaiselle kohteelle, jolla jo on POT (eli id)
      (when (::pot-domain/id takuupvm)
        (q/paivita-paallystysilmoituksen-takuupvm! db {:id (::pot-domain/id takuupvm)
                                                       :takuupvm (konv/sql-date (::pot-domain/takuupvm takuupvm))})))
    []))

(defn- aseta-paallystysilmoituksen-tila
  "Mahdollistaa päällystysilmoituksen tilan muuttamisen palvelun kautta. Haluaa
  parametrina urakka-idn, päällystyskohde-id:n sekä uuden tilan. Tällähetkellä tukee
  vain lukituksen poistamista, mutta nimetty geneerisempänä mahdollisia
  myöhempiä tarpeita varten."
  [db user {urakka-id ::urakka-domain/id
            kohde-id ::pot-domain/paallystyskohde-id
            tila ::pot-domain/tila}]
  (log/debug "Aseta päällystysilmoituksen lukitus urakka " urakka-id " kohde-id " kohde-id " tila: " tila)
  (yy/vaadi-yllapitokohde-kuuluu-urakkaan db urakka-id kohde-id)
  (oikeudet/vaadi-oikeus "päätös" oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)

  (jdbc/with-db-transaction [db db]
    (cond
      (= tila :valmis)
      (q/avaa-paallystysilmoituksen-lukko! db {:yllapitokohde_id kohde-id})

      :else
      (log/warn "Aseta päällystysilmoituksen tila kutsuttiin odottamattomilla parametreilla kohde-id: " kohde-id " uusi tila: " tila))
    (hae-urakan-paallystysilmoitus-paallystyskohteella db user {:urakka-id urakka-id
                                                                :paallystyskohde-id kohde-id})))

(defrecord Paallystys []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)
          fim (:fim this)
          email (:sonja-sahkoposti this)]
      (julkaise-palvelu http :urakan-paallystysilmoitukset
                        (fn [user tiedot]
                          (hae-urakan-paallystysilmoitukset db user tiedot)))
      (julkaise-palvelu http :urakan-paallystysilmoitus-paallystyskohteella
                        (fn [user tiedot]
                          (hae-urakan-paallystysilmoitus-paallystyskohteella db user tiedot)))
      (julkaise-palvelu http :tallenna-paallystysilmoitus
                        (fn [user tiedot]
                          (tallenna-paallystysilmoitus db user fim email tiedot)))
      (julkaise-palvelu http :tallenna-paallystysilmoitusten-takuupvmt
                        (fn [user tiedot]
                          (tallenna-paallystysilmoitusten-takuupvmt db user tiedot))
                        {:kysely-spec ::pot-domain/tallenna-paallystysilmoitusten-takuupvmt})
      (julkaise-palvelu http :hae-paallystyksen-maksuerat
                        (fn [user tiedot]
                          (hae-urakan-maksuerat db user tiedot))
                        {:kysely-spec ::paallystyksen-maksuerat/hae-paallystyksen-maksuerat-kysely
                         :vastaus-spec ::paallystyksen-maksuerat/hae-paallystyksen-maksuerat-vastaus})
      (julkaise-palvelu http :tallenna-paallystyksen-maksuerat
                        (fn [user tiedot]
                          (tallenna-urakan-maksuerat db user tiedot))
                        {:kysely-spec ::paallystyksen-maksuerat/tallenna-paallystyksen-maksuerat-kysely
                         :vastaus-spec ::paallystyksen-maksuerat/tallenna-paallystyksen-maksuerat-vastaus})
      (julkaise-palvelu http :aseta-paallystysilmoituksen-tila
                        (fn [user tiedot]
                          (aseta-paallystysilmoituksen-tila db user tiedot))
                        {:kysely-spec ::pot-domain/aseta-paallystysilmoituksen-tila})
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :urakan-paallystysilmoitukset
      :urakan-paallystysilmoitus-paallystyskohteella
      :tallenna-paallystysilmoitus
      :tallenna-paallystyskohteet
      :tallenna-paallystysilmoitusten-takuupvmt
      :hae-paallystyksen-maksuerat
      :tallenna-paallystyksen-maksuerat
      :aseta-paallystysilmoituksen-tila)
    this))
