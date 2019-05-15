(ns harja.palvelin.palvelut.yllapitokohteet
  "Tässä namespacessa on palvelut ylläpitokohteiden ja -kohdeosien hakuun ja tallentamiseen."
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.core.async :refer [go <! >! thread >!! timeout] :as async]
            [clojure.set :as set]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.yha.yha-komponentti :as yha]
            [harja.domain
             [oikeudet :as oikeudet]
             [skeema :refer [Toteuma validoi]]
             [tierekisteri :as tr]]
            [harja.kyselyt
             [konversio :as konv]
             [yllapitokohteet :as q]
             [tieverkko :as tieverkko-q]]
            [harja.palvelin.komponentit.http-palvelin
             :refer
             [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.palvelut.yhteyshenkilot :as yhteyshenkilot]
            [slingshot.slingshot :refer [throw+ try+]]
            [harja.palvelin.palvelut.yha-apurit :as yha-apurit]
            [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.domain.tiemerkinta :as tm-domain]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [clj-time.coerce :as c]
            [harja.palvelin.palvelut.yllapitokohteet.viestinta :as viestinta]
            [harja.palvelin.palvelut.yllapitokohteet.maaramuutokset :as maaramuutokset]
            [harja.palvelin.palvelut.valitavoitteet.urakkakohtaiset-valitavoitteet :as valitavoitteet]

            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yy]
            [harja.id :refer [id-olemassa?]]
            [harja.domain.tierekisteri :as tr-domain]
            [harja.domain.roolit :as roolit]
            [harja.pvm :as pvm]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.tyokalut.lukot :as lukot]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.id :as id]
            [harja.tyokalut.tietoturva :as tietoturva])
  (:use org.httpkit.fake)
  (:import (harja.domain.roolit EiOikeutta)))

(defn hae-urakan-yllapitokohteet [db user {:keys [urakka-id] :as tiedot}]
  (yy/tarkista-urakkatyypin-mukainen-lukuoikeus db user urakka-id)
  (jdbc/with-db-transaction [db db]
    (yy/hae-urakan-yllapitokohteet db tiedot)))

(defn hae-tiemerkintaurakalle-osoitetut-yllapitokohteet [db user {:keys [urakka-id vuosi]}]
  (yy/tarkista-urakkatyypin-mukainen-lukuoikeus db user urakka-id)
  (log/debug "Haetaan tiemerkintäurakalle osoitetut ylläpitokohteet.")
  (jdbc/with-db-transaction [db db]
    (let [yllapitokohteet (into []
                                (map konv/alaviiva->rakenne)
                                (q/hae-tiemerkintaurakalle-osoitetut-yllapitokohteet db {:urakka urakka-id
                                                                                         :vuosi vuosi}))
          yllapitokohteet (mapv (partial yy/lisaa-yllapitokohteelle-pituus db) yllapitokohteet)
          yllapitokohteet (konv/sarakkeet-vektoriin
                            yllapitokohteet
                            {:kohdeosa :kohdeosat})]
      yllapitokohteet)))

(defn hae-urakan-yllapitokohteet-lomakkeelle [db user {:keys [urakka-id sopimus-id]}]
  (log/debug "Haetaan urakan ylläpitokohteet laatupoikkeamalomakkeelle")
  ;; Tätä kutsutaan toistaiseksi vain laadunseurannasta. ELY_Laadunvalvojan siis mm. saatava kohteet tätä kautta,
  ;; mutta heille emme halua antaa täyttä pääsyä kohdeluetteloon
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-tarkastukset user urakka-id)
  (jdbc/with-db-transaction [db db]
    (let [vastaus (q/hae-urakan-yllapitokohteet-lomakkeelle db {:urakka urakka-id
                                                                :sopimus sopimus-id})]
      (log/debug "Ylläpitokohteet saatu: " (count vastaus) " kpl")
      vastaus)))

(defn hae-yllapitokohteen-yllapitokohdeosat [db user {:keys [urakka-id sopimus-id yllapitokohde-id]}]
  (log/debug "Haetaan ylläpitokohteen ylläpitokohdeosat. Urakka-id " urakka-id ", sopimus-id: " sopimus-id ", yllapitokohde-id: " yllapitokohde-id)
  (yy/tarkista-urakkatyypin-mukainen-lukuoikeus db user urakka-id)
  (yy/vaadi-yllapitokohde-kuuluu-urakkaan-tai-on-suoritettavana-tiemerkintaurakassa db urakka-id yllapitokohde-id)
  (let [vastaus (into []
                      q/kohdeosa-xf
                      (q/hae-urakan-yllapitokohteen-yllapitokohdeosat db {:yllapitokohde yllapitokohde-id}))]
    vastaus))

(defn- hae-urakkatyyppi [db urakka-id]
  (keyword (:tyyppi (first (q/hae-urakan-tyyppi db {:urakka urakka-id})))))

(defn- hae-paallystysurakan-aikataulu [{:keys [db urakka-id sopimus-id vuosi]}]
  (let [aikataulu (->> (q/hae-paallystysurakan-aikataulu db {:urakka urakka-id :sopimus sopimus-id :vuosi vuosi})
                       (map konv/alaviiva->rakenne)
                       (mapv #(assoc % :tiemerkintaurakan-voi-vaihtaa?
                                       (yy/yllapitokohteen-suorittavan-tiemerkintaurakan-voi-vaihtaa?
                                         db (:id %) (:suorittava-tiemerkintaurakka %)))))
        aikataulu (konv/sarakkeet-vektoriin
                    aikataulu
                    {:tarkkaaikataulu :tarkka-aikataulu})
        aikataulu (map (fn [rivi]
                         (assoc rivi :tarkka-aikataulu
                                     (map (fn [tarkka-aikataulu]
                                            (konv/string->keyword tarkka-aikataulu :toimenpide))
                                          (:tarkka-aikataulu rivi))))
                       aikataulu)
        aikataulu (map #(yy/lisaa-yllapitokohteelle-pituus db %) aikataulu)]
    aikataulu))

(defn- hae-tiemerkintaurakan-aikataulu [db urakka-id vuosi]
  (log/debug (prn-str "*************** hae-tiemerkintaurakan-aikataulu" urakka-id  vuosi))
  (let [aikataulu (into []
                        (comp
                          (map #(konv/array->set % :sahkopostitiedot_muut-vastaanottajat))
                          (map konv/alaviiva->rakenne))
                        (q/hae-tiemerkintaurakan-aikataulu db {:suorittava_tiemerkintaurakka urakka-id
                                                               :vuosi vuosi}))
        aikataulu (konv/sarakkeet-vektoriin
                    aikataulu
                    {:tarkkaaikataulu :tarkka-aikataulu})
        aikataulu (map (fn [rivi]
                         (assoc rivi :tarkka-aikataulu
                                     (map (fn [tarkka-aikataulu]
                                            (konv/string->keyword tarkka-aikataulu :toimenpide))
                                          (:tarkka-aikataulu rivi))))
                       aikataulu)
        aikataulu (map #(yy/lisaa-yllapitokohteelle-pituus db %) aikataulu)]
    aikataulu))

(defn- lisaa-yllapitokohteille-valitavoitteet
  "Lisää ylläpitokohteille välitavoitteet, mikäli käyttäjällä on oikeus nähdä urakan välitavoitteet"
  [db user urakka-id yllapitokohteet]
  (if (oikeudet/voi-lukea? oikeudet/urakat-valitavoitteet urakka-id user)
    (let [urakan-valitavoitteet (valitavoitteet/hae-urakan-valitavoitteet db user urakka-id)]
      (map (fn [yllapitokohde]
             (let [kohteen-valitavoitteet (filter
                                            #(= (:yllapitokohde-id %) (:id yllapitokohde))
                                            urakan-valitavoitteet)]
               (assoc yllapitokohde :valitavoitteet kohteen-valitavoitteet)))
           yllapitokohteet))
    yllapitokohteet))

(defn hae-urakan-aikataulu [db user {:keys [urakka-id sopimus-id vuosi]}]
  (assert (and urakka-id sopimus-id) "anna urakka-id ja sopimus-id")
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-aikataulu user urakka-id)
  (log/debug "Haetaan aikataulutiedot urakalle: " urakka-id " Vuosi: " vuosi)
  (jdbc/with-db-transaction [db db]
    ;; Urakkatyypin mukaan näytetään vain tietyt asiat, joten erilliset kyselyt
    (doall
      (let [aikataulu (case (hae-urakkatyyppi db urakka-id)
                        :paallystys
                        (hae-paallystysurakan-aikataulu {:db db :urakka-id urakka-id :sopimus-id sopimus-id :vuosi vuosi})
                        :tiemerkinta
                        (hae-tiemerkintaurakan-aikataulu db urakka-id vuosi))
            aikataulu (lisaa-yllapitokohteille-valitavoitteet db user urakka-id aikataulu)]
        aikataulu))))

(defn hae-tiemerkinnan-suorittavat-urakat [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-aikataulu user urakka-id)
  (log/debug "Haetaan tiemerkinnän suorittavat urakat.")
  (q/hae-tiemerkinnan-suorittavat-urakat db))

(defn merkitse-kohde-valmiiksi-tiemerkintaan
  "Merkitsee kohteen valmiiksi tiemerkintään annettuna päivämääränä tai peruu valmiuden.
   Palauttaa päivitetyt kohteet aikataulunäkymään"
  [db fim email user
   {:keys [urakka-id sopimus-id vuosi tiemerkintapvm
           kopio-itselle? saate kohde-id muut-vastaanottajat] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-aikataulu user urakka-id)
  (yy/vaadi-yllapitokohde-kuuluu-urakkaan db urakka-id kohde-id)
  (if tiemerkintapvm
    (log/debug "Merkitään urakan " urakka-id " kohde " kohde-id " valmiiksi tiemerkintään päivämäärällä " tiemerkintapvm)
    (log/debug "Perutaan urakan " urakka-id " kohteen " kohde-id " valmius tiemerkintään, tiemerkintapvm:n oltava nil: " tiemerkintapvm))

  (jdbc/with-db-transaction [db db]
    (let [vanha-tiemerkintapvm (:valmis-tiemerkintaan
                                 (first (q/hae-yllapitokohteen-aikataulu
                                          db {:id kohde-id})))]
      (q/merkitse-kohde-valmiiksi-tiemerkintaan<!
        db
        {:valmis_tiemerkintaan tiemerkintapvm
         :aikataulu_tiemerkinta_takaraja (-> tiemerkintapvm
                                             (c/from-date)
                                             tm-domain/tiemerkinta-oltava-valmis
                                             (c/to-date))
         :id kohde-id
         :urakka urakka-id})

      (when (or (viestinta/valita-tieto-valmis-tiemerkintaan? vanha-tiemerkintapvm tiemerkintapvm)
                (viestinta/valita-tieto-peru-valmius-tiemerkintaan? vanha-tiemerkintapvm tiemerkintapvm))
        (let [kohteen-tiedot (first (q/yllapitokohteiden-tiedot-sahkopostilahetykseen
                                      db [kohde-id]))
              kohteen-tiedot (yy/lisaa-yllapitokohteelle-pituus db kohteen-tiedot)]
          (viestinta/valita-tieto-kohteen-valmiudesta-tiemerkintaan
            {:fim fim :email email :kohteen-tiedot kohteen-tiedot
             :tiemerkintapvm tiemerkintapvm
             :kopio-itselle? kopio-itselle?
             :saate saate
             :sahkopostitiedot muut-vastaanottajat
             :kayttaja user})))

      (hae-urakan-aikataulu db user {:urakka-id urakka-id
                                     :sopimus-id sopimus-id
                                     :vuosi vuosi}))))

(defn- tallenna-paallystyskohteiden-aikataulu [{:keys [db user kohteet paallystysurakka-id
                                                       voi-tallentaa-tiemerkinnan-takarajan?] :as tiedot}]
  (log/debug "Tallennetaan päällystysurakan " paallystysurakka-id " ylläpitokohteiden aikataulutiedot.")

  (doseq [kohde kohteet]
    (yy/vaadi-yllapitokohde-kuuluu-urakkaan db paallystysurakka-id (:id kohde)))
  (jdbc/with-db-transaction [db db]
    (doseq [kohde kohteet]
      (let [kayttajan-valitsema-suorittava-tiemerkintaurakka-id (:suorittava-tiemerkintaurakka kohde)
            kohteen-nykyinen-suorittava-tiemerkintaurakka-id (:id (first (q/hae-yllapitokohteen-suorittava-tiemerkintaurakka-id
                                                                           db
                                                                           {:id (:id kohde)})))]
        (q/tallenna-paallystyskohteen-aikataulu!
          db
          {:aikataulu_kohde_alku (:aikataulu-kohde-alku kohde)
           :aikataulu_paallystys_alku (:aikataulu-paallystys-alku kohde)
           :aikataulu_paallystys_loppu (:aikataulu-paallystys-loppu kohde)
           :aikataulu_kohde_valmis (:aikataulu-kohde-valmis kohde)
           :aikataulu_muokkaaja (:id user)
           :id (:id kohde)
           :urakka paallystysurakka-id})
        (q/tallenna-yllapitokohteen-suorittava-tiemerkintaurakka!
          db
          {:suorittava_tiemerkintaurakka
           (if (= kayttajan-valitsema-suorittava-tiemerkintaurakka-id
                  kohteen-nykyinen-suorittava-tiemerkintaurakka-id)
             kohteen-nykyinen-suorittava-tiemerkintaurakka-id
             ;; Suorittajaa yritetään vaihtaa, tarkistetaan onko sallittu
             (if (yy/yllapitokohteen-suorittavan-tiemerkintaurakan-voi-vaihtaa?
                   db (:id kohde) kohteen-nykyinen-suorittava-tiemerkintaurakka-id)
               (:suorittava-tiemerkintaurakka kohde)
               kohteen-nykyinen-suorittava-tiemerkintaurakka-id))
           :id (:id kohde)
           :urakka paallystysurakka-id})
        (q/paivita-yllapitokohteen-numero-ja-nimi!
          db
          {:kohdenumero (:kohdenumero kohde)
           :nimi (:nimi kohde)
           :id (:id kohde)
           :urakka paallystysurakka-id}))
      (when voi-tallentaa-tiemerkinnan-takarajan?
        (q/tallenna-yllapitokohteen-valmis-viimeistaan-paallystysurakasta!
          db
          {:aikataulu_tiemerkinta_takaraja (:aikataulu-tiemerkinta-takaraja kohde)
           :id (:id kohde)
           :urakka paallystysurakka-id})))))

(defn- tallenna-tiemerkintakohteiden-aikataulu [{:keys [fim email db user kohteet tiemerkintaurakka-id
                                                        voi-tallentaa-tiemerkinnan-takarajan?] :as tiedot}]
  (log/debug "Tallennetaan tiemerkintäurakan " tiemerkintaurakka-id " ylläpitokohteiden aikataulutiedot.")
  (doseq [kohde kohteet]
    (yy/vaadi-yllapitokohde-osoitettu-tiemerkintaurakkaan db tiemerkintaurakka-id (:id kohde)))
  (jdbc/with-db-transaction [db db]
    (let [nykyiset-kohteet-kannassa (into [] (q/yllapitokohteiden-tiedot-sahkopostilahetykseen
                                               db (map :id kohteet)))
          valmistuneet-kohteet (viestinta/suodata-tiemerkityt-kohteet-viestintaan nykyiset-kohteet-kannassa kohteet)
          mailattavat-kohteet (filter #(pvm/sama-tai-jalkeen?
                                         ;; Asiakkaan pyynnöstä toteutettu niin, että maili lähtee vain jos
                                         ;; loppupvm on nykypäivä tai mennyt aika. Tulevaisuuteen suunniteltu
                                         ;; loppupvm ei generoi maililähetystä (ajastettu taski käsittelee ne myöhemmin).
                                         (pvm/suomen-aikavyohykkeeseen (pvm/joda-timeksi (pvm/nyt)))
                                         (pvm/suomen-aikavyohykkeeseen (pvm/joda-timeksi (:aikataulu-tiemerkinta-loppu %))))
                                      valmistuneet-kohteet)]

      (doseq [kohde kohteet]
        (q/tallenna-tiemerkintakohteen-aikataulu!
          db
          {:aikataulu_tiemerkinta_alku (:aikataulu-tiemerkinta-alku kohde)
           :aikataulu_tiemerkinta_loppu (:aikataulu-tiemerkinta-loppu kohde)
           :aikataulu_muokkaaja (:id user)
           :id (:id kohde)
           :suorittava_tiemerkintaurakka tiemerkintaurakka-id})
        (when voi-tallentaa-tiemerkinnan-takarajan?
          (q/tallenna-yllapitokohteen-valmis-viimeistaan-tiemerkintaurakasta!
            db
            {:aikataulu_tiemerkinta_takaraja (:aikataulu-tiemerkinta-takaraja kohde)
             :id (:id kohde)
             :suorittava_tiemerkintaurakka tiemerkintaurakka-id}))
        ;; Tallenna käyttäjän kirjoittajamat vastaanottajat, selite ja kopio-viesti.
        ;; Tietojen on oltava tallessa kannassa, koska viestinvälitys lukee ne aina kannasta.
        ;; Näin siksi, että toteutus olisi yhtenäinen riippumatta siitä, lähetetäänkö viesti
        ;; heti, vai myöhemmin.
        (q/poista-valmistuneen-tiemerkinnan-sahkopostitiedot! db {:yllapitokohde_id (:id kohde)})
        (q/tallenna-valmistuneen-tiemerkkinnan-sahkopostitiedot<!
          db
          {:yllapitokohde_id (:id kohde)
           :vastaanottajat (konv/seq->array (get-in kohde [:sahkopostitiedot :muut-vastaanottajat]))
           :saate (get-in kohde [:sahkopostitiedot :saate])
           :kopio_lahettajalle (boolean (get-in kohde [:sahkopostitiedot :kopio-itselle?]))}))

      (viestinta/valita-tieto-tiemerkinnan-valmistumisesta
        {:kayttaja user
         :fim fim
         :db db
         :email email
         :valmistuneet-kohteet (into [] (q/yllapitokohteiden-tiedot-sahkopostilahetykseen
                                          db (map :id mailattavat-kohteet)))}))))

(declare tallenna-yllapitokohteiden-tarkka-aikataulu)

(defn tallenna-yllapitokohteiden-aikataulu
  "Tallentaa ylläpitokohteiden aikataulun.

   Tallentaa ns. 'perusaikataulun' sekä tarkan aikataulun, mikäli sellainen kohteelta löytyy."
  [db fim email user {:keys [urakka-id sopimus-id vuosi kohteet] :as tiedot}]
  (assert (and urakka-id sopimus-id kohteet) (str "Anna urakka-id, sopimus-id ja kohteet. Sain: " urakka-id sopimus-id kohteet))
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-aikataulu user urakka-id)
  (log/debug "Tallennetaan urakan " urakka-id " ylläpitokohteiden aikataulutiedot: " kohteet)
  (jdbc/with-db-transaction [db db]
    (let [voi-tallentaa-tiemerkinnan-takarajan?
          (oikeudet/on-muu-oikeus? "TM-valmis"
                                   oikeudet/urakat-aikataulu
                                   urakka-id
                                   user)]
      (case (hae-urakkatyyppi db urakka-id)
        ;; Päällystysurakoitsija ja tiemerkkari eivät saa muokata samoja asioita,
        ;; siksi urakkatyypin mukainen kysely
        :paallystys
        (tallenna-paallystyskohteiden-aikataulu
          {:db db :user user
           :kohteet kohteet
           :paallystysurakka-id urakka-id
           :voi-tallentaa-tiemerkinnan-takarajan? voi-tallentaa-tiemerkinnan-takarajan?})
        :tiemerkinta
        (tallenna-tiemerkintakohteiden-aikataulu
          {:fim fim :email email :db db :user user
           :kohteet kohteet
           :tiemerkintaurakka-id urakka-id
           :voi-tallentaa-tiemerkinnan-takarajan? voi-tallentaa-tiemerkinnan-takarajan?}))

      ;; Päivitä myös tarkka aikataulu, mikäli sellainen payloadissa on.
      (doseq [{:keys [id tarkka-aikataulu] :as kohde} kohteet]
        (tallenna-yllapitokohteiden-tarkka-aikataulu db user {:urakka-id urakka-id
                                                              :sopimus-id sopimus-id
                                                              :vuosi vuosi
                                                              :yllapitokohde-id id
                                                              :aikataulurivit tarkka-aikataulu}))))

  (log/debug "Aikataulutiedot tallennettu!")
  (hae-urakan-aikataulu db user {:urakka-id urakka-id
                                 :sopimus-id sopimus-id
                                 :vuosi vuosi}))

(defn tallenna-yllapitokohteiden-tarkka-aikataulu
  [db user {:keys [urakka-id sopimus-id vuosi yllapitokohde-id aikataulurivit] :as tiedot}]
  (assert (and urakka-id sopimus-id yllapitokohde-id) (str "Anna urakka-id, sopimus-id, yllapitokohde-id."
                                                           " Sain: " urakka-id "," sopimus-id "," yllapitokohde-id))
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-aikataulu user urakka-id)
  (yy/vaadi-yllapitokohde-kuuluu-urakkaan-tai-on-suoritettavana-tiemerkintaurakassa db urakka-id yllapitokohde-id)
  ;; Aikataulurivin kuulumista ylläpitokohteeseen tai urakkaan ei tarkisteta erikseen, vaan sisältyy UPDATE-kyselyn WHERE-lauseeseen.

  (log/debug "Tallennetaan urakan " urakka-id " ylläpitokohteiden yksityiskohtaiset aikataulutiedot: " aikataulurivit)

  (jdbc/with-db-transaction [db db]
    (doseq [rivi aikataulurivit]
      (if (id/id-olemassa? (:id rivi))
        (q/paivita-yllapitokohteen-tarkka-aikataulu!
          db
          {:toimenpide (name (:toimenpide rivi))
           :kuvaus (:kuvaus rivi)
           :alku (konv/sql-date (:alku rivi))
           :loppu (konv/sql-date (:loppu rivi))
           :muokkaaja (:id user)
           :poistettu (true? (:poistettu rivi))
           :id (:id rivi)
           :yllapitokohde yllapitokohde-id
           :urakka urakka-id})
        (q/lisaa-yllapitokohteen-tarkka-aikataulu!
          db
          {:urakka urakka-id
           :yllapitokohde yllapitokohde-id
           :toimenpide (name (:toimenpide rivi))
           :kuvaus (:kuvaus rivi)
           :alku (konv/sql-date (:alku rivi))
           :loppu (konv/sql-date (:loppu rivi))
           :luoja (:id user)}))))

  (log/debug "Aikataulutiedot tallennettu!")
  (hae-urakan-aikataulu db user {:urakka-id urakka-id
                                 :sopimus-id sopimus-id
                                 :vuosi vuosi}))

(defn- luo-uusi-yllapitokohdeosa [db user yllapitokohde-id
                                  {:keys [nimi tunnus tr-numero tr-alkuosa tr-alkuetaisyys tr-loppuosa
                                          tr-loppuetaisyys tr-ajorata tr-kaista toimenpide poistettu
                                          paallystetyyppi raekoko tyomenetelma massamaara]}]
  (log/debug "Luodaan uusi ylläpitokohdeosa, jonka ylläpitokohde-id: " yllapitokohde-id)
  (when-not poistettu
    (q/luo-yllapitokohdeosa<! db
                              {:yllapitokohde yllapitokohde-id
                               :nimi nimi
                               :tunnus tunnus
                               :tr_numero tr-numero
                               :tr_alkuosa tr-alkuosa
                               :tr_alkuetaisyys tr-alkuetaisyys
                               :tr_loppuosa tr-loppuosa
                               :tr_loppuetaisyys tr-loppuetaisyys
                               :tr_ajorata tr-ajorata
                               :tr_kaista tr-kaista
                               :paallystetyyppi paallystetyyppi
                               :raekoko raekoko
                               :tyomenetelma tyomenetelma
                               :massamaara massamaara
                               :toimenpide toimenpide
                               :ulkoinen-id nil})))

(defn- paivita-yllapitokohdeosa [db user urakka-id
                                 {:keys [id nimi tunnus tr-numero tr-alkuosa tr-alkuetaisyys
                                         tr-loppuosa tr-loppuetaisyys tr-ajorata
                                         tr-kaista toimenpide paallystetyyppi raekoko tyomenetelma massamaara]
                                  :as kohdeosa}]
  (log/debug "Päivitetään ylläpitokohdeosa")
  (q/paivita-yllapitokohdeosa<! db
                                {:nimi nimi
                                 :tr_numero tr-numero
                                 :tr_alkuosa tr-alkuosa
                                 :tr_alkuetaisyys tr-alkuetaisyys
                                 :tr_loppuosa tr-loppuosa
                                 :tr_loppuetaisyys tr-loppuetaisyys
                                 :tr_ajorata tr-ajorata
                                 :tr_kaista tr-kaista
                                 :paallystetyyppi paallystetyyppi
                                 :raekoko raekoko
                                 :tyomenetelma tyomenetelma
                                 :massamaara massamaara
                                 :toimenpide toimenpide
                                 :id id
                                 :urakka urakka-id}))

(defn tallenna-yllapitokohdeosat
  "Tallentaa ylläpitokohdeosat kantaan.

   Olettaa saavansa kohteen kaikki osat, ja tämän oletuksen varassa tarkistaa,
   tuleeko kohdeosat päivittää, poistaa vai luoda uutena.

   Palauttaa kohteen päivittyneet kohdeosat."
  [db user {:keys [urakka-id sopimus-id yllapitokohde-id osat osatyyppi vuosi] :as tiedot}]
  (yy/tarkista-urakkatyypin-mukainen-kirjoitusoikeus db user urakka-id)
  (yy/vaadi-yllapitokohde-kuuluu-urakkaan db urakka-id yllapitokohde-id)
  (jdbc/with-db-transaction [db db]
    (let [kohteen-tienumero (:tr-numero (first (q/hae-yllapitokohde db {:id yllapitokohde-id})))
          muut-kohteet (filter #(not= (:tr-numero %) kohteen-tienumero) osat)]
      (let [paallekkaiset-kohdeosat (yy/paallekkaiset-kohdeosat-saman-vuoden-osien-kanssa
                                      db
                                      yllapitokohde-id
                                      vuosi
                                      muut-kohteet)]
        (if-not (empty? paallekkaiset-kohdeosat)
          {:validointivirheet paallekkaiset-kohdeosat}
          (do
            (yha-apurit/lukitse-urakan-yha-sidonta db urakka-id)
            (let [hae-kaikki-osat #(hae-yllapitokohteen-yllapitokohdeosat db user
                                                                          {:urakka-id urakka-id
                                                                           :sopimus-id sopimus-id
                                                                           :yllapitokohde-id yllapitokohde-id})
                  kaikki-osat (hae-kaikki-osat)
                  tarkasteltavat-osat (case osatyyppi
                                        :kohteen-omat-kohdeosat (filter #(= (:tr-numero %) kohteen-tienumero) kaikki-osat)
                                        :kohteen-muut-kohdeosat (filter #(not= (:tr-numero %) kohteen-tienumero) kaikki-osat)
                                        kaikki-osat)
                  vanhat-osa-idt (into #{} (map :id) tarkasteltavat-osat)
                  uudet-osa-idt (into #{} (keep :id) osat)
                  poistuneet-osa-idt (set/difference vanhat-osa-idt uudet-osa-idt)]

              (doseq [id poistuneet-osa-idt]
                (q/poista-yllapitokohdeosa! db {:urakka urakka-id
                                                :id id}))

              (log/debug "Tallennetaan ylläpitokohdeosat: " (pr-str osat) " Ylläpitokohde-id: " yllapitokohde-id)
              (doseq [osa osat]
                (if (id-olemassa? (:id osa))
                  (paivita-yllapitokohdeosa db user urakka-id osa)
                  (luo-uusi-yllapitokohdeosa db user yllapitokohde-id osa)))
              (yy/paivita-yllapitourakan-geometria db urakka-id)
              (let [yllapitokohdeosat (hae-kaikki-osat)]
                (log/debug "Tallennus suoritettu. Uudet ylläpitokohdeosat: " (pr-str yllapitokohdeosat))
                (tr-domain/jarjesta-tiet yllapitokohdeosat)))))))))

(defn- luo-uusi-yllapitokohde [db user urakka-id sopimus-id vuosi
                               {:keys [kohdenumero nimi
                                       tr-numero tr-alkuosa tr-alkuetaisyys
                                       tr-loppuosa tr-loppuetaisyys tr-ajorata tr-kaista
                                       yllapitoluokka yllapitokohdetyyppi yllapitokohdetyotyyppi
                                       sopimuksen-mukaiset-tyot arvonvahennykset bitumi-indeksi
                                       kaasuindeksi toteutunut-hinta
                                       poistettu keskimaarainen-vuorokausiliikenne]}]
  (log/debug "Luodaan uusi ylläpitokohde tyyppiä " yllapitokohdetyotyyppi)
  (when-not poistettu
    (let [kohde (q/luo-yllapitokohde<! db
                                       {:urakka urakka-id
                                        :sopimus sopimus-id
                                        :kohdenumero kohdenumero
                                        :nimi nimi
                                        :tr_numero tr-numero
                                        :tr_alkuosa tr-alkuosa
                                        :tr_alkuetaisyys tr-alkuetaisyys
                                        :tr_loppuosa tr-loppuosa
                                        :tr_loppuetaisyys tr-loppuetaisyys
                                        :tr_ajorata tr-ajorata
                                        :tr_kaista tr-kaista
                                        :keskimaarainen_vuorokausiliikenne keskimaarainen-vuorokausiliikenne
                                        :yllapitoluokka (if (map? yllapitoluokka)
                                                          (:numero yllapitoluokka)
                                                          yllapitoluokka)

                                        :yllapitokohdetyyppi (when yllapitokohdetyyppi (name yllapitokohdetyyppi))
                                        :yllapitokohdetyotyyppi (when yllapitokohdetyotyyppi (name yllapitokohdetyotyyppi))
                                        :vuodet (konv/seq->array [vuosi])})]
      (q/luo-yllapitokohteelle-tyhja-aikataulu<! db {:yllapitokohde (:id kohde)})
      (q/luo-yllapitokohteelle-kustannukset<! db {:yllapitokohde (:id kohde)
                                                  :sopimuksen_mukaiset_tyot sopimuksen-mukaiset-tyot
                                                  :arvonvahennykset arvonvahennykset
                                                  :bitumi_indeksi bitumi-indeksi
                                                  :kaasuindeksi kaasuindeksi
                                                  :toteutunut_hinta toteutunut-hinta})
      (:id kohde))))

(defmulti poista-yllapitokohde
          (fn [db yha kohde urakka-id]
            (:yllapitokohdetyotyyppi kohde)))

(defmethod poista-yllapitokohde :paallystys
  [db yha {:keys [id] :as kohde} urakka-id]
  (let [poistaminen-ok (into {}
                             (keep (fn [[tarkistettu-asia ok?]]
                                     (when-not ok?
                                       [tarkistettu-asia ok?])))
                             (first (q/paallystyskohteen-saa-poistaa db {:id id})))
        yha-id (:yhaid (first (q/kohteen-yhaid db {:kohde-id id :urakka-id urakka-id})))]
    (if (empty? poistaminen-ok)
      (do
        ;; Merkataan kohde ja sen alikohteet poistetuiksi
        (q/poista-yllapitokohde! db {:id id :urakka urakka-id})
        (q/merkitse-yllapitokohteen-kohdeosat-poistetuiksi! db {:yllapitokohdeid id :urakka urakka-id})
        ;; Lähetetään YHA:an poistoviesti. YHA:an lähetettyä viestiä ei käsitellä asyncisti.
        (when yha-id
          (yha/poista-kohde yha yha-id)))
      (throw+ {:type    :kohdetta-ei-voi-poistaa
               :virheet [poistaminen-ok]}))))

(defmethod poista-yllapitokohde :default
  [db _ {:keys [id] :as kohde} urakka-id]
  (when (yy/yllapitokohteen-voi-poistaa? db id)
    (q/poista-yllapitokohde! db {:id id :urakka urakka-id})
    (q/merkitse-yllapitokohteen-kohdeosat-poistetuiksi! db {:yllapitokohdeid id :urakka urakka-id})))

(defn- paivita-yllapitokohde [db user urakka-id sopimus-id
                              {:keys [id kohdenumero nimi tunnus
                                      tr-numero tr-alkuosa tr-alkuetaisyys
                                      tr-loppuosa tr-loppuetaisyys tr-ajorata tr-kaista
                                      yllapitoluokka sopimuksen-mukaiset-tyot
                                      arvonvahennykset bitumi-indeksi kaasuindeksi
                                      toteutunut-hinta
                                      keskimaarainen-vuorokausiliikenne]
                               :as kohde}]
  (do (log/debug "Päivitetään ylläpitokohde")
      (q/paivita-yllapitokohde<! db
                                 {:kohdenumero                       kohdenumero
                                  :nimi                              nimi
                                  :tunnus                            tunnus
                                  :tr_numero                         tr-numero
                                  :tr_alkuosa                        tr-alkuosa
                                  :tr_alkuetaisyys                   tr-alkuetaisyys
                                  :tr_loppuosa                       tr-loppuosa
                                  :tr_loppuetaisyys                  tr-loppuetaisyys
                                  :tr_ajorata                        tr-ajorata
                                  :tr_kaista                         tr-kaista
                                  :keskimaarainen_vuorokausiliikenne keskimaarainen-vuorokausiliikenne
                                  :yllapitoluokka                    (if (map? yllapitoluokka)
                                                                       (:numero yllapitoluokka)
                                                                       yllapitoluokka)

                                  :id                                id
                                  :urakka                            urakka-id})
      (q/tallenna-yllapitokohteen-kustannukset! db {:yllapitokohde            id
                                                    :urakka                   urakka-id
                                                    :sopimuksen_mukaiset_tyot sopimuksen-mukaiset-tyot
                                                    :arvonvahennykset         arvonvahennykset
                                                    :bitumi_indeksi           bitumi-indeksi
                                                    :kaasuindeksi             kaasuindeksi
                                                    :toteutunut_hinta         toteutunut-hinta
                                                    :muokkaaja                (:id user)})

      ;; Mikäli pääkohde kutistuu lyhyemmäksi kuin alikohteet, korjataan tilanne:
      (let [kohdeosat (hae-yllapitokohteen-yllapitokohdeosat db user {:urakka-id        urakka-id
                                                                      :sopimus-id       sopimus-id
                                                                      :yllapitokohde-id id})
            paakohteen-tien-kohdeosat (filter #(= (:tr-numero %) (:tr-numero kohde)) kohdeosat)
            korjatut-kohdeosat (tierekisteri/alikohteet-tayttamaan-kutistunut-paakohde kohde paakohteen-tien-kohdeosat)
            korjatut+muut (map (fn [kohdeosa]
                                 (if-let [korjattu (first (filter #(= (:id %) (:id kohdeosa)) korjatut-kohdeosat))]
                                   korjattu
                                   kohdeosa))
                               kohdeosat)]
        (tallenna-yllapitokohdeosat db user {:urakka-id        urakka-id
                                             :sopimus-id       sopimus-id
                                             :yllapitokohde-id id
                                             :osat             korjatut+muut}))))

(defn validoi-kohde [db kohde yhden-vuoden-kohteet urakka-id vuosi]
  (let [tr-osoite (select-keys kohde #{:tr-numero :tr-ajorata :tr-kaista :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys})
        ali-ja-muut-kohteet (:kohdeosat kohde)
        alustatoimet nil
        kohde-id (:id kohde)]
    (yllapitokohteet-domain/validoi-kaikki-backilla db kohde-id urakka-id vuosi tr-osoite ali-ja-muut-kohteet alustatoimet yhden-vuoden-kohteet)))

(defn tarkasta-kohteet [db kohteet urakka-id vuosi]
  (jdbc/with-db-transaction [db db]
                            (let [vuosi (or vuosi (pvm/vuosi (pvm/nyt)))
                                  yhden-vuoden-kohteet (q/hae-yhden-vuoden-yha-kohteet db {:vuosi vuosi})
                                  ;; Mikäli käyttäjä on tekemässä päivityksiä olemassa oleviin saman vuoden kohteisiin, otetaan
                                  ;; vertailuun uusin tieto kohteista
                                  yhden-vuoden-kohteet (keep
                                                         (fn [verrattava-kohde]
                                                           (if-let [kohde-payloadissa (first (filter #(= (:id %) (:id verrattava-kohde))
                                                                                                     kohteet))]
                                                             ;; Jos kohde ollaan poistamassa, ei oteta sitä vertailuun
                                                             (when-not (:poistettu kohde-payloadissa)
                                                               kohde-payloadissa)
                                                             verrattava-kohde))
                                                         yhden-vuoden-kohteet)]
                              ;; Sequence ei pakota lazy sequja ja palauttaa lazy seqn. Tämä on ongelma db-yhteyden takia,
                              ;; joten pakotetaan tämä realisoitumaan heti.
                              (doall
                                (sequence
                                  (comp (remove :poistettu)
                                        (keep #(let [virheviestit (validoi-kohde db % yhden-vuoden-kohteet urakka-id vuosi)]
                                                 (when-not (empty? virheviestit)
                                                   virheviestit))))
                                  kohteet)))))

(defn tallenna-yllapitokohteet
  "Tallentaa yllapitokohteet. Jos jokin ylläpitokohde on poistettu, lähettää tästä viestin YHA:an."
  [db yha user {:keys [urakka-id sopimus-id vuosi kohteet]}]
  (yy/tarkista-urakkatyypin-mukainen-kirjoitusoikeus db user urakka-id)
  (doseq [kohde kohteet]
    (yy/vaadi-yllapitokohde-kuuluu-urakkaan db urakka-id (:id kohde)))
  (let [vuosi (or vuosi (pvm/vuosi (pvm/nyt)))
        kohteiden-virheviestit (tarkasta-kohteet db kohteet urakka-id vuosi)]
    (if (empty? kohteiden-virheviestit)
      (let [poistettava-kohde? (fn [kohde]
                                 (and (id-olemassa? (:id kohde))
                                      (:poistettu kohde)))
            ;; Pyritään poistamaan kaikki YHA:ssa olevat poistettavat kohteet. Nämä tehdään omissa transaktioissaan, koska ei haluta
            ;; tilannetta jossa jokin kohde on poistettu YHA:sta mutta koko transaktion epäonnistuttua jonkin toisen kohteen
            ;; kohdalla, YHA:sta poistettu kohde löytyykin vielä Harjasta.
            yha-poistot (for [kohde (filter poistettava-kohde? kohteet)]
                          (try+ (jdbc/with-db-transaction [db db]
                                                          (log/debug (str "Käsitellään saapunut ylläpitokohde: " kohde))
                                                          (poista-yllapitokohde db yha kohde urakka-id))
                                (catch [:type yha/+virhe-kohteen-poistamisessa+] {:keys [virheet]}
                                  {:virheviesti (mapv (fn [{:keys [kohde-yha-id] :as virhe}]
                                                        (assoc virhe :kohteen-nimi (some #(when (= (:yhaid %) kohde-yha-id)
                                                                                            (:nimi %))
                                                                                         kohteet)))
                                                      virheet)
                                   :status      :yha-virhe})
                                (catch [:type yha/+virhe-yha-viestin-lukemisessa+] {:keys [virheet]}
                                  {:virheviesti (mapv (fn [{:keys [kohde-yha-id] :as virhe}]
                                                        (assoc virhe :kohteen-nimi (some #(when (= (:yhaid %) kohde-yha-id)
                                                                                            (:nimi %))
                                                                                         kohteet)))
                                                      virheet)
                                   :status      :yha-virhe})))
            yha-poistovirheet (keep identity yha-poistot)
            joku-poisto-epaonnistui? (not (empty? yha-poistovirheet))
            kaikki-poistot-epaonnistui? (= (count yha-poistovirheet)
                                           (count (map poistettava-kohde? kohteet)))]
        (when (and joku-poisto-epaonnistui?
                   (not kaikki-poistot-epaonnistui?))
          (yha-apurit/lukitse-urakan-yha-sidonta db urakka-id))
        (try (jdbc/with-db-transaction [db db]
                                       (log/debug "Tallennetaan ylläpitokohteet: " (pr-str kohteet))
                                       (doseq [kohde (remove poistettava-kohde? kohteet)]
                                         (log/debug (str "Käsitellään saapunut ylläpitokohde: " kohde))
                                         (if (id-olemassa? (:id kohde))
                                           (paivita-yllapitokohde db user urakka-id sopimus-id kohde)
                                           (luo-uusi-yllapitokohde db user urakka-id sopimus-id vuosi kohde))))
             (finally
               ;; Halutaan aina päivittää geometriat siltä varalta, että jokin kohde on poistettu
               (yy/paivita-yllapitourakan-geometria db urakka-id)))
        (let [yllapitokohteet (hae-urakan-yllapitokohteet db user {:urakka-id  urakka-id
                                                                   :sopimus-id sopimus-id
                                                                   :vuosi      vuosi})]
          (if joku-poisto-epaonnistui?
            (reduce (fn [lopullinen-virhe virhe]
                      (update lopullinen-virhe :virheviesti concat (:virheviesti virhe)))
                    {:status :yha-virhe
                     :yllapitokohteet yllapitokohteet} yha-poistovirheet)
            (do
              (log/debug "Tallennus suoritettu. Tuoreet ylläpitokohteet: " (pr-str yllapitokohteet))
              {:status          :ok
               :yllapitokohteet yllapitokohteet}))))
      {:status            :validointiongelma
       :virheviesti kohteiden-virheviestit})))

(defn hae-yllapitokohteen-urakan-yhteyshenkilot [db fim user {:keys [yllapitokohde-id urakkatyyppi]}]
  (if (or (oikeudet/voi-lukea? oikeudet/tilannekuva-nykytilanne nil user)
          (oikeudet/voi-lukea? oikeudet/tilannekuva-historia nil user)
          (yy/lukuoikeus-paallystys-tai-tiemerkintaurakan-aikatauluun? db user yllapitokohde-id))
    (let [urakka-id (case urakkatyyppi
                      :paallystys (:id (first (q/hae-yllapitokohteen-urakka-id
                                                db {:id yllapitokohde-id})))
                      :tiemerkinta (:id (first (q/hae-yllapitokohteen-suorittava-tiemerkintaurakka-id
                                                 db {:id yllapitokohde-id}))))
          fim-kayttajat (yhteyshenkilot/hae-urakan-kayttajat db fim urakka-id)
          yhteyshenkilot (yhteyshenkilot/hae-urakan-yhteyshenkilot db user urakka-id true)]
      {:fim-kayttajat (vec fim-kayttajat)
       :yhteyshenkilot (vec yhteyshenkilot)})
    (throw+ (roolit/->EiOikeutta "Ei oikeutta"))))

(defn tee-ajastettu-sahkopostin-lahetystehtava [db fim email lahetysaika]
  (if lahetysaika
    (do
      (log/debug "Ajastetaan ylläpitokohteiden sähköpostin lähetys ajettavaksi joka päivä kello: " lahetysaika)
      (ajastettu-tehtava/ajasta-paivittain
        lahetysaika
        (fn [_]
          (lukot/yrita-ajaa-lukon-kanssa
            db
            "yllapitokohteiden-sahkoposti"
            #(let [mailattavat-kohteet (q/hae-tanaan-valmistuvien-tiemerkintakohteiden-idt db)]
               (viestinta/valita-tieto-tiemerkinnan-valmistumisesta
                 {:fim fim
                  :db db
                  :email email
                  :valmistuneet-kohteet (into [] (q/yllapitokohteiden-tiedot-sahkopostilahetykseen
                                                   db (map :id mailattavat-kohteet)))}))))))
    (constantly nil)))

(defrecord Yllapitokohteet [asetukset]
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)
          fim (:fim this)
          email (:sonja-sahkoposti this)
          yha (:yha-integraatio this)]
      (julkaise-palvelu http :urakan-yllapitokohteet
                        (fn [user tiedot]
                          (hae-urakan-yllapitokohteet db user tiedot)))
      (julkaise-palvelu http :tiemerkintaurakalle-osoitetut-yllapitokohteet
                        (fn [user tiedot]
                          (hae-tiemerkintaurakalle-osoitetut-yllapitokohteet db user tiedot)))
      (julkaise-palvelu http :urakan-yllapitokohteet-lomakkeelle
                        (fn [user tiedot]
                          (hae-urakan-yllapitokohteet-lomakkeelle db user tiedot)))
      (julkaise-palvelu http :yllapitokohteen-yllapitokohdeosat
                        (fn [user tiedot]
                          (hae-yllapitokohteen-yllapitokohdeosat db user tiedot)))
      (julkaise-palvelu http :tallenna-yllapitokohteet
                        (fn [user tiedot]
                          (tallenna-yllapitokohteet db yha user tiedot)))
      (julkaise-palvelu http :tallenna-yllapitokohdeosat
                        (fn [user tiedot]
                          (tallenna-yllapitokohdeosat db user tiedot)))
      (julkaise-palvelu http :hae-yllapitourakan-aikataulu
                        (fn [user tiedot]
                          (hae-urakan-aikataulu db user tiedot)))
      (julkaise-palvelu http :hae-tiemerkinnan-suorittavat-urakat
                        (fn [user tiedot]
                          (hae-tiemerkinnan-suorittavat-urakat db user tiedot)))
      (julkaise-palvelu http :tallenna-yllapitokohteiden-aikataulu
                        (fn [user tiedot]
                          (tallenna-yllapitokohteiden-aikataulu db fim email user tiedot)))
      (julkaise-palvelu http :tallenna-yllapitokohteiden-tarkka-aikataulu
                        (fn [user tiedot]
                          (tallenna-yllapitokohteiden-tarkka-aikataulu db user tiedot)))
      (julkaise-palvelu http :merkitse-kohde-valmiiksi-tiemerkintaan
                        (fn [user tiedot]
                          (merkitse-kohde-valmiiksi-tiemerkintaan db fim email user tiedot)))
      (julkaise-palvelu http :yllapitokohteen-urakan-yhteyshenkilot
                        (fn [user tiedot]
                          (hae-yllapitokohteen-urakan-yhteyshenkilot db fim user tiedot)))
      (assoc this ::sahkopostin-lahetys
                  (tee-ajastettu-sahkopostin-lahetystehtava
                    db fim email
                    (:paivittainen-sahkopostin-lahetysaika asetukset)))))

  (stop [{sahkopostin-lahetys ::sahkopostin-lahetys :as this}]
    (poista-palvelut (:http-palvelin this)
                     :urakan-yllapitokohteet
                     :tiemerkintaurakalle-osoitetut-yllapitokohteet
                     :yllapitokohteen-yllapitokohdeosat
                     :tallenna-yllapitokohteet
                     :tallenna-yllapitokohdeosat
                     :hae-yllapitourakan-aikataulu
                     :tallenna-yllapitokohteiden-aikataulu
                     :sahkopostin-lahetys)

    (when sahkopostin-lahetys
      (sahkopostin-lahetys))
    (dissoc this ::sahkopostin-lahetys)))
