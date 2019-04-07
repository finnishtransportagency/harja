(ns harja.palvelin.ajastetut-tehtavat.kanavasiltojen-geometriat
  (:require [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.kyselyt.geometriapaivitykset :as q-geometriapaivitykset]
            [harja.pvm :as pvm]
            [clj-time.coerce :as c]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [cheshire.core :as cheshire]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.kanavat.kanavasillat :as q-kanavasillat]
            [harja.geo :as geo]
            [harja.kyselyt.konversio :as konv]
            [clojure.data.json :as json]
            [clojure.set :as set]
            [clojure.string :as str])
  (:import (org.postgis Point)))

;; Kanavasillat täydentävät kanavasulkuja. Molemmat ovat kanavakokonaisuuden kohteen osia.
;; Yhteen kohteeseen voi kuulua esimerkiksi kaksi osaa: silta ja sulku tai useampia siltoja ja sulkuja.
;; Kohteet muodostavat suuremman kanavakokonaisuuden.
(def geometriapaivitystunnus "kanavasillat")

(def virhekasittely
  {:error-handler #(log/error "Käsittelemätön poikkeus ajastetussa tehtävässä:" %)})

(def avattavat-siltatyypit {
                            :teraksinen-kaantosilta "Teräksinen kääntösilta"
                            :teraksinen-kaantosilta-teraskansi "Teräksinen kääntösilta, teräskantinen"
                            :teraksinen-kantosilta-puukansi "Teräksinen kääntösilta, puukantinen"
                            :teraksinen-lappasilta-teraskansi "Teräksinen läppäsilta, teräskantinen"
                            :teraksinen-lappasilta-terasbetonikansi "Teräksinen läppäsilta, teräsbetonikantinen"
                            :teraksinen-nostosilta "Teräksinen nostosilta"
                            :teraksinen-nostosilta-teraskansi "Teräksinen nostosilta, teräskantinen"})

;; Osa silloista on tyypiltään sellaisia, ettei niitä tunnista avattaviksi. TREX:istä ei saa tarpeisiimme sopivaa luokittelua.
;; Siksi suodatus on täydennetty siltanumerolistalla.

;; Aineistosta puuttuu kokonaan Saimaan kanavan Venäjän puoleiset sillat, mutta ne ovat mukana rajauksessa
;; 1399, Saimaan kanava (Venäjä), Pällin läppäsilta
;; 1401, Saimaan kanava (Venäjä), Rättijärven läppäsilta
;; 1402, Saimaan kanava (Venäjä), Särkijärven läppäsilta

;; Vektorissa siltanro ja tunnus-prefix
(def nimetyt-sillat {:pohjanlahti [1151, "U"]
                     :kellosalmi [2724, "U"]
                     :lillholmen [1510, "T"]
                     :itikka [234, "SK"]
                     :uimasalmi [1148, "SK"]
                     :kaltimonkoski [1219, "SK"]
                     :kyronsalmi-rata [2619, "SK"]
                     :uimasalmen-rata [2621 "SK"]
                     :palli [1399 "KaS"]
                     :rattijarvi [1401 "KaS"]
                     :sarkijarvi [1402 "KaS"]
                     :pielisjoki-rata [2622 "SK"]})


(def poistetut-siltatilat {:poistettu "poistettu"
                           :purettu "purettu"
                           :liikennointi-lopetettu "liikennointi-lopetettu"})

(defn onko-silta-poistettu? [elinkaaritila]
  (if (= (some #(= elinkaaritila %) (vals poistetut-siltatilat)) nil) false true))

(defn paivitys-tarvitaan? [db paivitysvali-paivissa]
  (let [viimeisin-paivitys (c/from-sql-time
                             (:viimeisin_paivitys
                               (first (q-geometriapaivitykset/hae-paivitys db geometriapaivitystunnus))))]
    (or (nil? viimeisin-paivitys)
        (>= (pvm/paivia-valissa viimeisin-paivitys (pvm/nyt-suomessa)) paivitysvali-paivissa))))

(defn poista-viimeinen-pilkku[teksti]
  (str/join (assoc (vec teksti) (str/last-index-of teksti ",") nil)))

(defn muunna-mapiksi [osoite]
  (into {} (map (fn [[k v]]
                  {(keyword k) v})
                osoite)))

(defn muunna-tallennettavaan-muotoon [osoite]
  (let [osoite-map (muunna-mapiksi osoite)]
    (str "ROW (" (:tie osoite-map) "," (:osa osoite-map) "," (:etaisyys osoite-map) ",,," (:ajorata osoite-map) ",,,,) ::TR_OSOITE_LAAJENNETTU,")))

(defn muodosta-tr-osoite-array [osoitteet]
  (poista-viimeinen-pilkku (pr-str "ARRAY[" (doall (map muunna-tallennettavaan-muotoon osoitteet)) "]")))

;; Avattavat sillat haetaan TREX:sta. TREX:in (= taitorakennerekisteri) rajapinnan kuvaus on liitetty tikettiin HAR-6948.
(defn tallenna-kanavasilta [db kanavasilta sivunro]
  (let [siltanro (kanavasilta :siltanro)
        nimi (kanavasilta :siltanimi)
        tunnus (kanavasilta :tunnus_prefix)
        kayttotarkoitus (when (kanavasilta :d_kayttotar_koodi) (konv/seq->array (kanavasilta :d_kayttotar_koodi)))
        tila (kanavasilta :elinkaaritila)
        pituus (kanavasilta :siltapit)
        rakennetiedot (when (kanavasilta :rakennety) (konv/seq->array (kanavasilta :rakennety)))
        tieosoitteet nil   ;ei toteutettu loppuun, tietoa ei käytetä (when (kanavasilta :tieosoitteet) (konv/seq->array (map #((muunna-tallennettavaan-muotoon (muunna-mapiksi %))) (kanavasilta :tieosoitteet)) ))
        sijainti_lev (kanavasilta :sijainti_n)
        sijainti_pit (kanavasilta :sijainti_e)
        avattu (when (kanavasilta :avattuliikenteellepvm) (konv/unix-date->java-date (kanavasilta :avattuliikenteellepvm)))
        trex_muutettu (when (kanavasilta :muutospvm) (konv/unix-date->java-date (kanavasilta :muutospvm)))
        trex_oid (kanavasilta :trex_oid)
        trex_sivu sivunro
        poistettu (onko-silta-poistettu? (kanavasilta :elinkaaritila))
        sql-parametrit {:siltanro siltanro
                        :nimi nimi
                        :tunnus tunnus
                        :kayttotarkoitus kayttotarkoitus
                        :tila tila
                        :pituus pituus
                        :rakennetiedot rakennetiedot
                        :tieosoitteet tieosoitteet
                        :sijainti_lev sijainti_lev
                        :sijainti_pit sijainti_pit
                        :avattu nil
                        :trex_muutettu nil
                        :trex_oid trex_oid
                        :trex_sivu trex_sivu
                        :luoja "Integraatio"
                        :muokkaaja "Integraatio"
                        :poistettu poistettu}]
    (q-kanavasillat/luo-kanavasilta<! db sql-parametrit)))

(defn kasittele-kanavasillat [db kanavasillat sivunro]
  (jdbc/with-db-transaction [db db]
                            (doseq [kanavasilta kanavasillat]
                              (tallenna-kanavasilta db kanavasilta sivunro))
                            (q-geometriapaivitykset/paivita-viimeisin-paivitys db geometriapaivitystunnus (harja.pvm/nyt))))

(defn suodata-avattavat-sillat-rakennetyypin-mukaan [vastaus]
  (filter #(not-empty (set/intersection
                        (set (vals avattavat-siltatyypit))
                        (set (% :rakennety))))
          (vastaus :tulokset)))

(defn suodata-sillat-numeron-ja-tunnuksen-mukaan [vastaus]
  (let [haettavat-sillat (set (vals nimetyt-sillat))
        palautuneet-sillat (set (map #(vector (:siltanro %) (:tunnus_prefix %)) (vastaus :tulokset)))
        relevantit-sillat (set/intersection haettavat-sillat palautuneet-sillat)]
    (filter #(contains? relevantit-sillat (vector (:siltanro %) (:tunnus_prefix %)))(vastaus :tulokset))))

(defn suodata-sillat [vastaus]
  (concat (suodata-avattavat-sillat-rakennetyypin-mukaan vastaus)
          (suodata-sillat-numeron-ja-tunnuksen-mukaan vastaus)))

(defn muodosta-sivutettu-url [url sivunro]
  (clojure.string/replace url #"%1" (str sivunro)))

(defn paivita-kanavasillat [integraatioloki db url]
  "Hakee kanavasillat Taitorakennerekisteristä. Kutsu tehdään 25 kertaa. Yli 24 000 siltaa haetaan sivu kerrallaan.
   Yhdellä sivulla palautuu 1000 siltaa. Jos yksi kutsu epäonnistuu, koko integraatioajo epäonnistuu, eikä mitään päivitetä. "
  (log/debug "Päivitetään kanavasiltojen geometriat")
  (integraatiotapahtuma/suorita-integraatio
    db
    integraatioloki
    "trex"
    "kanavasillat-haku"
    (fn [konteksti]
      (dotimes [num 25]                                     ;; kutsutaan rajapintaa 25 kertaa, jolloin kaikki sillat tulevat haetuksi
        (let [http-asetukset {:metodi :GET :url (muodosta-sivutettu-url url (+ num 1))} ;; indeksi alkaa nollasta, sivunumerot ykkösestä
              {vastaus :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
          (if vastaus
            (let [data (cheshire/decode vastaus keyword)]
              (kasittele-kanavasillat db (suodata-sillat data) num)
              (log/debug (str "Kanavasiltoja ei palautunut. Sivunumero: " (+ num 1)))))))))
    (log/debug "Kanavasiltojen päivitys tehty"))

(defn- kanavasiltojen-geometriahakutehtava [integraatioloki db url paivittainen-tarkistusaika paivitysvali-paivissa]
  (log/debug (format "Ajastetaan kanavasiltojen geometrioiden haku tehtäväksi %s päivän väl ein osoitteesta: %s."
                     paivitysvali-paivissa
                     url))
  (when (and paivittainen-tarkistusaika paivitysvali-paivissa url)
    (ajastettu-tehtava/ajasta-paivittain
      paivittainen-tarkistusaika
      (fn [_]
        (when (paivitys-tarvitaan? db paivitysvali-paivissa)
          (paivita-kanavasillat integraatioloki db url))))))

(defrecord KanavasiltojenGeometriahaku [url paivittainen-tarkistusaika paivitysvali-paivissa]
  component/Lifecycle
  (start [{:keys [integraatioloki db] :as this}]
    (log/debug "kanavasiltojen geometriahaku-komponentti käynnistyy")
    (assoc this :kanavasiltojen-geometriahaku
                (kanavasiltojen-geometriahakutehtava
                  integraatioloki
                  db
                  url
                  paivittainen-tarkistusaika
                  paivitysvali-paivissa)))
  (stop [this]
    (when-let [lopeta-fn (:kanavasiltojen-geometriahaku this)]
      (lopeta-fn))
    this))
