(ns harja.ui.kartta.esitettavat-asiat
  (:require [harja.pvm :as pvm]
            [clojure.string :as str]
            [harja.loki :refer [log warn] :refer-macros [mittaa-aika]]
            [cljs-time.core :as t]
            [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat :as laatupoikkeamat]
            [harja.tiedot.urakka.laadunseuranta.tarkastukset :as tarkastukset]
            [harja.ui.dom :refer [karttakuva]]
            [harja.geo :as geo]))

(defn arrow-line [optiot geometria]
  (merge optiot
         {:type :arrow-line}
         (case (:type geometria)
           :line
           {:points (:points geometria)}

           :multiline
           {:points (mapcat :points (:lines geometria))}

           :point
           {:points [(:coordinates geometria)]})))

(defn tack-icon [optiot geometria]
  (merge optiot
         (case (:type geometria)
           :line
           {:type   :tack-icon-line
            :points (:points geometria)}

           :multiline
           {:type   :tack-icon-line
            :points (mapcat :points (:lines geometria))}

           :point
           {:type        :tack-icon
            :coordinates (:coordinates geometria)})))

(def +valitun-skaala+ 1.5)
(def +normaali-skaala+ 1)
(def +zindex+ 4)
(def +oletusikoni+ "turvallisuuspoikkeama-tack-vihrea")

(defn- laske-skaala [valittu?]
  (if valittu? +valitun-skaala+ +normaali-skaala+))

(defn viivan-vari
  ([valittu?] (viivan-vari valittu? "blue" "black"))
  ([valittu? valittu-vari] (viivan-vari valittu? valittu-vari "black"))
  ([valittu? valittu-vari ei-valittu-vari]
   (if valittu? valittu-vari ei-valittu-vari)))

(defn viivan-leveys
  ([valittu?] (viivan-leveys valittu? 8 nil))               ;; Nil tarkoittaa että käytetään openlayersissä määriteltyä defaulttia
  ([valittu? valittu-leveys] (viivan-leveys valittu? valittu-leveys nil))
  ([valittu? valittu-leveys ei-valittu-leveys]
   (if valittu? valittu-leveys ei-valittu-leveys)))

(defn- tack-ikoni
  ([asia ikoni valittu-fn?] (tack-ikoni asia ikoni valittu-fn? {}))
  ([asia ikoni valittu-fn? optiot]
   (tack-icon
     (merge
       {:scale (laske-skaala (valittu-fn? asia))
        :color (viivan-vari (valittu-fn? asia))
        :width (viivan-leveys (valittu-fn? asia))
        :img   ikoni}
       optiot)
     (:sijainti asia))))

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
    :else viivat))

(defn- maarittele-piste
  [valittu? merkki]
  (let [merkki (first (validoi-merkkiasetukset merkki))]
    (merge
      {:img (karttakuva +oletusikoni+)
       :scale (laske-skaala valittu?)}
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
                              :scale  (laske-skaala valittu?)
                              :img    (karttakuva +oletusikoni+)}
                             i)) merkit)}))

(defn- maarittele-feature
  "Funktio palauttaa mäpin, joka määrittelee featuren openlayersin haluamassa muodossa.
  Pakolliset parametrit:
  * Asia: kannasta haettu, piirrettävä asia. Tai pelkästään juttu joka sisältää geometriatiedot.
  * Valittu?: Onko asia valittu? true/false

  Valinnaiset (mutta tärkeät!) parametrit:
  * Merkit: Vektori mäppejä, mäppi, tai string, joka määrittelee featureen piirrettävät ikonit
    - Jos parametri on string, muutetaan se muotoon {:img string}. Jos siis piirrettävä
      asia on pistemäinen, riittää parametriksi pelkkä string, muuten voidaan mennä
      oletusasetuksilla
    - Reittimäisille asioille tällä parametrilla on enemmän merkitystä.
      Mäpille voi antaa seuraavia arvoja:
      -- paikka: alku, loppu, taitokset, tai vektori näitä. Mihin paikkoihin ikoni piirretään?
      -- tyyppi: nuoli tai merkki. Merkit kääntyvät viivan suunnan mukaan, merkit aina pystyssä.
      -- img: käytettävä ikoni
      -- Lisäksi openlayersin asetukset scale, zindex, anchor. Jätä mieluummin antamatta
  * Viivat: Vektori mäppejä tai mäppi, joka määrittelee viivan piirtotyylit
    - Käytä vektoria mäppejä, jos haluat tehdä kaksivärisiä viivoja. Voit esim. piirtää
      paksumman mustan viivan, ja sitten ohuemman sinisen viivan sen päälle.
    - Mäpillä on seuraavat arvot:
    -- Openlayersin color, width, zindex, dash, cap, join, miter
  * Pisteen merkki: Valinnainen parametri, johon pätee samat säännöt kuin em. merkkeihin.
    Jos tätä ei ole annettu, käytetään merkin piirtämiseen merkit vektorissa ensimmäisenä
    määriteltyä merkkiä (tai jos taas merkit on string tai pelkkä mäp, niin käytetään vaan sitä..).
    Käytännöllinen jos haluaa esim piirtää reitit nuoliviivoilla, ja pisteen ikonilla.

  Esimerkkejä:

  (maarittele-feature juttu val? (karttakuva 'mun-ikoni'))
    Juttu on todennäköisesti pistemäinen asia. Käytetään ikonia mun-ikoni. Jos juttu
    onkin reitillinen, käytetään reitin piirtämiseen puhtaasti oletusasetuksia.

  (maarittele-feature homma val? (karttakuva 'mun-homma) {:color (if (val? homma) 'green' 'yellow')})
    Samanlainen kuin edellinen, mutta on määritelty millä värillä halutaan piirtää reitti

  (maarittele-feature foo val? [{:paikka :loppu :img (karttakuva 'mun-foo')}
                                {:paikka :taitokset :img (karttakuva 'nuoli-foo')}]
                                [{:width 12 :color 'black'} {:width 6 :color 'red'}])
    Jos foo on pistemäinen, käytetään ikonia 'mun-foo'. Reitilliselle foolle piirretään
    kaksivärinen viiva, jonka taitoksissa on nuoli, ja loppupäässä 'mun-foo' ikoni.

  (maarittele-feature bar val? {:paikka [:alku :loppu :taitokset] :img (karttakuva 'nuoli-bar')}
                      nil (karttakuva 'mun-bar')
    Reitillinen bar piirretään käyttäen viivojen oletusasetuksia. Alku- ja loppupäähän sekä
    jokaiseen taitokseen piirretään nuoli. Jos bar onkin piste, käytetään vaan 'mun-bar' ikonia."
  ([asia valittu?] (maarittele-feature asia valittu? [{}] [{}]))
  ([asia valittu? merkit] (maarittele-feature asia valittu? merkit [{}]))
  ([asia valittu? merkit viivat] (maarittele-feature asia valittu? merkit viivat nil))
  ([asia valittu? merkit viivat pisteen-ikoni]
   (let [geo (or (:sijainti asia) asia)]
     (case (:type geo)
      :line
      (merge
        (maarittele-viiva valittu? merkit viivat)
        {:type   :viiva
         :points (:points geo)})

      :multiline
      (merge
        (maarittele-viiva valittu? merkit viivat)
        {:type   :viiva
         :points (mapcat :points (:lines geo))})

      :point
      (merge
        (maarittele-piste valittu? (or pisteen-ikoni merkit))
        {:type        :merkki
         :coordinates (:coordinates geo)})))))

(defmulti
  ^{:private true}
  asia-kartalle :tyyppi-kartalla)

(defn sisaltaako-kuittauksen? [ilmoitus kuittaustyyppi]
  (some #(= (:kuittaustyyppi %) kuittaustyyppi) (get-in ilmoitus [:kuittaukset])))

(defn ilmoituksen-tooltip [ilmoitus oletusteksti]
  (if (empty? (:kuittaukset ilmoitus))
    "Ei kuittauksia"

    (case (last (map :kuittaustyyppi (:kuittaukset ilmoitus)))
      :vastaanotto "Vastaanottokuittaus annettu"
      :vastaus "Vastauskuittaus annettu"
      :aloitus "Aloituskuittaus annettu"
      :lopetus "Lopetuskuittaus annettu"
      :muutos "Muutoskuittaus annettu"
      oletusteksti)))

(defmethod asia-kartalle :tiedoitus [ilmoitus valittu-fn?]
  (assoc ilmoitus
    :type :ilmoitus
    :nimi (ilmoituksen-tooltip ilmoitus "Tiedotus")
    :selite {:teksti "Tiedotus"
             :img    (karttakuva "tiedotus-tack-violetti")}
    :alue (maarittele-feature ilmoitus (valittu-fn? ilmoitus) (karttakuva "tiedotus-tack-violetti"))))


(defmethod asia-kartalle :kysely [ilmoitus valittu-fn?]
  (let [aloitettu? (sisaltaako-kuittauksen? ilmoitus :aloitus)
        lopetettu? (sisaltaako-kuittauksen? ilmoitus :lopetus)
        ikoni (karttakuva (cond
                            lopetettu? "kysely-tack-harmaa"
                            aloitettu? "kysely-tack-violetti"
                            :else "kysely-tack-punainen"))]
    (assoc ilmoitus
      :type :ilmoitus
      :nimi (ilmoituksen-tooltip ilmoitus "Kysely")
      :selite {:teksti (cond
                         aloitettu? "Kysely, aloitettu"
                         lopetettu? "Kysely, lopetettu"
                         :else "Kysely, ei aloituskuittausta.")
               :img    ikoni}
      :alue (maarittele-feature ilmoitus (valittu-fn? ilmoitus) ikoni))))

(defmethod asia-kartalle :toimenpidepyynto [ilmoitus valittu-fn?]
  (let [vastaanotettu? (sisaltaako-kuittauksen? ilmoitus :vastaanotettu)
        lopetettu? (sisaltaako-kuittauksen? ilmoitus :lopetus)
        ikoni (karttakuva (cond
                            lopetettu? "toimenpidepyynto-tack-harmaa"
                            vastaanotettu? "toimenpidepyynto-tack-violetti"
                            :else "toimenpidepyynto-tack-punainen"))]
    (assoc ilmoitus
      :type :ilmoitus
      :nimi (ilmoituksen-tooltip ilmoitus "Toimenpidepyyntö")
      :selite {:teksti (cond
                         vastaanotettu? "Toimenpidepyyntö, kuitattu"
                         lopetettu? "Toimenpidepyyntö, lopetettu"
                         :else "Toimenpidepyyntö, kuittaamaton")
               :img    ikoni}
      :alue (maarittele-feature ilmoitus (valittu-fn? ilmoitus) ikoni))))

(defn selvita-laadunseurannan-ikoni [ikonityyppi tekija]
  (karttakuva
    (case tekija
      :urakoitsija (str ikonityyppi "-urakoitsija-tack-violetti")
      :tilaaja (str ikonityyppi "-tilaaja-tack-violetti")
      :konsultti (str ikonityyppi "-konsultti-tack-violetti")
      (str ikonityyppi "-tack-violetti"))))

(defn selvita-tarkastuksen-ikoni [tekija]
  (selvita-laadunseurannan-ikoni "tarkastus" tekija))

(defn selvita-laatupoikkeaman-ikoni [tekija]
  (selvita-laadunseurannan-ikoni "laatupoikkeama" tekija))

(defn otsikko-tekijalla [etuliite laatupoikkeama]
  (str etuliite " (" (laatupoikkeamat/kuvaile-tekija (:tekija laatupoikkeama)) ")"))

(defmethod asia-kartalle :laatupoikkeama [laatupoikkeama valittu-fn?]
  (let [ikoni (selvita-laatupoikkeaman-ikoni (:tekija laatupoikkeama))
        otsikko (otsikko-tekijalla "Laatupoikkeama" laatupoikkeama)]
    (assoc laatupoikkeama
      :type :laatupoikkeama
      :nimi (or (:nimi laatupoikkeama) otsikko)
      :selite {:teksti otsikko
               :img    ikoni}
      :alue (maarittele-feature laatupoikkeama (valittu-fn? laatupoikkeama) ikoni))))

(defmethod asia-kartalle :tarkastus [tarkastus valittu-fn?]
  (let [ikoni (selvita-tarkastuksen-ikoni (:tekija tarkastus))]
    (assoc tarkastus
      :type :tarkastus
      :nimi (or (:nimi tarkastus)
                (otsikko-tekijalla (tarkastukset/+tarkastustyyppi->nimi+ (:tyyppi tarkastus)) tarkastus))
      :selite {:teksti (otsikko-tekijalla "Tarkastus" tarkastus)
               :img    ikoni}
      :alue (maarittele-feature tarkastus (valittu-fn? tarkastus) ikoni))))

(defmethod asia-kartalle :varustetoteuma [varustetoteuma valittu-fn?]
  (let [ikoni (karttakuva "varusteet-ja-laitteet-tack-violetti")]
    (assoc varustetoteuma
      :type :varustetoteuma
      :nimi (or (:selitys-kartalla varustetoteuma) "Varustetoteuma")
      :selite {:teksti "Varustetoteuma"
               :img    ikoni}
      :alue (maarittele-feature varustetoteuma (valittu-fn? varustetoteuma) ikoni))))

;; TODO Vaihda käyttöön harja.ui.kartta.varit.core
;; Tehdään samalla kun saadaan esim uudet nuoli-ikonit?
(def toteuma-varit-ja-nuolet
  [["rgb(255,0,0)" "punainen"]
   ["rgb(255,128,0)" "oranssi"]
   ["rgb(255,255,0)" "keltainen"]
   ["rgb(255,0,255)" "magenta"]
   ["rgb(0,255,0)" "vihrea"]
   ["rgb(0,255,128)" "turkoosi"]
   ["rgb(0,255,255)" "syaani"]
   ["rgb(0,128,255)" "sininen"]
   ["rgb(0,0,255)" "tummansininen"]
   ["rgb(128,0,255)" "violetti"]
   ["rgb(128,255,0)" "lime"]
   ["rgb(255,0,128)" "pinkki"]])

(let [varien-lkm (count toteuma-varit-ja-nuolet)]
  (defn tehtavan-vari-ja-nuoli [tehtavan-nimi]
    (nth toteuma-varit-ja-nuolet (Math/abs (rem (hash tehtavan-nimi) varien-lkm)))))

(defmethod asia-kartalle :toteuma [toteuma valittu-fn?]
  ;; Piirretään toteuma sen tieverkolle projisoidusta reitistä (ei yksittäisistä reittipisteistä)
  (when-let [reitti (:reitti toteuma)]
    (let [nimi (or
                 ;; toteumalla on suoraan nimi
                 (:nimi toteuma)
                 ;; tai nimi muodostetaan yhdistämällä tehtävien toimenpiteet
                 (str/join ", " (map :toimenpide (:tehtavat toteuma))))
          [vari nuoli] (tehtavan-vari-ja-nuoli nimi)]
      (assoc toteuma
        :type :toteuma
        :nimi nimi
        :selite {:teksti nimi
                 :vari   vari}
        :alue (maarittele-feature reitti (valittu-fn? toteuma)
                                  {:paikka [:taitokset :loppu]
                                   :tyyppi :nuoli
                                   :img    (karttakuva (str "nuoli-" nuoli))
                                   :scale  (if (valittu-fn? toteuma) 1.0 0.75)}
                                  {:color vari
                                   :width 5})))))

(defn paattele-turpon-ikoni [turpo]
  (let [kt (:korjaavattoimenpiteet turpo)]
    (if (empty? kt)
      [(karttakuva "turvallisuuspoikkeama-tack-oranssi") "Turvallisuuspoikkeama, avoin"]

      (if (some (comp nil? :suoritettu) kt)
        [(karttakuva "turvallisuuspoikkeama-tack-punainen") "Turvallisuuspoikkeama, ei korjauksia"]

        [(karttakuva "turvallisuuspoikkeama-tack-vihrea") "Turvallisuuspoikkeama, kaikki korjattu"]))))

(defmethod asia-kartalle :turvallisuuspoikkeama [tp valittu-fn?]
  (let [[ikoni selite] (paattele-turpon-ikoni tp)]
    (when (:sijainti tp)
      (assoc tp
        :type :turvallisuuspoikkeama
        :nimi (or (:nimi tp) "Turvallisuuspoikkeama")
        :selite {:teksti selite
                 :img    ikoni}
        :alue (maarittele-feature tp (valittu-fn? tp) ikoni)))))

(defn- paattele-yllapidon-ikoni-ja-viivan-vari
  [teksti asia]
  (let [[ikonin-vari viivan-vari] (if (:tila asia)
                                    (case (name (:tila asia))
                                      "aloitettu" ["keltainen" "rgb(255,255,0)"]
                                      "valmis" ["vihrea" "rgb(0,255,0)"]
                                      ["sininen" "rgb(0,128,255)"])

                                    ["sininen" "rgb(0,128,255)"])] ;; tila on nil jos vasta suunnitteilla
    [(karttakuva
       (str teksti "-tack-" ikonin-vari)) viivan-vari]))

(defn- paikkaus-paallystys [pt valittu-fn? tyo teksti]
  (let [[ikoni viiva] (paattele-yllapidon-ikoni-ja-viivan-vari tyo pt)]
    (assoc pt
      :nimi (or (:nimi pt) teksti)
      :selite {:teksti teksti
               :img    ikoni}
      :alue (maarittele-feature pt (valittu-fn? pt) ikoni {:color viiva
                                                      :width (if (:avoin? pt)
                                                               (if (valittu-fn? pt) 8 5)
                                                               (if (valittu-fn? pt) 8 nil))})))) ;; nil on default

(defmethod asia-kartalle :paallystys [pt valittu-fn?]
  (assoc (paikkaus-paallystys pt valittu-fn? "paallystystyo" "Päällystys")
    :type :paallystys))

(defmethod asia-kartalle :paikkaus [pt valittu-fn?]
  (assoc (paikkaus-paallystys pt valittu-fn? "paikkaustyo" "Paikkaus")
    :type :paikkaus))

(def ikonikartta {"auraus ja sohjonpoisto"          ["auraus-tai-sohjonpoisto" "Auraus tai sohjonpoisto"]
                  "suolaus"                         ["suolaus" "Suolaus"]
                  "pistehiekoitus"                  ["pistehiekoitus" "Pistehiekoitus"]
                  "linjahiekoitus"                  ["linjahiekoitus" "Linjahiekoitus"]
                  "lumivallien madaltaminen"        ["lumivallien-madaltaminen" "Lumivallien madaltaminen"]
                  "sulamisveden haittojen torjunta" ["sulamisveden haittojen torjunta" "Sulamisveden haittojen torjunta"]
                  "kelintarkastus"                  ["talvihoito" "Talvihoito"]

                  "tiestotarkastus"                 ["tiestotarkastus" "Tiestötarkastus"]
                  "koneellinen niitto"              ["koneellinen-niitto" "Koneellinen niitto"]
                  "koneellinen vesakonraivaus"      ["koneellinen-vesakonraivaus" "Koneellinen vesakonraivaus"]

                  "liikennemerkkien puhdistus"      ["liikennemerkkien-puhdistus" "Liikennemerkkien puhdistus"]

                  "sorateiden muokkaushoylays"      ["sorateiden-muokkaushoylays" "Sorateiden muokkaushöyläys"]
                  "sorateiden polynsidonta"         ["sorateiden-polynsidonta" "Sorateiden pölynsidonta"]
                  "sorateiden tasaus"               ["sorateiden-tasaus" "Sorateiden tasaus"]
                  "sorastus"                        ["sorastus" "Sorastus"]

                  "harjaus"                         ["harjaus" "Harjaus"]
                  "pinnan tasaus"                   ["pinnan-tasaus" "Pinnan tasaus"]
                  "paallysteiden paikkaus"          ["paallysteiden-paikkaus" "Päällysteiden paikkaus"]
                  "paallysteiden juotostyot"        ["paallysteiden-juotostyot" "Päällysteiden juotostyöt"]

                  "siltojen puhdistus"              ["siltojen-puhdistus" "Siltojen puhdistus"]

                  "l- ja p-alueiden puhdistus"      ["l-ja-p-alueiden-puhdistus" "L- ja P-alueiden puhdistus"]
                  "muu"                             ["tuntematon-koneen-tekema-tyo" "Muu"]})

(defn- paattele-tyokoneen-ikoni
  [tehtavat lahetetty valittu?]
  ;; TODO Miten päätellään järkevästi mikä ikoni työkoneelle näytetään?
  ;; Työkoneella voi olla useampi tehtävä. Miten se hoidetaan?
  ;; Voisi kuvitella että jotkut tehtävät ovat luonnostaan kiinnostavampia,
  ;; Esim jos talvella aurataan paljon mutta suolataan vain vähän (ja yleensä aurataan kun suolataan),
  ;; niin silloin pitäisi näyttää suolauksen ikoni silloin harvoin kun sitä tehdään.
  (let [tila (cond
               valittu? "valittu"
               (and lahetetty (t/before? lahetetty (t/now)) (> 20 (t/in-minutes (t/interval lahetetty (t/now)))))
               "sininen"
               :else "harmaa")
        [ikoni selite] (or (get ikonikartta (first tehtavat)) ["tuntematon-koneen-tekema-tyo" "Muu"])]
    [(karttakuva (str ikoni "-sticker-" tila)) (karttakuva (str ikoni "-sticker-sininen")) selite]))

(defn muunna-tyokoneen-suunta [kulma]
  (+ (- Math/PI)
     (* (/ Math/PI 180)
        kulma)))

(defmethod asia-kartalle :tyokone [tyokone valittu-fn?]
  (let [[img selite-img selite-teksti] (paattele-tyokoneen-ikoni
                                         (:tehtavat tyokone)
                                         (or (:lahetysaika tyokone) (:vastaanotettu tyokone))
                                         (valittu-fn? tyokone))]
    (assoc tyokone
      :type :tyokone
      :nimi (or (:nimi tyokone) (str/capitalize (name (:tyokonetyyppi tyokone))))
      :selite {:teksti selite-teksti
               :img    [(karttakuva "sticker-sininen") selite-img]}
      :alue (if-let [reitti (:reitti tyokone)]
              {:type      :sticker-icon-line
               :points    reitti
               :direction (muunna-tyokoneen-suunta (:suunta tyokone))
               :img       img}
              {:type        :sticker-icon
               :coordinates (:sijainti tyokone)
               :direction   (muunna-tyokoneen-suunta (:suunta tyokone))
               :img         img}))))

(defmethod asia-kartalle :default [asia _]
  (warn "Kartalla esitettävillä asioilla pitää olla :tyyppi-kartalla avain!, sain: " (pr-str asia))
  nil)

(defn- valittu-fn? [valittu tunniste asia]
  (let [tunniste (if (vector? tunniste) tunniste [tunniste])]
    (and
      (not (nil? valittu))
      (= (get-in asia tunniste) (get-in valittu tunniste)))))

(defn- tallenna-selitteet-xf [selitteet]
  (fn [xf]
    (fn
      ([] (xf))
      ([result] (xf result))
      ([result input]
       (when-let [selite (:selite input)]
         (vswap! selitteet conj selite))
       (xf result input)))))

;; Palauttaa joukon vektoreita joten kutsu (mapcat kartalla-xf @jutut)
;; Tämä sen takia, että aiemmin toteumille piirrettiin "itse toteuma" viivana, ja jokaiselle reittipisteelle
;; oma merkki. Tästä luovuttiin, mutta pidetään vielä kiinni siitä että täältä palautetaan joukko vektoreita,
;; jos vastaisuudessa tulee samankaltaisia tilanteita.
(defn kartalla-xf
  ([asia] (kartalla-xf asia nil nil))
  ([asia valittu] (kartalla-xf asia valittu [:id]))
  ([asia valittu tunniste] (asia-kartalle asia
                                          (if valittu
                                            (partial valittu-fn? valittu tunniste)
                                            (constantly false)))))

(defn kartalla-esitettavaan-muotoon
  ([asiat] (kartalla-esitettavaan-muotoon asiat nil nil))
  ([asiat valittu] (kartalla-esitettavaan-muotoon asiat valittu [:id]))
  ([asiat valittu tunniste]
   (kartalla-esitettavaan-muotoon asiat valittu tunniste nil))
  ([asiat valittu tunniste asia-xf]
   (let [extent (volatile! nil)
         selitteet (volatile! #{})]
     (with-meta
       (into []
             (comp (or asia-xf identity)
                   (map #(kartalla-xf % valittu (or tunniste [:id])))
                   (geo/laske-extent-xf extent)
                   (tallenna-selitteet-xf selitteet))
             asiat)
       {:extent    @extent
        :selitteet @selitteet}))))


