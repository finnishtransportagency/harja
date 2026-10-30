(ns harja.kyselyt.laatupoikkeamat
  "Laatupoikkeamiin liittyvät tietokantakyselyt"
  (:require [clojure.java.jdbc :as jdbc]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.sanktiot :as sanktio-kyselyt]
            [harja.kyselyt.urakat :as urakat]
            [jeesql.core :refer [defqueries]]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [harja.geo :as geo]
            [harja.id :refer [id-olemassa?]]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yy]))

(defqueries "harja/kyselyt/laatupoikkeamat.sql"
  {:positional? true})

(declare onko-olemassa-ulkoisella-idlla paivita-laatupoikkeaman-perustiedot<! luo-laatupoikkeama<!
  poista-laatupoikkeama! hae-bonuksen-liitteet hae-omat-laatupoikkeamat hae-kaikki-laatupoikkeamat
  hae-selvitysta-odottavat-laatupoikkeamat hae-kasitellyt-laatupoikkeamat hae-poikkeamaraportilliset-laatupoikkeamat
  hae-laatupoikkeaman-urakka-id hae-laatupoikkeaman-liitteet hae-urakan-laatupoikkeama-liitteet
  hae-laatupoikkeaman-tiedot hae-laatupoikkeaman-kommentit liita-kommentti<! liita-liite<! kirjaa-laatupoikkeaman-paatos!
  paivita-laatupoikkeama-ulkoisella-idlla<!
  peru-laatupoikkeaman-paatos! hae-laatupoikkeaman-tallennuksen-perustiedot)

(defn onko-olemassa-ulkoisella-idlla? [db ulkoinen-id urakka-id]
  (:exists (first (onko-olemassa-ulkoisella-idlla db ulkoinen-id urakka-id))))

(defn luo-tai-paivita-laatupoikkeama
  "Luo uuden laatupoikkeaman tai päivittää olemassaolevan laatupoikkeaman perustiedot. Palauttaa laatupoikkeaman id:n."
  [db user {:keys [id kohde tekija urakka aika selvitys-pyydetty kuvaus
                   sijainti tr yllapitokohde poistettu sisaltaa-poikkeamaraportin?]}]
  (let [{:keys [numero alkuosa loppuosa alkuetaisyys loppuetaisyys]} tr]
    (yy/vaadi-yllapitokohde-kuuluu-urakkaan-tai-on-suoritettavana-tiemerkintaurakassa db urakka yllapitokohde)
    (if id
      (do
        (paivita-laatupoikkeaman-perustiedot<! db
          (konv/sql-timestamp aika)
          (when tekija (name tekija))
          kohde
          (boolean selvitys-pyydetty)
          (:id user)
          kuvaus
          (when sijainti (geo/geometry (geo/clj->pg sijainti)))
          numero
          alkuosa
          loppuosa
          alkuetaisyys
          loppuetaisyys
          yllapitokohde
          sisaltaa-poikkeamaraportin?
          (boolean poistettu)
          id
          urakka)
        id)

      (:id (luo-laatupoikkeama<! db
             "harja-ui"
             urakka
             (konv/sql-timestamp aika)
             (when tekija (name tekija))
             kohde
             (boolean selvitys-pyydetty)
             (:id user)
             kuvaus
             (when sijainti (geo/geometry (geo/clj->pg sijainti)))
             numero
             alkuosa
             loppuosa
             alkuetaisyys
             loppuetaisyys
             yllapitokohde
             sisaltaa-poikkeamaraportin?
             nil)))))

(defn poista-laatupoikkeama
  "Merkitsee laatupoikkeaman poistetuksi. Palauttaa laatupoikkeaman ID:n."
  [db user {laatupoikkeama-id :id urakka-id :urakka-id :as tiedot}]

  (log/debug "Merkitse laatupoikkeama " laatupoikkeama-id " poistetuksi urakalle " urakka-id)

  (poista-laatupoikkeama! db {:id laatupoikkeama-id :muokkaaja (:id user) :urakka-id urakka-id}))


(defn vaadi-sanktio-kuuluu-urakkaan
  "Tarkistaa, että sanktio kuuluu annettuun urakkaan"
  [db urakka-id sanktio-id]
  (when (id-olemassa? sanktio-id)
    (let [sanktion-urakka (:urakka (first (sanktio-kyselyt/hae-sanktion-urakka-id db {:sanktioid sanktio-id})))]
      (when-not (= sanktion-urakka urakka-id)
        (throw (SecurityException. (str "Sanktio " sanktio-id " ei kuulu valittuun urakkaan "
                                     urakka-id " vaan urakkaan " sanktion-urakka)))))))

(defn poista-laatupoikkeaman-sanktio
  "Poistaa laatupoikkeaman kautta määrätyn (ei-suora)sanktion. Palauttaa sanktion ID:n.

  Toisin kuin suorasanktion poistossa, laatupoikkeamaa EI poisteta, koska laatupoikkeamalla voi
  olla omaa arvoa ja se voi sisältää useita sanktioita. Sanktion poiston jälkeen laatupoikkeaman
  päätös perutaan (käsittelytiedot ja päätös nollataan). Tämä poistaa laatupoikkeaman lukituksen ja
  mahdollistaa laatupoikkeaman muokkauksen (vrt. HARJA-1954). Päätös perutaan silloinkin, kun
  laatupoikkeamalle jää muita aktiivisia sanktioita — päätös tulee tällöin antaa uudelleen."
  [db user {sanktio-id :id urakka-id :urakka-id} sanktio]
  (oikeudet/vaadi-oikeus "poisto" oikeudet/urakat-laadunseuranta-sanktiot user urakka-id)
  (vaadi-sanktio-kuuluu-urakkaan db urakka-id sanktio-id)
  (jdbc/with-db-transaction [c db]
    (sanktio-kyselyt/poista-sanktio! c {:id sanktio-id
                                        :muokkaaja (:id user)})
    ;; Perutaan laatupoikkeaman päätös, jotta lukitus poistuu ja laatupoikkeamaa voi taas muokata.
    ;; Laatupoikkeamalle mahdollisesti jäävät muut aktiiviset sanktiot säilyvät ennallaan, ja päätös
    ;; annetaan niille uudelleen laatupoikkeaman muokkauksen yhteydessä.
    (when-let [laatupoikkeama-id (:laatupoikkeama_id sanktio)]
      (peru-laatupoikkeaman-paatos! c {:id laatupoikkeama-id
                                                         :muokkaaja (:id user)}))
    (sanktio-kyselyt/merkitse-maksuera-likaiseksi! c sanktio-id)
    sanktio-id))

(defn poista-sanktio-tietokannasta
  "Merkitsee suorasanktion ja siihen liittyvän laatupoikkeaman poistetuksi. Palauttaa sanktion ID:n.

  Laatupoikkeaman kautta määrätty (ei-suora)sanktio poistetaan poista-laatupoikkeaman-sanktio
  -funktiolla, jolloin laatupoikkeamaa ei poisteta vaan sen päätös perutaan."
  [db user urakka-id sanktio]
  (if-not (:suorasanktio sanktio)
    ;; Laatupoikkeaman kautta määrätty sanktio: laatupoikkeamaa ei poisteta.
    (poista-laatupoikkeaman-sanktio db user {:id (:id sanktio) :urakka-id urakka-id} sanktio)
    ;; Suorasanktio: poistetaan sekä sanktio että siihen liittyvä laatupoikkeama.
    (jdbc/with-db-transaction [c db]
      (when-let [laatupoikkeama-id (:laatupoikkeama_id sanktio)]
        (poista-laatupoikkeama c user {:id laatupoikkeama-id
                                       :urakka-id urakka-id}))
      (sanktio-kyselyt/poista-sanktio! c {:id (:id sanktio)
                                          :muokkaaja (:id user)})
      (sanktio-kyselyt/merkitse-maksuera-likaiseksi! c (:id sanktio))
      (:id sanktio))))
