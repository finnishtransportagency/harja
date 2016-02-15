(ns harja.ui.kartta.esitettavat-asiat
  (:require [harja.pvm :as pvm]
            [clojure.string :as str]
            [harja.loki :refer [log warn] :refer-macros [mittaa-aika]]
            [cljs-time.core :as t]
            [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat :as laatupoikkeamat]
            [harja.tiedot.urakka.laadunseuranta.tarkastukset :as tarkastukset]
            [harja.ui.dom :refer [karttakuva]]
            [harja.geo :as geo]
            [harja.ui.kartta.varit.puhtaat :as varit]
            [harja.ui.kartta.varit.alpha :as alpha]))

(def +valitun-skaala+ 1.5)
(def +normaali-skaala+ 1)
(def +zindex+ 4)
(def +oletusikoni+ "turvallisuuspoikkeama-tack-vihrea") ;; TODO vaihda
(def +normaali-leveys+ nil) ;;Openlayers default
(def +valitun-leveys+ 8)
(def +normaali-vari+ "black")
(def +valitun-vari+ "blue")

(defn- laske-skaala [valittu?]
  (if valittu? +valitun-skaala+ +normaali-skaala+))

(defn viivan-vari
  ([valittu?] (viivan-vari valittu? +valitun-vari+ +normaali-vari+))
  ([valittu? valittu-vari] (viivan-vari valittu? valittu-vari +normaali-vari+))
  ([valittu? valittu-vari ei-valittu-vari]
   (if valittu? valittu-vari ei-valittu-vari)))

(defn viivan-leveys
  ([valittu?] (viivan-leveys valittu? +valitun-leveys+ +normaali-leveys+))
  ([valittu? valittu-leveys] (viivan-leveys valittu? valittu-leveys +normaali-leveys+))
  ([valittu? valittu-leveys ei-valittu-leveys]
   (if valittu? valittu-leveys ei-valittu-leveys)))

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
                              :scale  (laske-skaala valittu?)}
                             i)) merkit)}))

(defn maarittele-feature
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
      -- paikka: :alku, :loppu, :taitokset, tai vektori näitä. Mihin paikkoihin ikoni piirretään?
      -- tyyppi: :nuoli tai :merkki. Merkit kääntyvät viivan suunnan mukaan, merkit aina pystyssä.
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
   (let [geo (or (:sijainti asia) asia)
         tyyppi (:type geo)
         koordinaatit (or (:coordinates geo) (:points geo) (mapcat :points (:lines geo)))]
     (cond
       ;; Näyttää siltä että joskus saattaa löytyä LINESTRINGejä, joilla on vain yksi piste
       ;; Ei tietoa onko tämä virheellistä testidataa vai real world case, mutta varaudutaan siihen joka tapauksessa
       (or (= :point tyyppi) (= 1 (count koordinaatit)))
       (when merkit
         (merge
          (maarittele-piste valittu? (or pisteen-ikoni merkit))
          {:type        :merkki
           :coordinates (flatten koordinaatit)}))           ;; [x y] -> [x y] && [[x y]] -> [x y]

       (= :line tyyppi)
       (merge
         (maarittele-viiva valittu? merkit viivat)
         {:type   :viiva
          :points koordinaatit})

       (= :multiline tyyppi)
       (merge
         (maarittele-viiva valittu? merkit viivat)
         {:type   :viiva
          :points koordinaatit})))))

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
      :alue (if (:ok? tarkastus)
              (maarittele-feature tarkastus (valittu-fn? tarkastus) nil {:color alpha/vaaleanharmaa})
              (maarittele-feature tarkastus (valittu-fn? tarkastus) ikoni)))))

(defmethod asia-kartalle :varustetoteuma [varustetoteuma valittu-fn?]
  (let [ikoni (karttakuva "varusteet-ja-laitteet-tack-violetti")]
    (assoc varustetoteuma
      :type :varustetoteuma
      :nimi (or (:selitys-kartalla varustetoteuma) "Varustetoteuma")
      :selite {:teksti "Varustetoteuma"
               :img    ikoni}
      :alue (maarittele-feature varustetoteuma (valittu-fn? varustetoteuma) ikoni))))

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

(def toteuma-varit-ja-nuolet
  [[{:color varit/punainen} "nuoli-punainen"]
   [{:color varit/oranssi} "nuoli-oranssi"]
   [{:color varit/keltainen} "nuoli-keltainen"]
   [{:color varit/magenta} "nuoli-magenta"]
   [{:color varit/vihrea} "nuoli-vihrea"]
   [{:color varit/turkoosi} "nuoli-turkoosi"]
   [{:color varit/syaani} "nuoli-syaani"]
   [{:color varit/sininen} "nuoli-sininen"]
   [{:color varit/tummansininen} "nuoli-tummansininen"]
   [{:color varit/violetti} "nuoli-violetti"]
   [{:color varit/lime} "nuoli-lime"]
   [{:color varit/pinkki} "nuoli-pinkki"]])

(let [varien-lkm (count toteuma-varit-ja-nuolet)]
  (defn generoitu-tyyli [tehtavan-nimi]
    (log "WARN: "tehtavan-nimi" määritys puuttuu esitettävistä asioista, generoidaan tyyli koneellisesti!")
    (nth toteuma-varit-ja-nuolet (Math/abs (rem (hash tehtavan-nimi) varien-lkm)))))

(def tehtavien-nimet
  {"AURAUS JA SOHJONPOISTO"          "Auraus tai sohjonpoisto"
   "SUOLAUS"                         "Suolaus"
   "PISTEHIEKOITUS"                  "Pistehiekoitus"
   "LINJAHIEKOITUS"                  "Linjahiekoitus"
   "LUMIVALLIEN MADALTAMINEN"        "Lumivallien madaltaminen"
   "SULAMISVEDEN HAITTOJEN TORJUNTA" "Sulamisveden haittojen torjunta"
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
   "PINNAN TASAUS"                   "Pinnan tasaus"
   "PAALLYSTEIDEN PAIKKAUS"          "Päällysteiden paikkaus"
   "PAALLYSTEIDEN JUOTOSTYOT"        "Päällysteiden juotostyöt"

   "SILTOJEN PUHDISTUS"              "Siltojen puhdistus"

   "L- JA P-ALUEIDEN PUHDISTUS"      "L- ja P-alueiden puhdistus"
   "MUU"                             "Muu"})

(defn tehtavan-nimi [tehtavat]
  (str/join ", " (map #(or (get tehtavien-nimet (str/upper-case %)) %) tehtavat)))

(def auraus-tasaus-ja-kolmas [[{:color varit/punainen :width 10} {:color varit/sininen :width 6}] "nuoli-punainen"])
(def auraus-ja-hiekoitus [])
(def auraus-ja-suolaus [])
(defn viiva-mustalla-rajalla [vari]
  [{:color varit/musta :width 8} {:color vari :width 5}])
;; Mäppi muotoa
;; {#{"tehtävän nimi"} ["viivan väri" "nuolen tiedoston nimi"]
;;  #{"foo"}           [{:color "väri"} "tiedoston nimi"]
;;  #{"bar"}           [[{:color "b" :width 10} {:color "r" :width 6}] "tiedoston nimi"]}
;; Avaimet ovat settejä, koska yhdistelmätoimenpiteille halutaan tehdä omat suunnitellut tyylit.
;; Näissä tapauksissa tehtävät tulevat vektorissa, eikä tietenkään kannata luottaa järjestykseen
(def tehtavien-varit
  {;; yhdistelmätoimenpiteet
   #{"AURAUS JA SOHJONPOISTO" "PINNAN TASAUS" "PISTEHIEKOITUS"} auraus-tasaus-ja-kolmas
   #{"AURAUS JA SOHJONPOISTO" "PINNAN TASAUS" "LINJAHIEKOITUS"} auraus-tasaus-ja-kolmas
   #{"AURAUS JA SOHJONPOISTO" "PINNAN TASAUS" "SUOLAUS"}        auraus-tasaus-ja-kolmas
   #{"AURAUS JA SOHJONPOISTO" "PINNAN TASAUS" "LIUOSSUOLAUS"}   auraus-tasaus-ja-kolmas
   #{"AURAUS JA SOHJONPOISTO" "PISTEHIEKOITUS"}                 auraus-ja-hiekoitus
   #{"AURAUS JA SOHJONPOISTO" "LINJAHIEKOITUS"}                 auraus-ja-hiekoitus
   #{"AURAUS JA SOHJONPOISTO" "SUOLAUS"}                        auraus-ja-suolaus
   #{"AURAUS JA SOHJONPOISTO" "LIUOSSUOLAUS"}                   auraus-ja-suolaus
   ;; tilannekuva/talvihoito
   #{"AURAUS JA SOHJONPOISTO"}                                  [(viiva-mustalla-rajalla varit/oranssi) "nuoli-oranssi"]
   #{"SUOLAUS"}                                                 [(viiva-mustalla-rajalla varit/sininen) "nuoli-sininen"]
   #{"LIUOSSUOLAUS"}                                            [(viiva-mustalla-rajalla varit/tummansininen) "nuoli-tummansininen"]
   #{"PISTEHIEKOITUS"}                                          [(viiva-mustalla-rajalla varit/pinkki) "nuoli-pinkki"]
   #{"LINJAHIEKOITUS"}                                          [(viiva-mustalla-rajalla varit/magenta) "nuoli-magenta"]
   #{"PINNAN TASAUS"}                                           [(viiva-mustalla-rajalla varit/violetti) "nuoli-violetti"]
   #{"LUMIVALLIEN MADALTAMINEN"}                                []
   #{"SULAMISVEDEN HAITTOJEN TORJUNTA"}                         []
   #{"AURAUSVIITOITUS JA KINOSTIMET"}                           []
   #{"LUMENSIIRTO"}                                             []
   #{"PAANNEJAAN POISTO"}                                       []
   #{"MUU"}                                                     []
   ;; tilannekuva/kesähoito
   #{"SORATEIDEN POLYNSIDONTA"}                                 []
   #{"SORASTUS"}                                                []
   #{"SORATEIDEN TASAUS"}                                       []
   #{"SORATEIDEN MUOKKAUSHOYLAYS"}                              []
   #{"PAALLYSTEIDEN PAIKKAUS"}                                  []
   #{"PAALLYSTEIDEN JUOTOSTYOT"}                                []
   #{"KONEELLINEN NIITTO"}                                      []
   #{"KONEELLINEN VESAKONRAIVAUS"}                              []
   #{"HARJAUS"}                                                 []
   #{"LIIKENNEMERKKIEN PUHDISTUS"}                              []
   #{"L- JA P-ALUEIDEN PUHDISTUS"}                              []
   #{"SILTOJEN PUHDISTUS"}                                      []})

(defn- maaritelty-tyyli [tehtava]
  (let [koodi (into #{} (map str/upper-case tehtava))
        tulos (get tehtavien-varit koodi)]
    (when-not (empty? tulos) tulos)))

(defn- tehtavan-viivat-ja-nuolitiedosto
  "Hakee toimenpiteelle esitysasetukset joko yllä määritellystä mäpistä, tai generoi sellaisen itse."
  [tehtava valittu?]
  (let [;; Prosessoi asetukset siten, että stringinä määritellystä nuoli-ikonista tehdään :nuoli,
        ;; ja jos viivoille on määritelty leveydet (monivärinen nuoli), niin kasvatetaan niitä jos toteuma
        ;; on valittu.
        viimeistele-asetukset (fn [[viivat nuoli :as asetukset]]
                                [(mapv
                                    ;; Kasvata jokaisen viivan määriteltyä leveyttä kahdella jos toteuma on valittu.
                                    ;; Jos leveyttä ei ole annettu, niin mennään oletusasetuksilla, jotka ottavat
                                    ;; valinnan jo huomioon.
                                    #(if (and (:width %) valittu?) (assoc % :width (+ 2 (:width %))) %)
                                    viivat)
                                  (karttakuva nuoli)])
        validoi-viiva (fn [[viivat nuoli :as asetukset]]
                        (cond
                          (string? viivat) [[{:color viivat}] nuoli]
                          (map? viivat) [[viivat] nuoli]
                          :else asetukset))]
    (-> (or (maaritelty-tyyli tehtava) (generoitu-tyyli (str/join ", " tehtava)))
        validoi-viiva
        viimeistele-asetukset)))

(defn- viivojen-varit-leveimmasta-kapeimpaan [viivat]
  (let [sortattu (sort-by :width >
                          ;; Täydennä väliaikaisesti tänne oletusarvot,
                          ;; muuten leveysvertailu failaa, ja halutaanhan toki palauttaa
                          ;; jokin väri myös jutuille, joille sellaista ei ole (vielä!) määritelty.
                          (mapv #(assoc % :width (or (:width %) +normaali-leveys+)
                                          :color (or (:color %) +normaali-vari+))
                                viivat))]
    (mapv :color sortattu)))

(defmethod asia-kartalle :toteuma [toteuma valittu-fn?]
  ;; Piirretään toteuma sen tieverkolle projisoidusta reitistä (ei yksittäisistä reittipisteistä)
  (when-let [reitti (:reitti toteuma)]
    (let [toimenpiteet (map :toimenpide (:tehtavat toteuma))
          nimi (or
                 ;; toteumalla on suoraan nimi
                 (:nimi toteuma)
                 ;; tai nimi muodostetaan yhdistämällä tehtävien toimenpiteet
                 (tehtavan-nimi toimenpiteet))
          [viivat nuolen-kuva] (tehtavan-viivat-ja-nuolitiedosto toimenpiteet (valittu-fn? toteuma))]
      (assoc toteuma
        :type :toteuma
        :nimi nimi
        :selite {:teksti nimi
                 :vari   (last (viivojen-varit-leveimmasta-kapeimpaan viivat))} ; TODO selitteiden värilaatikko tukemaan montaa väriä
        :alue (maarittele-feature reitti (valittu-fn? toteuma)
                                  {:paikka [:taitokset :loppu]
                                   :tyyppi :nuoli
                                   :img    nuolen-kuva}
                                  viivat)))))

(defn muunna-tyokoneen-suunta [kulma]
  (+ (- Math/PI)
     (* (/ Math/PI 180)
        kulma)))

(defmethod asia-kartalle :tyokone [tyokone valittu-fn?]
  (let [selite-teksti (tehtavan-nimi (:tehtavat tyokone))
        [viivat nuolen-kuva] (tehtavan-viivat-ja-nuolitiedosto (:tehtavat tyokone) (valittu-fn? tyokone))
        paikka {:sijainti {:type (if (:reitti tyokone) :line :point)}}
        paikka (if (:reitti tyokone)
                 (assoc-in paikka [:sijainti :points] (:reitti tyokone))
                 (assoc-in paikka [:sijainti :coordinates] (:sijainti tyokone)))]
    (assoc tyokone
      :type :tyokone
      :nimi (or (:nimi tyokone) (str/capitalize (name (:tyokonetyyppi tyokone))))
      :selite {:teksti selite-teksti
               :vari   (last (viivojen-varit-leveimmasta-kapeimpaan viivat))} ; TODO selitteiden värilaatikko tukemaan montaa väriä
      :alue (maarittele-feature paikka (valittu-fn? tyokone)
                                {:paikka   :loppu
                                 :tyyppi   :nuoli
                                 :img      nuolen-kuva
                                 :rotation (muunna-tyokoneen-suunta (:suunta tyokone))}
                                viivat))))

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


