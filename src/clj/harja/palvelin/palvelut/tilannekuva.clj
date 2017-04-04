(ns harja.palvelin.palvelut.tilannekuva
  "Tilannekuva-näkymän palvelinkomponentti.

  Tilannekuva-näkymässä voidaan hakea useita eri kartalla näkyviä
  asioita kartalle tarkasteltavaksi. Kartalle haku tehdään kaikkien
  tietojen osalta yhdellä palvelukutsulla, jolle kaikki hakuparametrit
  välitetään. Tilannekuva palauttaa mäpin, jossa avaimena on osion
  nimi keyword ja arvona hakuehdoilla löytyneet asiat.

  Löytyneet asiat voivat olla suoraan frontilla renderöitäviä asioita
  tai muuta metatietoa kuten karttaselitteitä, tasosta riippuen.

  ks. hae-osio multimetodi, joka on toteutettu jokaiselle osiolle.

  PALVELUT

  :hae-tilannekuvaan tekee yllä kuvatun haun tilannekuvan parametrien
  perusteella.

  :hae-urakat-tilannekuvaan hakee urakat, joiden katselu tilannekuvassa
  on mahdollista käyttäjän oikeuksilla.

  KYSELYT

  Tilannekuvan kyselyt ovat harja.kyselyt.tilannekuva nimiavaruudessa.
  Tilannekuvaa varten on tehty omat kyselyt koska filtterit ja
  palautettavien kenttien määrät ovat erit kuin yleisesti listausnäkymissä.

  KARTTAKUVAT JA INFOPANEELI

  Tilannekuva-komponentti rekisteröi karttakuvan lähteen kaikille
  palvelimella renderöitäville tilannekuvan karttatasoille.

  Karttakuvien lisäksi rekisteröidään jokaiselle palvelimella
  renderöintävälle karttatasolle funktio, joka hakee klikkauspisteessä
  löytyvät asiat ja palauttaa niiden tiedot infopaneelissa näyttämistä
  varten."
  (:require [clojure.core.async :as async]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :refer [union]]
            [com.stuartsierra.component :as component]
            [harja
             [geo :as geo]
             [pvm :as pvm]
             [transit :as transit]]
            [harja.domain
             [ilmoitukset :as ilmoitukset-domain]
             [laadunseuranta :as laadunseuranta]
             [oikeudet :as oikeudet]
             [roolit :as roolit]
             [tilannekuva :as tk]]
            [harja.kyselyt
             [hallintayksikot :as hal-q]
             [konversio :as konv]
             [tilannekuva :as q]
             [turvallisuuspoikkeamat :as turvallisuuspoikkeamat-q]
             [urakat :as urakat-q]
             [toteumat :as toteumat-q]
             [tietyoilmoitukset :as tietyoilmoitukset-q]]
            [harja.palvelin.komponentit.http-palvelin
             :refer
             [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.palvelut
             [karttakuvat :as karttakuvat]
             [urakat :as urakat]
             [interpolointi :as interpolointi]]
            [harja.ui.kartta.esitettavat-asiat
             :as esitettavat-asiat
             :refer [kartalla-esitettavaan-muotoon-xf]]
            [harja.palvelin.palvelut.karttakuvat :as karttakuvat]
            [clojure.set :refer [union]]
            [harja.transit :as transit]
            [harja.kyselyt.turvallisuuspoikkeamat :as turvallisuuspoikkeamat-q]
            [harja.domain.oikeudet :as oikeudet]
            [clojure.core.async :as async]
            [clojure.java.jdbc :as jdbc]
            [harja.domain.roolit :as roolit]
            [harja.palvelin.palvelut.kayttajatiedot :as kayttajatiedot]
            [taoensso.timbre :as log]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [harja.kyselyt.yllapitokohteet :as yllapitokohteet-q]
            [harja.domain.tierekisteri :as tr]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yllapitokohteet-yleiset]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :as pko]))

(defn tulosta-virhe! [asiat e]
  (log/error (str "*** ERROR *** Yritettiin hakea tilannekuvaan " asiat
                  ", mutta virhe tapahtui: " (.getMessage e))))

(defn tulosta-tulos!
  ([asiaa tulos] (tulosta-tulos! asiaa tulos identity))
  ([asiaa tulos fn]
   (if (vector? (fn tulos))
     (log/debug (str "  - " (count (fn tulos)) " " asiaa))
     (log/debug (str "  - " (count (keys (fn tulos))) " " asiaa)))
   tulos))

(defn haettavat [s]
  (into #{}
        (map (comp :nimi tk/suodattimet-idlla))
        s))

(def ilmoitus-xf
  (comp
   (geo/muunna-pg-tulokset :sijainti)
   (map konv/alaviiva->rakenne)
   (map #(konv/string->keyword % :tila))
   (map #(assoc % :urakkatyyppi (keyword (:urakkatyyppi %))))
   (map #(konv/array->vec % :selitteet))
   (map #(assoc % :selitteet (mapv keyword (:selitteet %))))
   (map #(assoc-in
          %
          [:kuittaus :kuittaustyyppi]
          (keyword (get-in % [:kuittaus :kuittaustyyppi]))))
   (map #(assoc % :ilmoitustyyppi (keyword (:ilmoitustyyppi %))))
   (map #(assoc-in % [:ilmoittaja :tyyppi]
                   (keyword (get-in % [:ilmoittaja :tyyppi]))))))

(defn- hae-ilmoitukset
  [db user {:keys [toleranssi] {:keys [tyypit]} :ilmoitukset :as tiedot} urakat]
  (when-not (empty? urakat)
    (let [haettavat (haettavat tyypit)]
     (when-not (empty? haettavat)
       (mapv
         #(assoc % :uusinkuittaus
                   (when-not (empty? (:kuittaukset %))
                     (:kuitattu (last (sort-by :kuitattu (:kuittaukset %))))))
         (let [ilmoitukset (konv/sarakkeet-vektoriin
                            (into []
                                  ilmoitus-xf
                                  (q/hae-ilmoitukset db
                                                     toleranssi
                                                     (konv/sql-date (:alku tiedot))
                                                     (konv/sql-date (:loppu tiedot))
                                                     urakat
                                                     (mapv name haettavat)))
                            {:kuittaus :kuittaukset})]
           ilmoitukset))))))

(defn- hae-tietyoilmoitukset [db user {:keys [tietyoilmoitukset nykytilanne?] :as tiedot} urakat]
  (when (tk/valittu? tietyoilmoitukset tk/tietyoilmoitukset)
    (when (or (not (empty? urakat))
              (oikeudet/voi-lukea? (if nykytilanne?
                                     oikeudet/tilannekuva-nykytilanne
                                     oikeudet/tilannekuva-historia)
                                   nil user))
      (tietyoilmoitukset-q/hae-ilmoitukset-tilannekuvaan db (assoc tiedot :urakat urakat
                                                                          :tilaaja? (roolit/tilaajan-kayttaja? user))))))

(defn- hae-yllapitokohteet
  [db user {:keys [toleranssi alku loppu yllapito nykytilanne? tyyppi]} urakat]
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
    (when (tk/valittu? yllapito (case tyyppi
                                  "paallystys" tk/paallystys
                                  "paikkaus" tk/paikkaus))
      (let [vastaus (into []
                          (comp
                            (map konv/alaviiva->rakenne)
                            (map #(assoc % :tila (yllapitokohteet-domain/yllapitokohteen-tarkka-tila %)))
                            (map #(konv/string-polusta->keyword % [:paallystysilmoitus-tila]))
                            (map #(konv/string-polusta->keyword % [:paikkausilmoitus-tila]))
                            (map #(konv/string-polusta->keyword % [:yllapitokohdetyotyyppi]))
                            (map #(konv/string-polusta->keyword % [:yllapitokohdetyyppi]))
                            (map #(yllapitokohteet-q/liita-kohdeosat db % (:id %))))
                          (if nykytilanne?
                            (case tyyppi
                              "paallystys" (q/hae-paallystykset-nykytilanteeseen db)
                              "paikkaus" (q/hae-paikkaukset-nykytilanteeseen db))
                            (case tyyppi
                              "paallystys" (q/hae-paallystykset-historiakuvaan db
                                                                               (konv/sql-date loppu)
                                                                               (konv/sql-date alku))
                              "paikkaus" (q/hae-paikkaukset-historiakuvaan db
                                                                           (konv/sql-date loppu)
                                                                           (konv/sql-date alku)))))
            vastaus (konv/sarakkeet-vektoriin
                      vastaus
                      {:yhteyshenkilo :yhteyshenkilot})
            osien-pituudet-tielle (yllapitokohteet-yleiset/laske-osien-pituudet db vastaus)
            vastaus (mapv #(assoc %
                             :pituus
                             (tr/laske-tien-pituus (osien-pituudet-tielle (:tr-numero %)) %))
                          vastaus)]
        vastaus))))

(defn- hae-paallystystyot
  [db user suodattimet urakat]
  (hae-yllapitokohteet db user (assoc suodattimet :tyyppi "paallystys") urakat))

(defn- hae-paikkaustyot
  [db user suodattimet urakat]
  (hae-yllapitokohteet db user (assoc suodattimet :tyyppi "paikkaus") urakat))

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

(defn- laajenna-tyokone-extent [alue]
  (let [{:keys [xmin xmax ymin ymax]} alue
        hypotenuusa (geo/alueen-hypotenuusa alue)
        laajennettu-alue (geo/laajenna-extent [xmin ymin xmax ymax]
                                              hypotenuusa)]
    (zipmap [:xmin :ymin :xmax :ymax] laajennettu-alue)))

(defn- tyokoneiden-toimenpiteet
  "Palauttaa haettavat tehtävä työkonekyselyille"
  [talvi kesa yllapito]
  (let [yllapito (filter tk/yllapidon-reaaliaikaseurattava? yllapito)
        haettavat-toimenpiteet (haettavat (union talvi kesa yllapito))]
    (konv/seq->array haettavat-toimenpiteet)))

(defn- hae-tyokoneiden-selitteet
  [db user
   {:keys [alue alku loppu talvi kesa urakka-id hallintayksikko nykytilanne?
           yllapito toleranssi] :as optiot}
   urakat]
  (when nykytilanne?
    (let [rivit (q/hae-tyokoneselitteet
                 db
                 (merge
                  (laajenna-tyokone-extent alue)
                  {:nayta-kaikki (roolit/tilaajan-kayttaja? user)
                   :organisaatio (:id (:organisaatio user))
                   :urakat urakat
                   :toimenpiteet (tyokoneiden-toimenpiteet talvi kesa yllapito)
                   :alku alku
                   :loppu loppu}))
          tehtavat (into #{}
                         (map (comp :tehtavat #(konv/array->set % :tehtavat)))
                         rivit)
          viimeisin (if (empty? rivit)
                      0
                      (apply max (map (comp #(.getTime %) :viimeisin) rivit)))]
      {:tehtavat tehtavat
       :viimeisin viimeisin})))

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

(defn- hae-tarkastusten-reitit
  [db ch user {:keys [toleranssi alue alku loppu tarkastukset] :as tiedot} urakat]
  (when-not (empty? urakat)
    (q/hae-tarkastukset db ch
                        {:toleranssi toleranssi
                         :alku (konv/sql-date alku)
                         :loppu (konv/sql-date loppu)
                         :urakat urakat
                         :xmin (:xmin alue)
                         :ymin (:ymin alue)
                         :xmax (:xmax alue)
                         :ymax (:ymax alue)
                         :tyypit (map name (haettavat tarkastukset))
                         :kayttaja_on_urakoitsija (roolit/urakoitsija? user)})))

(defn- hae-tyokoneiden-reitit
  [db ch user {:keys [toleranssi alue alku loppu toimenpiteet talvi kesa yllapito] :as tiedot} urakat]
  (q/hae-tyokonereitit-kartalle
   db ch
   (merge
    (laajenna-tyokone-extent alue)
    {:urakat urakat
     :nayta-kaikki (roolit/tilaajan-kayttaja? user)
     :toimenpiteet (tyokoneiden-toimenpiteet talvi kesa yllapito)
     :alku alku
     :loppu loppu
     :organisaatio (get-in user [:organisaatio :id])})))

(defn- hae-tietyomaat
  [db user {:keys [yllapito alue nykytilanne?]} urakat]
  (when (or (not-empty urakat) (oikeudet/voi-lukea? (if nykytilanne?
                                                      oikeudet/tilannekuva-nykytilanne
                                                      oikeudet/tilannekuva-historia)
                                                    nil user))
    (when (tk/valittu? yllapito tk/tietyomaat)
      (mapv (comp #(konv/array->vec % :kaistat)
                 #(konv/array->vec % :ajoradat))
           (q/hae-tietyomaat db {:x1 (:xmin alue)
                                 :y1 (:ymin alue)
                                 :x2 (:xmax alue)
                                 :y2 (:ymax alue)})))))

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
  #{:toteumat :tyokoneet :turvallisuuspoikkeamat
    :laatupoikkeamat :paikkaus :paallystys :ilmoitukset :tietyomaat
    :tietyoilmoitukset})

(defmulti hae-osio (fn [db user tiedot urakat osio] osio))
(defmethod hae-osio :toteumat [db user tiedot urakat _]
  (tulosta-tulos! "uniikkia toteuman tehtävää"
                  (hae-toteumien-selitteet db user tiedot urakat)))

(defmethod hae-osio :tyokoneet [db user tiedot urakat _]
  (tulosta-tulos! "uniikkia työkoneen tehtävää"
                  (hae-tyokoneiden-selitteet db user tiedot urakat)
                  :tehtavat))

(defmethod hae-osio :turvallisuuspoikkeamat [db user tiedot urakat _]
  (tulosta-tulos! "turvallisuuspoikkeamaa"
                  (hae-turvallisuuspoikkeamat db user tiedot urakat)))

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

(defmethod hae-osio :tietyomaat [db user tiedot urakat _]
  (tulosta-tulos! "suljettua tieosuutta"
                  (hae-tietyomaat db user tiedot urakat)))

(defmethod hae-osio :tietyoilmoitukset [db user tiedot urakat _]
  (when
    (pko/ominaisuus-kaytossa?
     :tietyoilmoitukset)
    (tulosta-tulos! "tietyoilmoitusta"
                    (hae-tietyoilmoitukset db user tiedot urakat))))

(defn yrita-hakea-osio [db user tiedot urakat osio]
  (try
    (hae-osio db user tiedot urakat osio)
    (catch Exception e
      (tulosta-virhe! (name osio) e)
      nil)))

(defn hae-urakat [db user tiedot]
  (kayttajatiedot/kayttajan-urakat-aikavalilta-alueineen
   db user (if (:nykytilanne? tiedot)
             (fn [urakka-id kayttaja]
               (oikeudet/voi-lukea? oikeudet/tilannekuva-nykytilanne
                                    urakka-id
                                    kayttaja))
             (fn [urakka-id kayttaja]
               (oikeudet/voi-lukea? oikeudet/tilannekuva-historia
                                    urakka-id
                                    kayttaja)))
   nil (:urakoitsija tiedot) nil
   nil (:alku tiedot) (:loppu tiedot)))

(defn hae-tilannekuvaan
  ([db user tiedot]
   (hae-tilannekuvaan db user tiedot tilannekuvan-osiot))
  ([db user tiedot osiot]
   (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
   (let [urakat (filter #(oikeudet/voi-lukea? (if (:nykytilanne? tiedot)
                                                oikeudet/tilannekuva-nykytilanne
                                                oikeudet/tilannekuva-historia) % user)
                        (:urakat tiedot))]
     ;(log/debug "Haetaan tilannekuvaan asioita urakoista " (pr-str urakat))
     (let [tiedot (assoc tiedot :toleranssi (geo/karkeistustoleranssi (:alue tiedot)))]
       (into {}
             (map (juxt identity (partial yrita-hakea-osio db user tiedot urakat)))
             osiot)))))

(defn- aikavalinta
  "Jos annettu suhteellinen aikavalinta tunteina, pura se :alku ja :loppu avaimiksi."
  [{aikavalinta :aikavalinta :as hakuparametrit}]
  (if-not aikavalinta
    hakuparametrit
    (let [loppu (java.util.Date.)
          alku (java.util.Date. (- (System/currentTimeMillis)
                                   (* 1000 60 60 aikavalinta)))]
      (assoc hakuparametrit
             :alku alku
             :loppu loppu))))

(defn- suodattimet-parametreista [parametrit]
  {:pre [(map? parametrit)]}
  (aikavalinta parametrit))

(defn- karttakuvan-suodattimet
  "Tekee karttakuvan URL parametreistä suodattimet"
  [{:keys [extent parametrit] :as arg}]
  (let [[x1 y1 x2 y2] extent]
    (as-> parametrit p
          (suodattimet-parametreista p)
          (merge p
                 {:alue {:xmin x1 :ymin y1
                         :xmax x2 :ymax y2}})
          (assoc p :toleranssi (geo/karkeistustoleranssi (:alue p))))))

(defn- luettavat-urakat [user tiedot]
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (filter #(oikeudet/voi-lukea? (if (:nykytilanne? tiedot)
                                  oikeudet/tilannekuva-nykytilanne
                                  oikeudet/tilannekuva-historia) % user)
          (:urakat tiedot)))

(defn- hae-karttakuvan-tiedot [db user parametrit haku-fn xf ]
  (let [tiedot (karttakuvan-suodattimet parametrit)
        kartalle-xf (kartalla-esitettavaan-muotoon-xf)
        ch (async/chan 32
                       (comp
                         (map konv/alaviiva->rakenne)
                         xf
                         kartalle-xf))
        urakat (luettavat-urakat user tiedot)]
    (async/thread
      (jdbc/with-db-transaction [db db
                                 {:read-only? true}]
        (try
          (haku-fn db ch user tiedot urakat)
          (catch Throwable t
            (log/error t "Virhe haettaessa tilannekuvan karttatietoja")
            (async/close! ch)))))
    ch))

(defn- hae-toteumien-sijainnit-kartalle
  "Hakee toteumien reitit karttakuvaan piirrettäväksi."
  [db user parametrit]
  (hae-karttakuvan-tiedot db user parametrit hae-toteumien-reitit
                          (map #(assoc %
                                       :tyyppi :toteuma
                                       :tyyppi-kartalla :toteuma
                                       :tehtavat [(:tehtava %)]))))

(defn- hae-toteumien-tiedot-kartalle
  "Hakee toteumien tiedot pisteessä infopaneelia varten."
  [db user parametrit]
  (konv/sarakkeet-vektoriin
    (into []
          (comp
            (map konv/alaviiva->rakenne)
            (map #(assoc % :tyyppi-kartalla :toteuma))
            (map #(update % :tierekisteriosoite konv/lue-tr-osoite))
            (map #(interpolointi/interpoloi-toteuman-aika-pisteelle % parametrit db)))
          (q/hae-toteumien-asiat db
                                 (as-> parametrit p
                                       (suodattimet-parametreista p)
                                       (assoc p :urakat (luettavat-urakat user p))
                                       (assoc p :toimenpidekoodit (toteumien-toimenpidekoodit db p))
                                       (merge p (select-keys parametrit [:x :y])))))
    {:tehtava :tehtavat
     :materiaalitoteuma :materiaalit}))
(defn- hae-tarkastuksien-sijainnit-kartalle
  "Hakee tarkastuksien sijainnit karttakuvaan piirrettäväksi."
  [db user parametrit]
  (hae-karttakuvan-tiedot db user parametrit hae-tarkastusten-reitit
                          (comp (map laadunseuranta/tarkastus-tiedolla-onko-ok)
                                (map #(konv/string->keyword % :tyyppi :tekija))
                                (map #(assoc %
                                             :tyyppi-kartalla :tarkastus
                                             :sijainti (:reitti %))))))


(defn- hae-tarkastuksien-tiedot-kartalle
  "Hakee tarkastuksien tiedot pisteessä infopaneelia varten."
  [db user {x :x y :y :as parametrit}]
  (into []
        (comp (map #(assoc % :tyyppi-kartalla :tarkastus))
              (map #(konv/string->keyword % :tyyppi))
              (map #(update % :tierekisteriosoite konv/lue-tr-osoite)))
        (q/hae-tarkastusten-asiat db
                                  (as-> parametrit p
                                    (suodattimet-parametreista p)
                                    (assoc p
                                           :urakat (luettavat-urakat user p)
                                           :tyypit (map name (haettavat (:tarkastukset p)))
                                           :kayttaja_on_urakoitsija (roolit/urakoitsija? user)
                                           :x x :y y)))))

(defn- hae-tyokoneiden-sijainnit-kartalle [db user parametrit]
  (hae-karttakuvan-tiedot db user parametrit hae-tyokoneiden-reitit
                          (comp (geo/muunna-pg-tulokset :reitti)
                                (map #(konv/array->set % :tehtavat))
                                (map #(assoc %
                                             :tyyppi-kartalla :tyokone)))))



(defn- hae-tyokoneiden-tiedot-kartalle
  "Hakee työkoneiden tiedot pisteessä infopaneelia varten."
  [db user {x :x y :y :as parametrit}]
  (into []
        (comp (map #(assoc % :tyyppi-kartalla :tyokone))
              (map #(konv/array->set % :tehtavat))
              (map #(konv/string->keyword % :tyyppi)))
        (q/hae-tyokoneiden-asiat db
                                 (as-> parametrit p
                                   (suodattimet-parametreista p)
                                   (assoc p
                                          :urakat (luettavat-urakat user p)
                                          :x x
                                          :y y
                                          :nayta-kaikki (roolit/tilaajan-kayttaja? user)
                                          :organisaatio (-> user :organisaatio :id))))))

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
      karttakuvat :tilannekuva-toteumat
      (partial hae-toteumien-sijainnit-kartalle db)
      (partial #'hae-toteumien-tiedot-kartalle db)
      "tk")
    (karttakuvat/rekisteroi-karttakuvan-lahde!
     karttakuvat :tilannekuva-tarkastukset
     (partial hae-tarkastuksien-sijainnit-kartalle db)
     (partial #'hae-tarkastuksien-tiedot-kartalle db)
     "tk")
    (karttakuvat/rekisteroi-karttakuvan-lahde!
     karttakuvat :tilannekuva-tyokoneet
     (partial #'hae-tyokoneiden-sijainnit-kartalle db)
     (partial #'hae-tyokoneiden-tiedot-kartalle db)
     "tk")
    this)

  (stop [{karttakuvat :karttakuvat :as this}]
    (poista-palvelut (:http-palvelin this)
                     :hae-tilannekuvaan
                     :hae-urakat-tilannekuvaan)
    (karttakuvat/poista-karttakuvan-lahde! karttakuvat :tilannekuva-toteumat)
    (karttakuvat/poista-karttakuvan-lahde! karttakuvat :tilannekuva-tarkastukset)
    (karttakuvat/poista-karttakuvan-lahde! karttakuvat :tilannekuva-tyokoneet)
    this))
