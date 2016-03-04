(ns harja.ui.kartta.esitettavat-asiat
  (:require [clojure.string :as str]
            [harja.loki :refer [log warn] :refer-macros [mittaa-aika]]
            [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat :as laatupoikkeamat]
            [harja.tiedot.urakka.laadunseuranta.tarkastukset :as tarkastukset]
            [harja.domain.ilmoitukset :as ilmoitukset]
            [harja.geo :as geo]

            [harja.ui.kartta.asioiden-ulkoasu :as ulkoasu]))

(defn- laske-skaala [valittu?]
  (if valittu? ulkoasu/+valitun-skaala+ ulkoasu/+normaali-skaala+))

(defn viivan-vari
  ([valittu?] (viivan-vari valittu? ulkoasu/+valitun-vari+ ulkoasu/+normaali-vari+))
  ([valittu? valittu-vari] (viivan-vari valittu? valittu-vari ulkoasu/+normaali-vari+))
  ([valittu? valittu-vari ei-valittu-vari]
   (if valittu? valittu-vari ei-valittu-vari)))

(defn viivan-leveys
  ([valittu?] (viivan-leveys valittu? ulkoasu/+valitun-leveys+ ulkoasu/+normaali-leveys+))
  ([valittu? valittu-leveys] (viivan-leveys valittu? valittu-leveys ulkoasu/+normaali-leveys+))
  ([valittu? valittu-leveys ei-valittu-leveys]
   (if valittu? valittu-leveys ei-valittu-leveys)))

(defn reitillinen-asia? [asia]
  (case (:type (or (:sijainti asia) asia))
    :point false
    :line true
    :multiline true))

(defn asia-on-piste? [asia]
  (not (reitillinen-asia? asia)))

;; Varmistaa, että merkkiasetukset ovat vähintään [{}].
;; Jos annettu asetus on merkkijono, palautetaan [{:img merkkijono}]
(defn- validoi-merkkiasetukset [merkit]
  (cond
    (empty? merkit) [{}]
    (string? merkit) [{:img merkit}]
    (map? merkit) [merkit]
    :else merkit))

(defn- validoi-viiva-asetukset [viivat]
  (cond
    (empty? viivat) [{}]
    (map? viivat) [viivat]
    (string? viivat) [{:color viivat}]
    :else viivat))

(defn- maarittele-piste
  [valittu? merkki]
  (let [merkki (first (validoi-merkkiasetukset merkki))]
    (merge
      {:scale (laske-skaala valittu?)}
      merkki)))

(defn- maarittele-viiva
  [valittu? merkit viivat]
  (let [merkit (validoi-merkkiasetukset merkit)
        viivat (validoi-viiva-asetukset viivat)]
    {:viivat (mapv (fn [v] (merge
                             ;; Ylikirjoitettavat oletusasetukset
                             {:color (viivan-vari valittu?)
                              :width (viivan-leveys valittu?)}
                             v)) viivat)
     :ikonit (mapv (fn [i] (merge
                             ;; Oletusasetukset
                             {:tyyppi :merkki
                              :paikka :loppu
                              :scale  (laske-skaala valittu?)}
                             i)) merkit)}))

(defn maarittele-feature
  "Funktio palauttaa mäpin, joka määrittelee featuren openlayersin
  haluamassa muodossa.
  Pakolliset parametrit:
  * Asia: kannasta haettu, piirrettävä asia. Tai pelkästään juttu,
   joka sisältää geometriatiedot.
  * Valittu?: Onko asia valittu? true/false

  Valinnaiset (mutta tärkeät!) parametrit:
  * Merkit: Vektori mäppejä, mäppi, tai string, joka määrittelee featureen
    piirrettävät ikonit
    - Jos parametri on string, muutetaan se muotoon {:img string}.
      Jos siis piirrettävä asia on pistemäinen, riittää parametriksi pelkkä
      string, muuten voidaan mennä oletusasetuksilla
    - Reittimäisille asioille tällä parametrilla on enemmän merkitystä.
      Mäpille voi antaa seuraavia arvoja:
      -- paikka: :alku, :loppu, :taitokset, tai vektori näitä. Mihin paikkoihin
         ikoni piirretään?
      -- tyyppi: :nuoli tai :merkki. Merkit kääntyvät viivan suunnan mukaan,
         merkit aina pystyssä.
      -- img: käytettävä ikoni
      -- Lisäksi openlayersin asetukset scale, zindex, anchor.
         Jätä mieluummin antamatta
  * Viivat: Vektori mäppejä tai mäppi, joka määrittelee viivan piirtotyylit
    - Käytä vektoria mäppejä, jos haluat tehdä kaksivärisiä viivoja.
      Voit esim. piirtää paksumman mustan viivan, ja sitten ohuemman sinisen
      viivan sen päälle.
    - Mäpillä on seuraavat arvot:
    -- Openlayersin color, width, zindex, dash, cap, join, miter
  * Pisteen merkki: Valinnainen parametri, johon pätee samat säännöt kuin
    em. merkkeihin.
    Jos tätä ei ole annettu, käytetään merkin piirtämiseen merkit vektorissa
    ensimmäisenä määriteltyä merkkiä (tai jos taas merkit on string tai
    pelkkä mäp, niin käytetään vaan sitä..).
    Käytännöllinen jos haluaa esim piirtää reitit nuoliviivoilla, ja pisteen
    ikonilla.

  Esimerkkejä:

  (maarittele-feature juttu val? (pinni-ikoni 'vihrea'))
    Juttu on todennäköisesti pistemäinen asia. Käytetään vihrää pinniä.
    Jos juttu onkin reitillinen, käytetään reitin piirtämiseen puhtaasti
    oletusasetuksia.

  (maarittele-feature homma val? (sijainti-ikoni 'magenta')
                      {:color (if (val? homma) varit/vihrea varit/keltainen)})
    Samanlainen kuin edellinen, mutta on määritelty millä värillä halutaan
    piirtää reitti

  (maarittele-feature foo val?
                      [{:paikka :loppu :img (pinni-ikoni 'sininen)}
                       {:paikka :taitokset :img (nuoli-ikoni 'sininen')}]
                      [{:width 12 :color varit/musta}
                       {:width 6 :color varit/sininen}])
    Jos foo on pistemäinen, käytetään sinistä pinniä. Reitilliselle foolle
    piirretään kaksivärinen viiva, jonka taitoksissa on nuoli, ja loppupäässä
    sininen pinni.

  (maarittele-feature bar val? {:paikka [:alku :loppu :taitokset]
                                :img (nuoli-ikoni 'lime')}
                      nil (pinn-ikoni 'lime')
    Reitillinen bar piirretään käyttäen viivojen oletusasetuksia. Alku- ja
    loppupäähän sekä jokaiseen taitokseen piirretään nuoli. Jos bar onkin
    piste, käytetään vaan limeä pinniä."
  ([asia valittu?] (maarittele-feature asia valittu? [{}] [{}]))
  ([asia valittu? merkit] (maarittele-feature asia valittu? merkit [{}]))
  ([asia valittu? merkit viivat]
   (maarittele-feature asia valittu? merkit viivat nil))
  ([asia valittu? merkit viivat pisteen-ikoni]
   (let [geo (or (:sijainti asia) asia)
         tyyppi (:type geo)
         koordinaatit (or (:coordinates geo) (:points geo) (mapcat :points (:lines geo)))]
     (when (not (empty? koordinaatit))
       (cond
         ;; Näyttää siltä että joskus saattaa löytyä LINESTRINGejä, joilla on vain yksi piste
         ;; Ei tietoa onko tämä virheellistä testidataa vai real world case, mutta varaudutaan siihen joka tapauksessa
         (or (= :point tyyppi) (= 1 (count koordinaatit)))
         (when merkit
           (merge
            (maarittele-piste valittu? (or pisteen-ikoni merkit))
            {:type        :merkki
             :coordinates (flatten koordinaatit)})) ;; [x y] -> [x y] && [[x y]] -> [x y]

         (= :line tyyppi)
         (merge
          (maarittele-viiva valittu? merkit viivat)
          {:type   :viiva
           :points koordinaatit})

         (= :multiline tyyppi)
         (merge
          (maarittele-viiva valittu? merkit viivat)
          {:type   :viiva
           :points koordinaatit}))))))

;;;;;;

(defn- viivojen-varit-leveimmasta-kapeimpaan [viivat]
  ;; Täydennä väliaikaisesti tänne oletusarvot,
  ;; muuten leveysvertailu failaa, ja halutaanhan toki palauttaa
  ;; jokin väri myös jutuille, joille sellaista ei ole (vielä!) määritelty.
  (->> viivat
       (mapv #(assoc % :width (or (:width %) ulkoasu/+normaali-leveys+)
                     :color (or (:color %) ulkoasu/+normaali-vari+)))
       (sort-by :width >)
       (mapv :color)))
       
(defmulti
  ^{:private true}
  asia-kartalle :tyyppi-kartalla)

(defn ilmoituksen-tooltip [ilmoitus]
  (str (ilmoitukset/ilmoitustyypin-nimi (:ilmoitustyyppi ilmoitus))
       " ("
       (str/lower-case (ilmoitukset/kuittaustyypin-selite (:tila ilmoitus)))
       ")"))


(defn ilmoitus-kartalle [{:keys [tila ilmoitustyyppi] :as ilmoitus} valittu-fn?]
  (let [ikoni (ulkoasu/ilmoituksen-ikoni (:tila ilmoitus))]
    (assoc ilmoitus
      :type :ilmoitus
      :nimi (ilmoituksen-tooltip ilmoitus)
      :selite {:teksti (str (ilmoitukset/ilmoitustyypin-nimi ilmoitustyyppi)
                            "("
                            (ilmoitukset/kuittaustyypin-selite tila)
                            ")")
               :img    ikoni}
      :alue (maarittele-feature ilmoitus (valittu-fn? ilmoitus) ikoni))))

(defmethod asia-kartalle :tiedoitus [ilmoitus valittu-fn?]
  (ilmoitus-kartalle ilmoitus valittu-fn?))

(defmethod asia-kartalle :kysely [ilmoitus valittu-fn?]
  (ilmoitus-kartalle ilmoitus valittu-fn?))

(defmethod asia-kartalle :toimenpidepyynto [ilmoitus valittu-fn?]
  (ilmoitus-kartalle ilmoitus valittu-fn?))

(defn otsikko-tekijalla [etuliite laatupoikkeama]
  (str etuliite
       " (" (laatupoikkeamat/kuvaile-tekija (:tekija laatupoikkeama)) ")"))

(defmethod asia-kartalle :laatupoikkeama [laatupoikkeama valittu-fn?]
  (let [ikoni (ulkoasu/laatupoikkeaman-ikoni (:tekija laatupoikkeama))
        otsikko (otsikko-tekijalla "Laatupoikkeama" laatupoikkeama)]
    (assoc laatupoikkeama
      :type :laatupoikkeama
      :nimi (or (:nimi laatupoikkeama) otsikko)
      :selite {:teksti otsikko
               :img    ikoni}
      :alue (maarittele-feature laatupoikkeama (valittu-fn? laatupoikkeama)
                                ikoni))))

(defmethod asia-kartalle :tarkastus [tarkastus valittu-fn?]
  (let [ikoni (ulkoasu/tarkastuksen-ikoni
                (valittu-fn? tarkastus) (:ok? tarkastus) (reitillinen-asia? tarkastus))
        viiva (ulkoasu/tarkastuksen-reitti (:ok? tarkastus))]
    (assoc tarkastus
      :type :tarkastus
      :nimi (or (:nimi tarkastus)
                (otsikko-tekijalla
                 (tarkastukset/+tarkastustyyppi->nimi+ (:tyyppi tarkastus))
                 tarkastus))
      :selite {:teksti (otsikko-tekijalla "Tarkastus" tarkastus)
               :img    ikoni}
      :alue (maarittele-feature tarkastus (valittu-fn? tarkastus) ikoni viiva))))

(defmethod asia-kartalle :varustetoteuma [varustetoteuma valittu-fn?]
  (let [ikoni (ulkoasu/varustetoteuman-ikoni)]
    (assoc varustetoteuma
      :type :varustetoteuma
      :nimi (or (:selitys-kartalla varustetoteuma) "Varustetoteuma")
      :selite {:teksti "Varustetoteuma"
               :img    ikoni}
      :alue (maarittele-feature varustetoteuma (valittu-fn? varustetoteuma)
                                (ulkoasu/varustetoteuman-ikoni)))))


(defn paattele-turpon-ikoni [turpo]
  (let [kt (:korjaavattoimenpiteet turpo)]
    (if (empty? kt)
      [:tyhja "Turvallisuuspoikkeama, avoin"]

      (if (some (comp nil? :suoritettu) kt)
        [:avoimia "Turvallisuuspoikkeama, ei korjauksia"]

        [:valmis "Turvallisuuspoikkeama, kaikki korjattu"]))))


(defmethod asia-kartalle :turvallisuuspoikkeama [tp valittu-fn?]
  (let [[kt-tila selite] (paattele-turpon-ikoni tp)
        ikoni (ulkoasu/turvallisuuspoikkeaman-ikoni kt-tila)]
    (when (:sijainti tp)
      (assoc tp
        :type :turvallisuuspoikkeama
        :nimi (or (:nimi tp) "Turvallisuuspoikkeama")
        :selite {:teksti selite
                 :img    ikoni}
        :alue (maarittele-feature tp (valittu-fn? tp) ikoni)))))

(defn- paikkaus-paallystys [tyyppi pt valittu-fn? teksti]
  (let [tila (:tila pt)
        tila-teksti (str ", " ((fnil name "suunniteltu") tila))
        ikoni (ulkoasu/yllapidon-ikoni)
        viiva (ulkoasu/yllapidon-viiva (valittu-fn? pt) (:avoin? pt) tila tyyppi)]
    (assoc pt
      :nimi (or (:nimi pt) teksti)
      :selite {:teksti (str teksti tila-teksti)
               :vari   (viivojen-varit-leveimmasta-kapeimpaan viiva)}
      :alue (maarittele-feature pt (valittu-fn? pt)
                                ikoni
                                viiva))))

(defmethod asia-kartalle :paallystys [pt valittu-fn?]
  (assoc (paikkaus-paallystys :paallystys pt valittu-fn? "Päällystys")
    :type :paallystys))

(defmethod asia-kartalle :paikkaus [pt valittu-fn?]
  (assoc (paikkaus-paallystys :paikkaus pt valittu-fn? "Paikkaus")
    :type :paikkaus))

(let [varien-lkm (count ulkoasu/toteuma-varit-ja-nuolet)]
  (defn generoitu-tyyli [tehtavan-nimi]
    (log "WARN: "tehtavan-nimi" määritys puuttuu esitettävistä asioista, generoidaan tyyli koneellisesti!")
    (nth ulkoasu/toteuma-varit-ja-nuolet (Math/abs (rem (hash tehtavan-nimi) varien-lkm)))))

(def tehtavien-nimet
  {"AURAUS JA SOHJONPOISTO"          "Auraus tai sohjonpoisto"
   "SUOLAUS"                         "Suolaus"
   "LIUOSSUOLAUS"                    "Liuossuolaus"
   "PISTEHIEKOITUS"                  "Pistehiekoitus"
   "LINJAHIEKOITUS"                  "Linjahiekoitus"
   "PINNAN TASAUS"                   "Pinnan tasaus"
   "LUMIVALLIEN MADALTAMINEN"        "Lumivallien madaltaminen"
   "SULAMISVEDEN HAITTOJEN TORJUNTA" "Sulamisveden haittojen torjunta"
   "AURAUSVIITOITUS JA KINOSTIMET"   "Aurausviitoitus ja kinostimet"
   "LUMENSIIRTO"                     "Lumensiirto"
   "PAANNEJAAN POISTO"               "Paannejään poisto"
   "KELINTARKASTUS"                  "Talvihoito"

   "TIESTOTARKASTUS"                 "Tiestötarkastus"
   "KONEELLINEN NIITTO"              "Koneellinen niitto"
   "KONEELLINEN VESAKONRAIVAUS"      "Koneellinen vesakonraivaus"

   "LIIKENNEMERKKIEN PUHDISTUS"      "Liikennemerkkien puhdistus"

   "SORATEIDEN MUOKKAUSHOYLAYS"      "Sorateiden muokkaushöyläys"
   "SORATEIDEN POLYNSIDONTA"         "Sorateiden pölynsidonta"
   "SORATEIDEN TASAUS"               "Sorateiden tasaus"
   "SORASTUS"                        "Sorastus"

   "HARJAUS"                         "Harjaus"
   "PAALLYSTEIDEN PAIKKAUS"          "Päällysteiden paikkaus"
   "PAALLYSTEIDEN JUOTOSTYOT"        "Päällysteiden juotostyöt"

   "SILTOJEN PUHDISTUS"              "Siltojen puhdistus"

   "L- JA P-ALUEIDEN PUHDISTUS"      "L- ja P-alueiden puhdistus"
   "MUU"                             "Muu"})

(defn tehtavan-nimi [tehtavat]
  (str/join ", " (into []
                       (comp
                         (map str/capitalize)
                         (map #(or (get tehtavien-nimet (str/upper-case %)) %)))
                       tehtavat)))


(defn- maaritelty-tyyli [tehtava]
  (let [koodi (into #{} (map str/upper-case tehtava))
        tulos (get ulkoasu/tehtavien-varit koodi)]
    (when-not (empty? tulos) tulos)))

(defn kasvata-viivan-leveytta
  "Kasvattaa viivan leveyttä kahdella, jos leveys on määritelty"
  [{leveys :width :as viiva}]
  (if leveys
    (assoc viiva
           :width (+ 2 leveys))
    viiva))

(defn- viimeistele-asetukset [[viivat nuoli] valittu?]
  [(if valittu?
     ;; Kasvata jokaisen viivan määriteltyä leveyttä kahdella jos toteuma on
     ;; valittu. Jos leveyttä ei ole annettu, niin mennään oletusasetuksilla,
     ;; jotka ottavat valinnan jo huomioon.
     (mapv kasvata-viivan-leveytta viivat)
     (vec viivat))
   nuoli])

(defn- validoi-viiva
  "Varmista että viiva on vektori määrityksiä. Yksittäinen string tai mäp
  muunnetaan yhden elementin vektoriksi viivamäärityksiä.
  Yksittäinen string tulkitaan viivan väriksi."
  [[viivat nuoli]]
  [(cond
     (string? viivat) [{:color viivat}]
     (map? viivat) [viivat]
     :else viivat)
   nuoli])

(defn- tehtavan-viivat-ja-nuolitiedosto
  "Hakee toimenpiteelle esitysasetukset joko yllä määritellystä mäpistä, tai
  generoi sellaisen itse."
  [tehtava valittu?]
  ;; Prosessoi asetukset siten, että stringinä määritellystä
  ;; nuoli-ikonista tehdään :nuoli,
  ;; ja jos viivoille on määritelty leveydet (monivärinen nuoli),
  ;; niin kasvatetaan niitä jos toteuma on valittu.
  (-> (or (maaritelty-tyyli tehtava)
          (generoitu-tyyli (str/join ", " tehtava)))
      validoi-viiva
      (viimeistele-asetukset valittu?)))





(defmethod asia-kartalle :toteuma [toteuma valittu-fn?]
  ;; Piirretään toteuma sen tieverkolle projisoidusta reitistä
  ;; (ei yksittäisistä reittipisteistä)
  (when-let [reitti (:reitti toteuma)]
    (let [toimenpiteet (map :toimenpide (:tehtavat toteuma))
          _ (when (empty? toimenpiteet) (harja.loki/warn "Toteuman tehtävät ovat tyhjät! TÄMÄ ON BUGI."))
          nimi (or
                 ;; toteumalla on suoraan nimi
                 (:nimi toteuma)
                 ;; tai nimi muodostetaan yhdistämällä tehtävien toimenpiteet
                 (tehtavan-nimi toimenpiteet))
          [viivat nuolen-vari] (tehtavan-viivat-ja-nuolitiedosto
                                toimenpiteet (valittu-fn? toteuma))]
      (assoc toteuma
        :type :toteuma
        :nimi nimi
        :selite {:teksti nimi
                 :vari   (viivojen-varit-leveimmasta-kapeimpaan viivat)}
        :alue (maarittele-feature reitti (valittu-fn? toteuma)
                                  (ulkoasu/toteuman-nuoli nuolen-vari)
                                  viivat
                                  (ulkoasu/toteuman-ikoni nuolen-vari))))))

(defn muunna-tyokoneen-suunta [kulma]
  (+ (- Math/PI)
     (* (/ Math/PI 180)
        kulma)))

(defmethod asia-kartalle :tyokone [tyokone valittu-fn?]
  (let [selite-teksti (tehtavan-nimi (:tehtavat tyokone))
        [viivat nuolen-vari] (tehtavan-viivat-ja-nuolitiedosto
                              (:tehtavat tyokone) (valittu-fn? tyokone))
        paikka {:sijainti {:type (if (:reitti tyokone) :line :point)}}
        paikka (if (:reitti tyokone)
                 (assoc-in paikka [:sijainti :points] (:reitti tyokone))
                 (assoc-in paikka [:sijainti :coordinates]
                           (:sijainti tyokone)))]
    (assoc tyokone
      :type :tyokone
      :nimi (or (:nimi tyokone) (str/capitalize (name (:tyokonetyyppi tyokone))))
      :selite {:teksti selite-teksti
               :vari   (viivojen-varit-leveimmasta-kapeimpaan viivat)}
      :alue (maarittele-feature paikka (valittu-fn? tyokone)
                                (ulkoasu/tyokoneen-ikoni nuolen-vari (muunna-tyokoneen-suunta (:suunta tyokone)))
                                viivat))))

(defmethod asia-kartalle :default [asia _]
  (warn "Kartalla esitettävillä asioilla pitää olla :tyyppi-kartalla avain!, "
        "sain: " (pr-str asia))
  nil)

(defn- valittu-fn? [valittu tunniste asia]
  (log "Tunniste on: " (pr-str tunniste))
  (let [tunniste (if (vector? tunniste) tunniste [tunniste])
        tunnisteet (if (vector? (first tunniste)) tunniste [tunniste tunniste])]
    (and
      (not (nil? valittu))
      (= (get-in asia (first tunnisteet)) (get-in valittu (second tunnisteet))))))

(defn- tallenna-selitteet-xf [selitteet]
  (fn [xf]
    (fn
      ([] (xf))
      ([result] (xf result))
      ([result input]
       (when-let [selite (:selite input)]
         (vswap! selitteet conj selite))
       (xf result input)))))

(defn kartalla-xf
  ([asia] (kartalla-xf asia nil nil))
  ([asia valittu] (kartalla-xf asia valittu [:id]))
  ([asia valittu tunniste]
   (asia-kartalle asia
                  (if valittu
                    (partial valittu-fn? valittu tunniste)
                    (constantly false)))))

(defn kartalla-esitettavaan-muotoon
  "Valitun asian tunniste on defaulttina :id. Voi antaa :id, [:tehtava :id], tai jos
  esitettävän asian ja valitun asian id on eri, [[:id] [:toteuma-id]]"
  ([asiat] (kartalla-esitettavaan-muotoon asiat nil nil))
  ([asiat valittu] (kartalla-esitettavaan-muotoon asiat valittu [:id]))
  ([asiat valittu tunniste]
   (kartalla-esitettavaan-muotoon asiat valittu tunniste nil))
  ([asiat valittu tunniste asia-xf]
   (let [extent (volatile! nil)
         selitteet (volatile! #{})]
     (log "Asioiden eka: " (first asiat))
     (with-meta
       (into []
             (comp (or asia-xf identity)
                   (map #(kartalla-xf % valittu (or tunniste [:id])))
                   (filter some?)
                   (filter #(some? (:alue %)))
                   (geo/laske-extent-xf extent)
                   (tallenna-selitteet-xf selitteet))
             asiat)
       {:extent    @extent
        :selitteet @selitteet}))))
