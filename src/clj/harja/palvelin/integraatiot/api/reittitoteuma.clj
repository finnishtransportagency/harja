(ns harja.palvelin.integraatiot.api.reittitoteuma
  "Reittitoteuman kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET DELETE]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.materiaalit :as materiaalit]
            [harja.kyselyt.toteumat :as toteumat]
            [harja.palvelin.integraatiot.api.toteuma :as api-toteuma]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [harja.kyselyt.tieverkko :as tieverkko]
            [clojure.java.jdbc :as jdbc]
            [harja.geo :as geo]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.api.validointi.toteumat :as toteuman-validointi]
            [clojure.string :as str]
            [clojure.core.async :as async]
            [harja.domain.reittipiste :as rp])
  (:use [slingshot.slingshot :only [throw+]]))

(def ^{:const true} +yhdistamis-virhe+ :virhe-reitin-yhdistamisessa)

(def ^{:const true
       :doc "Etäisyys, jota lähempänä toisiaan olevat reittipisteet yhdistetään linnuntietä,
jos niille ei löydy yhteistä tietä tieverkolta."}
maksimi-linnuntien-etaisyys 200)

(defn- yhdista-viivat [viivat]
  (if-not (empty? viivat)
    {:type :multiline
     :lines (mapcat
              (fn [viiva]
                (if (= :line (:type viiva))
                  (list viiva)
                  (:lines viiva)))
              viivat)}
    +yhdistamis-virhe+))

(defn- piste [{reittipiste :reittipiste}]
  [(get-in reittipiste [:koordinaatit :x])
   (get-in reittipiste [:koordinaatit :y])
   (:aika reittipiste)])

(def ^{:private true} piste-aika (juxt (comp :x :koordinaatit :reittipiste)
                                       (comp :y :koordinaatit :reittipiste)
                                       (comp :aika :reittipiste)))

(defn- valin-geometria
  ([reitti] (valin-geometria reitti maksimi-linnuntien-etaisyys))
  ([{:keys [alku loppu geometria] :as vali} maksimi-etaisyys]
   (or (and geometria (geo/pg->clj geometria))
       (let [[x1 y1 :as p1] (:coordinates (geo/pg->clj alku))
             [x2 y2 :as p2] (:coordinates (geo/pg->clj loppu))
             etaisyys (geo/etaisyys p1 p2)]
         (if (or (nil? maksimi-etaisyys) (< etaisyys maksimi-etaisyys))
           (do (log/debug "Reittitoteuman pisteillä"
                          " (x1:" x1 " y1: " y1
                          " & x2: " x2 " y2: " y2 " )"
                          " ei ole yhteistä tietä. Tehdään linnuntie, etäisyys: " etaisyys ", max: " maksimi-etaisyys)
               {:type :line
                :points [[x1 y1]
                         [x2 y2]]})
           (do (log/info "EI TEHDÄ linnuntietä, etäisyys: " etaisyys ", max: " maksimi-etaisyys)
               nil))))))

(defn hae-reitti
  ([db pisteet] (hae-reitti db maksimi-linnuntien-etaisyys pisteet))
  ([db maksimi-etaisyys pisteet]
   (as-> pisteet p
         (map (fn [[x y aika]]
                (str "\"(" x "," y "," aika ")\"")) p)
         (str/join "," p)
         (str "{" p "}")
         (tieverkko/hae-tieviivat-pisteille-aika db p)
         (keep #(valin-geometria % maksimi-etaisyys) p)
         (yhdista-viivat p))))

(defn luo-reitti-geometria [db reitti]
  (let [reitti (->> reitti
                    (sort-by (comp :aika :reittipiste))
                    (map piste)
                    (hae-reitti db))]
    (if (= reitti +yhdistamis-virhe+)
      +yhdistamis-virhe+
      (-> reitti
          geo/clj->pg
          geo/geometry))))

(defn paivita-toteuman-reitti
  "REPL testausta ja ajastettua tehtävää varten, laskee annetun toteuman reitin uudelleen reittipisteistä."
  ([db toteuma-id] (paivita-toteuman-reitti db toteuma-id maksimi-linnuntien-etaisyys))
  ([db toteuma-id maksimi-etaisyys]
   (let [reitti (->> toteuma-id
                     (toteumat/hae-toteuman-reittipisteet db)
                     (map (fn [{sijainti :sijainti aika :aika}]
                            [(.-x sijainti) (.-y sijainti) aika]))
                     (hae-reitti db maksimi-etaisyys))
         geometria (when-not (= reitti +yhdistamis-virhe+)
                     (-> reitti
                         geo/clj->pg
                         geo/geometry))]
     (if geometria
       (do
         (log/debug "Tallennetaan reitti toteumalle " toteuma-id)
         (toteumat/paivita-toteuman-reitti! db {:reitti geometria
                                                :id toteuma-id}))

       (log/debug "Reittiä ei saatu kasattua toteumalle " toteuma-id)))))

(defn tee-onnistunut-vastaus []
  (tee-kirjausvastauksen-body {:ilmoitukset "Reittitoteuma kirjattu onnistuneesti"}))

(defn luo-reitti [db reitti toteuma-id]
  (log/debug "Luodaan uusi reittipiste")
  (toteumat/tallenna-toteuman-reittipisteet!
   db
   {::rp/toteuma-id toteuma-id
    ::rp/reittipisteet
    (for [{:keys [aika koordinaatit tehtavat materiaalit]} (map :reittipiste reitti)]
      (rp/reittipiste
       (aika-string->java-sql-date aika)
       koordinaatit
       (toteumat/pisteen-hoitoluokat db koordinaatit)
       (for [t tehtavat]
         {::rp/toimenpidekoodi (get-in t [:tehtava :id])
          ::rp/maara (some-> (get-in t [:tehtava :maara :maara]) bigdec)})
       (for [m materiaalit]
         {::rp/materiaalikoodi (->> m :materiaali
                                    (materiaalit/hae-materiaalikoodin-id-nimella db)
                                    first :id)
          ::rp/maara (some-> (get-in m [:maara :maara]) bigdec)})))}))

(defn poista-toteuman-reitti [db toteuma-id]
  (log/debug "Poistetaan reittipisteet")
  ;; Poistetaan reittipistedata: pisteet, tehtävät ja materiaalit
  (toteumat/poista-reittipiste-toteuma-idlla! db toteuma-id))

(defn tallenna-yksittainen-reittitoteuma [db db-replica urakka-id kirjaaja reittitoteuma]
  (let [reitti (:reitti reittitoteuma)
        toteuma (assoc (:toteuma reittitoteuma)
                  ;; Reitti liitetään lopuksi
                  :reitti nil)
        toteuman-reitti (async/thread (luo-reitti-geometria db-replica reitti))]
    (jdbc/with-db-transaction [db db]
      (let [toteuma-id (api-toteuma/paivita-tai-luo-uusi-toteuma db urakka-id kirjaaja toteuma)]
        (log/debug "Toteuman perustiedot tallennettu. id: " toteuma-id)
        (log/debug "Aloitetaan toteuman tehtävien tallennus")
        (api-toteuma/tallenna-tehtavat db kirjaaja toteuma toteuma-id)
        (log/debug "Aloitetaan toteuman materiaalien tallennus")
        (api-toteuma/tallenna-materiaalit db kirjaaja toteuma toteuma-id urakka-id)
        (log/debug "Aloitetaan toteuman vanhan reitin poistaminen, jos sellainen on")
        (poista-toteuman-reitti db toteuma-id)
        (log/debug "Aloitetaan reitin tallennus")
        (luo-reitti db reitti toteuma-id)
        (log/debug "Liitetään toteuman reitti")
        (let [reitti (async/<!! toteuman-reitti)]
          (when (= reitti +yhdistamis-virhe+)
            (log/warn (format "Reittitoteuman reitin geometriaa ei saatu luotua. Kirjaaja oli %s, ja toteuman aikaleimat olivat %s %s"
                               kirjaaja
                               (pr-str (:alkanut toteuma))
                               (pr-str (:paattynyt toteuma)))))
          (api-toteuma/paivita-toteuman-reitti db toteuma-id (if (= reitti +yhdistamis-virhe+) nil reitti)))))))

(defn tallenna-kaikki-pyynnon-reittitoteumat [db db-replica urakka-id kirjaaja data]
  (when (:reittitoteuma data)
    (tallenna-yksittainen-reittitoteuma db db-replica
                                        urakka-id kirjaaja (:reittitoteuma data)))
  (doseq [toteuma (:reittitoteumat data)]
    (tallenna-yksittainen-reittitoteuma db db-replica
                                        urakka-id kirjaaja (:reittitoteuma toteuma))))

(defn tarkista-pyynto [db urakka-id kirjaaja data]
  (let [sopimus-idt (api-toteuma/hae-toteuman-kaikki-sopimus-idt :reittitoteuma :reittitoteumat data)]
    (doseq [sopimus-id sopimus-idt]
      (validointi/tarkista-urakka-sopimus-ja-kayttaja db urakka-id sopimus-id kirjaaja)))
  (when (:reittitoteuma data)
    (toteuman-validointi/tarkista-reittipisteet data)
    (toteuman-validointi/tarkista-tehtavat
      db
      (get-in data [:reittitoteuma :toteuma :tehtavat])
      (get-in data [:reittitoteuma :toteuma :toteumatyyppi])))
  (doseq [reittitoteuma (:reittitoteumat data)]
    (toteuman-validointi/tarkista-reittipisteet reittitoteuma)
    (toteuman-validointi/tarkista-tehtavat
      db
      (get-in reittitoteuma [:reittitoteuma :toteuma :tehtavat])
      (get-in reittitoteuma [:reittitoteuma :toteuma :toteumatyyppi]))))

(defn kirjaa-toteuma [db db-replica {id :id} data kirjaaja]
  (let [urakka-id (Integer/parseInt id)]
    (log/debug "Kirjataan uusi reittitoteuma urakalle id:" urakka-id " kayttäjän:" (:kayttajanimi kirjaaja)
               " (id:" (:id kirjaaja) ") tekemänä.")
    (tarkista-pyynto db urakka-id kirjaaja data)
    (tallenna-kaikki-pyynnon-reittitoteumat db db-replica urakka-id kirjaaja data)
    (tee-onnistunut-vastaus)))


(defn poista-toteuma [db _ {id :id} data kirjaaja]
  (let [urakka-id (Integer/parseInt id)
        ulkoiset-idt (-> data :toteumien-tunnisteet)]
    (log/debug "Poistetaan reittitoteumat id:lla:" ulkoiset-idt "urakalta id:" urakka-id " kayttäjän:" (:kayttajanimi kirjaaja)
               " (id:" (:id kirjaaja) " tekemänä")
    (tarkista-pyynto db urakka-id kirjaaja data)
    (api-toteuma/poista-toteumat db kirjaaja ulkoiset-idt urakka-id)))

(defrecord Reittitoteuma []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db
           db-replica :db-replica
           integraatioloki :integraatioloki
           :as this}]
    (julkaise-reitti
     http :lisaa-reittitoteuma
      (POST "/api/urakat/:id/toteumat/reitti" request
        (kasittele-kutsu db
                         integraatioloki
                         :lisaa-reittitoteuma
                         request
                         json-skeemat/reittitoteuman-kirjaus
                         json-skeemat/kirjausvastaus
                         (fn [parametit data kayttaja db]
                           (#'kirjaa-toteuma db db-replica
                             parametit data kayttaja)))))

    (julkaise-reitti
     http :lisaa-reittitoteuma
    (DELETE "/api/urakat/:id/toteumat/reitti" request
        (kasittele-kutsu db
                         integraatioloki
                         :poista-reittitoteuma
                         request
                         json-skeemat/reittitoteuman-poisto
                         json-skeemat/kirjausvastaus
                         (fn [parametit data kayttaja db]
                           (#'poista-toteuma db db-replica
                                             parametit data kayttaja)))))

    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :lisaa-reittitoteuma)
    this))

;; Reittitoteuman kirjaaminen tiedostosta (esim. payload integraatiolokista)
;;(def toteuma (cheshire.core/parse-string (slurp "reittitoteuma-urakka-125.json") keyword))
;;(def db (:db harja.palvelin.main/harja-jarjestelma))
;;(kirjaa-toteuma db db {:id "urakkaid"} toteuma {:id kayttajaid})
