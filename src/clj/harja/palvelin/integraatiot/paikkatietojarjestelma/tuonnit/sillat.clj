(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.sillat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.set :as set]
            [harja.pvm :as pvm]
            [harja.kyselyt.sillat :as q-sillat]
            [harja.kyselyt.urakat :as q-urakka]
            [harja.kyselyt.integraatioloki :as q-integraatioloki]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile])
  (:import [org.postgresql.util PSQLException]))

(defn string-intiksi [str]
  (if (string? str)
    (let [ei-numeeriset-poistettu (re-find #"\d+" str)]
      (if (nil? ei-numeeriset-poistettu)
        nil
        (Integer. ei-numeeriset-poistettu)))
    str))

(defn float-intiksi [v]
  (if (float? v)
    (int v)
    v))

(defn mapin-floatit-inteiksi [m]
  (into {} (map (fn [[k v]]
                  [k (float-intiksi v)])
                m)))

(defn- silta-kuuluu-kunnalle? [silta]
  (let [kunnossapitaja (str/lower-case (:nykyinenku silta))]
    (or (str/includes? kunnossapitaja "yksityinen")
      (str/includes? kunnossapitaja "kaupunki")
      (str/includes? kunnossapitaja "kunta")
      (str/includes? kunnossapitaja "tieyhtiö")
      (str/includes? kunnossapitaja "ruotsi")
      (str/includes? kunnossapitaja "hkl")
      (str/includes? kunnossapitaja "sairaanhoitopiiri"))))

(defn luo-tai-paivita-silta [db silta-floateilla]
  (let [silta (mapin-floatit-inteiksi silta-floateilla)
        ;; Parsitaan sillan tyyppi rakennetyypeistä. Sillalla voi olla useampi rakennetyyppi
        ;; On mahdollista, että tämä ei ota oikeaa, mutta riskin pitäisi olla pieni, sillä siltatyyppiä ei käytetä mihinkään tärkeään
        tyyppi (when-not (empty? (:janteet silta))
                 (second (re-find #"rakennetyypit:nimi:([^,]*)" (:janteet silta))))
        tunnus (:tunnus silta)
        siltanumero (string-intiksi (last (str/split tunnus #"-")))
        nimi (:nimi silta)
        geometria (:the_geom silta)
        geometria-str (.toString geometria)
        tie (string-intiksi (:tr_numero (:tieosoite silta)))
        alkuosa (string-intiksi (:tr_alkuosa (:tieosoite silta)))
        alkuetaisyys (string-intiksi (:tr_alkuetaisyys (:tieosoite silta)))
        muutospvm (:paivitetty silta)
        trex-oid (when-not (empty? (:oid silta))
                   (:oid silta))
        tila (:tila silta)
        kaytossa? (= "kaytossa" tila)
        urakkatiedot (q-sillat/hae-sillan-tiedot db {:trex-oid trex-oid :siltatunnus tunnus :siltanimi nimi})

        ;; Jos urakkatietoa on muokattu käsin, ei päivitetä sillan urakkoja.
        urakkatieto-kasin-muokattu? (:urakkatieto_kasin_muokattu (first urakkatiedot))

        urakat-sijainnilla (->> (loop [threshold 0]
                                 (let [urakat (q-urakka/hae-urakka-sijainnilla db {:x (.getX geometria) :y (.getY geometria)
                                                                                   :urakkatyyppi "hoito" :threshold threshold})]
                                   (if (and (empty? urakat) (< threshold 1000))
                                     (recur (+ threshold 100))
                                     urakat)))
                             (sort-by :alkupvm)
                             reverse)

        urakka-id (:id (first urakat-sijainnilla))

        _ (when (< 1 (count urakat-sijainnilla))
            (log/warn "Sillalle " trex-oid "löytyi useita urakoita! Urakka-id:t: ["
              (str/join ", " (map :id urakat-sijainnilla)) "]."
              (when-not urakkatieto-kasin-muokattu?
                (str "Vastuu-urakaksi merkitään " urakka-id))))

        ;; Jätetään vanha urakka sillalle jos urakka ei ole päättynyt ja sillä on siltatarkastuksia
        aktiiviset-urakat-joilla-tarkastuksia (filter (fn [silta]
                                                        (and (:siltatarkastuksia? silta)
                                                          (pvm/ennen? (pvm/nyt) (:loppupvm silta))
                                                          (not= urakka-id (:urakka-id silta)))) urakkatiedot)

        sillalla-tarkastuksia? (or (seq aktiiviset-urakat-joilla-tarkastuksia)
                                 (some #(and (= urakka-id (:urakka-id %))
                                          (:siltatarkastuksia? %)) urakkatiedot))

        sillan-vanhat-urakat (concat aktiiviset-urakat-joilla-tarkastuksia
                               ;; Jätetään talteen myös sillalle merkityt päättyneet urakat.
                               (filter (fn [silta]
                                         (pvm/ennen? (:loppupvm silta) (pvm/nyt))) urakkatiedot))

        ;; Silta siirtyy löydetylle urakalle, paitsi jos se on asetettu käsin.
        vastuu-urakka (if urakkatieto-kasin-muokattu?
                        (:vastuu-urakka (first urakkatiedot))
                        urakka-id)

        urakka-idt (distinct (keep identity (cond-> (map :urakka-id sillan-vanhat-urakat)
                                              (not urakkatieto-kasin-muokattu?)
                                              (conj urakka-id))))

        silta-kuuluu-kunnalle? (silta-kuuluu-kunnalle? silta)

        ;; Merkitään silta kokonaan poistetuksi, jos sen tila ei ole käytössä tai se kuuluu kunnalle,
        ;; eikä sille ole aktiivisissa urakoissa tehtyjä tarkastuksia.
        poistetaan? (boolean (and
                               (or (not kaytossa?)
                                 silta-kuuluu-kunnalle?)
                               (not sillalla-tarkastuksia?)))

        sql-parametrit {:tyyppi tyyppi
                        :siltanro siltanumero
                        :siltanimi nimi
                        :geometria geometria-str
                        :numero tie
                        :aosa alkuosa
                        :aet alkuetaisyys
                        :tunnus tunnus
                        :trex-oid trex-oid
                        :muutospvm muutospvm
                        :loppupvm (when-not kaytossa? muutospvm)
                        :poistettu poistetaan?
                        :urakat urakka-idt
                        :vastuu-urakka vastuu-urakka
                        :kunnan-vastuulla silta-kuuluu-kunnalle?}]

    ;; AINEISTOON LIITTYVÄT HUOMIOT

    ; HOX! Aineiston rakenne muuttunut 2022 lopulla huomattavasti, muutokset tehty 2023 alkupuolella.
    ;
    ; Uudessa aineistossa saadaan tieto vain siitä, onko silta päättynyt, lakkautettu, täytetty tms. Jos sillan tila
    ; on mitä tahansa muuta kuin käytössä, merkitän sen loppumispäiväksi sen edellisen päivityksen pvm.
    ;
    ; Jos silta siirtyy urakalta toiselle, ja sillalle on tehty siltatarkastuksia toisessa aktiivisessa urakassa,
    ; nostetaan virhe lokille eikä siirretä siltaa.
    ;
    ; Uusille silloille tätä ei pitäisi tapahtua, koska ainoa tilanne, jossa silta siirtyy toiselle urakalle, pitäisi
    ; olla kun urakka päättyy ja uusi alkaa. Vanhan aineiston mukana on kuitenkin tullut alueurakkaid, jonka takia
    ; kannassa saattaa olla urakoita, jotka kuuluvat eri urakalle kuin sillan sijainti antaa ymmärtää.
    ;
    ; Päättyneen urakan ID jätetään sillalle, koska sitä tarvitaan ainakin siltatarkastusraporteissa
    ;
    ; Ei tallenneta siltoja ollenkaan, joista puuttuu siltanumero tai trex-oid
    ; Ei myöskään luoda siltaa, jos se ei ole käytössä tai kuuluu kunnalle.

    (when-not (or (nil? siltanumero)
                (nil? trex-oid))
      (if-not (empty? urakkatiedot)
        (q-sillat/paivita-silta! db sql-parametrit)
        (when (and kaytossa? (not silta-kuuluu-kunnalle?))
          (q-sillat/luo-silta<! db sql-parametrit))))))


(defn vie-silta-entry [db silta]
  (luo-tai-paivita-silta db silta))

; Jotta ei tule tallennuksessa ongelmia yksilöivien avainten kanssa + suorituskyvyn parantamiseksi, jätetään tallennettavaan
; aineistoon ainoastaan yksi rivi per voimassaoleva silta.
(defn jarjesta-voimassaolevat-sillat-yksittaisille-riveille
  ([kaikki-siltarivit]
   (let [erottele (fn erottele [kaikki-siltarivit valitut-siltarivit]
                (lazy-seq
                  ((fn [[f :as kaikki-siltarivit] valitut-siltarivit]
                     (when-let [sillat (seq kaikki-siltarivit)]
                       (if (contains? valitut-siltarivit (:oid f))
                         (recur (rest sillat) valitut-siltarivit)
                         (cons f (erottele (rest sillat) (conj valitut-siltarivit (:oid f)))))))
                    kaikki-siltarivit valitut-siltarivit)))]
     (erottele kaikki-siltarivit #{}))))

(defn parsi-tieosoitteet [sillat]
  (map (fn [silta]
         (assoc silta :tieosoite
                      ;; Sillassa saattaa olla useampi tieosoite, esim. sekä ajoväylä että kävelyteitä.
                      ;; Parsitaan näistä ensimmäinen regexillä. Tämä ei ole täysin pomminvarma, joten virheitä saattaa tulla.
                      ;; Tämän ei kuitenkaan pitäisi haitata, sillä sillan tr-osoitetta ei käytetä mihinkään tärkeään.
                      (when-let [tieosoitteet (:tieosoitte silta)]
                        {:tr_numero (second (re-find #"tienumero:(.*?(?=,))" tieosoitteet))
                         :tr_alkuosa (second (re-find #"tieosa:(.*?(?=,))" tieosoitteet))
                         :tr_alkuetaisyys (second (re-find #"tieosa:(.*?(?=,))" tieosoitteet))
                         :tr_ajorata (second (re-find #"ajorata:(.*?(?=,))" tieosoitteet))})))
    sillat))

(defn- suodata-sillat [sillat]
  (filter #(and
             (str/includes? (:nykyinen_o %) "Väylävirasto")
             (and (seq (:vaylanpito %)) (str/includes? (str/lower-case (:vaylanpito %)) "tieverkko"))) sillat))

(defn vie-sillat-kantaan [db shapefile]
  (if shapefile
    (let [siltatietueet-shapefilesta (shapefile/tuo shapefile)
          tallennettavat-siltatietueet (-> siltatietueet-shapefilesta
                                         suodata-sillat
                                         jarjesta-voimassaolevat-sillat-yksittaisille-riveille
                                         parsi-tieosoitteet)]
      (log/debug (str "Tuodaan sillat kantaan tiedostosta " shapefile))
      (try (jdbc/with-db-transaction [db db]
             (doseq [silta tallennettavat-siltatietueet]
               (vie-silta-entry db silta)))
           (log/debug "siltojen tuonti kantaan valmis.")
           (catch PSQLException e
             (log/error "Siltojen tuonnissa kantaan tapahtui virhe: " e)
             (throw e))
           (catch Exception e
             (log/error "Siltojen tuonnissa tapahtui virhe: " e)
             (throw e))))
    (log/debug "Siltojen tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
