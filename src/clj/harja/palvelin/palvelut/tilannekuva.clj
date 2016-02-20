(ns harja.palvelin.palvelut.tilannekuva
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]

            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.hallintayksikot :as hal-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.tilannekuva :as q]
            [harja.palvelin.palvelut.urakat :as urakat]

            [harja.domain.laadunseuranta :as laadunseuranta]
            [harja.geo :as geo]
            [harja.pvm :as pvm]))

(defn tulosta-virhe! [asiat e]
  (log/error (str "*** ERROR *** Yritettiin hakea tilannekuvaan " asiat ", mutta virhe tapahtui: " (.getMessage e))))

(defn tulosta-tulos! [asiaa tulos]
  (if (vector? tulos)
    (log/debug (str "  - " (count tulos) " " asiaa))
    (log/debug (str "  - " (count (keys tulos)) " " asiaa)))
  tulos)

(defn haettavat [s]
  (into #{} (keep (fn [[avain arvo]] (when arvo avain)) s)))

(defn alueen-hypotenuusa
  "Laskee alueen hypotenuusan, jotta tiedetään minkä kokoista aluetta katsotaan."
  [{:keys [xmin ymin xmax ymax]}]
  (let [dx (- xmax xmin)
        dy (- ymax ymin)]
    (Math/sqrt (+ (* dx dx) (* dy dy)))))

(defn karkeistustoleranssi
  "Määrittelee reittien karkeistustoleranssin alueen koon mukaan."
  [alue]
  (let [pit (alueen-hypotenuusa alue)]
    (log/debug "Alueen pit: " pit " => karkeistus toleranssi: " (/ pit 200))
    (/ pit 200)))

(defn- hae-ilmoitukset
  [db user {:keys [toleranssi] {:keys [tyypit tilat]} :ilmoitukset :as tiedot} urakat]
  (let [haettavat (haettavat tyypit)]
    (when-not (empty? haettavat)
      (try
        (let [suljetut? (if (:suljetut tilat) true false)
              avoimet? (if (:avoimet tilat) true false)
              tulos (mapv
                      #(assoc % :uusinkuittaus
                                (when-not (empty? (:kuittaukset %))
                                  (:kuitattu (last (sort-by :kuitattu (:kuittaukset %))))))
                      (konv/sarakkeet-vektoriin
                        (into []
                              (comp
                                (geo/muunna-pg-tulokset :sijainti)
                                (map konv/alaviiva->rakenne)
                                (map #(assoc % :urakkatyyppi (keyword (:urakkatyyppi %))))
                                (map #(konv/array->vec % :selitteet))
                                (map #(assoc % :selitteet (mapv keyword (:selitteet %))))
                                (map #(assoc-in
                                       %
                                       [:kuittaus :kuittaustyyppi]
                                       (keyword (get-in % [:kuittaus :kuittaustyyppi]))))
                                (map #(assoc % :ilmoitustyyppi (keyword (:ilmoitustyyppi %))))
                                (map #(assoc-in % [:ilmoittaja :tyyppi] (keyword (get-in % [:ilmoittaja :tyyppi])))))
                              (q/hae-ilmoitukset db
                                                 toleranssi
                                                 (when-not (:nykytilanne? tiedot) (konv/sql-date (:alku tiedot)))
                                                 (when-not (:nykytilanne? tiedot) (konv/sql-date (:loppu tiedot)))
                                                 urakat
                                                 avoimet?
                                                 suljetut?
                                                 (mapv name haettavat)))
                        {:kuittaus :kuittaukset}))]
          tulos)
        (catch Exception e
          (tulosta-virhe! "ilmoituksia" e)
          nil)))))

(defn- hae-paallystystyot
  [db user {:keys [toleranssi alku loppu yllapito nykytilanne?]} urakat]
  (when (:paallystys yllapito)
    (try
      (into []
            (comp
              (geo/muunna-pg-tulokset :sijainti)
              (map konv/alaviiva->rakenne)
              (map #(konv/string-polusta->keyword % [:paallystysilmoitus :tila])))
            (if nykytilanne?
              (q/hae-paallystykset-nykytilanteeseen db toleranssi)
              (q/hae-paallystykset-historiakuvaan db
                                                  toleranssi
                                                  (konv/sql-date loppu)
                                                  (konv/sql-date alku))))
      (catch Exception e
        (tulosta-virhe! "paallystyksia" e)
        nil))))

(defn- hae-paikkaustyot
  [db user {:keys [toleranssi alku loppu yllapito nykytilanne?]} urakat]
  (when (:paikkaus yllapito)
    (try
      (into []
            (comp
              (geo/muunna-pg-tulokset :sijainti)
              (map konv/alaviiva->rakenne)
              (map #(konv/string-polusta->keyword % [:paikkausilmoitus :tila])))
            (if nykytilanne?
              (q/hae-paikkaukset-nykytilanteeseen db toleranssi)
              (q/hae-paikkaukset-historiakuvaan db
                                                toleranssi
                                                (konv/sql-date loppu)
                                                (konv/sql-date alku))))
      (catch Exception e
        (tulosta-virhe! "paikkauksia" e)
        nil))))

(defn- hae-laatupoikkeamat
  [db user {:keys [toleranssi alku loppu laatupoikkeamat]} urakat]
  (let [haettavat (haettavat laatupoikkeamat)]
    (when-not (empty? haettavat)
      (try
        (into []
              (comp
                (geo/muunna-pg-tulokset :sijainti)
                (map konv/alaviiva->rakenne)
                (map #(assoc % :selvitys-pyydetty (:selvityspyydetty %)))
                (map #(dissoc % :selvityspyydetty))
                (map #(assoc % :tekija (keyword (:tekija %))))
                (map #(update-in % [:paatos :paatos]
                                 (fn [p]
                                   (when p (keyword p)))))
                (map #(update-in % [:paatos :kasittelytapa]
                                 (fn [k]
                                   (when k (keyword k)))))
                (map #(if (nil? (:kasittelyaika (:paatos %)))
                       (dissoc % :paatos)
                       %)))
              (q/hae-laatupoikkeamat db toleranssi urakat
                                     (konv/sql-date alku)
                                     (konv/sql-date loppu)
                                     (map name haettavat)))
        (catch Exception e
          (tulosta-virhe! "laatupoikkeamia" e)
          nil)))))

(defn- hae-tarkastukset
  [db user {:keys [toleranssi alku loppu tarkastukset]} urakat]
  (let [haettavat (haettavat tarkastukset)]
    (when-not (empty? haettavat)
      (try
        (into []
              (comp
                (map laadunseuranta/tarkastus-tiedolla-onko-ok)
                (geo/muunna-pg-tulokset :sijainti)
                (map konv/alaviiva->rakenne)
                (map #(konv/string->keyword % :tyyppi))
                (map (fn [tarkastus]
                       (condp = (:tyyppi tarkastus)
                         :talvihoito (dissoc tarkastus :soratiemittaus)
                         :soratie (dissoc tarkastus :talvihoitomittaus)
                         :tiesto (dissoc tarkastus :soratiemittaus :talvihoitomittaus)
                         :laatu (dissoc tarkastus :soratiemittaus :talvihoitomittaus)
                         :pistokoe (dissoc tarkastus :soratiemittaus :talvihoitomittaus)))))
              (q/hae-tarkastukset db
                                  toleranssi
                                  urakat
                                  (konv/sql-date alku)
                                  (konv/sql-date loppu)
                                  (map name haettavat)))
        (catch Exception e
          (tulosta-virhe! "tarkastuksia" e)
          nil)))))

(defn- hae-turvallisuuspoikkeamat
  [db user {:keys [toleranssi alku loppu turvallisuus]} urakat]
  (when (:turvallisuuspoikkeamat turvallisuus)
    (try
      (konv/sarakkeet-vektoriin
        (into []
              (comp
                (map konv/alaviiva->rakenne)
                (geo/muunna-pg-tulokset :sijainti)
                (map #(konv/array->vec % :tyyppi))
                (map #(konv/string-vector->keyword-vector % :tyyppi))
                (map #(konv/string->keyword % :vakavuusaste)))
              (q/hae-turvallisuuspoikkeamat db toleranssi urakat (konv/sql-date alku)
                                            (konv/sql-date loppu)))
        {:korjaavatoimenpide :korjaavattoimenpiteet})
      (catch Exception e
        (tulosta-virhe! "turvallisuuspoikkeamia" e)
        nil))))

(defn- hae-tyokoneet
  [db user {:keys [alue alku loppu talvi kesa urakka-id hallintayksikko nykytilanne?]} urakat]
  (when nykytilanne?
    (let [haettavat-toimenpiteet (haettavat (merge talvi kesa))]
      (when-not (empty? haettavat-toimenpiteet)
        (try
          (let [tpi-str (str "{" (clojure.string/join "," haettavat-toimenpiteet) "}")
                valitun-alueen-geometria (if urakka-id
                                           (let [urakan-aluetiedot (first (urakat-q/hae-urakan-geometria db urakka-id))]
                                             (or (:urakka_alue urakan-aluetiedot)
                                                 (:alueurakka_alue urakan-aluetiedot)))
                                           (when hallintayksikko
                                             (:alue (first (hal-q/hae-hallintayksikon-geometria db hallintayksikko)))))]
            (into {}
                  (comp
                    (map #(update-in % [:sijainti] (comp geo/piste-koordinaatit)))
                    (map #(update-in % [:edellinensijainti] (fn [pos] (when pos
                                                                        (geo/piste-koordinaatit pos)))))
                    (map #(assoc % :tyyppi :tyokone))
                    (map #(konv/array->set % :tehtavat))
                    (map (juxt :tyokoneid identity)))
                  (q/hae-tyokoneet db (:xmin alue) (:ymin alue) (:xmax alue) (:ymax alue) valitun-alueen-geometria
                                   urakka-id tpi-str)))
          (catch Exception e
            (tulosta-virhe! "tyokoneet" e)
            nil))))))

(defn- hae-toteumien-reitit
  [db user {:keys [toleranssi alue alku loppu talvi kesa]} urakat]
  (let [haettavat-toimenpiteet (haettavat (merge talvi kesa))]
    (when-not (empty? haettavat-toimenpiteet)
      (try
        (let [toimenpidekoodit (map :id (q/hae-toimenpidekoodit db haettavat-toimenpiteet))]
          (when-not (empty? toimenpidekoodit)
            (konv/sarakkeet-vektoriin
              (into []
                    (comp
                      (harja.geo/muunna-pg-tulokset :reitti)
                      (map konv/alaviiva->rakenne)
                      (map #(assoc % :tyyppi :toteuma)))
                    (q/hae-toteumat db toleranssi
                                    (konv/sql-date alku) (konv/sql-date loppu) toimenpidekoodit
                                    urakat (:xmin alue) (:ymin alue) (:xmax alue) (:ymax alue)))
              {:tehtava     :tehtavat
               :materiaali  :materiaalit
               :reittipiste :reittipisteet})))
        (catch Exception e
          (tulosta-virhe! "toteumaa" e)
          nil)))))

(defn hae-tilannekuvaan
  [db user tiedot]
  (println (pr-str tiedot))
  (let [urakat (urakat/kayttajan-urakat-aikavalilta db user
                                                    (:urakka-id tiedot) (:urakoitsija tiedot) (:urakkatyyppi tiedot)
                                                    (:hallintayksikko tiedot) (:alku tiedot) (:loppu tiedot))]

    ;; Teoriassa on mahdollista, että käyttäjälle ei (näillä parametreilla) palauteta yhtään urakkaa.
    ;; Tällöin voitaisiin hakea kaikki "julkiset" asiat, esim ilmoitukset joita ei ole sidottu mihinkään
    ;; urakkaan. Käytännössä tästä syntyy ongelmia kyselyissä, sillä tuntuu olevan erittäin vaikeaa tehdä
    ;; kyselyä, joka esim palauttaa ilmoituksen jos a) ilmoitus ei kuulu mihinkään urakkaan TAI b) ilmoitus
    ;; kuuluu listassa olevaan urakkaan _jos lista urakoita ei ole tyhjä_. i.urakka IN (:urakat) epäonnistuu,
    ;; jos annettu lista on tyhjä.
    (when-not (empty? urakat)
      (log/debug "Löydettiin tilannekuvaan sisältöä urakoista: " (pr-str urakat))
      (let [tiedot (assoc tiedot :toleranssi (karkeistustoleranssi (:alue tiedot)))]
        {:toteumat               (tulosta-tulos! "toteumaa"
                                                 (hae-toteumien-reitit db user tiedot urakat))
         :tyokoneet              (tulosta-tulos! "tyokonetta"
                                                 (hae-tyokoneet db user tiedot urakat))
         :turvallisuuspoikkeamat (tulosta-tulos! "turvallisuuspoikkeamaa"
                                                 (hae-turvallisuuspoikkeamat db user tiedot urakat))
         :tarkastukset           (tulosta-tulos! "tarkastusta"
                                                 (hae-tarkastukset db user tiedot urakat))
         :laatupoikkeamat        (tulosta-tulos! "laatupoikkeamaa"
                                                 (hae-laatupoikkeamat db user tiedot urakat))
         :paikkaus               (tulosta-tulos! "paikkausta"
                                                 (hae-paikkaustyot db user tiedot urakat))
         :paallystys             (tulosta-tulos! "paallystysta"
                                                 (hae-paallystystyot db user tiedot urakat))
         :ilmoitukset            (tulosta-tulos! "ilmoitusta"
                                                 (hae-ilmoitukset db user tiedot urakat))}))))

(defrecord Tilannekuva []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-tilannekuvaan
                      (fn [user tiedot]
                        (hae-tilannekuvaan (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-tilannekuvaan)

    this))
