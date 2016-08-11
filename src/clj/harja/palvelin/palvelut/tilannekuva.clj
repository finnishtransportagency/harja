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
             :refer [kartalla-esitettavaan-muotoon-xf]]
            [harja.palvelin.palvelut.karttakuvat :as karttakuvat]
            [clojure.set :refer [union]]
            [harja.transit :as transit]
            [harja.kyselyt.turvallisuuspoikkeamat :as turvallisuuspoikkeamat-q]
            [harja.domain.oikeudet :as oikeudet]
            [clojure.core.async :as async]))

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

(defn- hae-ilmoitukset
  [db user {:keys [toleranssi] {:keys [tyypit tilat]} :ilmoitukset :as tiedot} urakat]
  (when-not (empty? urakat)
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
           {:kuittaus :kuittaukset}))))))

(defn- hae-paallystystyot
  [db user {:keys [toleranssi alku loppu yllapito nykytilanne?]} urakat]
  ;; Muut haut toimivat siten, että urakat parametrissa on vain urakoita, joihin
  ;; käyttäjällä on oikeudet. Jos lista on tyhjä, urakoita ei ole joko valittuna,
  ;; tai yritettiin hakea urakoilla, joihin käyttäjällä ei ole oikeuksia.
  ;; Ylläpidon hommat halutaan hakea, vaikkei valittuna olisikaan yhtään urakkaa.
  ;; Lista voi siis tulla tänne tyhjänä. Jos näin on, täytyy tarkastaa, onko käyttäjällä
  ;; yleistä tilannekuvaoikeutta.
  (when (or (not (empty? urakat)) (oikeudet/voi-lukea? (if nykytilanne?
                                                         oikeudet/tilannekuva-nykytilanne
                                                         oikeudet/tilannekuva-historia)
                                                       nil user))
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
                                                  (konv/sql-date alku)))))))

(defn- hae-paikkaustyot
  [db user {:keys [toleranssi alku loppu yllapito nykytilanne?]} urakat]
  ;; Muut haut toimivat siten, että urakat parametrissa on vain urakoita, joihin
  ;; käyttäjällä on oikeudet. Jos lista on tyhjä, urakoita ei ole joko valittuna,
  ;; tai yritettiin hakea urakoilla, joihin käyttäjällä ei ole oikeuksia.
  ;; Ylläpidon hommat halutaan hakea, vaikkei valittuna olisikaan yhtään urakkaa.
  ;; Lista voi siis tulla tänne tyhjänä. Jos näin on, täytyy tarkastaa, onko käyttäjällä
  ;; yleistä tilannekuvaoikeutta.
  (when (or (not (empty? urakat)) (oikeudet/voi-lukea? (if nykytilanne?
                                                         oikeudet/tilannekuva-nykytilanne
                                                         oikeudet/tilannekuva-historia)
                                                       nil user))
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
                                               (konv/sql-date alku)))))))

(defn- hae-laatupoikkeamat
  [db user {:keys [toleranssi alku loppu laatupoikkeamat nykytilanne?]} urakat]
  (when-not (empty? urakat)
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
                                    (map name haettavat)))))))

(defn- hae-tarkastukset
  [db user {:keys [toleranssi alku loppu tarkastukset]} urakat]
  (when-not (empty? urakat)
    (let [haettavat (haettavat tarkastukset)]
     (when-not (empty? haettavat)
       (into []
             (comp
               (map #(konv/array->set % :vakiohavainnot))
               (map laadunseuranta/tarkastus-tiedolla-onko-ok)
               (geo/muunna-pg-tulokset :sijainti)
               (map konv/alaviiva->rakenne)
               (map #(konv/string->keyword % :tyyppi))
               (map (fn [tarkastus]
                      (condp = (:tyyppi tarkastus)
                        :talvihoito (dissoc tarkastus :soratiemittaus)
                        :soratie (dissoc tarkastus :talvihoitomittaus)
                        :tiesto (dissoc tarkastus :soratiemittaus :talvihoitomittaus)
                        :laatu (dissoc tarkastus :soratiemittaus :talvihoitomittaus)))))
             (q/hae-tarkastukset db
                                 toleranssi
                                 urakat
                                 (konv/sql-date alku)
                                 (konv/sql-date loppu)
                                 (map name haettavat)))))))

(defn- hae-turvallisuuspoikkeamat
  [db user {:keys [toleranssi alku loppu turvallisuus]} urakat]
  (when-not (empty? urakat)
    (when (tk/valittu? turvallisuus tk/turvallisuuspoikkeamat)
     (let [tulos (konv/sarakkeet-vektoriin
                   (into []
                         turvallisuuspoikkeamat-q/turvallisuuspoikkeama-xf
                         (q/hae-turvallisuuspoikkeamat db toleranssi urakat (konv/sql-date alku)
                                                       (konv/sql-date loppu)))
                   {:korjaavatoimenpide :korjaavattoimenpiteet})]
       tulos))))

(defn- hae-tyokoneet
  [db user {:keys [alue alku loppu talvi kesa urakka-id hallintayksikko nykytilanne? yllapito] :as optiot} urakat]
  (when-not (empty? urakat)
    (when nykytilanne?
      (let [yllapito (filter tk/yllapidon-reaaliaikaseurattava? yllapito)
            haettavat-toimenpiteet (haettavat (union talvi kesa yllapito))]
        (when (not (empty? haettavat-toimenpiteet))
          (let [tpi-haku-str (konv/seq->array haettavat-toimenpiteet)
                valitun-alueen-geometria
                (if urakka-id
                  (let [urakan-aluetiedot (first (urakat-q/hae-urakan-geometria db urakka-id))]
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
                  (q/hae-tyokoneet db
                                   (:xmin alue) (:ymin alue)
                                   (:xmax alue) (:ymax alue)
                                   valitun-alueen-geometria
                                   urakat
                                   tpi-haku-str))))))))

(defn- toteumien-toimenpidekoodit [db {:keys [talvi kesa]}]
  (let [koodit (some->> (union talvi kesa)
                        haettavat
                        (q/hae-toimenpidekoodit db)
                        (map :id))]
    (if (empty? koodit)
      nil
      koodit)))

(defn- hae-toteumien-reitit
  [db ch user {:keys [toleranssi alue alku loppu] :as tiedot} urakat]
  (when-not (empty? urakat)
    (when-let [toimenpidekoodit (toteumien-toimenpidekoodit db tiedot)]
      (q/hae-toteumat db ch
                      {:toleranssi toleranssi
                       :alku (konv/sql-date alku)
                       :loppu (konv/sql-date loppu)
                       :toimenpidekoodit toimenpidekoodit
                       :urakat urakat
                       :xmin (:xmin alue)
                       :ymin (:ymin alue)
                       :xmax (:xmax alue)
                       :ymax (:ymax alue)}))))

(defn- hae-suljetut-tieosuudet
  [db user {:keys [yllapito alue]} urakat]
  (when (tk/valittu? yllapito tk/suljetut-tiet)
    (vec (map (comp #(konv/array->vec % :kaistat)
                    #(konv/array->vec % :ajoradat))
              (q/hae-suljetut-tieosuudet db {:x1 (:xmin alue)
                                             :y1 (:ymin alue)
                                             :x2 (:xmax alue)
                                             :y2 (:ymax alue)
                                             :urakat (when-not (every? nil? urakat)
                                                       urakat)
                                             :treshold 100})))))

(defn- hae-toteumien-selitteet
  [db user {:keys [alue alku loppu] :as tiedot} urakat]
  (when-not (empty? urakat)
    (when-let [toimenpidekoodit (toteumien-toimenpidekoodit db tiedot)]
      (q/hae-toteumien-selitteet db
                                 (konv/sql-date alku) (konv/sql-date loppu)
                                 toimenpidekoodit urakat
                                 (:xmin alue) (:ymin alue)
                                 (:xmax alue) (:ymax alue)))))

(def tilannekuvan-osiot
  #{:toteumat :tyokoneet :turvallisuuspoikkeamat :tarkastukset
    :laatupoikkeamat :paikkaus :paallystys :ilmoitukset :suljetut-tieosuudet})

(defmulti hae-osio (fn [db user tiedot urakat osio] osio))
(defmethod hae-osio :toteumat [db user tiedot urakat _]
  (tulosta-tulos! "toteuman selitettä"
                  (hae-toteumien-selitteet db user tiedot urakat)))

(defmethod hae-osio :toteumat-kuva [db user tiedot urakat _]
  (tulosta-tulos! "toteuman reittiä"
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

(defmethod hae-osio :suljetut-tieosuudet [db user tiedot urakat _]
  (tulosta-tulos! "suljettua tieosuutta"
                  (hae-suljetut-tieosuudet db user tiedot urakat)))

(defn yrita-hakea-osio [db user tiedot urakat osio]
  (try
    (hae-osio db user tiedot urakat osio)
    (catch Exception e
      (tulosta-virhe! (name osio) e)
      nil)))

(defn hae-urakat [db user tiedot]
  (urakat/kayttajan-urakat-aikavalilta-alueineen db user (if (:nykytilanne? tiedot)
                                                           oikeudet/tilannekuva-nykytilanne
                                                           oikeudet/tilannekuva-historia)
                                                 nil (:urakoitsija tiedot) (:urakkatyyppi tiedot)
                                                 nil (:alku tiedot) (:loppu tiedot)))

(defn hae-tilannekuvaan
  ([db user tiedot]
   (hae-tilannekuvaan db user tiedot tilannekuvan-osiot))
  ([db user tiedot osiot]
   (let [urakat (filter #(oikeudet/voi-lukea? (if (:nykytilanne? tiedot)
                                                oikeudet/tilannekuva-nykytilanne
                                                oikeudet/tilannekuva-historia) % user)
                        (:urakat tiedot))]
     (log/debug "Haetaan tilannekuvaan asioita urakoista " (pr-str urakat))
     (let [tiedot (assoc tiedot :toleranssi (geo/karkeistustoleranssi (:alue tiedot)))]
       (into {}
             (map (juxt identity (partial yrita-hakea-osio db user tiedot urakat)))
             osiot)))))

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
        hakuparametrit (some-> parametrit (get "tk") transit/lue-transit-string)
        ]
    (as-> hakuparametrit p
          (merge p
                 {:alue {:xmin x1 :ymin y1
                         :xmax x2 :ymax y2}})
          (assoc p :toleranssi (geo/karkeistustoleranssi (:alue p))))))

(defn- hae-karttakuvan-tiedot [db user parametrit]
  (let [tiedot (karttakuvan-suodattimet parametrit)
        kartalle-xf (kartalla-esitettavaan-muotoon-xf)
        ch (async/chan 32
                       (comp
                         (map konv/alaviiva->rakenne)
                         (map #(assoc %
                                :tyyppi :toteuma
                                :tyyppi-kartalla :toteuma
                                :tehtavat [(:tehtava %)]))
                         kartalle-xf))
        urakat (filter #(oikeudet/voi-lukea? (if (:nykytilanne? tiedot)
                                               oikeudet/tilannekuva-nykytilanne
                                               oikeudet/tilannekuva-historia) % user)
                       (:urakat tiedot))]
    (async/thread
      (hae-toteumien-reitit db ch user tiedot urakat))
    ch))

(defrecord Tilannekuva []
  component/Lifecycle
  (start [{karttakuvat :karttakuvat
           db :db
           http :http-palvelin
           :as this}]
    (julkaise-palvelu http :hae-tilannekuvaan
                      (fn [user tiedot]
                        (hae-tilannekuvaan db user tiedot)))
    (julkaise-palvelu http :hae-urakat-tilannekuvaan
                      (fn [user tiedot]
                        (hae-urakat db user tiedot)))
    (karttakuvat/rekisteroi-karttakuvan-lahde!
      karttakuvat :tilannekuva
      ;; Viitataan var kautta funktioon, jotta sen voi RPELissä määritellä uudestaan
      (partial #'hae-karttakuvan-tiedot db))
    this)

  (stop [{karttakuvat :karttakuvat :as this}]
    (poista-palvelut (:http-palvelin this)
                     :hae-tilannekuvaan
                     :hae-urakat-tilannekuvaan)
    (karttakuvat/poista-karttakuvan-lahde! karttakuvat :tilannekuva)
    this))
