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

(defn- tapahtuman-arvo [tapahtuma]
  ;; Clj-haara mahdollistaa komponenttien hiccup-tason testit JVM:llä.
  #?(:cljs (.. tapahtuma -target -value)
     :clj (get-in tapahtuma [:target :value])))

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

(defn valintaryhma
  "Natiivi select-kenttä labelilla, ohjeella ja virheellä.

  `:vaihtoehdot` on vektori `{:arvo ... :nimi ...}`. Arvo voi olla mikä tahansa arvo
  (esim. keyword), ja `:vaihda-fn` saa valitun arvon sellaisenaan. `:tyhja-valinta` lisää
  tyhjän vaihtoehdon, jonka valinta välittää `nil`:n."
  [{:keys [otsikko id vaihtoehdot arvo vaihda-fn tyhja-valinta ohje virhe pakollinen?
           disabled? data-cy luokka] :as asetukset}]
  (let [arvot (into {}
                (map-indexed (fn [indeksi {:keys [arvo]}] [(str indeksi) arvo]))
                vaihtoehdot)
        valittu (or (some (fn [[avain vaihtoehdon-arvo]]
                            (when (= vaihtoehdon-arvo arvo) avain))
                      arvot)
                  "")
        attribuutit (dissoc (kentan-attribuutit asetukset) :disabled?)]
    [:div {:class (luokat "ui-valintaryhma" luokka)
           :data-cy (or data-cy id)}
     [:label {:for id} (otsikko-ja-pakollisuus otsikko pakollinen?)]
     [:select
      (cond-> (assoc attribuutit
                :value valittu
                :data-cy (str (or data-cy id) "-valinta")
                :on-change #(vaihda-fn (get arvot (tapahtuman-arvo %))))
        disabled? (assoc :disabled true))
      (when tyhja-valinta
        [:option {:value ""} tyhja-valinta])
      (map-indexed
        (fn [indeksi {:keys [nimi]}]
          ^{:key indeksi}
          [:option {:value (str indeksi)} nimi])
        vaihtoehdot)]
     (when ohje
       [:p.ui-ohje {:id (str id "-ohje")} ohje])
     (when virhe
       [:p.ui-virhe {:id (str id "-virhe") :role "alert"} virhe])]))

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
