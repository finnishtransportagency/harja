(ns harja.ui.perus.simulaattori
  "Deklaratiivisen komponenttipuun pieni ja turvallinen validointikerros.

  Simulaattori hyväksyy vain rekisteröityjä komponentteja ja niiden dokumentoituja
  vanhempi-slot-suhteita. Se ei suorita agentin tuottamaa koodia."
  (:require [clojure.string :as str]
            [harja.ui.perus :as ui]
            [harja.ui.perus.rekisteri :as rekisteri]))

(def sivutilat
  "Simulaattorin yhteiset sivutilat ja niiden esitys viestinä."
  [{:id :normaali
    :nimi "Normaali"}
   {:id :lataus
    :nimi "Lataus"
    :tilaviesti {:tila :lataus :viesti "Ladataan sivua..."}
    :rooli (get ui/tilaviestin-roolit :lataus)}
   {:id :tyhja
    :nimi "Tyhjä"
    :tilaviesti {:tila :onnistui :viesti "Sivulla ei ole tuloksia."}
    :rooli (get ui/tilaviestin-roolit :onnistui)}
   {:id :virhe
    :nimi "Virhe"
    :tilaviesti {:tila :virhe :viesti "Sivun lataus epäonnistui."}
    :rooli (get ui/tilaviestin-roolit :virhe)}
   {:id :ei-oikeutta
    :nimi "Ei oikeutta"
    :tilaviesti {:tila :virhe :viesti "Ei käyttöoikeutta."}
    :rooli (get ui/tilaviestin-roolit :virhe)}])

(defn sivutila [id]
  (some #(when (= id (:id %)) %) sivutilat))

(def ^:private suojatut-asetukset
  "Renderer antaa nämä avaimet itse, joten syöte ei saa asettaa niitä."
  #{:id :arvo :vaihda-fn})

(defn komponentin-kuvaus [id]
  (rekisteri/hae id))

(defn- tagattu? [x]
  #?(:clj (instance? clojure.lang.TaggedLiteral x)
     :cljs false))

(defn- kielletty-tyyppi [x]
  (cond
    (fn? x) "funktio"
    (symbol? x) "symboli"
    (tagattu? x) "tagattu literaali"))

(defn- asetusvirheet [id kuvaus asetukset]
  (let [nimi (name id)]
    (for [[avain arvo] asetukset
          virhe (concat
                  (cond
                    (contains? suojatut-asetukset avain)
                    [(str nimi ": suojattu asetus " avain " ei ole sallittu")]

                    (not (contains? (rekisteri/simulaattori-asetukset kuvaus) avain))
                    [(str nimi ": tuntematon asetus " avain)])
                  (for [osa (tree-seq coll? seq arvo)
                        :let [tyyppi (kielletty-tyyppi osa)]
                        :when tyyppi]
                    (str nimi ": asetus " avain " on " tyyppi ", ei sallittu")))]
      virhe)))

(def ^:private simulaattorin-omistamat-asetustyypit
  #{:funktio :atomi :hiccup})

(defn- pakolliset-asetusvirheet [id kuvaus asetukset]
  (when (and (map? asetukset)
             (empty? (asetusvirheet id kuvaus asetukset)))
    (for [[avain asetustieto] (rekisteri/simulaattori-asetukset kuvaus)
          :when (:pakollinen? asetustieto)
          :when (not (contains? suojatut-asetukset avain))
          :when (not= :tyyppi avain)
          :when (not (contains? simulaattorin-omistamat-asetustyypit (:tyyppi asetustieto)))
          :when (not (contains? asetukset avain))]
      (str (name id) ": pakollinen asetus " avain " puuttuu"))))

(defn- slottivirheet [id kuvaus lapset]
  (let [lapsimaara (count lapset)]
    (for [[slotti {:keys [min max]}] (:simulaattori-slotit kuvaus)
          :when (or (and (some? min) (< lapsimaara min))
                    (and (some? max) (> lapsimaara max)))]
      (if (and (= 1 min) (= 1 max))
        (if (= slotti :kentta)
          (str (name id) ": vaatii täsmälleen yhden kenttäkomponentin")
          (str (name id) ": vaatii täsmälleen yhden " (name slotti) " komponentin"))
        (str (name id) ": slotin " slotti " lapsimäärä " lapsimaara " on virheellinen")))))

(def ^:private solutyypit
  "Perusgridin sarakkeen :solu-arvot, joita renderöijä osaa piirtää."
  #{:tila-indikaattori :linkki})

(defn- solutyyppivirheet [id asetukset]
  (let [nimi (name id)]
    (for [sarake (get asetukset :sarakkeet)
          :when (map? sarake)
          :let [solu (:solu sarake)]
          :when (and (some? solu)
                     (not (kielletty-tyyppi solu))
                     (not (contains? solutyypit solu)))]
      (str nimi ": tuntematon solutyyppi " (pr-str solu)))))

(def ^:private vetolaatikkotyypit
  "Perusgridin :vetolaatikko-arvot, joita renderöijä osaa piirtää."
  #{:rivin-tiedot})

(defn- vetolaatikkovirheet [id asetukset]
  (let [vetolaatikko (:vetolaatikko asetukset)]
    (when (and (some? vetolaatikko)
               (not (kielletty-tyyppi vetolaatikko))
               (not (contains? vetolaatikkotyypit vetolaatikko)))
      [(str (name id) ": tuntematon vetolaatikko " (pr-str vetolaatikko))])))

(def ^:private rivityypit
  "Perusgridin datarivien :rivityyppi-arvot, joita renderöijä osaa piirtää."
  #{:otsikko :yhteenveto})

(defn- rivityyppivirheet [id asetukset]
  (let [nimi (name id)]
    (mapcat (fn [rivi]
              (when (map? rivi)
                (let [rivityyppi (:rivityyppi rivi)]
                  (cond
                    (and (some? rivityyppi)
                         (not (kielletty-tyyppi rivityyppi))
                         (not (contains? rivityypit rivityyppi)))
                    [(str nimi ": tuntematon rivityyppi " (pr-str rivityyppi))]

                    (and (= :otsikko rivityyppi)
                         (or (not (string? (:teksti rivi)))
                             (str/blank? (:teksti rivi))))
                    [(str nimi ": :otsikko-riviltä puuttuu :teksti")]))))
            (get asetukset :rivit))))

(defn- sivutusvirheet [id asetukset]
  (let [sivuta (:sivuta asetukset)]
    (when (and (some? sivuta)
               (not (kielletty-tyyppi sivuta))
               (or (not (integer? sivuta))
                   (not (pos? sivuta))))
      [(str (name id) ": :sivuta pitää olla positiivinen kokonaisluku")])))

(defn- tarkista-solmu [solmu vanhempi]
  (if-not (map? solmu)
    [(str "Solmun pitää olla map: " (pr-str solmu))]
    (let [{:keys [komponentti asetukset lapset]} solmu
          kuvaus (komponentin-kuvaus komponentti)
          sallitut (or (:sallitut-vanhemmat kuvaus) #{})
          asetukset-kunnossa? (or (nil? asetukset) (map? asetukset))
          lapset-kunnossa? (or (nil? lapset) (sequential? lapset))]
      (concat
        (when-not kuvaus
          [(str "Tuntematon komponentti: " komponentti)])
        (when (and kuvaus (not (contains? sallitut vanhempi)))
          [(str komponentti ": vanhempi=" (pr-str vanhempi) " ei ole sallittu")])
        (when-not asetukset-kunnossa?
          [(str komponentti ": asetukset ei ole map")])
        (when-not lapset-kunnossa?
          [(str komponentti ": lapset ei ole lista")])
        (when (and kuvaus lapset-kunnossa?)
          (slottivirheet komponentti kuvaus lapset))
        (when (and (= :kenttaryhma komponentti)
                   asetukset-kunnossa?
                   (str/blank? (:data-cy asetukset)))
          ["kenttaryhma: simulaattorissa vaaditaan :data-cy elementti-id:n muodostamiseen"])
        (when (and kuvaus asetukset-kunnossa?)
          (concat (asetusvirheet komponentti kuvaus asetukset)
                  (pakolliset-asetusvirheet komponentti kuvaus asetukset)))
        (when (and (= :perusgrid komponentti) (map? asetukset))
          (concat (solutyyppivirheet komponentti asetukset)
                  (vetolaatikkovirheet komponentti asetukset)
                  (rivityyppivirheet komponentti asetukset)
                  (sivutusvirheet komponentti asetukset)))
        (when lapset-kunnossa?
          (mapcat #(tarkista-solmu % komponentti) lapset))))))

(defn virheet
  "Palauttaa deklaratiivisen sivupuun virheet tekstinä."
  [puu]
  (vec (mapcat #(tarkista-solmu % :sivu) puu)))

(defn kelvollinen?
  "Kertoo, voiko deklaratiivisen puun antaa rendererille."
  [puu]
  (empty? (virheet puu)))
