(ns harja.tiedot.kartta
  (:require [harja.geo :as geo]
            [cljs.core.async :refer [timeout <! >! chan] :as async]
            [reagent.core :refer [atom]]
            [harja.tiedot.navigaatio :as nav]
            [harja.views.kartta.tasot :as tasot]
            [harja.loki :refer [log]]
            [harja.ui.kartta.apurit :refer [+koko-suomi-extent+]]
            [harja.ui.openlayers :as openlayers]
            [harja.tiedot.kartta.infopaneelin-tila :as paneelin-tila]
            [harja.ui.kartta.apurit :as apurit])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [reagent.ratom :refer [reaction]]))

(def pida-geometria-nakyvilla-oletusarvo true)
(defonce pida-geometriat-nakyvilla? (atom pida-geometria-nakyvilla-oletusarvo))

(defonce infopaneeli-nakyvissa? (reaction @paneelin-tila/infopaneeli-nakyvissa?))
(defonce infopaneelin-linkkifunktiot (atom nil))

(defn keskita-kartta-alueeseen! [alue]
  (reset! nav/kartan-extent alue))

(defn piilota-infopaneeli! []
  (paneelin-tila/piilota-infopaneeli!))

(defn nayta-infopaneeli! []
  (paneelin-tila/nayta-infopaneeli!))

;; Määrittelee asiat, jotka ovat nykyisessä pisteessä.
;; Avaimet:
;; :koordinaatti  klikatun pisteen koordinatti (tai nil, jos ei valintaa)
;; :asiat         sekvenssi asioita, joita pisteestä löytyy
;; :haetaan?      true kun haku vielä kesken
(defonce asiat-pisteessa (atom {:koordinaatti nil
                                :haetaan? true
                                :asiat nil}))

(defn kasittele-infopaneelin-linkit!
  "Infopaneelin skeemat saattavat sisältää 'linkkejä', jotka käsitellään
  (tai ei käsitellä) näkymäkohtaisesti. Esimerkiksi toteumanäkymässä voidaan haluta
  mahdollistaa toteuman avaaminen lomakkeeseen infopaneelin kautta.
  Funktio ottaa parametriksi mäpin, jossa avaimet ovat :tyyppejä-kartalla
  (katso harja.ui.kartta.asioiden-tiedot), ja arvot ovat mappejä, jotka
  sisältävät avaimet :toiminto ja :teksti tai :teksti-fn. :toiminto  ja :teksti-fn -avaimen arvo on
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
    ;; Jos näkymässä on näkymän omia aktiivisia tasoja (esim tarkastusnäkymässä tarkastusreitit),
    ;; keskitetään kartta aina extenttiin. Jos ei ole aktiivisia näkymän tasoja ei ole,
    ;; zoomataan urakkaan/hallintayksikköön.
    ;; Frontilla piirretyt geometriatasot ovat "aktiivisia", jos niihin on piirretty asioita.
    ;; Niiden extent on aina kaikkien tasolla olevien asioiden extent.
    ;; Kuvatasot ovat aina "aktiivisia", ja niiden extent on aina nil.
    ;; Näin näkymissä, missä karttataso on palvelimella piirretty kuva, ei zoomia
    ;; resetoida koko ajan näyttämään urakka.
    (let [extent (reduce geo/yhdista-extent
                         (keep #(-> % meta :extent) (vals @tasot/geometriat-kartalle)))]
      (log "EXTENT TASOISTA: " (pr-str extent))
      (if (not-empty (tasot/aktiiviset-nakymien-tasot))
        (when extent (keskita-kartta-alueeseen! (-> extent
                                                    geo/laajenna-extent-prosentilla
                                                    (geo/laajenna-pinta-alaan (apurit/min-pinta-ala-automaattiselle-zoomille
                                                                                @nav/valittu-urakka)))))
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
