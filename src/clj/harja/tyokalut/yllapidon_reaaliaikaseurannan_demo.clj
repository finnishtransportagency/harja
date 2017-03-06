;; Tarkoitettu demoamaan ylläpidon reaaliaikaseurantaa tilannekuvan kanssa.
;; Lähettää ensinnä suljetun tieosuuden, jonka jälkeen sille TMA-aidat ja lopulta asettaa työkonehavaintoja 10s välein suljetun tieosuuden rajaavalle alueelle
;; Tarvii ssh putken stagingin tietokantaan porttiin 7771

(ns harja.tyokalut.yllapidon-reaaliaikaseurannan-demo
  (:require [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.tietyomaat :as q-tietyomaat]
            [harja.kyselyt.tyokoneseuranta :as tks]
            [harja.geo :as geo])
  (:import (java.util Calendar)
           (java.sql Timestamp)))

(def osuusid 123456789)

(defn arrayksi [db v]
  (with-open [conn (.getConnection (:datasource db))]
    (.createArrayOf conn "text" (to-array v))))

(defn paivita [db sql]
  (with-open [c (.getConnection (:datasource db))
              ps (.prepareStatement c (reduce str sql))]
    (.executeUpdate ps)))


(defn hae [db sql]
  (with-open [c (.getConnection (:datasource db))
              ps (.prepareStatement c (reduce str sql))
              rs (.executeQuery ps)]
    (let [cols (-> (.getMetaData rs) .getColumnCount)]
      (loop [res []
             more? (.next rs)]
        (if-not more?
          res
          (recur (conj res (loop [row []
                                  i 1]
                             (if (<= i cols)
                               (recur (conj row (.getObject rs i)) (inc i))
                               row)))
                 (.next rs)))))))

(defn hae-sijainti [db]
  (let [sql (str "SELECT CAST( st_lineinterpolatepoint(st_makeline(st_linemerge (geometria)), random()) AS point) AS ajoneuvo_sijainti "
                 "FROM tietyomaa WHERE osuus_id= "
                 osuusid ";")
        sijainti (:coordinates (geo/pg->clj (ffirst (hae db sql))))]
    {:x (first sijainti) :y (second sijainti)}))

(defn nyt []
  (new Timestamp (.getTime (.getTime (Calendar/getInstance)))))

(defn tee-tyokonehavainto [db id tyyppi x y urakka-id tehtava suunta]
  (tks/tallenna-tyokonehavainto<!
    db
    {:jarjestelma "Harja"
     :organisaationimi "Solita Oy"
     :ytunnus "1060155-5"
     :viestitunniste 1
     :lahetysaika (nyt)
     :tyokoneid id
     :tyokonetyyppi tyyppi
     :xkoordinaatti x
     :ykoordinaatti y
     :suunta suunta
     :urakkaid urakka-id
     :tehtavat (arrayksi db [tehtava])}))

(defn tee-tyokonehavainto-satunnaiseen-paikkaan [db id tyyppi urakka-id tehtava]
  (let [sijainti (hae-sijainti db)]
    (tee-tyokonehavainto db id tyyppi (:x sijainti) (:y sijainti) urakka-id tehtava (rand-int 360))))

(defn aja []
  (let [tietokanta {:palvelin "localhost"
                    :tietokanta "harja"
                    :portti 7771
                    :yhteyspoolin-koko 16
                    :kayttaja "[KAYTTAJATUNNUS]"
                    :salasana "[SALASANA]"}
        urakka-id 5
        yllapitokohde-id 7
        alkux 429108.469M
        alkuy 7212293.876M
        loppux 442231.469M
        loppuy 7223121.876M

        db (:db harja.palvelin.main/harja-jarjestelma)
        _ (paivita db "DELETE FROM tietyomaa WHERE osuus_id = 123456789;")
        suljettutieosuus {:jarjestelma "Harja"
                          :osuusid osuusid
                          :alkux alkux
                          :alkuy alkuy
                          :loppux loppux
                          :loppuy loppuy
                          :asetettu (nyt)
                          :kaistat (konv/seq->array [1])
                          :ajoradat (konv/seq->array [0])
                          :yllapitokohde yllapitokohde-id
                          :kirjaaja 13
                          :tr_tie 20
                          :tr_aosa 1
                          :tr_aet 1
                          :tr_losa 14
                          :tr_let 1
                          :nopeusrajoitus 20}]

    ;; Tehdään ensin suljettu tieosuus, joka rajaa alueen työkoneille
    (q-tietyomaat/luo-tietyomaa<! db suljettutieosuus)

    ;; Aseta TMA-aidat suljetun tieosuuden alkuun ja loppuun
    (tee-tyokonehavainto db 1000001 "TMA-aita 1" alkux alkuy urakka-id "turvalaite" 425)
    (tee-tyokonehavainto db 1000002 "TMA-aita 2" loppux loppuy urakka-id "turvalaite" 425)

    ;; Lisää ajoneuvot
    (let [tyokoneet [{:id 1000003
                      :tyyppi "Pääasfaltointilaite"
                      :tehtava "asfaltointi"}
                     {:id 1000004
                      :tyyppi "Kuumennuslaite"
                      :tehtava "kuumennus"}
                     {:id 1000005
                      :tyyppi "Sekoitus tai stabilointi laite"
                      :tehtava "sekoitus tai stabilointi"}]]
      (dotimes [_ 10]
        (doseq [{:keys [id tyyppi tehtava]} tyokoneet]
          (tee-tyokonehavainto-satunnaiseen-paikkaan db id tyyppi urakka-id tehtava)
          (Thread/sleep 10000))))))

