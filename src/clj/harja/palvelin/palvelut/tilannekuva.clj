(ns harja.palvelin.palvelut.tilannekuva
  "Tilannekuva-näkymän palvelinkomponentti.

  Tilannekuva-näkymässä voidaan hakea asioita kartalle näytettäväksi.
  Osa kartalle piirrettävistä asioista piirretään frontilla, osa palvelimella.

  Frontin asioiden kartalle haku tehdään kaikkien tietojen osalta yhdellä palvelukutsulla, jolle kaikki hakuparametrit
  välitetään. Tilannekuva palauttaa mäpin, jossa avaimena on osion
  nimi keyword ja arvona hakuehdoilla löytyneet asiat.

  Palvelinpiirto puolestaan tarjoaa frontille piirrettävät asiat karttapohjina.

  PALVELUT

  :hae-tilannekuvaan hakee frontille piirrettävät asiat annettujen
  argumenttien perusteella.

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
  varten.

  Karttakuvia varten silti palautetaan normaalissa haussa osio, jossa voi
  olla indikaattoreita tai legend otsikoita. Hyvän indikaattorin palauttaminen
  normaalista osiohausta on tärkeää, koska kuvataso luodaan uudestaan, jos se
  muuttuu. Jos indikaattoriarvo muuttuu liikaa, ladataan tarpeettomasti uusia
  kuvia ja 'räpsyntää' esiintyy. Jos indikaattori ei muutu vaikka data olisi
  oikeasti muuttunut, ei kuvissa näy kaikki geometriat."
  (:require [clojure.core.async :as async]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :refer [union]]
            [com.stuartsierra.component :as component]
            [harja
             [geo :as geo]]
            [harja.domain
             [oikeudet :as oikeudet]
             [roolit :as roolit]
             [tilannekuva :as tk]]
            [harja.domain.laadunseuranta.tarkastus :as tarkastus-domain]
            [harja.kyselyt
             [konversio :as konv]
             [tilannekuva :as q]
             [turvallisuuspoikkeamat :as turvallisuuspoikkeamat-q]
             [tietyoilmoitukset :as tietyoilmoitukset-q]]
            [harja.palvelin.komponentit.http-palvelin
             :refer
             [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.palvelut
             [karttakuvat :as karttakuvat]
             [interpolointi :as interpolointi]]
            [harja.ui.kartta.esitettavat-asiat
             :as esitettavat-asiat
             :refer [kartalla-esitettavaan-muotoon-xf]]
            [harja.palvelin.palvelut.karttakuvat :as karttakuvat]
            [clojure.set :refer [union]]
            [harja.kyselyt.turvallisuuspoikkeamat :as turvallisuuspoikkeamat-q]
            [harja.domain.oikeudet :as oikeudet]
            [clojure.core.async :as async]
            [clojure.java.jdbc :as jdbc]
            [harja.domain.roolit :as roolit]
            [slingshot.slingshot :refer [throw+]]
            [harja.palvelin.palvelut.kayttajatiedot :as kayttajatiedot]
            [taoensso.timbre :as log]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [harja.kyselyt.yllapitokohteet :as yllapitokohteet-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.domain.tierekisteri :as tr]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yllapitokohteet-yleiset]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :as pko]
            [harja.palvelin.palvelut.yhteyshenkilot :as yhteyshenkilot]
            [harja.palvelin.palvelut.toteumat :as toteumat]))

(defn- tulosta-virhe! [asiat e]
  (log/error (str "*** ERROR *** Yritettiin hakea tilannekuvaan " asiat
                  ", mutta virhe tapahtui: " (.getMessage e))))

(defn- tulosta-tulos!
  ([asiaa tulos] (tulosta-tulos! asiaa tulos identity))
  ([asiaa tulos fn]
   (if (vector? (fn tulos))
     (log/debug (str "  - " (count (fn tulos)) " " asiaa))
     (log/debug (str "  - " (count (keys (fn tulos))) " " asiaa)))
   tulos))

(defn- haettavat [s]
  (into #{}
        (map (comp :nimi tk/suodattimet-idlla))
        s))

(declare hae-kayttajan-urakat-alueittain)

(defn- rajaa-urakat-hakuoikeudella [db user hakuargumentit]
  (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
  (let [kayttajan-urakka-idt (if (roolit/tilaajan-kayttaja? user)
                               (constantly true)
                               ; fixme. hanskaa keissi jossa urakoitsija saa nähdä kaikkien urakkatyyppien tiedot omien urakoiden elyissä
                               (set (map :id (q/urakat-joihin-oikeus
                                               db {:organisaatio (get-in user [:organisaatio :id])}))))
        ;; Rajataan haettavat urakat niihin, joihin käyttäjällä on hakuoikeus
        oikeudelliset-urakat (set (filter kayttajan-urakka-idt (:urakat hakuargumentit)))]
    oikeudelliset-urakat))

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
    (tietyoilmoitukset-q/hae-ilmoitukset-tilannekuvaan db (assoc tiedot :urakat urakat
                                                                        :tilaaja? (roolit/tilaajan-kayttaja? user)))))

(defn- hae-yllapitokohteet
  [db user {:keys [toleranssi alku loppu yllapito nykytilanne? tyyppi alue]} urakat]
  ;; Huomaa: hakee vain sellaiset ylläpitokohteet, jotka piirretään frontilla (paikkauskohteet).
  ;; Yhtenäisyyden ja suorituskyvyn vuoksi olisi mahdollisesti syytä piirtää kaikki ylläpitokohteet palvelimella
  ;; ja palauttaa karttakuvina, kuten nyt tehdään päällystyksen kanssa.
  ;; Paikkauksia on kuitenkin verrattain vähän, joten toistaiseksi tämä toimii.
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
                          (map #(konv/string-polusta->keyword % [:yllapitokohdetyyppi])))
                        (if nykytilanne?
                          (case tyyppi
                            "paikkaus" (q/hae-paikkaukset-nykytilanteeseen db {:urakat urakat}))
                          (case tyyppi
                            "paikkaus" (q/hae-paikkaukset-historiakuvaan db
                                                                         {:urakat urakat
                                                                          :loppu (konv/sql-date loppu)
                                                                          :alku (konv/sql-date alku)}))))
          vastaus (yllapitokohteet-q/liita-kohdeosat-kohteisiin db vastaus :id
                                                                {:alue alue
                                                                 :toleranssi toleranssi})
          vastaus (remove #(empty? (:kohdeosat %)) vastaus)

          osien-pituudet-tielle (yllapitokohteet-yleiset/laske-osien-pituudet db vastaus)
          vastaus (mapv #(assoc %
                           :pituus
                           (tr/laske-tien-pituus (osien-pituudet-tielle (:tr-numero %)) %))
                        vastaus)]
      vastaus)))

(defn- hae-paikkaustyot
  [db user suodattimet urakat]
  (hae-yllapitokohteet db user (assoc suodattimet :tyyppi "paikkaus") urakat))

(defn- hae-paallystystyot
  "Hakee indikaattoritiedon siitä milloin päällystystyöt ovat muuttuneet viimeksi.
  Frontti käyttää tätä tietoa uuden karttatason luomiseen."
  [db user {nykytilanne? :nykytilanne? yllapito :yllapito} urakat]
  (when (tk/valittu? yllapito tk/paallystys)
    ;; Indikaattori on merkkijono, joka sisältää tiedon siitä onko nykytilanne (n)
    ;; vai historiakuva (h) sekä viimeisimmän kohteen muutosaikaleiman.
    [(str (if nykytilanne?
            "n" "h")
          (or (some-> db
                      q/hae-paallystysten-viimeisin-muokkaus
                      .getTime)
              0))]))

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
        sade (max (* 2 (geo/alueen-hypotenuusa alue)) 10000)
        [keskipiste-x keskipiste-y] (geo/extent-keskipiste [xmin ymin xmax ymax])
        params {:keskipiste_x keskipiste-x
                :keskipiste_y keskipiste-y
                :sade sade}]
    params))

(defn- tyokoneiden-toimenpiteet
  "Palauttaa haettavat tehtävä työkonekyselyille"
  [talvi kesa yllapito tarkastukset user]
  (let [yllapito (filter tk/yllapidon-reaaliaikaseurattava? yllapito)
        tarkastukset (when (roolit/tilaajan-kayttaja? user)
                       (filter tk/tarkastuksen-reaaliaikaseurattava? tarkastukset))
        haettavat-toimenpiteet (haettavat (union talvi kesa yllapito tarkastukset))]
    (konv/seq->array haettavat-toimenpiteet)))

(defn- hae-tyokoneiden-selitteet
  [db user
   {:keys [alue alku loppu talvi kesa urakka-id hallintayksikko nykytilanne?
           yllapito tilaajan-laadunvalvonta toleranssi tarkastukset] :as optiot}
   urakat]
  (when nykytilanne?
    (let [rivit (q/hae-tyokoneselitteet
                  db
                  (merge
                    (laajenna-tyokone-extent alue)
                    {:nayta-kaikki (roolit/tilaajan-kayttaja? user)
                     :organisaatio (:id (:organisaatio user))
                     :urakat urakat
                     :toimenpiteet (tyokoneiden-toimenpiteet talvi kesa yllapito tarkastukset user)
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
  [db ch user {:keys [toleranssi alue alku loppu toimenpiteet talvi kesa yllapito tarkastukset] :as tiedot} urakat]
  (q/hae-tyokonereitit-kartalle
    db ch
    (merge
      (laajenna-tyokone-extent alue)
      {:urakat urakat
       :nayta-kaikki (roolit/tilaajan-kayttaja? user)
       :toimenpiteet (tyokoneiden-toimenpiteet talvi kesa yllapito tarkastukset user)
       :alku alku
       :loppu loppu
       :organisaatio (get-in user [:organisaatio :id])})))

(defn- hae-paallystysten-reitit
  [db ch user {:keys [toleranssi alue alku loppu nykytilanne?] :as tiedot} urakat]
  (q/hae-paallystysten-reitit db ch
                              (merge alue
                                     {:toleranssi toleranssi
                                      :urakat urakat
                                      :nykytilanne nykytilanne?
                                      :historiakuva (not nykytilanne?)
                                      :alku alku
                                      :loppu loppu})))
(defn- hae-tietyomaat
  [db user {:keys [yllapito alue nykytilanne?]} urakat]
  (when (tk/valittu? yllapito tk/tietyomaat)
    (mapv (comp #(konv/array->vec % :kaistat)
                #(konv/array->vec % :ajoradat))
          (q/hae-tietyomaat db {:x1 (:xmin alue)
                                :y1 (:ymin alue)
                                :x2 (:xmax alue)
                                :y2 (:ymax alue)
                                :urakat urakat}))))

(defn- hae-toteumien-selitteet
  [db user {:keys [alue alku loppu] :as tiedot} urakat]
  (when-not (empty? urakat)
    (when-let [toimenpidekoodit (toteumien-toimenpidekoodit db tiedot)]
      {:viimeisin-toteuma (q/hae-valittujen-urakoiden-viimeisin-toteuma db {:urakat urakat})
       :selitteet (q/hae-toteumien-selitteet db
                                             {:alku (konv/sql-date alku)
                                              :loppu (konv/sql-date loppu)
                                              :toimenpidekoodit toimenpidekoodit
                                              :urakat urakat
                                              :xmin (:xmin alue) :ymin (:ymin alue)
                                              :xmax (:xmax alue) :ymax (:ymax alue)})})))

(defn- hae-varustetoteumat
  [db user {:keys [alue alku loppu varustetoteumat] :as tiedot} urakat]
  (when (and (tk/valittu? varustetoteumat tk/varustetoteumat)
             (not (empty? urakat)))
    (into []
          (toteumat/varustetoteuma-xf nil)
          (q/hae-varustetoteumat db {:urakat urakat
                                     :alku alku
                                     :loppu loppu}))))

(def tilannekuvan-osiot
  #{:toteumat :tyokoneet :turvallisuuspoikkeamat
    :laatupoikkeamat :paikkaus :paallystys :ilmoitukset :tietyomaat
    :tietyoilmoitukset :varustetoteumat})

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

(defmethod hae-osio :varustetoteumat [db user tiedot urakat _]
  (tulosta-tulos!
    "varustetoteumaa"
    (hae-varustetoteumat db user tiedot urakat)))

(defn yrita-hakea-osio [db user tiedot urakat osio]
  (try
    (hae-osio db user tiedot urakat osio)
    (catch Exception e
      (tulosta-virhe! (name osio) e)
      nil)))

(defn- maarita-oikeudet-omien-urakoiden-muihin-ely-urakoihin
  "Palauttaa mapin, joka kertoo, mihin käyttäjän omiin urakoihin käyttäjällä on erikoisoikeus
   oman-urakan-ely. Erikoisoikeus antaa näkyvyyden urakan ELY-alueen muihin urakoihin.
   Mapissa avaimet: :urakka-id, :hallintayksikko-id sekä tiedot oikeuksista"
  [user oikeus-nakyma kayttajan-urakat-alueittain]
  (vec (mapcat (fn [alue-ja-urakat]
                 (let [hallintayksikko-id (get-in alue-ja-urakat [:hallintayksikko :id])]
                   (map (fn [urakka]
                          (-> {:urakka-id (:id urakka)
                               :hallintayksikko-id hallintayksikko-id
                               :oikeus-urakan-muihin-ely-urakoihin?
                               (boolean (oikeudet/on-muu-oikeus? "oman-urakan-ely"
                                                                 oikeus-nakyma
                                                                 (:id urakka)
                                                                 user))}))
                        (:urakat alue-ja-urakat))))
               kayttajan-urakat-alueittain)))

(defn- lukuoikeus-urakkaan-lisaoikeudella?
  "Tarkistaa, muodostuuko annettuun urakkaan oikeus jonkin
  samalla ELY-alueella sijaitsevan urakan lisäoikeudella."
  [db urakka-id lisaoikeudet]
  (let [hallintayksikko-id (:hallintayksikko-id (first (urakat-q/urakan-hallintayksikko
                                                         db
                                                         {:id urakka-id})))
        lisaoikeus-urakkaan? (boolean (some
                                        (fn [lisaoikeus]
                                          (and (:oikeus-urakan-muihin-ely-urakoihin? lisaoikeus)
                                               (= (:hallintayksikko-id lisaoikeus) hallintayksikko-id)))
                                        lisaoikeudet))]
    lisaoikeus-urakkaan?))

(defn hae-kayttajan-urakat-alueittain [db user tiedot]
  (let [oikeus-nakyma (if (:nykytilanne? tiedot)
                        oikeudet/tilannekuva-nykytilanne
                        oikeudet/tilannekuva-historia)
        kayttajan-urakat-alueittain (->>
                                      (kayttajatiedot/kayttajan-urakat-aikavalilta-alueineen
                                       db user
                                       (if (roolit/tilaajan-kayttaja? user)
                                         (constantly true)
                                         (fn [urakka-id kayttaja]
                                           (oikeudet/voi-lukea? oikeus-nakyma
                                                                urakka-id
                                                                kayttaja)))
                                       nil (:urakoitsija tiedot) nil
                                       nil (:alku tiedot) (:loppu tiedot))
                                      (map
                                        (fn [alue]
                                          (update alue :urakat
                                                  (fn [urakat]
                                                    (filter :urakkanro urakat))))))
        ;; Käyttäjällä voi olla omaan urakkaan erikoisoikeus oman-urakan-ely, mikä tarkoittaa,
        ;; että käyttäjä saa nähdä oman urakan ELY-alueen kaikkien urakoiden asiat.
        ;; Jos tällaisia erikoisoikeuksia omiin urakoihin löytyy, niin haetaan ko. urakoiden
        ;; muut ELY-urakat ja liitetään mukaan käyttäjän näkemiin urakoihin.

        ;; HUOM! Jotta ELY:n muista urakoista voisi hakea asioita, täytyy käyttäjän valita sellainen
        ;; aikaväli, jolle oma urakka osuu, koska ennen ELY:n muiden urakoiden etsintää täytyy olla
        ;; löydettynä käyttäjän oma urakka. Tämä ajateltiin aluksi bugina, mutta sittemmin sitä pidettiinkin
        ;; ihan hyvänä rajoitteena. Käytännössä tämä rajaa haettavan aikavälin vain sellaiselle välille, jolla
        ;; käyttäjän oma urakka on voimassa.

        ;; Tilaajan käyttäjillä on oikeus kaikki urakoihin. Ei tarkastella silloin
        ;; lisäoikeuksia ollenkaan jotta ollaan nopeampia
        lisaoikeudet (if (roolit/tilaajan-kayttaja? user)
                       []
                       (maarita-oikeudet-omien-urakoiden-muihin-ely-urakoihin
                        user oikeus-nakyma
                        kayttajan-urakat-alueittain))
        lisaoikeuksien-urakat-alueittain (when-not (empty? lisaoikeudet)
                                           (->>
                                             (kayttajatiedot/kayttajan-urakat-aikavalilta-alueineen
                                               db user (fn [urakka-id kayttaja]
                                                         (lukuoikeus-urakkaan-lisaoikeudella? db urakka-id lisaoikeudet))
                                               nil (:urakoitsija tiedot) nil
                                               nil (:alku tiedot) (:loppu tiedot))
                                             (map
                                               (fn [alue]
                                                 (update alue :urakat
                                                         (fn [urakat]
                                                           (filter :urakkanro urakat)))))))
        lopulliset-kayttajan-urakat-alueittain (kayttajatiedot/yhdista-kayttajan-urakat-alueittain
                                                 kayttajan-urakat-alueittain
                                                 lisaoikeuksien-urakat-alueittain)]
    lopulliset-kayttajan-urakat-alueittain))

(defn hae-tilannekuvaan
  ([db user tiedot]
   (hae-tilannekuvaan db user tiedot tilannekuvan-osiot))
  ([db user tiedot osiot]
   (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
   (let [haettavat-urakat (rajaa-urakat-hakuoikeudella db user tiedot)]
     (let [tiedot (assoc tiedot :toleranssi (geo/karkeistustoleranssi (:alue tiedot)))]
       (into {}
             (map (juxt identity (partial yrita-hakea-osio db user tiedot haettavat-urakat)))
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

(defn- hae-karttakuvan-tiedot [db user parametrit haku-fn xf]
  (let [tiedot (karttakuvan-suodattimet parametrit)
        kartalle-xf (kartalla-esitettavaan-muotoon-xf)
        ch (async/chan 32
                       (comp
                         (map konv/alaviiva->rakenne)
                         xf
                         kartalle-xf))
        urakat (rajaa-urakat-hakuoikeudella db user tiedot)]
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
                                       (assoc p :urakat (rajaa-urakat-hakuoikeudella db user p))
                                       (assoc p :toimenpidekoodit (toteumien-toimenpidekoodit db p))
                                       (merge p (select-keys parametrit [:x :y])))))
    {:tehtava :tehtavat
     :materiaalitoteuma :materiaalit}))

(defn- hae-tarkastuksien-sijainnit-kartalle
  "Hakee tarkastuksien sijainnit karttakuvaan piirrettäväksi."
  [db user parametrit]
  (hae-karttakuvan-tiedot db user parametrit hae-tarkastusten-reitit
                          (comp (map tarkastus-domain/tarkastus-tiedolla-onko-ok)
                                (map #(konv/string->keyword % :tyyppi :tekija))
                                (map #(assoc %
                                        :tyyppi-kartalla :tarkastus
                                        :sijainti (:reitti %))))))


(defn- hae-tarkastuksien-tiedot-kartalle
  "Hakee tarkastuksien tiedot pisteessä infopaneelia varten."
  [db user {x :x y :y :as parametrit}]
  (into []
        (comp
          (map #(konv/array->set % :vakiohavainnot))
          (map #(assoc % :tyyppi-kartalla :tarkastus))
          (map #(konv/string->keyword % :tyyppi))
          (map #(update % :tierekisteriosoite konv/lue-tr-osoite))
          (map tarkastus-domain/tarkastus-tiedolla-onko-ok)
          (map konv/alaviiva->rakenne))
        (q/hae-tarkastusten-asiat db
                                  (as-> parametrit p
                                        (suodattimet-parametreista p)
                                        (assoc p
                                          :urakat (rajaa-urakat-hakuoikeudella db user p)
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
                                         :urakat (rajaa-urakat-hakuoikeudella db user p)
                                         :x x
                                         :y y
                                         :nayta-kaikki (roolit/tilaajan-kayttaja? user)
                                         :organisaatio (-> user :organisaatio :id))))))

(defn hae-paallystysten-sijainnit-kartalle
  "Hakee ylläpidon päällystystöiden geometriatiedot karttakuvan piirtoa varten"
  [db user parametrit]
  (hae-karttakuvan-tiedot db user parametrit
                          hae-paallystysten-reitit
                          (comp
                            (map #(assoc % :tyyppi-kartalla :paallystys))
                            (map #(assoc % :tila (yllapitokohteet-domain/yllapitokohteen-tarkka-tila %)))
                            (map #(update % :sijainti geo/pg->clj)))))

(def ^{:private true
       :doc "Päällystyskohdeosan tietojen muunto infopaneelia varten"}
paallystyskohdeosan-tiedot-xf
  (comp (map #(assoc % :tyyppi-kartalla :paallystys))
        (map #(konv/string->keyword % :yllapitokohde_yllapitokohdetyotyyppi))
        (map konv/alaviiva->rakenne)
        (map #(assoc-in % [:yllapitokohde :tila]
                        (yllapitokohteet-domain/yllapitokohteen-tarkka-tila (:yllapitokohde %))))))

(defn hae-paallystysten-tiedot-kartalle
  "Hakee klikkauspisteen perusteella kohteessa olevan päällystystyön tiedot"
  [db user {:keys [x y toleranssi nykytilanne? alku loppu] :as parametrit}]
  (let [urakat (rajaa-urakat-hakuoikeudella db user parametrit)]
    (when-not (empty? urakat)
      (let [vastaus (into []
                          paallystyskohdeosan-tiedot-xf
                          (q/hae-paallystysten-tiedot db {:x x :y y
                                                          :toleranssi toleranssi
                                                          :nykytilanne nykytilanne?
                                                          :urakat urakat
                                                          :historiakuva (not nykytilanne?)
                                                          :alku alku
                                                          :loppu loppu}))
            yllapitokohteet (map :yllapitokohde vastaus)
            osien-pituudet-tielle (yllapitokohteet-yleiset/laske-osien-pituudet db yllapitokohteet)

            vastaus (mapv (fn [kohdeosa]
                            (update kohdeosa :yllapitokohde
                                    #(assoc %
                                       :pituus
                                       (tr/laske-tien-pituus (osien-pituudet-tielle (:tr-numero %)) %))))
                          vastaus)]
        vastaus))))

(defrecord Tilannekuva []
  component/Lifecycle
  (start [{karttakuvat :karttakuvat
           db :db
           fim :fim
           http :http-palvelin
           :as this}]
    ;; Tämä palvelu palauttaa tilannekuvaan asiat, jotka piirretään frontilla
    (julkaise-palvelu http :hae-tilannekuvaan
                      (fn [user tiedot]
                        (hae-tilannekuvaan db user tiedot)))
    (julkaise-palvelu http :hae-urakat-tilannekuvaan
                      (fn [user tiedot]
                        (hae-kayttajan-urakat-alueittain db user tiedot)))

    ;; Karttakuvat palauttaa tilannekuvaan asiat, jotka piirretään palvelimella valmiiksi
    ;; ja palautetaan frontille karttakuvina.
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
    (karttakuvat/rekisteroi-karttakuvan-lahde!
      karttakuvat :tilannekuva-paallystys
      (partial #'hae-paallystysten-sijainnit-kartalle db)
      (partial #'hae-paallystysten-tiedot-kartalle db)
      "tk")
    this)

  (stop [{karttakuvat :karttakuvat :as this}]
    (poista-palvelut (:http-palvelin this)
                     :hae-tilannekuvaan
                     :hae-urakat-tilannekuvaan)
    (karttakuvat/poista-karttakuvan-lahde! karttakuvat :tilannekuva-toteumat)
    (karttakuvat/poista-karttakuvan-lahde! karttakuvat :tilannekuva-tarkastukset)
    (karttakuvat/poista-karttakuvan-lahde! karttakuvat :tilannekuva-tyokoneet)
    (karttakuvat/poista-karttakuvan-lahde! karttakuvat :tilannekuva-paallystys)
    this))
