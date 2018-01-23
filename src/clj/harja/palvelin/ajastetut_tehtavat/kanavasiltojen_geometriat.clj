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
;; Yhteen kohteeseen voi kuulua esimerkiksi kaksi osaa: silta ja sulku.
;; Kanavakokonaisuuden muodostaa.
(def geometriapaivitystunnus "kanavasillat")

(def avattavat-siltatyypit {
                            :teraksinen-kaantosilta "Teräksinen kääntösilta"
                            :teraksinen-kaantosilta-teraskansi "Teräksinen kääntösilta, teräskantinen"
                            :teraksinen-kantosilta-puukansi "Teräksinen kääntösilta, puukantinen"
                            :teraksinen-lappasilta-teraskansi "Teräksinen läppäsilta, teräskantinen"
                            :teraksinen-lappasilta-terasbetonikansi "Teräksinen läppäsilta, teräsbetonikantinen"
                            :teraksinen-nostosilta "Teräksinen nostosilta"
                            :teraksinen-nostosilta-teraskansi "Teräksinen nostosilta, teräskantinen"
                            :teraksinen-langer-palkkisilta-teraskansi "Teräksinen langerpalkkisilta,teräskantinen"
                            :terasbetoninen-ponttonisilta "Teräsbetoninen ponttonisilta"
                            ;;Jotkut avattavat sillat ovat tämän tyyppisiä, mutta näiden perusteella suodattaessa jäljelle jää paljon turhia siltojas
                            ;;:teraksinen-palkkisilta-terasbetonikansi "Teräksinen palkkisilta, teräsbetonikantinen"
                            ;;:teraksinen-jatkuva-palkkisilta-terasbetonikansi "Teräksinen jatkuva palkkisilta, teräsbetonikantinen"
                            ;;:teraksinen-jatkuva-kotelopalkkisilta "Teräksinen jatkuva kotelopalkkisilta"
                            ;;:teraksinen-liittorakenteinen-ulokepalkkisilta-terasbetonikansi "Teräksinen ulokepalkkisilta,teräsbetonikantinen,liittorakenteinen"
                            ;;:teraksinen-ristikkosilta-teraskansi "Teräksinen ristikkosilta,teräskantinen"
                            ;;:teraksinen-levypalkkisilta-ajorata-ylhaalla "Teräksinen levypalkkisilta, ajorata ylhäällä"
                            ;;:teraksinen-ristikkosilta-ajorata-ylhaalla "Teräksinen ristikkosilta, ajorata ylhäällä"
                            })

(def kanavasiltatunnukset {:kanavasilta "KaS"})

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

(defn muuta-tr-osoitteiksi [kanavasilta-osoite]
  {:tie (kanavasilta-osoite :tie)
   :aosa (kanavasilta-osoite :alku)
   :aet (kanavasilta-osoite :etaisyys)
   :ajorata (kanavasilta-osoite :ajorata)}
  )

;; MUODOSTA TR-OSOITE LAAJENNOS TYYPPINEN JUTTU
;;tyypitetty array, tallennus p

(defn tallenna-kanavasilta [db kanavasilta]
  ;; Avattavat sillat haetaan TREX:sta.
  ;; TREX:in (= taitorakennerekisteri) rajapinnan kuvaus on liitetty tikettiin HAR-6948.

  (let [siltanro (kanavasilta :siltanro)
        nimi (kanavasilta :siltanimi)
        tunnus (kanavasilta :tunnus_prefix)
        kayttotarkoitus (when (kanavasilta :d_kayttotar_koodi) (konv/seq->array (kanavasilta :d_kayttotar_koodi)))
        tila (kanavasilta :elinkaaritila)
        pituus (kanavasilta :siltapit)
        rakennetiedot (when (kanavasilta :rakennety) (konv/seq->array (kanavasilta :rakennety)))
        tieosoitteet (when (kanavasilta :tieosoitteet) (konv/seq->array (map muuta-tr-osoitteiksi (kanavasilta :tieosoitteet))))
        sijainti_lev (kanavasilta :sijainti_n)
        sijainti_pit (kanavasilta :sijainti_e)
        avattu (when (kanavasilta :avattuliikenteellepvm) (konv/unix-date->java-date (kanavasilta :avattuliikenteellepvm)))
        trex_muutettu (when (kanavasilta :muutospvm) (konv/unix-date->java-date (kanavasilta :muutospvm)))
        trex_oid (kanavasilta :trex_oid)
        trex_sivu (kanavasilta :sivu)
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
                        :avattu avattu
                        :trex_muutettu trex_muutettu
                        :trex_oid trex_oid
                        :trex_sivu trex_sivu
                        :luoja "Integraatio"
                        :muokkaaja "Integraatio"
                        :poistettu poistettu}]
    (println (str kanavasilta))
    (println (str "PArAMERIRI" sql-parametrit))
    (q-kanavasillat/luo-kanavasilta<! db sql-parametrit)))



(defn kasittele-kanavasillat [db kanavasillat]
  (jdbc/with-db-transaction [db db]
                            (doseq [kanavasilta kanavasillat]
                              (tallenna-kanavasilta db kanavasilta))
                            (q-geometriapaivitykset/paivita-viimeisin-paivitys db geometriapaivitystunnus (harja.pvm/nyt))))


(defn suodata-avattavat-sillat [vastaus]
  (filter #(not-empty (set/intersection
                        (set (vals avattavat-siltatyypit))
                        (set (% :rakennety))))
          (vastaus :tulokset)))

(defn muodosta-sivutettu-url [url sivunro]
  (clojure.string/replace url #"%1" (str sivunro)))


(defn paivita-kanavasillat [integraatioloki db url]
  (log/debug "Päivitetään kanavasiltojen geometriat")
  (integraatiotapahtuma/suorita-integraatio
    db
    integraatioloki
    "trex"
    "kanavasillat-haku"
    (fn [konteksti]
      (dotimes [num 25]
        (let [http-asetukset {:metodi :GET :url (muodosta-sivutettu-url url num)}
              {vastaus :body} (try
                                (integraatiotapahtuma/laheta konteksti :http http-asetukset)
                                (catch Exception e))]
          (if vastaus
            (let [data (cheshire/decode vastaus keyword)]
              (kasittele-kanavasillat db (suodata-avattavat-sillat data)))
            (log/debug (str "Kanavasiltoja ei käsitelty, vastausta ei saatu. Sivunumero: " num)))))))
  (log/debug "Kanavasiltojen päivitys tehty"))

(defn- kanavasiltojen-geometriahakutehtava [integraatioloki db url paivittainen-tarkistusaika paivitysvali-paivissa]
  (log/debug (format "Ajastetaan kanavasiltojen geometrioiden haku tehtäväksi %s päivän välein osoitteesta: %s."
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
  (start [{:keys [integraatioloki db] :as this}]sl
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
