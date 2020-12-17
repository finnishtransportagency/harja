(ns harja.palvelin.palvelut.yllapitokohteet.paallystys
  "Tässä namespacessa on esitelty palvelut, jotka liittyvät erityisesti päällystysurakkaan
   Tärkeimmät palvelut ovat päällystysilmoituksen haku/tallennus sekä määrämuutosten haku/tallennus

   Päällystysurakka on ylläpidon urakka ja siihen liittyy keskeisenä osana ylläpitokohteet.
   Ylläpitokohteiden hallintaan on olemassa oma palvelu."
  (:require [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [taoensso.timbre :as log]
            [hiccup.core :refer [html]]

            [clojure.set :as clj-set]
            [clojure.java.jdbc :as jdbc]

            [harja.kyselyt
             [kommentit :as kommentit-q]
             [paallystys :as q]
             [urakat :as urakat-q]
             [konversio :as konversio]
             [yllapitokohteet :as yllapitokohteet-q]
             [tieverkko :as tieverkko-q]]
            [harja.domain
             [paallystysilmoitus :as pot-domain]
             [pot2 :as pot2-domain]
             [skeema :refer [Toteuma validoi] :as skeema]
             [urakka :as urakka-domain]
             [sopimus :as sopimus-domain]
             [oikeudet :as oikeudet]
             [paallystyksen-maksuerat :as paallystyksen-maksuerat]
             [yllapitokohde :as yllapitokohteet-domain]
             [tierekisteri :as tr-domain]]
            [harja.palvelin.palvelut
             [yha-apurit :as yha-apurit]
             [yllapitokohteet :as yllapitokohteet]
             [viestinta :as viestinta]]
            [harja.palvelin.palvelut.yllapitokohteet
             [maaramuutokset :as maaramuutokset]
             [yleiset :as yy]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.tyokalut.html :refer [sanitoi]]))

(defn onko-pot2?
  "Palauttaa booleanin, onko kyseinen päällystysilmoitus POT2. False = POT1."
  [paallystysilmoitus]
  (= (:pot-versio paallystysilmoitus) 2))

(defn hae-urakan-paallystysilmoitukset [db user {:keys [urakka-id sopimus-id vuosi]}]
  (log/debug "Haetaan urakan päällystysilmoitukset. Urakka-id " urakka-id ", sopimus-id: " sopimus-id)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (let [vastaus (into []
                      (comp
                        (map #(konversio/string-polusta->keyword % [:yllapitokohdetyotyyppi])))
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
                              (konversio/sarakkeet-vektoriin
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

(def pot2-kulutuskerroksen-avaimet
  #{:kohdeosa-id :tr-kaista :tr-ajorata :tr-loppuosa :tr-alkuosa :tr-loppuetaisyys :nimi
    :tr-alkuetaisyys :tr-numero :materiaali :toimenpide :piennar :kokonaismassamaara
    :leveys :pinta_ala :massamenekki :pot2p_id})

(defn- pot2-kulutuskerros
  "Kasaa POT2-ilmoituksen tarvitsemaan muotoon päällystekerroksen (kulutuskerros) rivit.
  Käyttää PO1:n kohdeosat-avaimen tietoja pohjana, ja yhdistää ne kulutuskerros-avaimen alle pot2_paallystekerros taulussa
  oleviin tietoihin."
  [db paallystysilmoitus]
  (let [kulutuskerros
        (mapv (fn [kohdeosa]
                (let [kulutuskerros (first
                                      (q/hae-kohdeosan-pot2-paallystekerrokset db {:pot2_id (:id paallystysilmoitus)
                                                                                   :kohdeosa_id (:id kohdeosa)}))
                      rivi (select-keys (merge kohdeosa kulutuskerros
                                               ;; kohdeosan id on aina läsnä YLLAPITOKOHDEOSA-taulussa, mutta pot2_paallystekerros-taulun
                                               ;; riviä ei välttämättä ole tässä kohti vielä olemassa (jos INSERT)
                                               {:kohdeosa-id (:id kohdeosa)}) pot2-kulutuskerroksen-avaimet)]
                  rivi))
              (:kohdeosat paallystysilmoitus))]
    (assoc paallystysilmoitus :kulutuskerros kulutuskerros)))

(defn- pot1-kohdeosat [paallystysilmoitus]
  (first (konversio/sarakkeet-vektoriin
           paallystysilmoitus
           {:kohdeosa :kohdeosat}
           :id)))

(defn hae-urakan-paallystysilmoitus-paallystyskohteella     ; petar
  "Hakee päällystysilmoituksen ja kohteen tiedot.

   Päällystysilmoituksen kohdeosien tiedot haetaan yllapitokohdeosa-taulusta ja liitetään mukaan ilmoitukseen.

   Huomaa, että vaikka päällystysilmoitusta ei olisi tehty, tämä kysely palauttaa joka tapauksessa
   kohteen tiedot ja esitäytetyn ilmoituksen, jossa kohdeosat on syötetty valmiiksi."
  [db user {:keys [urakka-id paallystyskohde-id]}]
  (assert (and urakka-id paallystyskohde-id) "Virheelliset hakuparametrit!")
  (log/debug "Haetaan urakan päällystysilmoitus, jonka päällystyskohde-id " paallystyskohde-id)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (yy/vaadi-yllapitokohde-kuuluu-urakkaan db urakka-id paallystyskohde-id)
  (let [paallystysilmoitus (into []
                                 (comp (map konversio/alaviiva->rakenne)
                                       (map #(konversio/jsonb->clojuremap % :ilmoitustiedot))
                                       (map #(konversio/string-poluista->keyword
                                               %
                                               [[:tekninen-osa :paatos]
                                                [:tila]])))
                                 (q/hae-paallystysilmoitus-kohdetietoineen-paallystyskohteella
                                   db
                                   {:paallystyskohde paallystyskohde-id}))
        paallystysilmoitus (pot1-kohdeosat paallystysilmoitus)
        paallystysilmoitus (if (or (onko-pot2? paallystysilmoitus)
                                   ;; jos paallystysilmoitus puuttuu vielä, täytyy siltä palauttaa kulutuskerroksen kohdeosat!
                                   (nil? (:pot-versio paallystysilmoitus)))
                             (pot2-kulutuskerros db paallystysilmoitus)
                             paallystysilmoitus)
        paallystysilmoitus (update paallystysilmoitus :vuodet konversio/pgarray->vector)
        paallystysilmoitus (pyorista-kasittelypaksuus paallystysilmoitus)
        - (println "petar hajde da vidimo sta je vratio " (pr-str paallystysilmoitus))
        _ (when-let [ilmoitustiedot (:ilmoitustiedot paallystysilmoitus)]
            (println "petar OVDE NISAM SME DA UDJEM za POT2")
            (cond
              (some #(>= % 2019) (:vuodet paallystysilmoitus)) (skeema/validoi pot-domain/+paallystysilmoitus+ ilmoitustiedot)
              ;; Vuonna 2018 käytettiin uutta ja vanhaa mallia
              (some #(>= % 2018) (:vuodet paallystysilmoitus)) (try
                                                                 (skeema/validoi pot-domain/+vanha-paallystysilmoitus+ ilmoitustiedot)
                                                                 (catch Exception e
                                                                   (skeema/validoi pot-domain/+paallystysilmoitus+ ilmoitustiedot)))
              :else (skeema/validoi pot-domain/+vanha-paallystysilmoitus+ ilmoitustiedot)))
        ;; Tyhjälle ilmoitukselle esitäytetään kohdeosat. Jos ilmoituksessa on tehty toimenpiteitä
        ;; kohdeosille, niihin liitetään kohdeosan tiedot, jotta voidaan muokata frontissa.
        paallystysilmoitus (as-> paallystysilmoitus p
                                 (assoc p :ilmoitustiedot
                                          (muunna-tallennetut-ilmoitustiedot-lomakemuotoon (:ilmoitustiedot p)))
                                 (taydenna-paallystysilmoituksen-kohdeosien-tiedot p))
        kokonaishinta-ilman-maaramuutoksia (yllapitokohteet-domain/yllapitokohteen-kokonaishinta paallystysilmoitus)
        kommentit (into []
                        (comp (map konversio/alaviiva->rakenne)
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
                               {:keys [paallystyskohde-id ilmoitustiedot perustiedot pot-versio]
                                :as paallystysilmoitus}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (log/debug "Luodaan uusi päällystysilmoitus.")
  (let [pot2? (onko-pot2? paallystysilmoitus)
        {:keys [takuupvm tekninen-osa valmis-kasiteltavaksi]} perustiedot
        tila (pot-domain/paattele-ilmoituksen-tila
               valmis-kasiteltavaksi
               (= (:paatos tekninen-osa) :hyvaksytty))
        ilmoitustiedot (when-not pot2?
                         (->> ilmoitustiedot
                              (poista-ilmoitustiedoista-alikohteen-tiedot)
                              (muunna-ilmoitustiedot-tallennusmuotoon)
                              (skeema/validoi pot-domain/+paallystysilmoitus+)
                              (cheshire/encode)))]
    (:id (q/luo-paallystysilmoitus<!
           db
           {:paallystyskohde paallystyskohde-id
            :tila tila
            :versio pot-versio
            :ilmoitustiedot ilmoitustiedot
            :takuupvm (konversio/sql-date takuupvm)
            :kayttaja (:id user)}))))

(defn- tarkista-paallystysilmoituksen-lukinta [paallystysilmoitus-kannassa]
  (log/debug "Tarkistetaan onko POT lukittu...")
  (if (= :lukittu (:tila paallystysilmoitus-kannassa))
    (do (log/debug "POT on lukittu, ei voi päivittää!")
        (throw (SecurityException. "Päällystysilmoitus on lukittu, ei voi päivittää!")))
    (log/debug "POT ei ole lukittu, vaan " (pr-str (:tila paallystysilmoitus-kannassa)))))

(defn- paivita-kasittelytiedot [db user urakka-id
                                {paallystyskohde-id :paallystyskohde-id
                                 {tekninen-osa :tekninen-osa} :perustiedot}
                                paallystysilmoitus-kannassa]
  (let [tallennettava-data {:tekninen-osa_paatos (some-> tekninen-osa :paatos name)
                            :tekninen-osa_perustelu (:perustelu tekninen-osa)
                            :tekninen-osa_kasittelyaika (konversio/sql-date (:kasittelyaika tekninen-osa))}]
    (if (oikeudet/on-muu-oikeus? "päätös" oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                                 urakka-id user)
      (do
        (log/debug "Päivitetään päällystysilmoituksen käsittelytiedot")
        (q/paivita-paallystysilmoituksen-kasittelytiedot<!
          db
          (merge
            tallennettava-data
            {:muokkaaja (:id user)
             :id paallystyskohde-id
             :urakka urakka-id})))
      (do
        ;; Lähetetään frontille virhe, jos tätä yritettiin päivittää
        (log/debug "Ei oikeutta päivittää päätöstä.")
        ;; TODO
        (if (not= tallennettava-data (select-keys paallystysilmoitus-kannassa #{:tekninen-osa_paatos :tekninen-osa_perustelu :tekninen-osa_kasittelyaika}))
          nil
          nil)))))

(defn- paivita-asiatarkastus [db user urakka-id
                              {paallystyskohde-id :paallystyskohde-id
                               {asiatarkastus :asiatarkastus} :perustiedot}]
  (let [{:keys [tarkastusaika tarkastaja hyvaksytty lisatiedot]} asiatarkastus]
    (if (oikeudet/on-muu-oikeus? "asiatarkastus" oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                                 urakka-id user)
      (do (log/debug "Päivitetään päällystysilmoituksen asiatarkastus: " asiatarkastus)
          (q/paivita-paallystysilmoituksen-asiatarkastus<!
            db
            {:asiatarkastus_pvm (konversio/sql-date tarkastusaika)
             :asiatarkastus_tarkastaja tarkastaja
             :asiatarkastus_hyvaksytty hyvaksytty
             :asiatarkastus_lisatiedot lisatiedot
             :muokkaaja (:id user)
             :id paallystyskohde-id
             :urakka urakka-id}))
      (log/debug "Ei oikeutta päivittää asiatarkastusta."))))

(defn- paivita-paallystysilmoituksen-perustiedot
  [db user urakka-id
   {:keys [paallystyskohde-id ilmoitustiedot perustiedot] :as paallystysilmoitus}]
  (if (oikeudet/voi-kirjoittaa?
        oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
        urakka-id
        user)
    (do (log/debug "Päivitetään päällystysilmoituksen perustiedot")
        (let [pot2? (onko-pot2? paallystysilmoitus)
              {:keys [takuupvm tekninen-osa valmis-kasiteltavaksi]} perustiedot
              tila (pot-domain/paattele-ilmoituksen-tila
                     valmis-kasiteltavaksi
                     (= (:paatos tekninen-osa) :hyvaksytty))
              ilmoitustiedot (when-not pot2?
                               (->> ilmoitustiedot
                                    (poista-ilmoitustiedoista-alikohteen-tiedot)
                                    (muunna-ilmoitustiedot-tallennusmuotoon)
                                    (skeema/validoi pot-domain/+paallystysilmoitus+)
                                    (cheshire/encode)))]
          (q/paivita-paallystysilmoitus<!
            db
            {:tila tila
             :takuupvm (konversio/sql-date takuupvm)
             :ilmoitustiedot ilmoitustiedot
             :muokkaaja (:id user)
             :id paallystyskohde-id
             :urakka urakka-id})))
    (log/debug "Ei oikeutta päivittää perustietoja.")))

(defn- paivita-paallystysilmoitus [db user urakka-id
                                   uusi-paallystysilmoitus paallystysilmoitus-kannassa]
  ;; Ilmoituksen kaikki tiedot lähetetään aina tallennettavaksi, vaikka käyttäjällä olisi oikeus
  ;; muokata vain tiettyä osaa ilmoituksesta. Frontissa on estettyä muokkaamasta sellaisia asioita, joita
  ;; käyttäjä ei saa muokata. Täällä ilmoitus päivitetään osa kerrallaan niin, että jokaista
  ;; osaa vasten tarkistetaan tallennusoikeus.
  (log/debug "Päivitetään olemassa oleva päällystysilmoitus")
  (tarkista-paallystysilmoituksen-lukinta paallystysilmoitus-kannassa)
  (paivita-kasittelytiedot db user urakka-id uusi-paallystysilmoitus paallystysilmoitus-kannassa)
  (paivita-asiatarkastus db user urakka-id uusi-paallystysilmoitus)
  (paivita-paallystysilmoituksen-perustiedot db user urakka-id uusi-paallystysilmoitus)
  (log/debug "Päällystysilmoitus päivitetty!")
  (:id paallystysilmoitus-kannassa))

(defn tallenna-paallystysilmoituksen-kommentti [db user uusi-paallystysilmoitus paallystysilmoitus-id]
  (when-let [uusi-kommentti (get-in uusi-paallystysilmoitus [:perustiedot :uusi-kommentti])]
    (log/info "Tallennetaan uusi kommentti: " uusi-kommentti)
    (let [kommentti (kommentit-q/luo-kommentti<! db
                                               nil
                                               (:kommentti uusi-kommentti)
                                               nil
                                               (:id user))]
      (q/liita-kommentti<! db {:paallystysilmoitus paallystysilmoitus-id
                               :kommentti (:id kommentti)}))))

(defn lisaa-paallystysilmoitukseen-kohdeosien-idt
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
                                             (or (nil? (:tr-ajorata osoite)) (= (:tr-ajorata %) (:tr-ajorata osoite)))
                                             (or (nil? (:tr-kaista osoite)) (= (:tr-kaista %) (:tr-kaista osoite)))
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
          url (str "https://extranet.vayla.fi/harja#urakat/kohdeluettelo-paallystys/paallystysilmoitukset?"
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

(defn- ilmoituksen-kohdeosat
  [paallystysilmoitus pot2?]
  (println "petar evo ovde izdvajam slojeve " (pr-str paallystysilmoitus))
  (if pot2?
    (->> paallystysilmoitus
         :kulutuskerros
         (filter (comp not :poistettu)))
    (->> paallystysilmoitus
         :ilmoitustiedot
         :osoitteet
         (filter (comp not :poistettu)))))

;; petar da li treba ovde nesto
(defn- tallenna-pot2-paallystekerros
  [db paallystysilmoitus pot2-id paivitetyt-kohdeosat]
  (doseq [rivi (->> paallystysilmoitus
                    :kulutuskerros
                    (filter (comp not :poistettu)))]
    (let [;; Kohdeosan id voi olla rivillä jo, tai sitten se ei ole vaan luotiin juuri aiemmin samassa transaktiossa, ja täytyy
          ;; tällöin kaivaa paivitetyt-kohdeosat objektista tierekisteriosoitetietojen  perusteella
          kohdeosan-id (or (:kohdeosa-id rivi) (yllapitokohteet-domain/uuden-kohdeosan-id rivi paivitetyt-kohdeosat))
          params (merge rivi
                        {:kohdeosa_id kohdeosan-id
                         :piennar (boolean (:piennar rivi)) ;; Voi jäädä tulematta frontilta
                         :lisatieto (:lisatieto rivi)
                         :pot2_id pot2-id})]
      (if (:pot2p_id rivi)
        ;; UPDATE
        (q/paivita-pot2-paallystekerros<! db params)
        ;; HOX: Tämä ao. SQL ei vielä toimi, tai ei ole testattu...
        (q/luo-pot2-paallystekerros<! db params)))))

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
  (when-not (contains? paallystysilmoitus :pot-versio)
    (throw (IllegalArgumentException. "Pyynnöstä puuttuu versio. Ota yhteyttä Harjan tukeen.")))
  (jdbc/with-db-transaction [db db]
    ;; Kirjoitusoikeudet tarkistetaan syvemällä, päivitetään vain ne osat, jotka saa
    (yy/vaadi-yllapitokohde-kuuluu-urakkaan db urakka-id (:paallystyskohde-id paallystysilmoitus))
    (yha-apurit/lukitse-urakan-yha-sidonta db urakka-id)
    (let [pot2? (onko-pot2? paallystysilmoitus)
          paallystyskohde-id (:paallystyskohde-id paallystysilmoitus)
          hae-paallystysilmoitus (fn [paallystyskohde-id]
                                       (first (into []
                                                    (comp (map #(konversio/jsonb->clojuremap % :ilmoitustiedot))
                                                          (map #(konversio/string-poluista->keyword %
                                                                                                    [[:tila]])))
                                                    (q/hae-paallystysilmoitus-paallystyskohteella
                                                     db
                                                     {:paallystyskohde paallystyskohde-id}))))

          _ (println "petar trazim ovaj paallystyskohde-id=" paallystyskohde-id)
          vanha-paallystysilmoitus (hae-paallystysilmoitus paallystyskohde-id)
          - (println "petar i nasao sam ovo " (pr-str vanha-paallystysilmoitus))
          - (println "petar a novi koji hocu da upisem je " (pr-str paallystysilmoitus))]
      (when-not (nil? vanha-paallystysilmoitus)
        (let [versio-pyynnossa (:pot-versio paallystysilmoitus)
              oikea-versio (:pot-versio vanha-paallystysilmoitus)]
          (when-not (= versio-pyynnossa oikea-versio)
            (throw (IllegalArgumentException. (str "Väärä POT versio. Pyynnössä on " versio-pyynnossa
                                                   ", pitäisi olla " oikea-versio
                                                   ". Ota yhteyttä Harjan tukeen."))))))
      (let [tr-osoite (-> paallystysilmoitus :perustiedot :tr-osoite)
            ali-ja-muut-kohteet (remove :poistettu (-> paallystysilmoitus :ilmoitustiedot :osoitteet))
            alustatoimet (-> paallystysilmoitus :ilmoitustiedot :alustatoimet)
            kohde-id (:paallystyskohde-id paallystysilmoitus)
            virheviestit (yllapitokohteet-domain/validoi-kaikki-backilla db kohde-id urakka-id vuosi tr-osoite ali-ja-muut-kohteet alustatoimet)]
        (when (seq virheviestit)
          (throw (IllegalArgumentException. (cheshire/encode virheviestit)))))
      (let [paivitetyt-kohdeosat (yllapitokohteet/tallenna-yllapitokohdeosat
                                   db user {:urakka-id urakka-id :sopimus-id sopimus-id
                                            :vuosi vuosi
                                            :pot-versio (:pot-versio paallystysilmoitus)
                                            :yllapitokohde-id paallystyskohde-id
                                            :osat (map #(assoc % :id (:kohdeosa-id %))
                                                       (ilmoituksen-kohdeosat paallystysilmoitus pot2?))})
            _ (when-let [virhe (:validointivirheet paivitetyt-kohdeosat)]
                (throw (IllegalArgumentException. (cheshire/encode virhe))))
            tallennettava-kohde (-> (:perustiedot paallystysilmoitus)
                                    (select-keys #{:tr-numero :tr-ajorata :tr-kaista :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys :kohdenumero :kohdenimi :tunnus})
                                    (clj-set/rename-keys {:kohdenimi :nimi}))
            paallystysilmoitus (if pot2?
                                 paallystysilmoitus
                                 (lisaa-paallystysilmoitukseen-kohdeosien-idt paallystysilmoitus paivitetyt-kohdeosat))
            paallystysilmoitus-id (if vanha-paallystysilmoitus
                                    (paivita-paallystysilmoitus db user urakka-id paallystysilmoitus
                                                                vanha-paallystysilmoitus)
                                    (luo-paallystysilmoitus db user urakka-id paallystysilmoitus))
            _ (q/paivita-yllapitokohde! db
                                        {:tr-alkuosa (:tr-alkuosa tallennettava-kohde)
                                         :tr-alkuetaisyys (:tr-alkuetaisyys tallennettava-kohde)
                                         :tr-loppuosa (:tr-loppuosa tallennettava-kohde)
                                         :tr-loppuetaisyys (:tr-loppuetaisyys tallennettava-kohde)
                                         :id paallystyskohde-id
                                         :urakka urakka-id
                                         :muokkaaja (:id user)})
            pot2-paallystekerros (when pot2? (tallenna-pot2-paallystekerros db paallystysilmoitus
                                                                            paallystysilmoitus-id
                                                                            paivitetyt-kohdeosat))
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
                                                                           :sopimus-id sopimus-id
                                                                           :vuosi vuosi})]
          {:yllapitokohteet yllapitokohteet
           :paallystysilmoitukset uudet-ilmoitukset})))))

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
                                                       :takuupvm (konversio/sql-date (::pot-domain/takuupvm takuupvm))})))
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
                          (tallenna-paallystysilmoitus db user fim email tiedot))
                        {:kysely-spec ::pot-domain/tallenna-paallystysilmoitus-kysely})
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
