(ns harja.ui.perus
  "Harjan shared-komponentit, joita käyttöpaikat voivat käyttää ilman omaa Bootstrap-markupia.

  Komponentti omistaa DOM-rakenteen, id-yhteydet (label, ohje, virhe) ja saavutettavuusroolit.
  Käyttöpaikka omistaa datan, arvon, muutosten käsittelyn ja domain-päätökset.

  Yhteinen sopimus:
  - `:id` on käyttöpaikan antama vakaa id. Komponentti johtaa siitä labelin `for`-arvon sekä
    ohjeen ja virheen id:t. Id:tä ei generoida renderöinnissä.
  - `:data-cy` on valinnainen. Oletuksena käytetään `:id`:tä.
  - `:luokka` lisää käyttöpaikan luokan juurielementille.
  - Ulkoreunat (marginaalit) kuuluvat vanhemmalle, esim. `pino`-komponentille.

  Komponenttien tarkastelusivu ja rekisteri: harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu-nakyma"
  (:require [clojure.string :as str]))

(def tilaviestin-roolit
  "Tilaviestin tila -> ARIA-rooli. Vain virhe keskeyttää ruudunlukijan (alert)."
  {:lataus "status"
   :onnistui "status"
   :virhe "alert"})

(defn- luokat [& luokat]
  (str/join " " (remove str/blank? luokat)))

(defn- kentan-attribuutit
  "Attribuutit, jotka komponentti antaa kentälle: id ja kuvaus-/virhe-yhteydet."
  [{:keys [id ohje virhe pakollinen? disabled?]}]
  (let [kuvaukset (cond-> []
                    ohje (conj (str id "-ohje"))
                    virhe (conj (str id "-virhe")))]
    (cond-> {:id id}
      (seq kuvaukset) (assoc :aria-describedby (str/join " " kuvaukset))
      virhe (assoc :aria-invalid true)
      pakollinen? (assoc :aria-required true)
      disabled? (assoc :disabled? true))))

(defn- kentan-sisalto
  "Kenttä voi olla valmis hiccup tai funktio, joka saa komponentin johtamat attribuutit."
  [kentta attribuutit]
  (if (fn? kentta)
    (kentta attribuutit)
    kentta))

(defn- otsikko-ja-pakollisuus [otsikko pakollinen?]
  [:<>
   otsikko
   (when pakollinen?
     [:span.ui-pakollinen {:aria-hidden true} " *"])])

(defn pino
  "Pystysuuntainen asettelu, joka omistaa lasten väliset välit."
  [{:keys [data-cy luokka]} & lapset]
  (into [:div {:class (luokat "ui-pino" luokka)
               :data-cy data-cy}]
    lapset))

(def ^:private layout-valit #{:pieni :keski :suuri})
(def ^:private vaaka-tasaukset #{:alku :keskelle :loppu :valiin})
(def ^:private vaaka-ristitasaukset #{:alku :keskelle :loppu :venyta})

(defn vaaka
  "Vaakasuuntainen asettelu, joka omistaa lasten välit, rivityksen ja tasaukset.

  Väli on nimetty tokeni `:pieni`, `:keski` tai `:suuri`."
  [{:keys [data-cy luokka vali rivita? tasaus ristitasaus]
    :or {vali :keski rivita? false tasaus :alku ristitasaus :keskelle}} & lapset]
  {:pre [(layout-valit vali)
         (boolean? rivita?)
         (vaaka-tasaukset tasaus)
         (vaaka-ristitasaukset ristitasaus)]}
  (into [:div {:class (luokat "ui-vaaka"
                         (str "ui-vaaka-vali-" (name vali))
                         (when rivita? "ui-vaaka-rivita")
                         (str "ui-vaaka-tasaus-" (name tasaus))
                         (str "ui-vaaka-ristitasaus-" (name ristitasaus))
                         luokka)
               :data-cy data-cy}]
    lapset))

(defn sailio
  "Rajaa sivusisällön leveyden ja vaakasuuntaisen sisäreunan nimettyjen tokenien avulla."
  [{:keys [data-cy luokka leveys sisareuna]
    :or {leveys :suuri sisareuna :keski}} & sisalto]
  {:pre [(layout-valit leveys)
         (layout-valit sisareuna)]}
  (into [:div {:class (luokat "ui-sailio"
                         (str "ui-sailio-leveys-" (name leveys))
                         (str "ui-sailio-sisareuna-" (name sisareuna))
                         luokka)
               :data-cy data-cy}]
    sisalto))

(defn osio
  "Otsikollinen, semanttinen sisältöalue. Käyttöpaikka antaa vakaan id:n."
  [{:keys [id otsikko otsikkotaso vali data-cy luokka]
    :or {otsikkotaso 2 vali :keski}} & sisalto]
  {:pre [(and (string? id) (not (str/blank? id)))
         (some? otsikko)
         (and (integer? otsikkotaso) (<= 1 otsikkotaso 6))
         (layout-valit vali)]}
  (let [otsikko-id (str id "-otsikko")
        otsikkotagi (keyword (str "h" otsikkotaso))]
    [:section {:id id
               :class (luokat "ui-osio" (str "ui-osio-vali-" (name vali)) luokka)
               :aria-labelledby otsikko-id
               :data-cy (or data-cy id)}
     [otsikkotagi {:id otsikko-id :class "ui-osio-otsikko"} otsikko]
     (into [:div {:class "ui-osio-sisalto"}] sisalto)]))

(defn ruudukko
  "Responsiivinen 1–4-palstainen grid, joka pienenee yhdelle palstalle kapealla näytöllä."
  [{:keys [data-cy luokka palstoja vali]
    :or {palstoja 2 vali :keski}} & lapset]
  {:pre [(and (integer? palstoja) (<= 1 palstoja 4))
         (layout-valit vali)]}
  (into [:div {:class (luokat "ui-ruudukko"
                         (str "ui-ruudukko-palstoja-" palstoja)
                         (str "ui-ruudukko-vali-" (name vali))
                         luokka)
               :data-cy data-cy}]
    lapset))

(defn suodatinrivi
  "Rivittyvä rivi suodattimille."
  [{:keys [data-cy luokka]} & suodattimet]
  (into [:div {:class (luokat "ui-suodatinrivi" luokka)
               :data-cy data-cy}]
    suodattimet))

(defn suodatin
  "Suodattimen label ja kenttä. Otsikko on valinnainen, esim. checkboxilla label on kentän oma.

  Kenttä on hiccup tai funktio, joka saa `{:id ...}`."
  [{:keys [otsikko id data-cy luokka]} kentta]
  [:div {:class (luokat "ui-suodatin" luokka)
         :data-cy (or data-cy id)}
   (when otsikko
     [:label {:for id} otsikko])
   (kentan-sisalto kentta {:id id})])

(defn kenttaryhma
  "Ryhmittelee labelin, kentän sekä valinnaisen ohjeen ja virheen.

  Kenttä on hiccup tai funktio, joka saa attribuutit `:id`, `:aria-describedby`, `:aria-invalid`,
  `:aria-required` ja `:disabled?`. Kentän pitää käyttää annettua id:tä, jotta label ja ohje
  yhdistyvät siihen."
  [{:keys [otsikko id ohje virhe pakollinen? data-cy luokka] :as asetukset} kentta]
  [:div {:class (luokat "ui-kenttaryhma" luokka)
         :data-cy (or data-cy id)}
   [:label {:for id} (otsikko-ja-pakollisuus otsikko pakollinen?)]
   (kentan-sisalto kentta (kentan-attribuutit asetukset))
   (when ohje
     [:p.ui-ohje {:id (str id "-ohje")} ohje])
   (when virhe
     [:p.ui-virhe {:id (str id "-virhe") :role "alert"} virhe])])

(defn toimintoryhma
  "Ryhmittelee käyttöpaikan toimintopainikkeet."
  [{:keys [data-cy luokka]} & toiminnot]
  (into [:div {:class (luokat "ui-toimintoryhma" luokka)
               :data-cy data-cy}]
    toiminnot))

(defn tilaviesti
  "Lataus-, onnistumis- tai virhetila käyttöpaikan antamalla viestillä.

  Säiliö renderöidään aina, jotta ruudunlukija huomaa viestin muuttumisen. Tyhjä viesti ei näy."
  [{:keys [tila viesti data-cy luokka]}]
  {:pre [(contains? tilaviestin-roolit tila)]}
  [:p {:class (luokat "ui-tilaviesti" (str "ui-tilaviesti-" (name tila)) luokka)
       :role (tilaviestin-roolit tila)
       :data-cy data-cy}
   viesti])
