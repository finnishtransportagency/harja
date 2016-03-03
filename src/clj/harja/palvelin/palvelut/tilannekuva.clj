(ns harja.palvelin.palvelut.tilannekuva
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin
             :refer [julkaise-palvelu poista-palvelut]]

            [harja.domain.ilmoitukset :as ilmoitukset-domain]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.hallintayksikot :as hal-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.tilannekuva :as q]
            [harja.palvelin.palvelut.urakat :as urakat]

            [harja.domain.laadunseuranta :as laadunseuranta]
            [harja.geo :as geo]
            [harja.pvm :as pvm]
            [harja.domain.tilannekuva :as tk]
            [harja.ui.kartta.esitettavat-asiat
             :as esitettavat-asiat
             :refer [kartalla-esitettavaan-muotoon]]
            [harja.palvelin.palvelut.karttakuvat :as karttakuvat]
            [clojure.set :refer [union]]
            [harja.transit :as transit]))

(defn tulosta-virhe! [asiat e]
  (log/error (str "*** ERROR *** Yritettiin hakea tilannekuvaan " asiat
                  ", mutta virhe tapahtui: " (.getMessage e))))

(defn tulosta-tulos! [asiaa tulos]
  (if (vector? tulos)
    (log/debug (str "  - " (count tulos) " " asiaa))
    (log/debug (str "  - " (count (keys tulos)) " " asiaa)))
  tulos)

(defn haettavat [s]
  (into #{}
        (map (comp :nimi tk/suodattimet-idlla))
        s))

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
      (mapv
       #(assoc % :uusinkuittaus
               (when-not (empty? (:kuittaukset %))
                 (:kuitattu (last (sort-by :kuitattu (:kuittaukset %))))))
       (konv/sarakkeet-vektoriin
        (into []
              (comp
               (geo/muunna-pg-tulokset :sijainti)
               (map konv/alaviiva->rakenne)
               (map ilmoitukset-domain/lisaa-ilmoituksen-tila)
               (filter #(tilat (:tila %)))
               (map #(assoc % :urakkatyyppi (keyword (:urakkatyyppi %))))
               (map #(konv/array->vec % :selitteet))
               (map #(assoc % :selitteet (mapv keyword (:selitteet %))))
               (map #(assoc-in
                      %
                      [:kuittaus :kuittaustyyppi]
                      (keyword (get-in % [:kuittaus :kuittaustyyppi]))))
               (map #(assoc % :ilmoitustyyppi (keyword (:ilmoitustyyppi %))))
               (map #(assoc-in % [:ilmoittaja :tyyppi]
                               (keyword (get-in % [:ilmoittaja :tyyppi])))))
              (q/hae-ilmoitukset db
                                 toleranssi
                                 (when-not (:nykytilanne? tiedot)
                                   (konv/sql-date (:alku tiedot)))
                                 (when-not (:nykytilanne? tiedot)
                                   (konv/sql-date (:loppu tiedot)))
                                 urakat
                                 (mapv name haettavat)))
        {:kuittaus :kuittaukset})))))

(defn- hae-paallystystyot
  [db user {:keys [toleranssi alku loppu yllapito nykytilanne?]} urakat]
  (when (tk/valittu? yllapito tk/paallystys)
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
                                                (konv/sql-date alku))))))

(defn- hae-paikkaustyot
  [db user {:keys [toleranssi alku loppu yllapito nykytilanne?]} urakat]
  (when (tk/valittu? yllapito tk/paikkaus)
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
                                              (konv/sql-date alku))))))

(defn- hae-laatupoikkeamat
  [db user {:keys [toleranssi alku loppu laatupoikkeamat nykytilanne?]} urakat]
  (let [haettavat (haettavat laatupoikkeamat)]
    (when-not (empty? haettavat)
      (into []
              (comp
                (map konv/alaviiva->rakenne)
                (map #(update-in % [:paatos :paatos]
                                 (fn [p]
                                   (when p (keyword p)))))
                (remove (fn [lp]
                          (if nykytilanne?
                            (#{:hylatty :ei_sanktiota} (get-in lp [:paatos :paatos]))
                            false)))
                (map #(assoc % :selvitys-pyydetty (:selvityspyydetty %)))
                (map #(dissoc % :selvityspyydetty))
                (map #(assoc % :tekija (keyword (:tekija %))))
                (map #(update-in % [:paatos :kasittelytapa]
                                 (fn [k]
                                   (when k (keyword k)))))
                (map #(if (nil? (:kasittelyaika (:paatos %)))
                       (dissoc % :paatos)
                       %))
                (geo/muunna-pg-tulokset :sijainti))
              (q/hae-laatupoikkeamat db toleranssi urakat
                                     (konv/sql-date alku)
                                     (konv/sql-date loppu)
                                     (map name haettavat))))))

(defn- hae-tarkastukset
  [db user {:keys [toleranssi alku loppu tarkastukset]} urakat]
  (let [haettavat (haettavat tarkastukset)]
    (when-not (empty? haettavat)
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
                      :pistokoe (dissoc tarkastus :soratiemittaus
                                        :talvihoitomittaus)))))
            (q/hae-tarkastukset db
                                toleranssi
                                urakat
                                (konv/sql-date alku)
                                (konv/sql-date loppu)
                                (map name haettavat))))))

(defn- hae-turvallisuuspoikkeamat
  [db user {:keys [toleranssi alku loppu turvallisuus]} urakat]
  (when (tk/valittu? turvallisuus tk/turvallisuuspoikkeamat)
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
     {:korjaavatoimenpide :korjaavattoimenpiteet})))

(defn- hae-tyokoneet
  [db user {:keys [alue alku loppu talvi kesa
                   hallintayksikko nykytilanne?]} urakat]
  (when nykytilanne?
    (let [haettavat-toimenpiteet (haettavat (union talvi kesa))]
      (when-not (empty? haettavat-toimenpiteet)
        (let [tpi-str (str "{" (clojure.string/join "," haettavat-toimenpiteet) "}")
              valitun-alueen-geometria
              (if urakka-id
                (let [urakan-aluetiedot (first (urakat-q/hae-urakan-geometria
                                                db urakka-id))]
                  (or (:urakka_alue urakan-aluetiedot)
                      (:alueurakka_alue urakan-aluetiedot)))
                (when hallintayksikko
                  (:alue (first (hal-q/hae-hallintayksikon-geometria
                                 db hallintayksikko)))))]
          (into {}
                (comp
                 (map #(update-in % [:sijainti] (comp geo/piste-koordinaatit)))
                 (map #(update-in % [:edellinensijainti]
                                  (fn [pos] (when pos
                                              (geo/piste-koordinaatit pos)))))
                 (map #(assoc % :tyyppi :tyokone))
                 (map #(konv/array->set % :tehtavat))
                 (map (juxt :tyokoneid identity)))
                (q/hae-tyokoneet db (:xmin alue) (:ymin alue)
                                 (:xmax alue) (:ymax alue)
                                 valitun-alueen-geometria urakat tpi-str)))))))

(defn- toteumien-toimenpidekoodit [db {:keys [talvi kesa]}]
  (let [koodit (some->> (union talvi kesa)
                        haettavat
                        (q/hae-toimenpidekoodit db)
                        (map :id))]
    (if (empty? koodit)
      nil
      koodit)))

(defn- hae-toteumien-reitit
  [db user {:keys [toleranssi alue alku loppu] :as tiedot} urakat]
  (println "HAE REITIT: " (pr-str tiedot))
  (when-let [toimenpidekoodit (toteumien-toimenpidekoodit db tiedot)]
    (konv/sarakkeet-vektoriin
     (into []
           (comp
            (harja.geo/muunna-pg-tulokset :reitti)
            (map konv/alaviiva->rakenne)
            (map #(assoc % :tyyppi :toteuma)))
           (q/hae-toteumat db toleranssi
                           (konv/sql-date alku)
                           (konv/sql-date loppu) toimenpidekoodit
                           urakat
                           (:xmin alue) (:ymin alue)
                           (:xmax alue) (:ymax alue)))
     {:tehtava     :tehtavat
      :materiaali  :materiaalit
      :reittipiste :reittipisteet})))

(defn- hae-toteumien-selitteet
  [db user {:keys [alue alku loppu] :as tiedot} urakat]
  (when-let [toimenpidekoodit (toteumien-toimenpidekoodit db tiedot)]
    (q/hae-toteumien-selitteet db
                               (konv/sql-date alku) (konv/sql-date loppu)
                               toimenpidekoodit urakat
                               (:xmin alue) (:ymin alue)
                               (:xmax alue) (:ymax alue))))

(def tilannekuvan-osiot
  #{:toteumat :tyokoneet :turvallisuuspoikkeamat :tarkastukset
    :laatupoikkeamat :paikkaus :paallystys :ilmoitukset})

(defmulti hae-osio (fn [db user tiedot urakat osio] osio))
(defmethod hae-osio :toteumat [db user tiedot urakat _]
  (hae-toteumien-selitteet db user tiedot urakat))

(defmethod hae-osio :toteumat-kuva [db user tiedot urakat _]
  (tulosta-tulos! "toteumaa"
                  (hae-toteumien-reitit db user tiedot urakat)))

(defmethod hae-osio :tyokoneet [db user tiedot urakat _]
  (tulosta-tulos! "tyokonetta"
                  (hae-tyokoneet db user tiedot urakat)))

(defmethod hae-osio :turvallisuuspoikkeamat [db user tiedot urakat _]
  (tulosta-tulos! "turvallisuuspoikkeamaa"
                  (hae-turvallisuuspoikkeamat db user tiedot urakat)))

(defmethod hae-osio :tarkastukset [db user tiedot urakat _]
  (tulosta-tulos! "tarkastusta"
                  (hae-tarkastukset db user tiedot urakat)))

(defmethod hae-osio :laatupoikkeamat [db user tiedot urakat _]
  (tulosta-tulos! "laatupoikkeamaa"
                  (hae-laatupoikkeamat db user tiedot urakat)))

(defmethod hae-osio :paikkaus [db user tiedot urakat _]
  (tulosta-tulos! "paikkausta"
                  (hae-paikkaustyot db user tiedot urakat)))

(defmethod hae-osio :paallystys [db user tiedot urakat _]
  (tulosta-tulos! "paallystysta"
                  (hae-paallystystyot db user tiedot urakat)))

(defmethod hae-osio :ilmoitukset [db user tiedot urakat _]
  (tulosta-tulos! "ilmoitusta"
                  (hae-ilmoitukset db user tiedot urakat)))

(defn yrita-hakea-osio [db user tiedot urakat osio]
  (try
    (hae-osio db user tiedot urakat osio)
    (catch Exception e
      (tulosta-virhe! (name osio) e)
      nil)))

(defn hae-tilannekuvaan
<<<<<<< HEAD
  ([db user tiedot]
   (hae-tilannekuvaan db user tiedot tilannekuvan-osiot))
  ([db user tiedot osiot]
   (let [urakat (urakat/kayttajan-urakat-aikavalilta
                 db user
                 (:urakka-id tiedot) (:urakoitsija tiedot) (:urakkatyyppi tiedot)
                 (:hallintayksikko tiedot) (:alku tiedot) (:loppu tiedot))]

     ;; Teoriassa on mahdollista, että käyttäjälle ei (näillä parametreilla)
     ;; palauteta yhtään urakkaa.
     ;; Tällöin voitaisiin hakea kaikki "julkiset" asiat, esim ilmoitukset joita
     ;; ei ole sidottu mihinkään urakkaan. Käytännössä tästä syntyy ongelmia
     ;; kyselyissä, sillä tuntuu olevan erittäin vaikeaa tehdä kyselyä, joka esim.
     ;; palauttaa ilmoituksen jos a) ilmoitus ei kuulu mihinkään urakkaan
     ;; TAI b) ilmoitus kuuluu listassa olevaan urakkaan _jos lista urakoita ei ole
     ;; tyhjä_. i.urakka IN (:urakat) epäonnistuu, jos annettu lista on tyhjä.
     (when-not (empty? urakat)
       (log/debug "Löydettiin tilannekuvaan sisältöä urakoista: " (pr-str urakat))
       (let [tiedot (assoc tiedot :toleranssi (karkeistustoleranssi (:alue tiedot)))]
         (into {}
               (map (juxt identity (partial yrita-hakea-osio db user tiedot urakat)))
               osiot))))))

;; {:urakka-id nil, :alue {:xmin 440408, :ymin 7191776, :xmax 451848, :ymax 7196880}
;;  :ilmoitukset {:tilat #{:avoimet}}, :hallintayksikko nil, :urakoitsija nil,
;;  :tarkastukset #{7 6 9 10 8},
;;  :alku #inst "2016-02-05T14:48:16.000-00:00"
;;  :loppu #inst "2016-02-26T14:48:16.000-00:00"
;;  :nykytilanne? true,  :urakkatyyppi :hoito}

(defn- karttakuvan-suodattimet
  "Tekee karttakuvan URL parametreistä suodattimet"
  [{:keys [extent parametrit]}]
  (let [[x1 y1 x2 y2] extent
        hakuparametrit (some-> parametrit (get "tk") transit/lue-transit-string)]
    (merge hakuparametrit
           {:alue {:xmin x1 :ymin y1
                   :xmax x2 :ymax x2}})))

(defn- hae-karttakuvan-tiedot [db user parametrit]
  (let [tiedot (karttakuvan-suodattimet parametrit)]
    (kartalla-esitettavaan-muotoon
     (map #(assoc % :tyyppi-kartalla :toteuma)
          (:toteumat-kuva (hae-tilannekuvaan db user tiedot #{:toteumat-kuva})))
     nil nil)))
=======
  [db user tiedot]
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
>>>>>>> 55720a40f0d72f5a007516e284f2a81940e12307

(defrecord Tilannekuva []
  component/Lifecycle
  (start [{karttakuvat :karttakuvat
           db :db
           http :http-palvelin
           :as this}]
    (julkaise-palvelu http :hae-tilannekuvaan
                      (fn [user tiedot]
                        (hae-tilannekuvaan db user tiedot)))
    (karttakuvat/rekisteroi-karttakuvan-lahde!
     karttakuvat :tilannekuva (partial #'hae-karttakuvan-tiedot db))
    this)

  (stop [{karttakuvat :karttakuvat :as this}]
    (poista-palvelut (:http-palvelin this)
                     :hae-tilannekuvaan)
    (karttakuvat/poista-karttakuvan-lahde! karttakuvat :tilannekuva)
    this))
