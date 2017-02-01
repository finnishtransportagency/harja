(ns harja.tiedot.kartta
  (:require [harja.geo :as geo]
            [cljs.core.async :refer [timeout <! >! chan] :as async]
            [reagent.core :refer [atom]]
            [harja.tiedot.navigaatio :as nav]
            [harja.views.kartta.tasot :as tasot]
            [harja.loki :refer [log]]
            [harja.ui.kartta.apurit :refer [+koko-suomi-extent+]]
            [harja.ui.openlayers :as openlayers])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [reagent.ratom :refer [reaction]]))

(def pida-geometria-nakyvilla-oletusarvo true)
(defonce pida-geometriat-nakyvilla? (atom pida-geometria-nakyvilla-oletusarvo))

(defonce nayta-infopaneeli? (atom false))
(defonce infopaneeli-nakyvissa? (reaction (and @nayta-infopaneeli? @nav/kartta-nakyvissa?)))
(defonce infopaneelin-linkkifunktiot (atom nil))

(defn keskita-kartta-alueeseen! [alue]
  (reset! nav/kartan-extent alue))

(defn kasittele-infopaneelin-linkit!
  "Infopaneelin skeemat saattavat sisältää 'linkkejä', jotka käsitellään
  (tai ei käsitellä) näkymäkohtaisesti. Esimerkiksi toteumanäkymässä voidaan haluta
  mahdollistaa toteuman avaaminen lomakkeeseen infopaneelin kautta.
  Funktio ottaa parametriksi mäpin, jossa avaimet ovat :tyyppejä-kartalla
  (katso harja.ui.kartta.asioiden-tiedot), ja arvot ovat mappejä, jotka
  sisältävät avaimet :toiminto ja :teksti. :toiminto -avaimen arvo on
  funktio, joka saa parametrinaan valitun asian datan."
  [asetukset]
  (assert (or (nil? asetukset) (map? asetukset)) "Infopaneelin linkkiasetusten pitää olla mäppi tai nil")
  (reset! infopaneelin-linkkifunktiot asetukset))

(defn zoomaa-valittuun-hallintayksikkoon-tai-urakkaan
  []
  (let [v-hal @nav/valittu-hallintayksikko
        v-ur @nav/valittu-urakka]
    (if-let [alue (and v-ur (:alue v-ur))]
      (keskita-kartta-alueeseen! (geo/extent alue))
      (if-let [alue (and v-hal (:alue v-hal))]
        (keskita-kartta-alueeseen! (geo/extent alue))
        (keskita-kartta-alueeseen! +koko-suomi-extent+)))))

(defn zoomaa-geometrioihin
  "Zoomaa kartan joko kartalla näkyviin geometrioihin, tai jos kartalla ei ole geometrioita,
  valittuun hallintayksikköön tai urakkaan"
  []
  (when @pida-geometriat-nakyvilla?
    ;; Haetaan kaikkien tasojen extentit ja yhdistetään ne laajentamalla
    ;; extentiä siten, että kaikki mahtuvat.
    ;; Jos extentiä tasoista ei ole, zoomataan urakkaan tai hallintayksikköön.
    (let [extent (reduce geo/yhdista-extent
                         (keep #(-> % meta :extent) (vals @tasot/geometriat-kartalle)))
          extentin-margin-metreina geo/pisteen-extent-laajennus]
      (log "EXTENT TASOISTA: " (pr-str extent))
      (if extent
        (keskita-kartta-alueeseen! (geo/laajenna-extent extent extentin-margin-metreina))
        (zoomaa-valittuun-hallintayksikkoon-tai-urakkaan)))))

(defn kuuntele-valittua! [atomi]
  (add-watch atomi :kartan-valittu-kuuntelija (fn [_ _ _ uusi]
                                                (when-not uusi
                                                  (zoomaa-geometrioihin))))
  #(remove-watch atomi :kartan-valittu-kuuntelija))

(def kartan-ohjelaatikko-sisalto (atom nil))

(defn aseta-ohjelaatikon-sisalto! [uusi-sisalto]
  (reset! kartan-ohjelaatikko-sisalto uusi-sisalto))

(defn tyhjenna-ohjelaatikko! []
  (reset! kartan-ohjelaatikko-sisalto nil))

(def aseta-tooltip! openlayers/aseta-tooltip!)

(def aseta-kursori! openlayers/aseta-kursori!)

(def aseta-klik-kasittelija! openlayers/aseta-klik-kasittelija!)
(def aseta-hover-kasittelija! openlayers/aseta-hover-kasittelija!)

(def ^{:doc
       "Kartan kontrollit, jotka näytetään karttanäkymän päällä.
        Kartalla voi olla useita kontrolleja samaan aikaan.
        Kontrollit lisätään ja poistetaan avaimella."}
  kartan-yleiset-kontrollit-sisalto (atom {}))

(defn kaappaa-hiiri
  "Muuttaa kartan toiminnallisuutta siten, että hover, click ja dblclick eventit annetaan datana
  annettuun kanavaan. Palauttaa funktion, jolla kaappaamisen voi lopettaa. Tapahtumat ovat vektori,
  jossa on kaksi elementtiä: tyyppi ja sijainti.
  Kun kaappaaminen lopetetaan, suljetaan myös annettu kanava."
  [kanava]
  (let [kasittelija #(go (>! kanava %))
        poista-klik-kasittelija! (aseta-klik-kasittelija! kasittelija)
        poista-hover-kasittelija! (aseta-hover-kasittelija! kasittelija)]

    #(do (poista-klik-kasittelija!)
         (poista-hover-kasittelija!)
         (async/close! kanava))))

(defn nayta-kartan-kontrollit!
  "Näyttää kartan päällä annetut kontrollit. Nimi on nämä kontrollit yksilöivä
  keyword ja sisältö on mikä tahansa hiccup komponentti."
  [nimi sisalto]
  (swap! kartan-yleiset-kontrollit-sisalto assoc nimi sisalto))

(defn poista-kartan-kontrollit!
  "Poistaa nimetyt kartan kontrollit näkyvistä."
  [nimi]
  (swap! kartan-yleiset-kontrollit-sisalto dissoc nimi))

(def ikonien-selitykset-nakyvissa-oletusarvo true)
;; Eri näkymät voivat tarpeen mukaan asettaa ikonien selitykset päälle/pois komponenttiin tultaessa.
;; Komponentista poistuttaessa tulisi arvo asettaa takaisin oletukseksi
(defonce ikonien-selitykset-nakyvissa? (atom true))
(defonce ikonien-selitykset-auki (atom true))
(defonce ikonien-selitykset-sijainti (atom :oikea))
