(ns harja.ui.kartta.esitettavat-asiat
  (:require [clojure.string :as str]
    #?(:cljs [harja.ui.openlayers.edistymispalkki :as edistymispalkki])
            [taoensso.timbre :as log]
            [harja.domain.laadunseuranta.laatupoikkeama :as laatupoikkeamat]
            [harja.domain.laadunseuranta.tarkastus :as tarkastukset]
            [harja.domain.tieliikenneilmoitukset :as ilmoitukset]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [harja.geo :as geo]
            [harja.ui.kartta.asioiden-ulkoasu :as ulkoasu]
            [harja.pvm :as pvm]
            [harja.domain.tierekisteri :as tr]
            [harja.domain.tietyoilmoitus :as tietyoilmoitukset]
            [harja.domain.vesivaylat.turvalaite :as tu]
            [harja.domain.kanavat.kohteenosa :as osa]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.tielupa :as tielupa]
            [clojure.set :as set]))


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

(defn pura-geometry-collection [asia]
  (if (= :geometry-collection (:type (:sijainti asia)))
    (for [g (:geometries (:sijainti asia))]
      (assoc asia :sijainti g))
    [asia]))

(defn reitillinen-asia? [asia]
  (case (:type (or (:sijainti asia) (:reitti asia) asia))
    :point false
    :line true
    :multiline true
    false))

(defn asia-on-piste? [asia]
  (not (reitillinen-asia? asia)))


;; Jos annettu asetus on merkkijono, palautetaan [{:img merkkijono}]
(defn- validoi-merkkiasetukset [merkit]
  (cond
    (empty? merkit) []
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
        viivat (validoi-viiva-asetukset viivat)
        zindex (+ ulkoasu/+zindex+ (if valittu? 1 0))]
    {:viivat (mapv (fn [v] (merge
                             ;; Ylikirjoitettavat oletusasetukset
                             {:color (viivan-vari valittu?)
                              :width (viivan-leveys valittu?)
                              :zindex zindex}
                             v)) viivat)
     :ikonit (mapv (fn [i] (merge
                             ;; Oletusasetukset
                             {:tyyppi :merkki
                              :paikka [:loppu]
                              :scale (laske-skaala valittu?)
                              :zindex zindex}
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
      -- paikka: vektori, jonka elementtejä voivat olla :alku, :loppu ja :taitokset
         Mihin paikkoihin ikoni piirretään?
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
    Juttu on todennäköisesti pistemäinen asia. Käytetään vihreää pinniä.
    Jos juttu onkin reitillinen, käytetään reitin piirtämiseen puhtaasti
    oletusasetuksia.

  (maarittele-feature homma val? (sijainti-ikoni 'magenta')
                      {:color (if (val? homma) varit/vihrea varit/keltainen)})
    Samanlainen kuin edellinen, mutta on määritelty millä värillä halutaan
    piirtää reitti

  (maarittele-feature foo val?
                      [{:paikka [:loppu] :img (pinni-ikoni 'sininen)}
                       {:paikka [:taitokset] :img (nuoli-ikoni 'sininen')}]
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
         koordinaatit (or (:coordinates geo) (:points geo) (mapcat :points (:lines geo)))
         _ (log/debug "maarittele-feature :: geo" (pr-str geo))
         _ (log/debug "maarittele-feature :: tyyppi" (pr-str tyyppi))
         _ (log/debug "maarittele-feature :: koordinaatit" (pr-str koordinaatit))
         vastaus (if (= :geometry-collection tyyppi)
                   (merge
                     (maarittele-viiva valittu? merkit viivat)
                     asia)
                   (when (not (empty? koordinaatit))
                     (cond
                       ;; {:type :multipoint, :coordinates [{:type :point, :coordinates [298069.5999999996 6702931.0030000005]}]}
                       (and (= :multipoint tyyppi) (= 1 (count koordinaatit)))
                       (when (or pisteen-ikoni merkit)
                         (merge
                           (maarittele-piste valittu? (or pisteen-ikoni merkit))
                           {:type :merkki
                            :coordinates (:coordinates (first koordinaatit))}))

                       (= :multipoint tyyppi)
                       nil

                       ;; Näyttää siltä että joskus saattaa löytyä LINESTRINGejä, joilla on vain yksi piste
                       ;; Ei tietoa onko tämä virheellistä testidataa vai real world case, mutta varaudutaan siihen joka tapauksessa
                       (or (= :point tyyppi) (= 1 (count koordinaatit)))
                       (when (or pisteen-ikoni merkit)
                         (merge
                           (maarittele-piste valittu? (or pisteen-ikoni merkit))
                           {:type :merkki
                            :coordinates (flatten koordinaatit)})) ;; [x y] -> [x y] && [[x y]] -> [x y]

                       (= :line tyyppi)
                       (merge
                         (maarittele-viiva valittu? merkit viivat)
                         {:type :viiva
                          :points koordinaatit})

                       (= :multiline tyyppi)
                       (merge
                         (maarittele-viiva valittu? merkit viivat)
                         {:type :moniviiva
                          :lines (:lines geo)}))))
         _ (log/debug "maarittele-feature :: vastaus " (pr-str vastaus))]
     vastaus)))

;;;;;;

(defn viivojen-varit-leveimmasta-kapeimpaan [viivat]
  ;; Täydennä väliaikaisesti tänne oletusarvot,
  ;; muuten leveysvertailu failaa, ja halutaanhan toki palauttaa
  ;; jokin väri myös jutuille, joille sellaista ei ole (vielä!) määritelty.
  (if (sequential? viivat)
    (->> viivat
         (mapv #(assoc % :width (or (:width %) ulkoasu/+normaali-leveys+)
                         :color (or (:color %) ulkoasu/+normaali-vari+)))
         (sort-by :width >)
         (mapv :color))

    (:color viivat)))

(defmulti
  ^{:private true}
  asia-kartalle :tyyppi-kartalla)

(defn ilmoituksen-tooltip [ilmoitus]
  (str (ilmoitukset/ilmoitustyypin-nimi (:ilmoitustyyppi ilmoitus))
       " ("
       (str/lower-case (ilmoitukset/tilan-selite (:tila ilmoitus)))
       ")"))


(defn ilmoitus-kartalle [{:keys [tila ilmoitustyyppi] :as ilmoitus} valittu?]
  (let [ikoni (ulkoasu/ilmoituksen-ikoni ilmoitus)]
    (assoc ilmoitus
      :type :ilmoitus
      :nimi (ilmoituksen-tooltip ilmoitus)
      :selite {:teksti (str (ilmoitukset/ilmoitustyypin-lyhenne ilmoitustyyppi)
                            " ("
                            (str/lower-case (ilmoitukset/tilan-selite tila))
                            ")")
               :img ikoni}
      :alue (maarittele-feature ilmoitus valittu? ikoni))))

(defmethod asia-kartalle :tiedoitus [ilmoitus valittu?]
  (ilmoitus-kartalle ilmoitus valittu?))

(defmethod asia-kartalle :kysely [ilmoitus valittu?]
  (ilmoitus-kartalle ilmoitus valittu?))

(defmethod asia-kartalle :toimenpidepyynto [ilmoitus valittu?]
  (ilmoitus-kartalle ilmoitus valittu?))

(defn otsikko-tekijalla [etuliite laatupoikkeama]
  (let [tekijatyyppi (laatupoikkeamat/kuvaile-tekija (:tekija laatupoikkeama))]
    (str etuliite
         (when-not (empty? tekijatyyppi) (str " (" tekijatyyppi ")")))))

(defmethod asia-kartalle :laatupoikkeama [laatupoikkeama valittu?]
  (let [ikoni (ulkoasu/laatupoikkeaman-ikoni (:tekija laatupoikkeama))
        ;; Laatupoikkeamat ovat pistemäisiä, mutta annetaan viivamäärittely fallbackina.
        viiva (ulkoasu/laatupoikkeaman-reitti (:tekija laatupoikkeama))
        otsikko (otsikko-tekijalla "Laatupoikkeama" laatupoikkeama)]
    (assoc laatupoikkeama
      :type :laatupoikkeama
      :nimi (or (:nimi laatupoikkeama) otsikko)
      :selite {:teksti otsikko
               :img ikoni}
      :alue (maarittele-feature laatupoikkeama valittu?
                                ikoni viiva))))

(def tarkastus-selitteet-reiteille
  #{{:teksti "Tarkastus OK, tilaaja" :vari (viivojen-varit-leveimmasta-kapeimpaan (ulkoasu/tarkastuksen-reitti {:ok? true}))}
    {:teksti "Tarkastus OK, urakoitsija " :vari (viivojen-varit-leveimmasta-kapeimpaan (ulkoasu/tarkastuksen-reitti {:ok? true :tekija :urakoitsija}))}
    {:teksti "Tarkastus havainnolla" :vari (viivojen-varit-leveimmasta-kapeimpaan (ulkoasu/tarkastuksen-reitti {:ok? true :havainnot "Vesakko raivaamatta"}))}
    {:teksti "Tie luminen tai liukas" :vari (viivojen-varit-leveimmasta-kapeimpaan (ulkoasu/tarkastuksen-reitti {:ok? true
                                                                                                                 :vakiohavainnot #{"Lumista"}}))}
    {:teksti "Laadun\u00ADalitus, tilaaja" :vari (viivojen-varit-leveimmasta-kapeimpaan (ulkoasu/tarkastuksen-reitti {:ok? false}))}
    {:teksti "Laadun\u00ADalitus, urakoitsija" :vari (viivojen-varit-leveimmasta-kapeimpaan (ulkoasu/tarkastuksen-reitti {:ok? false :tekija :urakoitsija}))}})

(def tarkastus-selitteet-ikoneille
  #{{:teksti "Tarkastus OK, tilaaja" :img (ulkoasu/tarkastuksen-ikoni {:ok? true :tekija :tilaaja} false)}
    {:teksti "Tarkastus OK, urakoitsija " :img (ulkoasu/tarkastuksen-ikoni {:ok? true :tekija :urakoitsija} false)}
    {:teksti "Tarkastus havainnolla" :img (ulkoasu/tarkastuksen-ikoni {:ok? true :havainnot "Vesakko raivaamatta"} false)}
    {:teksti "Laadun\u00ADalitus, tilaaja" :img (ulkoasu/tarkastuksen-ikoni {:ok? false} false)}
    {:teksti "Laadun\u00ADalitus, urakoitsija" :img (ulkoasu/tarkastuksen-ikoni {:ok? false :tekija :urakoitsija} false)}})

(defmethod asia-kartalle :tarkastus [tarkastus valittu?]
  (let [ikoni (ulkoasu/tarkastuksen-ikoni tarkastus (reitillinen-asia? tarkastus))
        viiva (ulkoasu/tarkastuksen-reitti tarkastus)
        selite-teksti {:teksti (otsikko-tekijalla "Tarkastus" tarkastus)}
        selite (if ikoni
                 (assoc selite-teksti :img ikoni)
                 (assoc selite-teksti :vari (viivojen-varit-leveimmasta-kapeimpaan viiva)))]
    (assoc tarkastus
      :type :tarkastus
      :nimi (or (:nimi tarkastus)
                (otsikko-tekijalla
                  (tarkastukset/+tarkastustyyppi->nimi+ (:tyyppi tarkastus))
                  tarkastus))
      :selite selite
      :alue (maarittele-feature tarkastus valittu? ikoni viiva))))

(defmethod asia-kartalle :varustetoteuma [varustetoteuma valittu?]
  (let [ikoni (ulkoasu/varustetoteuman-ikoni)]
    (assoc varustetoteuma
      :type :varustetoteuma
      :nimi (or (:tooltip varustetoteuma) "Varustetoteuma")
      :selite {:teksti "Varustetoteuma"
               :img ikoni}
      :alue (maarittele-feature varustetoteuma valittu?
                                (ulkoasu/varustetoteuman-ikoni)))))

(defmethod asia-kartalle :varuste [varuste valittu?]
  (let [ikoni (ulkoasu/varusteen-ikoni)]
    (assoc varuste
      :type :varuste
      :nimi (or (:tooltip varuste) "Varuste")
      :selite {:teksti "Varuste"
               :img ikoni}
      :alue (maarittele-feature varuste valittu?
                                (ulkoasu/varusteen-ikoni)))))

(defmethod asia-kartalle :tietyoilmoitus [tietyoilmoitus valittu?]
  (let [ikoni (ulkoasu/tietyoilmoituksen-ikoni)
        viiva (ulkoasu/tietyoilmoituksen-viiva)]
    (assoc tietyoilmoitus
      :type :tietyoilmoitus
      :nimi (or (:tooltip tietyoilmoitus) "Tietyoilmoitus")
      :selite {:teksti "Tietyoilmoitus"
               :img ikoni
               :tietyoilmoituksen-viiva (viivojen-varit-leveimmasta-kapeimpaan viiva)}
      :alue (maarittele-feature (get-in tietyoilmoitus [::tietyoilmoitukset/osoite ::tr/geometria]) valittu? ikoni viiva))))

(defn paattele-turpon-ikoni [turpo]
  (let [kt (:korjaavattoimenpiteet turpo)]
    (if (empty? kt)
      [:tyhja "Turvallisuuspoikkeama, avoin"]

      (if (some (comp nil? :suoritettu) kt)
        [:avoimia "Turvallisuuspoikkeama, ei korjauksia"]

        [:valmis "Turvallisuuspoikkeama, kaikki korjattu"]))))


(defmethod asia-kartalle :turvallisuuspoikkeama [tp valittu?]
  (let [[kt-tila selite] (paattele-turpon-ikoni tp)
        ikoni (ulkoasu/turvallisuuspoikkeaman-ikoni kt-tila)]
    (when (:sijainti tp)
      (assoc tp
        :type :turvallisuuspoikkeama
        :nimi (or (:nimi tp) "Turvallisuuspoikkeama")
        :selite {:teksti selite
                 :img ikoni}
        :alue (maarittele-feature tp valittu? ikoni)))))

(defmethod asia-kartalle :turvalaite [turvalaite valittu?]
  (let [[ikoni selite] (ulkoasu/turvalaitteen-ikoni-ja-selite turvalaite)
        alue (maarittele-feature turvalaite valittu? ikoni)]
    (assoc turvalaite
      :type :turvalaite
      :nimi (or (::tu/nimi turvalaite) "Turvalaite")
      :selite {:teksti selite
               :img ikoni}
      :alue alue)))

(defmethod asia-kartalle :suolatoteuma [suolatoteuma valittu?]
  (let [viiva (ulkoasu/suolatoteuman-viiva)]
    (assoc suolatoteuma
      :type :suolatoteuma
      :nimi "Suolatoteuma"
      :selite {:teksti "Suolatoteuma"
               :vari (viivojen-varit-leveimmasta-kapeimpaan viiva)}
      :alue (maarittele-feature suolatoteuma false nil viiva))))

(defn- yllapitokohde [tyyppi yllapitokohde valittu? teksti]
  (let [tila-kartalla (yllapitokohteet-domain/yllapitokohteen-tila-kartalla (:tila yllapitokohde))
        tila-teksti (str/lower-case (yllapitokohteet-domain/kuvaile-kohteen-tila-kartalla tila-kartalla))
        ikoni nil ;; ei ikonia
        viiva (ulkoasu/yllapidon-viiva valittu? tila-kartalla tyyppi)]
    (assoc yllapitokohde
      :nimi (or (:nimi yllapitokohde) teksti)
      :selite {:teksti (str teksti " (" tila-teksti ")")
               :vari (viivojen-varit-leveimmasta-kapeimpaan viiva)}
      :alue (maarittele-feature yllapitokohde valittu?
                                ikoni
                                viiva))))

(def paallystys-selitteet
  (into #{}
        (for [t [:valmis :ei-aloitettu :kesken]
              :let [viiva (ulkoasu/yllapidon-viiva false t :paallystys)]]
          {:teksti (str "Päällystyskohde ("
                        (str/lower-case (yllapitokohteet-domain/kuvaile-kohteen-tila-kartalla t))
                        ")")
           :vari (viivojen-varit-leveimmasta-kapeimpaan viiva)})))

(defmethod asia-kartalle :paallystys [pt valittu?]
  (assoc (yllapitokohde :paallystys pt valittu? "Päällystyskohde")
    :type :paallystys))

(defmethod asia-kartalle :paikkaus [pt valittu?]
  (assoc (yllapitokohde :paikkaus pt valittu? "Paikkauskohde")
    :type :paikkaus))

(let [varien-lkm (count ulkoasu/toteuma-varit-ja-nuolet)]
  (defn generoitu-tyyli [tehtavan-nimi]
    (log/debug tehtavan-nimi " määritys puuttuu esitettävistä asioista, generoidaan tyyli koneellisesti!")
    (nth ulkoasu/toteuma-varit-ja-nuolet (Math/abs (rem (hash tehtavan-nimi) varien-lkm)))))

(def tehtavien-nimet
  {"AURAUS JA SOHJONPOISTO" "Auraus tai sohjonpoisto"
   "SUOLAUS" "Suolaus"
   ;; Liuossuolausta ei ymmärtääkseni enää seurata, mutta kesälomien takia tässä on korjauksen
   ;; hetkellä pieni informaatiouupelo. Nämä rivit voi poistaa tulevaisuudessa, jos lukija
   ;; kokee tietävänsä asian varmaksi.
   ;;"LIUOSSUOLAUS"                    "Liuossuolaus"
   "PISTEHIEKOITUS" "Pistehiekoitus"
   "LINJAHIEKOITUS" "Linjahiekoitus"
   "PINNAN TASAUS" "Pinnan tasaus"
   "LUMIVALLIEN MADALTAMINEN" "Lumivallien madaltaminen"
   "SULAMISVEDEN HAITTOJEN TORJUNTA" "Sulamisveden haittojen torjunta"
   "AURAUSVIITOITUS JA KINOSTIMET" "Aurausviitoitus ja kinostimet"
   "LUMENSIIRTO" "Lumensiirto"
   "PAANNEJAAN POISTO" "Paannejään poisto"
   "KELINTARKASTUS" "Talvihoito"

   "TIESTOTARKASTUS" "Tiestötarkastus"
   "KONEELLINEN NIITTO" "Koneellinen niitto"
   "KONEELLINEN VESAKONRAIVAUS" "Koneellinen vesakonraivaus"

   "LIIKENNEMERKKIEN PUHDISTUS" "Liikennemerkkien puhdistus"

   "SORATEIDEN MUOKKAUSHOYLAYS" "Sorateiden muokkaushöyläys"
   "SORATEIDEN POLYNSIDONTA" "Sorateiden pölynsidonta"
   "SORATEIDEN TASAUS" "Sorateiden tasaus"
   "SORASTUS" "Sorastus"

   "HARJAUS" "Harjaus"
   "PAALLYSTEIDEN PAIKKAUS" "Päällysteiden paikkaus"
   "PAALLYSTEIDEN JUOTOSTYOT" "Päällysteiden juotostyöt"

   "SILTOJEN PUHDISTUS" "Siltojen puhdistus"

   "L- JA P-ALUEIDEN PUHDISTUS" "L- ja P-alueiden puhdistus"
   "MUU" "Muu"})

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

(defn tehtavan-viivat-ja-nuolitiedosto
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


(defn toimenpiteen-selite
  "Antaa toimenpiteen nimelle sopivan selitteen"
  [toimenpide]
  (let [[viivat _] (tehtavan-viivat-ja-nuolitiedosto
                     [toimenpide] false)]
    {:teksti toimenpide
     :vari (viivojen-varit-leveimmasta-kapeimpaan viivat)}))

(defmethod asia-kartalle :toteuma [toteuma valittu?]
  ;; Piirretään toteuma sen tieverkolle projisoidusta reitistä
  ;; (ei yksittäisistä reittipisteistä)
  (when-let [reitti (:reitti toteuma)]
    (let [toimenpiteet (map :toimenpide (:tehtavat toteuma))
          toimenpiteet (if-not (empty? toimenpiteet)
                         toimenpiteet
                         [(get-in toteuma [:tehtava :nimi])])
          _ (when (empty? toimenpiteet)
              (log/warn "Toteuman tehtävät ovat tyhjät! TÄMÄ ON BUGI."))
          nimi (or
                 ;; toteumalla on suoraan nimi
                 (:nimi toteuma)
                 ;; tai nimi muodostetaan yhdistämällä tehtävien toimenpiteet
                 (tehtavan-nimi toimenpiteet))
          [viivat nuolen-vari] (tehtavan-viivat-ja-nuolitiedosto
                                 toimenpiteet valittu?)]
      (assoc toteuma
        :type :toteuma
        :nimi nimi
        :selite {:teksti nimi
                 :vari (viivojen-varit-leveimmasta-kapeimpaan viivat)}
        :alue (maarittele-feature reitti valittu?
                                  (if (:ei-nuolia? toteuma)
                                    nil
                                    (ulkoasu/toteuman-nuoli nuolen-vari))
                                  viivat
                                  (ulkoasu/toteuman-ikoni nuolen-vari))))))

(defn muunna-tyokoneen-suunta [kulma]
  (+ (- Math/PI)
     (* (/ Math/PI 180)
        kulma)))

(defmethod asia-kartalle :tietyomaa [aita valittu?]
  (let [viivat ulkoasu/tietyomaa]
    (assoc aita
      :type :tietyomaa
      :nimi "Tietyömaa"
      :selite {:teksti "Tietyömaa"
               :vari (viivojen-varit-leveimmasta-kapeimpaan viivat)}
      :alue (maarittele-feature {:sijainti (:geometria aita)}
                                valittu?
                                nil
                                viivat))))

(defn tyokoneen-selite [tehtavat]
  {:teksti (tehtavan-nimi tehtavat)
   :vari (viivojen-varit-leveimmasta-kapeimpaan
           (first (tehtavan-viivat-ja-nuolitiedosto tehtavat false)))})

(defmethod asia-kartalle :tyokone [tyokone valittu?]
  (let [selite-teksti (tehtavan-nimi (:tehtavat tyokone))
        [viivat nuolen-vari] (tehtavan-viivat-ja-nuolitiedosto
                               (:tehtavat tyokone) valittu?)
        viivat (ulkoasu/tehtavan-viivat-tyokoneelle viivat)
        paikka (or (:reitti tyokone)
                   {:type :point
                    :coordinates (:sijainti tyokone)})]
    (assoc tyokone
      :type :tyokone
      :nimi (or (:nimi tyokone) (str/capitalize (name (:tyokonetyyppi tyokone))))
      :selite {:teksti selite-teksti
               :vari (viivojen-varit-leveimmasta-kapeimpaan viivat)}
      :alue (maarittele-feature paikka valittu?
                                (ulkoasu/tyokoneen-nuoli nuolen-vari)
                                viivat))))

(defmethod asia-kartalle :tr-osoite-indikaattori [tr-osoite _]
  ;; TR-osoitteen indikaattori näyttää "raja-aidat" tieosoitevälille
  ;; molempiin päätyihin. Ensin lasketaan pisteiden välinen kulma, sitten
  ;; molempiin päihin lyhyt viiva alku/loppu pisteisiin.
  (let [koordinaatit (geo/pisteet tr-osoite)
        [alku-x alku-y] (first koordinaatit)
        [loppu-x loppu-y] (last koordinaatit)

        alku-ang (apply geo/kulma (take 2 koordinaatit))
        loppu-ang (apply geo/kulma (take 2 (reverse koordinaatit)))

        w 16
        viiva (fn [ang x y]
                (vec
                  (for [a [(+ ang (/ Math/PI 2))
                           (- ang (/ Math/PI 2))]]
                    [(+ x (* w (Math/cos a)))
                     (+ y (* w (Math/sin a)))])))]
    (assoc tr-osoite
      :alue {:type :geometry-collection
             :stroke {:color "black"
                      :width 5}
             :geometries [{:type :line
                           :points (viiva alku-ang alku-x alku-y)}
                          {:type :line
                           :points (viiva loppu-ang loppu-x loppu-y)}]})))

(defmethod asia-kartalle :reittipiste [{:keys [sijainti aika tehtavat] :as reittipiste} valittu?]
  ;; Näyttää toteuman reittipisteet palloina
  (let [toimenpiteet (map :toimenpide tehtavat)
        [viivat _] (tehtavan-viivat-ja-nuolitiedosto
                     toimenpiteet valittu?)
        vari (last (viivojen-varit-leveimmasta-kapeimpaan viivat))]
    {:type :reittipisteet
     :nimi (str (tehtavan-nimi toimenpiteet) "\n"
                (pvm/pvm-aika-sek aika))
     :alue (assoc sijainti
             :fill true
             :radius 50
             :color vari)}))

;; Tätä käytetään hallintanäkymässä - jos haluat näyttää kohteenosia kartalla muualla,
;; haluat mahdollisesti tehdä uuden metodin
(defmethod asia-kartalle :kohteenosa [osa osan-kohde-valittu?]
  ;; Näyttää toteuman reittipisteet palloina
  (let [[ikoni teksti] (ulkoasu/kohteenosa-kohteiden-luonnissa osa osan-kohde-valittu?)]
    (assoc osa
      :type :kohteenosa
      :nimi (kohde/fmt-kohde-ja-osa-nimi (::osa/kohde osa) osa)
      :selite {:teksti teksti
               :img ikoni}
      :alue (maarittele-feature osa
                                ;; Ei haluta piirtää valitun kohteen osia isommalla, vain eri värillä
                                false
                                ikoni))))

(defmethod asia-kartalle :kohde-toimenpide [kohde kohde-valittu?]
  (let [[ikoni teksti] (ulkoasu/kan-kohde kohde)]
    (assoc kohde
      :type :kohde-toimenpide
      :nimi (kohde/fmt-kohteen-nimi kohde)
      :selite {:teksti teksti
               :img ikoni}
      :alue (maarittele-feature kohde
                                kohde-valittu?
                                ikoni))))

(defmethod asia-kartalle :kan-toimenpide [toimenpide valittu?]
  (let [[ikoni teksti] (ulkoasu/kan-toimenpide toimenpide)]
    (assoc toimenpide
      :type :kan-toimenpide
      :nimi "Toimenpide"
      :selite {:teksti teksti
               :img ikoni}
      :alue (maarittele-feature toimenpide
                                valittu?
                                ikoni))))

(defmethod asia-kartalle :kan-hairiotilanne [hairiotilanne valittu?]
  (let [[ikoni teksti] (ulkoasu/kan-hairiotilanne hairiotilanne)]
    (assoc hairiotilanne
      :type :kan-hairiotilanne
      :nimi "Häiriötilanne"
      :selite {:teksti teksti
               :img ikoni}
      :alue (maarittele-feature hairiotilanne
                                valittu?
                                ikoni))))

(defmethod asia-kartalle :kohde-hairiotilanne [kohde kohde-valittu?]
  (let [[ikoni teksti] (ulkoasu/kan-kohde kohde)]
    (assoc kohde
      :type :kohde-hairiotilanne
      :nimi (kohde/fmt-kohteen-nimi kohde)
      :selite {:teksti teksti
               :img ikoni}
      :alue (maarittele-feature kohde
                                kohde-valittu?
                                ikoni))))

(defmethod asia-kartalle :tielupa [tielupa valittu?]
  (let [[teksti ikoni viivat viivamerkit] (ulkoasu/tielupa tielupa)]
    (assoc tielupa
      :type :tielupa
      :nimi (str (tielupa/tyyppi-fmt (::tielupa/tyyppi tielupa)) " " (::tielupa/paatoksen-diaarinumero tielupa))
      :selite {:teksti teksti
               :img ikoni}
      :alue (maarittele-feature tielupa
                                valittu?
                                viivamerkit
                                viivat
                                ikoni))))

(defmethod asia-kartalle :default [{tyyppi :tyyppi-kartalla :as asia} _]
  (if tyyppi
    (log/warn "Kartan :tyyppi-kartalla ei ole tuettu: " (str tyyppi))
    (log/warn "Kartalla esitettävillä asioilla pitää olla :tyyppi-kartalla avain!, "
              "sain: " (pr-str asia)))
  nil)

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
  ([asia] (kartalla-xf asia nil))
  ([asia valittu-fn]
   (asia-kartalle asia (and valittu-fn
                            (valittu-fn asia)))))

(defn kartalla-esitettavaan-muotoon-xf
  "Palauttaa transducerin, joka muuntaa läpi kulkevat asiat kartalla esitettävään
  muotoon."
  ([] (kartalla-esitettavaan-muotoon-xf nil nil))
  ([valittu-fn asia-xf]
   (comp #?(:cljs (fn [asia] (edistymispalkki/geometriataso-lataus-valmis!) asia))
         (or asia-xf identity)
         (mapcat pura-geometry-collection)
         (map #(kartalla-xf % valittu-fn))
         (filter some?)
         (filter #(some? (:alue %))))))

(defn kartalla-esitettavaan-muotoon
  "Muuttaa annetut asiat kartalle esitettävään muotoon. Asiat on sekvenssi
  mäppejä, joilla tulee olla :tyyppi-kartalla avain, jonka perusteella esitysmuoto
  tehdään. Jos valittu on annettu, sitä kutsutaan (ei muunnetulla) mäpillä
  päättelemään onko muunnettava asia nyt valittuna.
  Jos asia-xf on annettu, kaikki asiat ajetaan sen läpi ennen muuntamista."
  ([asiat]
   (kartalla-esitettavaan-muotoon asiat (constantly false) nil))
  ([asiat valittu-fn]
   (kartalla-esitettavaan-muotoon asiat valittu-fn nil))
  ([asiat valittu-fn asia-xf]
    ;; Haluamme näyttää edistymispalkin, mutta 100% valmius ei ole vielä siinä
    ;; vaiheessa, kun koko data on lapioitu.
    #?(:cljs (edistymispalkki/geometriataso-aloita-lataus! (* 2 (count asiat))))
   (let [extent (volatile! nil)
         selitteet (volatile! #{})]
     (with-meta
       (into []
             (comp (kartalla-esitettavaan-muotoon-xf valittu-fn asia-xf)
                   (geo/laske-extent-xf extent)
                   (tallenna-selitteet-xf selitteet))
             asiat)
       {:extent @extent
        :selitteet @selitteet}))))
