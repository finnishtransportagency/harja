(ns harja.palvelin.palvelut.yllapitokohteet.paallystys
  "Tässä namespacessa on esitelty palvelut, jotka liittyvät erityisesti päällystysurakkaan
   Tärkeimmät palvelut ovat päällystysilmoituksen haku/tallennus sekä määrämuutosten haku/tallennus

   Päällystysurakka on ylläpidon urakka ja siihen liittyy keskeisenä osana ylläpitokohteet.
   Ylläpitokohteiden hallintaan on olemassa oma palvelu."
  (:require [com.stuartsierra.component :as component]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [cheshire.core :as cheshire]
            [taoensso.timbre :as log]
            [hiccup.core :refer [html]]

            [clojure.set :as clj-set]
            [clojure.string :as s]
            [clojure.java.jdbc :as jdbc]

            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.kyselyt
             [kommentit :as kommentit-q]
             [paallystys-kyselyt :as q]
             [urakat :as urakat-q]
             [sopimukset :as sopimukset-q]
             [konversio :as konversio]
             [yllapitokohteet :as yllapitokohteet-q]
             [paikkaus :as paikkaus-q]
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
             [tierekisteri :as tr-domain]
             [paikkaus :as paikkaus-domain]]
            [harja.palvelin.palvelut
             [yha-apurit :as yha-apurit]
             [yllapitokohteet :as yllapitokohteet]
             [viestinta :as viestinta]]
            [harja.palvelin.palvelut.yllapitokohteet
             [maaramuutokset :as maaramuutokset]
             [yleiset :as yy]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut transit-vastaus]]
            [harja.tyokalut.html :refer [sanitoi]]
            [clojure.set :as set]
            [clj-time.coerce :as coerce]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.palvelut.yllapitokohteet.paallystyskohteet-excel :as p-excel]
            [harja.palvelin.komponentit.excel-vienti :as excel-vienti]
            [harja.pvm :as pvm]
            [specql.core :as specql]
            [dk.ative.docjure.spreadsheet :as xls])
  (:import (org.postgresql.util PSQLException)))

(defn onko-pot2?
  "Palauttaa booleanin, onko kyseinen päällystysilmoitus POT2. False = POT1."
  [paallystysilmoitus]
  (= (:versio paallystysilmoitus) 2))

(defn hae-urakan-paallystysilmoitukset [db user {:keys [urakka-id sopimus-id vuosi paikkauskohteet? tilat elyt]}]
  (log/debug "Haetaan urakan päällystysilmoitukset. Urakka-id " urakka-id ", sopimus-id: " sopimus-id)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (let [vastaus (into []
                      (comp
                        (map #(konversio/string-polusta->keyword % [:yllapitokohdetyotyyppi])))
                      (q/hae-urakan-paallystysilmoitukset-kohteineen db  {:urakka-id urakka-id 
                                                                          :sopimus-id sopimus-id 
                                                                          :vuosi vuosi 
                                                                          :paikkauskohteet? paikkauskohteet? 
                                                                          :tilat tilat
                                                                          :elyt elyt}))]
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
                                #(assoc % :kokonaishinta (yllapitokohteet-domain/yllapitokohteen-kokonaishinta % vuosi))
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

(def pot2-paallystekerroksen-avaimet
  #{:kohdeosa-id :tr-kaista :tr-ajorata :tr-loppuosa :tr-alkuosa :tr-loppuetaisyys :nimi
    :tr-alkuetaisyys :tr-numero :materiaali :toimenpide :piennar :kokonaismassamaara
    :leveys :pinta_ala :massamenekki :jarjestysnro :pot2p_id
    :velho-lahetyksen-aika :velho-lahetyksen-vastaus :velho-rivi-lahetyksen-tila :rc-prosentti})

(defn- laske-kulutuskerroksen-hypyt
  "Laskee ja palauttaa kulutuskerroksessa hyppyjen määrän integerinä"
  [data i hypyt]
  (if (> (count data) (dec i))
    (let [rivi (get-in data [i])
          rivi-tie (:tr-numero rivi)
          rivi-ajorata (:tr-ajorata rivi)
          rivi-aet (:tr-alkuetaisyys rivi)
          rivi-let (:tr-loppuetaisyys rivi)
          rivi-kaista (:tr-kaista rivi)

          seuraava-rivi (get-in data [(inc i)])
          seuraava-rivi-tie (:tr-numero seuraava-rivi)
          seuraava-rivi-ajorata (:tr-ajorata seuraava-rivi)
          seuraava-rivi-aet (:tr-alkuetaisyys seuraava-rivi)
          seuraava-rivi-kaista (:tr-kaista seuraava-rivi)

          hyppy-olemassa? (if (and
                                rivi-aet seuraava-rivi-aet
                                (= rivi-tie seuraava-rivi-tie)
                                (= rivi-ajorata seuraava-rivi-ajorata)
                                (= rivi-kaista seuraava-rivi-kaista)
                                (> seuraava-rivi-aet rivi-let))
                            true
                            false)]
      (if hyppy-olemassa?
        (recur data (inc i) (inc hypyt))
        (recur data (inc i) hypyt)))
    hypyt))

(defn- tunnista-paallystys-hypyt
  "Tunnistaa päällystyksessä tapahtuvat hypyt
   Palauttaa vectorin kohdeosista joihin lisätty hyppyjen tiedot avaimiin
   Avaimet käsitellään frontissa paallystekerros.cljs missä ne passataan muokkaus.cljs korostusta varten
   Laitetaan aluksi 50 metriä rajaksi, eli kaikki alle 50 metrin hypyt näytetään fronttiin
   Hyppy tulee tunnistaa kun mennään samalla ajoradalla, tiellä sekä samalla kaistalla. 
   Jos kaista tai tie vaihtuu, silloin tämä ei ole hyppy.
   Ajorata = Rata jossa on yksi tai usea tie (ajoradalla voi olla esim. moottoritie jossa 2 tietä)
   Tie = Tie jossa 2 kaistaa
   Kaista = Tien kaista 
   Aet = Alkuetäisyys metreinä
   Let = Loppuetäisyys metreinä"
  [y data palautus maara]
  (when (> (count data) (dec y))
    (let [rivi (nth data y nil)
          rivi-tie (:tr-numero rivi)
          rivi-ajorata (:tr-ajorata rivi)
          rivi-kaista (:tr-kaista rivi)
          rivi-aet (:tr-alkuetaisyys rivi)
          rivi-let (:tr-loppuetaisyys rivi)

          edellinen-rivi (nth data (dec y) nil)
          edellinen-rivi-tie (:tr-numero edellinen-rivi)
          edellinen-rivi-ajorata (:tr-ajorata edellinen-rivi)
          edellinen-rivi-kaista (:tr-kaista edellinen-rivi)
          edellinen-rivi-let (:tr-loppuetaisyys edellinen-rivi)

          seuraava-rivi (nth data (inc y) nil)
          seuraava-rivi-tie (:tr-numero seuraava-rivi)
          seuraava-rivi-ajorata (:tr-ajorata seuraava-rivi)
          seuraava-rivi-kaista (:tr-kaista seuraava-rivi)
          seuraava-rivi-aet (:tr-alkuetaisyys seuraava-rivi)

          rivi (cond
                 ;; Korosta molemmat aet/let (hyppy tällä ja edellisellä rivillä)
                 (and
                   rivi-aet seuraava-rivi-aet
                   rivi-aet edellinen-rivi-let
                   (= rivi-tie seuraava-rivi-tie)
                   (= rivi-ajorata seuraava-rivi-ajorata)
                   (= rivi-kaista seuraava-rivi-kaista)
                   (= rivi-tie edellinen-rivi-tie)
                   (= rivi-ajorata edellinen-rivi-ajorata)
                   (= rivi-kaista edellinen-rivi-kaista)
                   (> seuraava-rivi-aet rivi-let)
                   (> rivi-aet edellinen-rivi-let))
                 (merge rivi {:aet-hyppy? true :let-hyppy? true})

                 ;; Seuraava ja tämä rivi olemassa sekä molemmilla sama tie&ajorata&kaista
                 ;; Seuraavan rivin alkuetäisyys on isompi kun tämän rivin loppuetäisyys = hyppy
                 ;; (korosta LET)
                 (and
                   rivi-aet seuraava-rivi-aet
                   (= rivi-tie seuraava-rivi-tie)
                   (= rivi-ajorata seuraava-rivi-ajorata)
                   (= rivi-kaista seuraava-rivi-kaista)
                   (> seuraava-rivi-aet rivi-let))
                 (merge rivi {:let-hyppy? true})

                 ;; Tämä rivi ja edellinen rivi olemassa sekä molemmilla sama tie&ajorata&kaista
                 ;; Tämän rivin alkuetäisyys isompi kuin edellisen loppuetäisyys = hyppy 
                 ;; (korosta AET)
                 (and
                   rivi-aet edellinen-rivi-let
                   (= rivi-tie edellinen-rivi-tie)
                   (= rivi-ajorata edellinen-rivi-ajorata)
                   (= rivi-kaista edellinen-rivi-kaista)
                   (> rivi-aet edellinen-rivi-let))
                 (merge rivi {:aet-hyppy? true})

                 ;; Ei hyppyjä
                 :else rivi)
          ;; Lisätään hyppyjen määrä tieto jokaiseen riviin
          rivi (when rivi (merge rivi {:hyppyjen-maara maara}))
          ;; Älä palauta nil arvoa, tämä lisää muuten tyhjän rivin kulutuskerrostaulukkoon
          palautus (if rivi (conj palautus rivi) palautus)]
      ;; Jos riviä ei enää olemassa, palauta tiedot, muuten jatka looppia
      (if (nil? rivi)
        palautus
        (recur
          (inc y) data palautus maara)))))

(defn- pot2-paallystekerros
  "Kasaa POT2-ilmoituksen tarvitsemaan muotoon päällystekerroksen rivit
  Käyttää PO1:n kohdeosat-avaimen tietoja pohjana, ja yhdistää ne pot2_paallystekerros taulussa
  oleviin tietoihin."
  [db paallystysilmoitus]
  (let [kohdeosat (mapv
                    (fn [kohdeosa]
                      (let [paallystekerros (first
                                              (q/hae-kohdeosan-pot2-paallystekerrokset db {:pot2_id (:id paallystysilmoitus)
                                                                                           :kohdeosa_id (:id kohdeosa)}))
                            rivi (select-keys (merge kohdeosa paallystekerros
                                                ;; kohdeosan id on aina läsnä YLLAPITOKOHDEOSA-taulussa, mutta pot2_paallystekerros-taulun
                                                ;; riviä ei välttämättä ole tässä kohti vielä olemassa (jos INSERT)
                                                {:kohdeosa-id (:id kohdeosa)}) pot2-paallystekerroksen-avaimet)]
                        rivi))
                    (:kohdeosat paallystysilmoitus))
        
        kohdeosat (vec (sort-by yllapitokohteet-domain/yllapitokohteen-jarjestys kohdeosat))
        ;; Laske hypyt
        hyppyjen-maara (laske-kulutuskerroksen-hypyt kohdeosat 0 0)
        ;; Lisää avaimet riveille joissa hyppy 
        kohdeosat (tunnista-paallystys-hypyt 0 kohdeosat [] hyppyjen-maara)]
    kohdeosat))

(defn pot2-alusta
  "Kasaa POT2-ilmoituksen tarvitsemaan muotoon alustakerroksen rivit"
  [db paallystysilmoitus]
  (into []
        (q/hae-pot2-alustarivit db {:pot2_id (:id paallystysilmoitus)})))

(defn- pot2-paallystekerros-ja-alusta
  "Hakee pot2-spesifiset tiedot lomakkeelle, kuten päällystekerros ja alusta"
  [db paallystysilmoitus]
  (assoc paallystysilmoitus :paallystekerros (pot2-paallystekerros db paallystysilmoitus)
                            :alusta (when (onko-pot2? paallystysilmoitus)
                                      (pot2-alusta db paallystysilmoitus))))

(defn- pot1-kohdeosat [paallystysilmoitus]
  (first (konversio/sarakkeet-vektoriin
           paallystysilmoitus
           {:kohdeosa :kohdeosat}
           :id)))

(defn- lisaa-versio-jos-potia-ei-viela-ole [pot]
  (cond
    ;; Jos pot on jo luotu, versio on eksplisiittisesti paallystysilmoitus-taulun versio-sarakkeessa
    (:versio pot)
    pot

    ;; muutoin tutkitaan ylläpitokohteen vuodet-kenttää
    (some #(>= % pot-domain/pot2-vuodesta-eteenpain)
          (:vuodet pot))
    (assoc pot :versio 2)

    :else
    (assoc pot :versio 1)))

(defn hae-urakan-paallystysilmoitus-paallystyskohteella
  "Hakee päällystysilmoituksen ja kohteen tiedot.

   Päällystysilmoituksen kohdeosien tiedot haetaan yllapitokohdeosa-taulusta ja liitetään mukaan ilmoitukseen.

   Huomaa, että vaikka päällystysilmoitusta ei olisi tehty, tämä kysely palauttaa joka tapauksessa
   kohteen tiedot ja esitäytetyn ilmoituksen, jossa kohdeosat on syötetty valmiiksi.

   Päällystysilmoitus voidaan tehdä myös paikkauskohteille ja sitä varten on muutama poikkeus."
  [db user {:keys [urakka-id paallystyskohde-id paikkauskohde?]}]
  (assert (and urakka-id paallystyskohde-id) "Virheelliset hakuparametrit!")
  (log/debug "Haetaan urakan päällystysilmoitus, jonka päällystyskohde-id " paallystyskohde-id)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (let [paallystysilmoitus (into []
                                 (comp (map konversio/alaviiva->rakenne)
                                       (map #(konversio/jsonb->clojuremap % :ilmoitustiedot))
                                       (map #(update % :yha-tr-osoite konv/lue-tr-osoite))
                                       (map #(konversio/string-poluista->keyword
                                               %
                                               [[:tekninen-osa :paatos]
                                                [:tila]])))
                                 (q/hae-paallystysilmoitus-kohdetietoineen-paallystyskohteella
                                   db
                                   {:paallystyskohde paallystyskohde-id}))
        paallystysilmoitus (pot1-kohdeosat paallystysilmoitus)
        paallystysilmoitus (if (or (onko-pot2? paallystysilmoitus)
                                   ;; jos paallystysilmoitus puuttuu vielä, täytyy silti palauttaa päällystekerroksen kohdeosat!
                                   (nil? (:versio paallystysilmoitus)))
                             (pot2-paallystekerros-ja-alusta db paallystysilmoitus)
                             paallystysilmoitus)
        ;; Paikkauskohteen päällystysilmoituksella ei ensimmäisellä hakukerralla ole olemassa päällystyskerrosta, joten kasataan se käsin
        paallystysilmoitus (if (and paikkauskohde? (empty? (:paallystekerros paallystysilmoitus)))
                             (assoc paallystysilmoitus :paallystekerros [{:nimi (:kohdenimi paallystysilmoitus)
                                                                          :toimenpide nil
                                                                          :tr-ajorata (:tr-ajorata paallystysilmoitus)
                                                                          :tr-alkuetaisyys (:tr-alkuetaisyys paallystysilmoitus)
                                                                          :tr-alkuosa (:tr-alkuosa paallystysilmoitus)
                                                                          :tr-kaista (:tr-kaista paallystysilmoitus)
                                                                          :tr-loppuetaisyys (:tr-loppuetaisyys paallystysilmoitus)
                                                                          :tr-loppuosa (:tr-loppuosa paallystysilmoitus)
                                                                          :tr-numero (:tr-numero paallystysilmoitus)}])
                             paallystysilmoitus)

        paallystysilmoitus (update paallystysilmoitus :vuodet konversio/pgarray->vector)
        paallystysilmoitus (lisaa-versio-jos-potia-ei-viela-ole paallystysilmoitus)
        paallystysilmoitus (pyorista-kasittelypaksuus paallystysilmoitus)
        _ (when-let [ilmoitustiedot (:ilmoitustiedot paallystysilmoitus)]
            (cond
              (some #(>= % 2019) (:vuodet paallystysilmoitus)) (skeema/validoi pot-domain/+paallystysilmoitus-ilmoitustiedot+ ilmoitustiedot)
              ;; Vuonna 2018 käytettiin uutta ja vanhaa mallia
              (some #(>= % 2018) (:vuodet paallystysilmoitus)) (try
                                                                 (skeema/validoi pot-domain/+vanha-paallystysilmoitus+ ilmoitustiedot)
                                                                 (catch Exception e
                                                                   (skeema/validoi pot-domain/+paallystysilmoitus-ilmoitustiedot+ ilmoitustiedot)))
              :else (skeema/validoi pot-domain/+vanha-paallystysilmoitus+ ilmoitustiedot)))
        ;; Tyhjälle ilmoitukselle esitäytetään kohdeosat. Jos ilmoituksessa on tehty toimenpiteitä
        ;; kohdeosille, niihin liitetään kohdeosan tiedot, jotta voidaan muokata frontissa.
        paallystysilmoitus (as-> paallystysilmoitus p
                                 (assoc p :ilmoitustiedot
                                          (muunna-tallennetut-ilmoitustiedot-lomakemuotoon (:ilmoitustiedot p)))
                                 (taydenna-paallystysilmoituksen-kohdeosien-tiedot p))
        kokonaishinta-ilman-maaramuutoksia (yllapitokohteet-domain/yllapitokohteen-kokonaishinta paallystysilmoitus
                                                                                                 ;; käytännössä kaikilla kohteilla on vuodet-sarakkeessa vain yksi vuosi mutta se on sekvenssissä
                                                                                                 (apply max (or (:vuodet paallystysilmoitus)
                                                                                                                [(pvm/vuosi (pvm/nyt))])))
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
                               {:keys [paallystyskohde-id ilmoitustiedot perustiedot lisatiedot versio]
                                :as paallystysilmoitus}]
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
                              (skeema/validoi pot-domain/+paallystysilmoitus-ilmoitustiedot+)
                              (cheshire/encode)))]
    (:id (q/luo-paallystysilmoitus<!
           db
           {:paallystyskohde paallystyskohde-id
            :tila tila
            :versio versio
            :ilmoitustiedot ilmoitustiedot
            :takuupvm (konversio/sql-date takuupvm)
            :kayttaja (:id user)
            :lisatiedot lisatiedot}))))

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

(defn- tallenna-paikkauskohteen-poikkeukset
  "Ottaa vastaan paikkausilmoituksen perustiedot ja tallentaa niistä saadut tiedot ylläpitokohde_aikataulu -tauluun"
  [db perustiedot paallystyskohde-id urakka-id kayttaja-id]
  (let [;; Muokkaa takuupäivämäärästä päällystyksen loppupäivämäärän suhteen takuuajanmukainen
        ;; eli lisää takuuaika (vuodet) päällystyksen loppuaikaan
        takuupvm (when (and (:valmispvm-paallystys perustiedot) (:takuuaika perustiedot))
                   (coerce/to-sql-time
                     (pvm/ajan-muokkaus
                       (pvm/joda-timeksi (:valmispvm-paallystys perustiedot))
                       true (:takuuaika perustiedot) :vuosi)))
        ;; Tallennetaan ylläpitokohteelle aikataulu
        _ (yllapitokohteet-q/tallenna-paallystyskohteen-aikataulu!
            db {:id paallystyskohde-id ;; Nimestä huolimatta päällystyskohde ja ylläpitokohde ovat sama asia
                :urakka urakka-id
                :aikataulu_kohde_alku (:aloituspvm perustiedot)
                :aikataulu_paallystys_alku (:paallystys-alku perustiedot)
                :aikataulu_paallystys_loppu (:valmispvm-paallystys perustiedot)
                :aikataulu_kohde_valmis (:valmispvm-kohde perustiedot)
                :aikataulu_muokkaaja kayttaja-id})
        ;; Hae kyseessä oleva paikkauskohde
        paikkauskohde (first (specql/fetch db ::paikkaus-domain/paikkauskohde
                                           (specql/columns ::paikkaus-domain/paikkauskohde)
                                           {::paikkaus-domain/id (:paikkauskohde-id perustiedot)}))
        ;; Tallenna paikkauskohteelle uusi takuuaika pot lomakkeelta
        _ (specql/upsert! db ::paikkaus-domain/paikkauskohde
                          (merge paikkauskohde
                                 {::paikkaus-domain/takuuaika (bigdec (:takuuaika perustiedot))})
                          {::paikkaus-domain/id (:paikkauskohde-id perustiedot)})]
    takuupvm))

(defn- paivita-paallystysilmoituksen-perustiedot
  [db user urakka-id
   {:keys [paallystyskohde-id ilmoitustiedot perustiedot lisatiedot] :as paallystysilmoitus}]
  (if (oikeudet/voi-kirjoittaa?
        oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
        urakka-id
        user)
    (do (log/debug "Päivitetään päällystysilmoituksen perustiedot" (pr-str perustiedot))
        (let [pot2? (onko-pot2? paallystysilmoitus)
              {:keys [takuupvm tekninen-osa valmis-kasiteltavaksi]} perustiedot
              ;; Paikkauskohteelle tehtävän potin takuupäivämäärä tulee takuuajan (vuosia) määrästä
              takuupvm (if (nil? (:takuuaika perustiedot))
                         takuupvm ;; Ei muutoksia takuupäivään
                         ;; lisätään päällystyksen loppumiseen annettu takuuaika vuosina sekä huolehditaan
                         ;; paikkauskohteen muistakin poikkeuksista
                         (tallenna-paikkauskohteen-poikkeukset db perustiedot paallystyskohde-id urakka-id (:id user)))
              tila (pot-domain/paattele-ilmoituksen-tila
                     valmis-kasiteltavaksi
                     (= (:paatos tekninen-osa) :hyvaksytty))
              ilmoitustiedot (when-not pot2?
                               (->> ilmoitustiedot
                                    (poista-ilmoitustiedoista-alikohteen-tiedot)
                                    (muunna-ilmoitustiedot-tallennusmuotoon)
                                    (skeema/validoi pot-domain/+paallystysilmoitus-ilmoitustiedot+)
                                    (cheshire/encode)))]
          (q/paivita-paallystysilmoitus<!
            db
            {:tila tila
             :takuupvm (konversio/sql-date takuupvm)
             :ilmoitustiedot ilmoitustiedot
             :muokkaaja (:id user)
             :lisatiedot lisatiedot
             :id paallystyskohde-id
             :urakka urakka-id})))
    (log/debug "Ei oikeutta päivittää perustietoja.")))

(defn- tallenna-pot-mukana-tulevat-aikataulutiedot
  [db user {:keys [paallystyskohde-id perustiedot]} urakka-id]
  (when-not (:valmispvm-kohde perustiedot)
    (throw (IllegalArgumentException. (cheshire/encode {:perustiedot "Kohteen valmistumispvm on pakollinen tieto"}))))
  (yllapitokohteet-q/tallenna-paallystyskohteen-aloitus-ja-lopetus! db
                                                                    {:id paallystyskohde-id
                                                                     :urakka urakka-id
                                                                     :aikataulu_kohde_alku (:aloituspvm perustiedot)
                                                                     :aikataulu_paallystys_valmis (:valmispvm-paallystys perustiedot)
                                                                     :aikataulu_kohde_valmis (:valmispvm-kohde perustiedot)
                                                                     :aikataulu_muokkaaja (:id user)}))

(defn- paivita-paallystysilmoitus [db user urakka-id
                                   uusi-paallystysilmoitus paallystysilmoitus-kannassa]
  ;; Ilmoituksen kaikki tiedot lähetetään aina tallennettavaksi, vaikka käyttäjällä olisi oikeus
  ;; muokata vain tiettyä osaa ilmoituksesta. Frontissa on estettyä muokkaamasta sellaisia asioita, joita
  ;; käyttäjä ei saa muokata. Täällä ilmoitus päivitetään osa kerrallaan niin, että jokaista
  ;; osaa vasten tarkistetaan tallennusoikeus.
  (try
    (log/debug "Päivitetään olemassa oleva päällystysilmoitus")

    (tarkista-paallystysilmoituksen-lukinta paallystysilmoitus-kannassa)
    (paivita-kasittelytiedot db user urakka-id uusi-paallystysilmoitus paallystysilmoitus-kannassa)
    (paivita-asiatarkastus db user urakka-id uusi-paallystysilmoitus)
    (paivita-paallystysilmoituksen-perustiedot db user urakka-id uusi-paallystysilmoitus)
    (log/debug "Päällystysilmoitus päivitetty!")
    (:id paallystysilmoitus-kannassa)
    (catch Throwable t
      (if (instance? clojure.lang.ExceptionInfo t)
        [(:error (ex-data t))]
        (throw t)))))

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
                     (sanitoi urakka-nimi)
                     (sanitoi (:nimi paallystyskohde))
                     (if sposti-urakan-valvojalle?
                       "valmis käsiteltäväksi"
                       (str "on " (if (= "hylatty" uusi-paatos) "hylätty" "hyväksytty")))))
          url (str "https://harja.vaylapilvi.fi/#urakat/kohdeluettelo-paallystys/paallystysilmoitukset?"
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
  (if pot2?
    (->> paallystysilmoitus
         :paallystekerros
         (filter (comp not :poistettu)))
    (->> paallystysilmoitus
         :ilmoitustiedot
         :osoitteet
         (filter (comp not :poistettu)))))

(defn- tallenna-pot2-paallystekerros
  [db paallystysilmoitus pot2-id paivitetyt-kohdeosat]
  (try
    (doseq [rivi (->> paallystysilmoitus
                     :paallystekerros
                     (filter (comp not :poistettu)))]
     (let [;; Kohdeosan id voi olla rivillä jo, tai sitten se ei ole vaan luotiin juuri aiemmin samassa transaktiossa, ja täytyy
           ;; tällöin kaivaa paivitetyt-kohdeosat objektista tierekisteriosoitetietojen  perusteella
           kohdeosan-id (or (:kohdeosa-id rivi) (yllapitokohteet-domain/uuden-kohdeosan-id rivi paivitetyt-kohdeosat))
           params (merge rivi
                         {:kohdeosa_id kohdeosan-id
                          :piennar (boolean (:piennar rivi)) ;; Voi jäädä tulematta frontilta
                          :lisatieto (:lisatieto rivi)
                          :pot2_id pot2-id
                          :materiaali (if (and (nil? (:materiaali rivi))
                                               (not= pot2-domain/+kulutuskerros-toimenpide-karhinta+ (:toimenpide rivi)))
                                        (throw (IllegalArgumentException.
                                                 (str "Materiaali on valinnainen vain jos toimenpide on KAR, kohdeosa-id = "
                                                      kohdeosan-id)))
                                        (:materiaali rivi))})]
       (if (:pot2p_id rivi)
         (q/paivita-pot2-paallystekerros<! db params)
         (q/luo-pot2-paallystekerros<! db params))))
    (catch Throwable t
      (throw (IllegalArgumentException. (cheshire/encode {:kulutuskerros (ex-message t)}))))))

(defn- tallenna-pot2-alustarivit
  [db paallystysilmoitus pot2-id]
  (let [alustarivit (:alusta paallystysilmoitus)
        idt-ennen-tallennusta (into #{} (map :pot2a_id (q/hae-pot2-alustarivit db {:pot2_id pot2-id})))
        hyotykuorman-idt (into #{} (map :pot2a_id alustarivit))
        poistuneet-idt (set/difference idt-ennen-tallennusta hyotykuorman-idt)]

    (q/poista-pot2-alustarivit! db {:pot2a_idt poistuneet-idt})
    (doseq [rivi (->> paallystysilmoitus
                      :alusta
                      (filter (comp not :poistettu)))]
      (let [annetut-lisaparams (pot2-domain/alusta-kaikki-lisaparams rivi)
            toimenpide (:toimenpide rivi)
            [sallittut-avaimet pakolliset-avaimet] (pot2-domain/alusta-sallitut-ja-pakolliset-lisaavaimet rivi)
            rivi-ja-kaikki-lisaparametrit (if (and (empty? (pot2-domain/alusta-ylimaaraiset-lisaparams-avaimet rivi))
                                                   (set/subset? pakolliset-avaimet (-> annetut-lisaparams keys set)))
                                            (merge rivi
                                                   {:pot2_id pot2-id}
                                                   (zipmap (keys pot2-domain/alusta-toimenpide-kaikki-lisaavaimet) (repeat nil))
                                                   annetut-lisaparams)
                                            (throw (IllegalArgumentException.
                                                     (str "Alustassa väärät lisätiedot. Toimenpide = " toimenpide
                                                          " Odotettu: "
                                                          (pr-str sallittut-avaimet) " tuli: "
                                                          (pr-str annetut-lisaparams)))))]
        (try
          (if (:pot2a_id rivi-ja-kaikki-lisaparametrit)
            (q/paivita-pot2-alusta<! db rivi-ja-kaikki-lisaparametrit)
            (q/luo-pot2-alusta<! db rivi-ja-kaikki-lisaparametrit))
          (catch PSQLException pe
            (throw (IllegalArgumentException.
                     (cheshire/encode {:alustatoimenpide (ex-message pe)})))))))))

(defn tallenna-paallystysilmoitus*
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
  (when-not (contains? paallystysilmoitus :versio)
    (throw (IllegalArgumentException. "Pyynnöstä puuttuu versio. Ota yhteyttä Harjan tukeen.")))
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kohdeluettelo-paallystysilmoitukset user urakka-id)
  (jdbc/with-db-transaction [db db]
    ;; Kirjoitusoikeudet tarkistetaan syvemällä, päivitetään vain ne osat, jotka saa
    (yy/vaadi-yllapitokohde-kuuluu-urakkaan db urakka-id (:paallystyskohde-id paallystysilmoitus))
    (yha-apurit/lukitse-urakan-yha-sidonta db urakka-id)

    (let [pot2? (onko-pot2? paallystysilmoitus)
          paallystyskohde-id (:paallystyskohde-id paallystysilmoitus)
          paikkauskohde? (q/yllapitokohde-paikkauskohde? db paallystyskohde-id)
          hae-paallystysilmoitus (fn [paallystyskohde-id]
                                       (first (into []
                                                    (comp (map #(konversio/jsonb->clojuremap % :ilmoitustiedot))
                                                          (map #(konversio/string-poluista->keyword %
                                                                                                    [[:tila]])))
                                                    (q/hae-paallystysilmoitus-paallystyskohteella
                                                     db
                                                     {:paallystyskohde paallystyskohde-id}))))

          vanha-paallystysilmoitus (hae-paallystysilmoitus paallystyskohde-id)]
      (when-not (nil? vanha-paallystysilmoitus)
        (let [versio-pyynnossa (:versio paallystysilmoitus)
              oikea-versio (:versio vanha-paallystysilmoitus)]
          (when-not (= versio-pyynnossa oikea-versio)
            (throw (IllegalArgumentException. (str "Väärä POT versio. Pyynnössä on " versio-pyynnossa
                                                   ", pitäisi olla " oikea-versio
                                                   ". Ota yhteyttä Harjan tukeen."))))))
      (let [seuraava-tila (keyword
                            (pot-domain/paattele-ilmoituksen-tila
                              (get-in paallystysilmoitus [:perustiedot :valmis-kasiteltavaksi])
                              (= :hyvaksytty (get-in paallystysilmoitus [:perustiedot :tekninen-osa :paatos]))))
            tr-osoite (-> paallystysilmoitus :perustiedot :tr-osoite)
            ali-ja-muut-kohteet (remove :poistettu (if pot2?
                                                     (-> paallystysilmoitus :paallystekerros)
                                                     (-> paallystysilmoitus :ilmoitustiedot :osoitteet)))
            alustatoimet (if pot2?
                           (-> paallystysilmoitus :alusta)
                           (-> paallystysilmoitus :ilmoitustiedot :alustatoimet))]

        ;; Validoidaan data ainoastaan silloin, kun päällystysilmoituksen seuraava tila on jotain muuta kuin "aloitettu".
        ;;  Eli, päällystysilmoitus on jo kerran tallennettu kantaan ja se olisi siirtymässä tilaan valmis tai lukittu.
        (when (not= seuraava-tila :aloitettu)
          (let [virheviestit (yllapitokohteet-domain/validoi-kaikki-backilla
                               db paallystyskohde-id urakka-id vuosi tr-osoite ali-ja-muut-kohteet alustatoimet)]
            (when (seq virheviestit)
              (throw (IllegalArgumentException. (cheshire/encode virheviestit))))))


        (let [paivitetyt-kohdeosat (yllapitokohteet/tallenna-yllapitokohdeosat
                                     db user {:urakka-id urakka-id :sopimus-id sopimus-id
                                              :vuosi vuosi
                                              :versio (:versio paallystysilmoitus)
                                              :yllapitokohde-id paallystyskohde-id
                                              :osat (map #(assoc % :id (:kohdeosa-id %))
                                                      (ilmoituksen-kohdeosat paallystysilmoitus pot2?))})
              _ (when-let [virhe (:validointivirheet paivitetyt-kohdeosat)]
                  (throw (IllegalArgumentException. (cheshire/encode virhe))))
              _ (when-not paikkauskohde?
                  (tallenna-pot-mukana-tulevat-aikataulutiedot db user paallystysilmoitus urakka-id))
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
              _ (when (and (not vanha-paallystysilmoitus) paikkauskohde?)
                  (tallenna-paikkauskohteen-poikkeukset db (:perustiedot paallystysilmoitus) paallystyskohde-id urakka-id (:id user)))
              _ (when-not (number? paallystysilmoitus-id)
                  (throw (IllegalArgumentException. (cheshire/encode paallystysilmoitus-id))))
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
              pot2-alustarivit (when pot2? (tallenna-pot2-alustarivit db paallystysilmoitus
                                             paallystysilmoitus-id))
              tuore-paallystysilmoitus (hae-paallystysilmoitus paallystyskohde-id)]


          (tallenna-paallystysilmoituksen-kommentti db user paallystysilmoitus paallystysilmoitus-id)
          (laheta-paallystysilmoitussahkoposti-tarvittaessa {:db db :fim fim :email email :urakka-id urakka-id
                                                             :paallystyskohde-id paallystyskohde-id
                                                             :uusi-tila (:tila tuore-paallystysilmoitus)
                                                             :vanha-tila (:tila vanha-paallystysilmoitus)
                                                             :uusi-paatos (:tekninen-osa_paatos tuore-paallystysilmoitus)
                                                             :vanha-paatos (:tekninen-osa_paatos vanha-paallystysilmoitus)})

          ;; Poista mahdolliset vanhat virheet tietokannasta lopullisen tallennuksen onnistuessa
          ;; Säilytetään virheet kuitenkin, jos käyttäjä on kokeillut esimerkiksi lähettää ilmoitusta tarkastettavaksi,
          ;; mutta muokkaakin ilmoitusta sen jälkeen
          (when-not (= :aloitettu (:tila tuore-paallystysilmoitus))
            (q/paivita-paallystysilmoituksen-virhe! db {:id paallystyskohde-id :virhe nil :aikaleima nil}))

          ;; Rakennetaan vastaus
          (let [yllapitokohteet (yllapitokohteet/hae-urakan-yllapitokohteet db user {:urakka-id urakka-id
                                                                                     :sopimus-id sopimus-id
                                                                                     :vuosi vuosi})
                uudet-ilmoitukset (hae-urakan-paallystysilmoitukset db user {:urakka-id urakka-id
                                                                             :sopimus-id sopimus-id
                                                                             :vuosi vuosi})]
            {:yllapitokohteet yllapitokohteet
             :paallystysilmoitukset uudet-ilmoitukset}))))))

(defn tallenna-paallystysilmoitus
  "Tallentaa päällysilmoituksen ja tallennukseen liittyvät mahdolliset validointivirheet kantaan.
  Virheet throwataan varsinaisen päällystysilmoituksen tallentamisen ja validoinnin toteuttavassa funktiossa ja napataan täällä.
  Virheet tallennetaan tietokantaan erillisessä transaktiossa."
  [db user fim email {:keys [urakka-id sopimus-id vuosi paallystysilmoitus] :as parametrit}]
  (try
    (tallenna-paallystysilmoitus* db user fim email parametrit)
    (catch IllegalArgumentException e
      (jdbc/with-db-transaction [db db]
        (let [paallystyskohde-id (:paallystyskohde-id paallystysilmoitus)]
          ;; Tallenna tallennukseen/validointiin liittyvät virheviestit kantaan, jotta niitä voidaan tutkia myöhemmin tarvittaessa
          (when paallystyskohde-id
            (q/paivita-paallystysilmoituksen-virhe! db {:id paallystyskohde-id
                                                        :virhe (.getMessage e)
                                                        :aikaleima (pvm/nyt)}))))

      ;; Heitetään virhe uudestaan, jotta UI voi prosessoida virheviestin
      (throw (IllegalArgumentException. (.getMessage e))))))

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


(defn- kasittele-excel [db user {:keys [urakka-id req]}]
  (let [workbook (xls/load-workbook-from-file (:path (bean (get-in req [:params "file" :tempfile]))))
        kohteet (p-excel/tallenna-paallystyskohteet-excelista db user workbook urakka-id)]
   kohteet))

(defn lue-paallystysten-kustannusexcel [db req]
  (let [urakka-id (Integer/parseInt (get (:params req) "urakka-id"))
        sopimus-id (:id (first (sopimukset-q/hae-urakan-paasopimus db {:urakka urakka-id})))
        vuosi (Integer/parseInt (get (:params req) "vuosi"))
        kayttaja (:kayttaja req)]
    (assert (int? urakka-id) "Urakka ID:n parsinta epäonnistui")
    (assert kayttaja "Käyttäjän parsinta epäonnistui")
    (yy/tarkista-urakkatyypin-mukainen-kirjoitusoikeus db kayttaja urakka-id)
    ;; Tarkistetaan, että kutsussa on mukana urakka ja kayttaja
    (jdbc/with-db-transaction [db db]
      (kasittele-excel db kayttaja {:urakka-id urakka-id
                                    :sopimus-id sopimus-id
                                    :vuosi vuosi
                                    :req req})
      ;; palautetaan päivittyneet kohteet käyttöliittymää varten
      (transit-vastaus
        (yllapitokohteet/hae-urakan-yllapitokohteet db kayttaja {:urakka-id urakka-id
                                                                 :sopimus-id sopimus-id
                                                                 :vuosi vuosi})))))

(defrecord Paallystys []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)
          fim (:fim this)
          email (:api-sahkoposti this)
          excel (:excel-vienti this)]
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
      (julkaise-palvelu http :tuo-paallystyskustannukset-excelista
                        (wrap-multipart-params (fn [req] (lue-paallystysten-kustannusexcel db req)))
                        {:ring-kasittelija? true})
      (julkaise-palvelu http :laske-tieosoitteen-pituus
        (fn [user kohde]
          (tieverkko-q/laske-tierekisteriosoitteen-pituus db kohde)))
      (when excel
        (excel-vienti/rekisteroi-excel-kasittelija! excel :paallystyskohteet-excel
                                                    (partial #'p-excel/vie-paallystyskohteet-exceliin db)))
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
      :aseta-paallystysilmoituksen-tila
      :laske-tieosoitteen-pituus
      :tuo-paallystyskustannukset-excelista)
    (when (:excel-vienti this)
      (excel-vienti/poista-excel-kasittelija! (:excel-vienti this) :paallystyskohteet-excel))
    this))
