(ns harja.palvelin.palvelut.laadunseuranta.tarkastukset
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [harja.kyselyt.laatupoikkeamat :as laatupoikkeamat]
            [harja.kyselyt.tarkastukset :as tarkastukset]

            [harja.kyselyt.konversio :as konv]
            [harja.domain.roolit :as roolit]
            [harja.domain.laadunseuranta.sanktiot :as sanktiot-domain]
            [harja.geo :as geo]

            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.domain.laadunseuranta :as laadunseuranta]

            [harja.ui.kartta.esitettavat-asiat :as esitettavat-asiat]
            [harja.palvelin.palvelut.karttakuvat :as karttakuvat]
            [harja.domain.oikeudet :as oikeudet]
            [harja.id :refer [id-olemassa?]]
            [clojure.core.async :as async]))

(def tarkastus-xf
  (comp
    (geo/muunna-pg-tulokset :sijainti)
    (map konv/alaviiva->rakenne)
    (map #(konv/array->set % :vakiohavainnot))
    (map laadunseuranta/tarkastus-tiedolla-onko-ok)
    (map #(konv/string->keyword % :tyyppi :tekija))
    (map #(dissoc % :sopimus))
    (map (fn [tarkastus]
           (condp = (:tyyppi tarkastus)
             :talvihoito (dissoc tarkastus :soratiemittaus)
             :soratie (dissoc tarkastus :talvihoitomittaus)
             :tiesto (dissoc tarkastus :soratiemittaus :talvihoitomittaus)
             tarkastus)))))


(defn hae-urakan-tarkastukset
  "Palauttaa urakan tarkastukset annetulle aikavälille."
  ([db user parametrit]
   (hae-urakan-tarkastukset db user parametrit false 501))
  ([db user {:keys [urakka-id alkupvm loppupvm tienumero tyyppi
                    havaintoja-sisaltavat? vain-laadunalitukset?]}
    palauta-reitti? max-rivimaara]
   (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-tarkastukset user urakka-id)
   (let [urakoitsija? (roolit/urakoitsija? user)
         tarkastukset-raakana (tarkastukset/hae-urakan-tarkastukset
                                db
                                {:urakka                  urakka-id
                                 :kayttaja_on_urakoitsija urakoitsija?
                                 :alku                    (konv/sql-timestamp alkupvm)
                                 :loppu                   (konv/sql-timestamp loppupvm)
                                 :rajaa_tienumerolla      (boolean tienumero)
                                 :tienumero               tienumero
                                 :rajaa_tyypilla          (boolean tyyppi)
                                 :tyyppi                  (and tyyppi (name tyyppi))
                                 :havaintoja_sisaltavat   havaintoja-sisaltavat?
                                 :vain_laadunalitukset    vain-laadunalitukset?
                                 :maxrivimaara            max-rivimaara})
         tarkastukset (into []
                            (comp tarkastus-xf
                                  (if palauta-reitti?
                                    identity
                                    (map #(dissoc % :sijainti))))
                            tarkastukset-raakana)
         tarkastukset (konv/sarakkeet-vektoriin tarkastukset {:liite :liitteet})]
     tarkastukset)))

(defn hae-tarkastus [db user urakka-id tarkastus-id]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-tarkastukset user urakka-id)
  (let [urakoitsija? (roolit/urakoitsija? user)
        tarkastus (first (into [] tarkastus-xf (tarkastukset/hae-tarkastus
                                                 db
                                                 urakka-id
                                                 tarkastus-id
                                                 urakoitsija?)))]
    (when tarkastus
      (assoc tarkastus
       :liitteet (into [] (tarkastukset/hae-tarkastuksen-liitteet db tarkastus-id))))))

(defn tallenna-tarkastus [db user urakka-id tarkastus]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laadunseuranta-tarkastukset user urakka-id)
  (try
    (jdbc/with-db-transaction [c db]
      (let [uusi-tarkastus? (nil? (:id tarkastus))
            tarkastustyyppi (:tyyppi tarkastus)
            talvihoitomittaus? (some #(get-in (:talvihoitomittaus tarkastus) %)
                                     laadunseuranta/talvihoitomittauksen-kentat)
            soratiemittaus? (some #(get-in (:soratiemittaus tarkastus) %)
                                     laadunseuranta/soratiemittauksen-kentat)
            tarkastus (assoc tarkastus :lahde "harja-ui")
            id (tarkastukset/luo-tai-paivita-tarkastus c user urakka-id tarkastus)]

        (when (and (or
                     (= :talvihoito tarkastustyyppi)
                     (= :laatu tarkastustyyppi))
                   talvihoitomittaus?)
          (tarkastukset/luo-tai-paivita-talvihoitomittaus
           c id (or uusi-tarkastus? (not (:tarkastus (:talvihoitomittaus tarkastus))))
           (-> (:talvihoitomittaus tarkastus)
               (assoc :lampotila-tie
                      (get-in (:talvihoitomittaus tarkastus) [:lampotila :tie]))
               (assoc :lampotila-ilma
                      (get-in (:talvihoitomittaus tarkastus) [:lampotila :ilma]))
               (assoc :tr
                      (:tr tarkastus)))))

        (when (and (or
                     (= :soratie tarkastustyyppi)
                     (= :laatu tarkastustyyppi))
                   soratiemittaus?)
          (tarkastukset/luo-tai-paivita-soratiemittaus
            c id
            (or uusi-tarkastus? (not (:tarkastus (:soratiemittaus tarkastus))))
            (:soratiemittaus tarkastus)))

        (when-let [uusi-liite (:uusi-liite tarkastus)]
          (log/info "UUSI LIITE: " uusi-liite)
          (tarkastukset/luo-liite<! c id (:id uusi-liite)))

        (hae-tarkastus c user urakka-id id)))
    (catch Exception e
      (log/info e "Tarkastuksen tallennuksessa poikkeus!"))))

(defn- tarkastusreittien-parametrit
  [user {:keys [havaintoja-sisaltavat? vain-laadunalitukset? tienumero
                alkupvm loppupvm tyyppi urakka-id] :as parametrit}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-tarkastukset
                             user :urakka-id)
  {:urakka urakka-id
   :alku alkupvm :loppu loppupvm
   :rajaa_tienumerolla (some? tienumero) :tienumero tienumero
   :rajaa_tyypilla (some? tyyppi) :tyyppi (and tyyppi (name tyyppi))
   :havaintoja_sisaltavat havaintoja-sisaltavat?
   :vain_laadunalitukset vain-laadunalitukset?
   :kayttaja_on_urakoitsija (roolit/urakoitsija? user)})

(defn hae-tarkastusreitit-kartalle [db user {:keys [extent parametrit]}]
  (let [parametrit (tarkastusreittien-parametrit user parametrit)
        [x1 y1 x2 y2] extent
        alue {:xmin x1 :ymin y1 :xmax x2 :ymax y2}
        alue (assoc alue :toleranssi (geo/karkeistustoleranssi alue))
        ch (async/chan 32
                       (comp
                         (map #(konv/array->set % :vakiohavainnot))
                         (map laadunseuranta/tarkastus-tiedolla-onko-ok)
                         (map #(konv/string->keyword % :tyyppi :tekija))
                         (map #(assoc %
                                 :tyyppi-kartalla :tarkastus
                                 :sijainti (:reitti %)))
                         (esitettavat-asiat/kartalla-esitettavaan-muotoon-xf)))]
    (async/thread
      (try
        (jdbc/with-db-transaction [db db
                                   {:read-only? true}]
          (tarkastukset/hae-urakan-tarkastukset-kartalle
           db ch
           (merge alue
                  parametrit)))
        (catch Throwable t
          (log/warn t "Virhe haettaessa tarkastuksia kartalle"))))

    ch))

(defn hae-tarkastusreittien-asiat-kartalle
  [db user {x :x y :y toleranssi :toleranssi :as parametrit}]
  (let [parametrit (tarkastusreittien-parametrit user parametrit)]
    (into []
          (comp
            (map #(konv/array->set % :vakiohavainnot))
            (map #(assoc % :tyyppi-kartalla :tarkastus))
            (map #(konv/string->keyword % :tyyppi))
            (map #(update % :tierekisteriosoite konv/lue-tr-osoite)))
          (tarkastukset/hae-urakan-tarkastusten-asiat-kartalle
            db
            (assoc parametrit
              :x x :y y
              :toleranssi toleranssi)))))

(defn lisaa-tarkastukselle-laatupoikkeama [db user urakka-id tarkastus-id]
  (log/debug (format "Luodaan laatupoikkeama tarkastukselle (id: %s)" tarkastus-id))
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laadunseuranta-laatupoikkeamat user urakka-id)
  (when-let [tarkastus (hae-tarkastus db user urakka-id tarkastus-id)]
    (jdbc/with-db-transaction [db db]
      (let [laatupoikkeama {:sijainti (:sijainti tarkastus)
                            :kuvaus (:havainnot tarkastus)
                            :aika (:aika tarkastus)
                            :yllapitokohde (:yllapitokohde tarkastus)
                            :tr (:tr tarkastus)
                            :urakka urakka-id
                            :tekija (:tekija tarkastus)}
            laatupoikkeama-id (laatupoikkeamat/luo-tai-paivita-laatupoikkeama db user laatupoikkeama)]
        (tarkastukset/liita-tarkastukselle-laatupoikkeama<!
          db
          {:tarkastus tarkastus-id
           :laatupoikkeama laatupoikkeama-id})
        (tarkastukset/liita-tarkastuksen-liitteet-laatupoikkeamalle<!
          db
          {:tarkastus tarkastus-id
           :laatupoikkeama laatupoikkeama-id})
        laatupoikkeama-id))))

(defn hae-tarkastusajon-reittipisteet
  "Palauttaa tarkastusajon id:tä vastaan ko. ajon reittipisteet kartalle piirtoa varten.
  Ajateltu käyttö ainoastaan debug-tarkoituksiin jvh-käyttäjällä ns. salaisessa TR-osiossa."
  [db user tarkastusajon-id]
  (roolit/vaadi-rooli user roolit/jarjestelmavastaava)
  (into []
        (comp
          (map #(konv/array->vec % :havainnot))
          (geo/muunna-pg-tulokset :sijainti))
        (tarkastukset/hae-tarkastusajon-reittipisteet db {:tarkastusajoid tarkastusajon-id})))

(defrecord Tarkastukset []
  component/Lifecycle
  (start [{:keys [http-palvelin db karttakuvat] :as this}]

    (karttakuvat/rekisteroi-karttakuvan-lahde!
     karttakuvat :tarkastusreitit
     (partial #'hae-tarkastusreitit-kartalle db)
     (partial #'hae-tarkastusreittien-asiat-kartalle db)
     "tr")

    (julkaise-palvelut
      http-palvelin
      :hae-urakan-tarkastukset
      (fn [user tiedot]
        (hae-urakan-tarkastukset db user tiedot))

      :tallenna-tarkastus
      (fn [user {:keys [urakka-id tarkastus]}]
        (tallenna-tarkastus db user urakka-id tarkastus))

      :hae-tarkastus
      (fn [user {:keys [urakka-id tarkastus-id]}]
        (hae-tarkastus db user urakka-id tarkastus-id))

      :lisaa-tarkastukselle-laatupoikkeama
      (fn [user {:keys [urakka-id tarkastus-id]}]
        (lisaa-tarkastukselle-laatupoikkeama db user urakka-id tarkastus-id))

      :hae-tarkastusajon-reittipisteet
      (fn [user {:keys [tarkastusajon-id]}]
        (hae-tarkastusajon-reittipisteet db user tarkastusajon-id)))
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
                     :hae-urakan-tarkastukset
                     :tallenna-tarkastus
                     :hae-tarkastus
                     :lisaa-tarkastukselle-laatupoikkeama
                     :hae-tarkastusajon-reittipisteet)
    this))
