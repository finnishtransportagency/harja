(ns harja.palvelin.palvelut.yllapitokohteet
  "Tässä namespacessa on palvelut ylläpitokohteiden ja -kohdeosien hakuun ja tallentamiseen."
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [com.stuartsierra.component :as component]
            [harja.domain
             [oikeudet :as oikeudet]
             [skeema :refer [Toteuma validoi]]
             [tierekisteri :as tr]]
            [harja.kyselyt
             [konversio :as konv]
             [yllapitokohteet :as q]]
            [harja.palvelin.komponentit.http-palvelin
             :refer
             [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.palvelut.yha :as yha]
            [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.domain.tiemerkinta :as tm-domain]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.domain.yllapitokohteet :as yllapitokohteet-domain]
            [harja.kyselyt.paallystys :as paallystys-q]
            [harja.kyselyt.paikkaus :as paikkaus-q]
            [harja.palvelin.palvelut.tierek-haku :as tr-haku]
            [clj-time.coerce :as c]
            [harja.palvelin.palvelut.yllapitokohteet.viestinta :as viestinta]
            [harja.palvelin.palvelut.yllapitokohteet.maaramuutokset :as maaramuutokset]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yy]
            [harja.kyselyt.yllapitokohteet :as yllapitokohteet-q]
            [harja.id :refer [id-olemassa?]])
  (:use org.httpkit.fake))

(defn hae-urakan-yllapitokohteet [db user {:keys [urakka-id sopimus-id vuosi]}]
  (yy/tarkista-urakkatyypin-mukainen-lukuoikeus db user urakka-id)
  (log/debug "Haetaan urakan ylläpitokohteet.")
  (jdbc/with-db-transaction [db db]
    (let [yllapitokohteet (yy/hae-urakan-yllapitokohteet db user {:urakka-id urakka-id
                                                                  :sopimus-id sopimus-id
                                                                  :vuosi vuosi})
          yllapitokohteet (maaramuutokset/liita-yllapitokohteisiin-maaramuutokset
                            db user {:yllapitokohteet yllapitokohteet
                                     :urakka-id urakka-id})]
      (into []
            yllapitokohteet-domain/yllapitoluokka-xf
            yllapitokohteet))))

(defn hae-tiemerkintaurakalle-osoitetut-yllapitokohteet [db user {:keys [urakka-id]}]
  (yy/tarkista-urakkatyypin-mukainen-lukuoikeus db user urakka-id)
  (log/debug "Haetaan tiemerkintäurakalle osoitetut ylläpitokohteet.")
  (jdbc/with-db-transaction [db db]
    (let [yllapitokohteet (q/hae-tiemerkintaurakalle-osoitetut-yllapitokohteet db {:urakka urakka-id})
          yllapitokohteet (mapv (partial yy/lisaa-yllapitokohteelle-pituus db) yllapitokohteet)]
      yllapitokohteet)))

(defn hae-urakan-yllapitokohteet-lomakkeelle [db user {:keys [urakka-id sopimus-id]}]
  (yy/tarkista-urakkatyypin-mukainen-lukuoikeus db user urakka-id)
  (log/debug "Haetaan urakan ylläpitokohteet laatupoikkeamalomakkeelle")
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
                      yllapitokohteet-q/kohdeosa-xf
                      (q/hae-urakan-yllapitokohteen-yllapitokohdeosat db {:yllapitokohde yllapitokohde-id}))]
    (log/debug "Ylläpitokohdeosat saatu: " (pr-str vastaus))
    vastaus))

(defn- hae-urakkatyyppi [db urakka-id]
  (keyword (:tyyppi (first (q/hae-urakan-tyyppi db {:urakka urakka-id})))))

(defn- hae-paallystysurakan-aikataulu [{:keys [db urakka-id sopimus-id vuosi]}]
  (->> (q/hae-paallystysurakan-aikataulu db {:urakka urakka-id :sopimus sopimus-id :vuosi vuosi})
       (mapv #(assoc % :tiemerkintaurakan-voi-vaihtaa?
                       (yy/yllapitokohteen-suorittavan-tiemerkintaurakan-voi-vaihtaa?
                         db (:id %) (:suorittava-tiemerkintaurakka %))))))

(defn- hae-tiemerkintaurakan-aikataulu [db urakka-id vuosi]
  (q/hae-tiemerkintaurakan-aikataulu db {:suorittava_tiemerkintaurakka urakka-id
                                         :vuosi vuosi}))

(defn hae-urakan-aikataulu [db user {:keys [urakka-id sopimus-id vuosi]}]
  (assert (and urakka-id sopimus-id) "anna urakka-id ja sopimus-id")
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-aikataulu user urakka-id)
  (log/debug "Haetaan aikataulutiedot urakalle: " urakka-id)
  (jdbc/with-db-transaction [db db]
    ;; Urakkatyypin mukaan näytetään vain tietyt asiat, joten erilliset kyselyt
    (case (hae-urakkatyyppi db urakka-id)
      :paallystys
      (hae-paallystysurakan-aikataulu {:db db :urakka-id urakka-id :sopimus-id sopimus-id :vuosi vuosi})
      :tiemerkinta
      (hae-tiemerkintaurakan-aikataulu db urakka-id vuosi))))

(defn hae-tiemerkinnan-suorittavat-urakat [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-aikataulu user urakka-id)
  (log/debug "Haetaan tiemerkinnän suorittavat urakat.")
  (q/hae-tiemerkinnan-suorittavat-urakat db))

(defn merkitse-kohde-valmiiksi-tiemerkintaan
  "Merkitsee kohteen valmiiksi tiemerkintään annettuna päivämääränä.
   Palauttaa päivitetyt kohteet aikataulunäkymään"
  [db fim email user
   {:keys [urakka-id sopimus-id tiemerkintapvm kohde-id] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-aikataulu user urakka-id)
  (log/debug "Merkitään urakan " urakka-id " kohde " kohde-id " valmiiksi tiemerkintää päivämäärällä " tiemerkintapvm)
  (jdbc/with-db-transaction [db db]
    (q/merkitse-kohde-valmiiksi-tiemerkintaan<!
      db
      {:valmis_tiemerkintaan tiemerkintapvm
       :aikataulu_tiemerkinta_takaraja (-> tiemerkintapvm
                                           (c/from-date)
                                           tm-domain/tiemerkinta-oltava-valmis
                                           (c/to-date))
       :id kohde-id
       :urakka urakka-id}))

  (viestinta/sahkoposti-kohde-valmis-merkintaan db fim email
                                                kohde-id tiemerkintapvm user)

  (hae-urakan-aikataulu db user {:urakka-id urakka-id
                                 :sopimus-id sopimus-id}))


(defn- tallenna-paallystyskohteiden-aikataulu [{:keys [db user kohteet paallystysurakka-id
                                                       voi-tallentaa-tiemerkinnan-takarajan?] :as tiedot}]
  (log/debug "Tallennetaan päällystysurakan " paallystysurakka-id " ylläpitokohteiden aikataulutiedot.")
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
           :suorittava_tiemerkintaurakka
           (if (= kayttajan-valitsema-suorittava-tiemerkintaurakka-id
                  kohteen-nykyinen-suorittava-tiemerkintaurakka-id)
             kohteen-nykyinen-suorittava-tiemerkintaurakka-id
             ;; Suorittajaa yritetään vaihtaa, tarkistetaan onko sallittu
             (if (yy/yllapitokohteen-suorittavan-tiemerkintaurakan-voi-vaihtaa?
                   db (:id kohde) kohteen-nykyinen-suorittava-tiemerkintaurakka-id)
               (:suorittava-tiemerkintaurakka kohde)
               kohteen-nykyinen-suorittava-tiemerkintaurakka-id))
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
    (jdbc/with-db-transaction [db db]
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
           :suorittava_tiemerkintaurakka tiemerkintaurakka-id}))))

  (viestinta/sahkoposti-tiemerkinta-valmis db fim email (map :id kohteet) user))

(defn tallenna-yllapitokohteiden-aikataulu [db fim email user {:keys [urakka-id sopimus-id vuosi kohteet]}]
  (assert (and urakka-id sopimus-id kohteet) "anna urakka-id ja sopimus-id ja kohteet")
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-aikataulu user urakka-id)
  (log/debug "Tallennetaan urakan " urakka-id " ylläpitokohteiden aikataulutiedot: " kohteet)
  (let [voi-tallentaa-tiemerkinnan-takarajan?
        (oikeudet/on-muu-oikeus? "TM-valmis"
                                 oikeudet/urakat-aikataulu
                                 urakka-id
                                 user)]
    (case (hae-urakkatyyppi db urakka-id)
      ;; NOTE Päällystysurakoitsija ja tiemerkkari eivät saa muokata samoja asioita,
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

    (log/debug "Aikataulutiedot tallennettu!")
    (hae-urakan-aikataulu db user {:urakka-id urakka-id
                                   :sopimus-id sopimus-id
                                   :vuosi vuosi})))

(defn- luo-uusi-yllapitokohde [db user urakka-id sopimus-id vuosi
                               {:keys [kohdenumero nimi
                                       tr-numero tr-alkuosa tr-alkuetaisyys
                                       tr-loppuosa tr-loppuetaisyys tr-ajorata tr-kaista
                                       yllapitoluokka yllapitokohdetyyppi yllapitokohdetyotyyppi
                                       sopimuksen-mukaiset-tyot arvonvahennykset bitumi-indeksi
                                       kaasuindeksi poistettu keskimaarainen-vuorokausiliikenne]}]
  (log/debug "Luodaan uusi ylläpitokohde tyyppiä " yllapitokohdetyotyyppi)
  (when-not poistettu
    (q/luo-yllapitokohde<! db
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
                            :sopimuksen_mukaiset_tyot sopimuksen-mukaiset-tyot
                            :arvonvahennykset arvonvahennykset
                            :bitumi_indeksi bitumi-indeksi
                            :kaasuindeksi kaasuindeksi
                            :yllapitokohdetyyppi (when yllapitokohdetyyppi (name yllapitokohdetyyppi))
                            :yllapitokohdetyotyyppi (when yllapitokohdetyotyyppi (name yllapitokohdetyotyyppi))
                            :vuodet (konv/seq->array [vuosi])})))

(defn- paivita-yllapitokohde [db user urakka-id
                              {:keys [id kohdenumero nimi
                                      tr-numero tr-alkuosa tr-alkuetaisyys
                                      tr-loppuosa tr-loppuetaisyys tr-ajorata tr-kaista
                                      yllapitoluokka sopimuksen-mukaiset-tyot
                                      arvonvahennykset bitumi-indeksi kaasuindeksi
                                      keskimaarainen-vuorokausiliikenne poistettu]}]
  (if poistettu
    (when (yy/yllapitokohteen-voi-poistaa? db id)
      (log/debug "Poistetaan ylläpitokohde")
      (q/poista-yllapitokohde! db {:id id :urakka urakka-id}))
    (do (log/debug "Päivitetään ylläpitokohde")
        (q/paivita-yllapitokohde! db
                                  {:kohdenumero kohdenumero
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
                                   :sopimuksen_mukaiset_tyot sopimuksen-mukaiset-tyot
                                   :arvonvanhennykset arvonvahennykset
                                   :bitumi_indeksi bitumi-indeksi
                                   :kaasuindeksi kaasuindeksi
                                   :id id
                                   :urakka urakka-id}))))

(defn tallenna-yllapitokohteet [db user {:keys [urakka-id sopimus-id vuosi kohteet]}]
  (yy/tarkista-urakkatyypin-mukainen-kirjoitusoikeus db user urakka-id)
  (jdbc/with-db-transaction [c db]
    (yha/lukitse-urakan-yha-sidonta db urakka-id)
    (log/debug "Tallennetaan ylläpitokohteet: " (pr-str kohteet))
    (doseq [kohde kohteet]
      (log/debug (str "Käsitellään saapunut ylläpitokohde: " kohde))
      (if (id-olemassa? (:id kohde))
        (paivita-yllapitokohde c user urakka-id kohde)
        (luo-uusi-yllapitokohde c user urakka-id sopimus-id vuosi kohde)))
    (yy/paivita-yllapitourakan-geometria c urakka-id)
    (let [paallystyskohteet (hae-urakan-yllapitokohteet c user {:urakka-id urakka-id
                                                                :sopimus-id sopimus-id})]
      (log/debug "Tallennus suoritettu. Tuoreet ylläpitokohteet: " (pr-str paallystyskohteet))
      paallystyskohteet)))

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
                                 :id id
                                 :urakka urakka-id}))

(defn tallenna-yllapitokohdeosat
  "Tallentaa ylläpitokohdeosat kantaan.
   Tarkistaa, tuleeko kohdeosat päivittää, poistaa vai luoda uutena.
   Palauttaa kohteen päivittyneet kohdeosat."
  [db user {:keys [urakka-id sopimus-id yllapitokohde-id osat]}]
  (yy/tarkista-urakkatyypin-mukainen-kirjoitusoikeus db user urakka-id)
  (jdbc/with-db-transaction [c db]
    (yha/lukitse-urakan-yha-sidonta db urakka-id)

    (log/debug "SAIN OSAT: " osat)
    (let [hae-osat #(hae-yllapitokohteen-yllapitokohdeosat c user
                                                           {:urakka-id urakka-id
                                                            :sopimus-id sopimus-id
                                                            :yllapitokohde-id yllapitokohde-id})
          vanhat-osa-idt (into #{}
                               (map :id)
                               (hae-osat))
          uudet-osa-idt (into #{}
                              (keep :id)
                              osat)
          poistuneet-osa-idt (set/difference vanhat-osa-idt uudet-osa-idt)]

      (doseq [id poistuneet-osa-idt]
        (q/poista-yllapitokohdeosa! c {:urakka urakka-id
                                       :id id}))

      (log/debug "Tallennetaan ylläpitokohdeosat: " (pr-str osat) " Ylläpitokohde-id: " yllapitokohde-id)
      (doseq [osa osat]
        (if (id-olemassa? (:id osa))
          (paivita-yllapitokohdeosa c user urakka-id osa)
          (luo-uusi-yllapitokohdeosa c user yllapitokohde-id osa)))
      (yy/paivita-yllapitourakan-geometria c urakka-id)
      (let [yllapitokohdeosat (hae-osat)]
        (log/debug "Tallennus suoritettu. Tuoreet ylläpitokohdeosat: " (pr-str yllapitokohdeosat))
        (sort-by tr/tiekohteiden-jarjestys yllapitokohdeosat)))))


(defrecord Yllapitokohteet []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)
          fim (:fim this)
          email (:sonja-sahkoposti this)]
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
                          (tallenna-yllapitokohteet db user tiedot)))
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
      (julkaise-palvelu http :merkitse-kohde-valmiiksi-tiemerkintaan
                        (fn [user tiedot]
                          (merkitse-kohde-valmiiksi-tiemerkintaan db fim email user tiedot)))

      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :urakan-yllapitokohteet
      :tiemerkintaurakalle-osoitetut-yllapitokohteet
      :yllapitokohteen-yllapitokohdeosat
      :tallenna-yllapitokohteet
      :tallenna-yllapitokohdeosat
      :hae-yllapitourakan-aikataulu
      :tallenna-yllapitokohteiden-aikataulu)
    this))
