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
        urakat-sijainnilla (loop [threshold 0]
                             (let [urakat (q-urakka/hae-urakka-sijainnilla db {:x (.getX geometria) :y (.getY geometria)
                                                                               :urakkatyyppi "hoito" :threshold threshold})]
                               (if (and (empty? urakat) (< threshold 1000))
                                 (recur (+ threshold 100))
                                 urakat)))

        _ (when (< 1 (count urakat-sijainnilla))
            (log/warn "Sillalle " trex-oid "löytyi useita urakoita! Urakka-id:t: "
              (str/join (map (:id urakat-sijainnilla) ","))
              ". Sillalle merkitään vain ensimmäinen."))

        urakka-id (:id (first urakat-sijainnilla))

        ;; Jätetään vanha urakka sillalle jos urakka ei ole päättynyt ja sillä on siltatarkastuksia
        aktiiviset-urakat-joilla-tarkastuksia (filter (fn [silta]
                                                        (and (:siltatarkastuksia? silta)
                                                          (pvm/ennen? (pvm/nyt) (:loppupvm silta))
                                                          (not= urakka-id (:urakka-id silta)))) urakkatiedot)

        _ (when-not (empty? aktiiviset-urakat-joilla-tarkastuksia)
            (log/warn "Kaikkia käsiteltyyn siltaan ( trex-oid:" trex-oid ") virheellisesti liitettyjä urakoita ("
              (mapv :urakka-id aktiiviset-urakat-joilla-tarkastuksia) ") ei saatu poistettua sillan tiedoista. "
              "Ei lisätä oikeaa urakkaa ( urakka: " urakka-id ") listaan."))

        sillan-vanhat-urakat (concat aktiiviset-urakat-joilla-tarkastuksia
                               ;; Jätetään talteen myös sillalle merkityt päättyneet urakat.
                               (filter (fn [silta]
                                         (pvm/ennen? (:loppupvm silta) (pvm/nyt))) urakkatiedot))

        urakka-idt (distinct (keep identity (cond-> (map :urakka-id sillan-vanhat-urakat)
                                              (empty? aktiiviset-urakat-joilla-tarkastuksia)
                                              (conj urakka-id))))

        ;; Merkitään silta kokonaan poistetuksi, jos sen tila ei ole käytössä, eikä sille ole aktiivisissa urakoissa
        ;; tehtyjä tarkastuksia.
        poistetaan? (boolean (and (not kaytossa?)
                               (empty? aktiiviset-urakat-joilla-tarkastuksia)
                               (not (some #(and (= urakka-id (:urakka-id %))
                                             (:siltatarkastuksia? %)) urakkatiedot))))

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
                        ;; Ei merkitä siltaa poistetuksi, jos sillä on tarkastuksia.
                        :poistettu poistetaan?
                        :urakat urakka-idt
                        :kunnan-vastuulla false}]

    ;; AINEISTOON LIITTYVÄT HUOMIOT

    ; Ensimmäinen Harjassa tehty siltatarkastus: 2016-09-28 => Suodatetaan aineistosta mukaan vain ne sillat,
    ; joissa ei ole loppupäivämäärää tai joiden loppupäivämäärä on Harjan käyttöönoton jälkeen.
    ; Päivitettävä silta etsitään siltatunnuksella tai uudella Taitorakennerekisterin tunnuksella

    ; Loppupäivämäärä: Tietty ominaisuus, päätös tai asian tila päättyy tieverkolla sen loppupäivämääränä.   
    ; Lakkautuspäivämäärä: Lakkautuspäivämäärä on vain ja ainoastaan ajorataa ja tietä koskeva termi. Ajorataosuus on poistunut lakkautuspäivänä yleiseltä tieverkolta. Tämänkin hetken jälkeen tie saattaaa jäädä olemaan ja sille voidaan myöhemmin muuttaa ainakin teoriassa ominaisuuksia.  
    ; Kun ajorataosuus lakkautetaan, geometria poistuu tierekisteristä (=> aineistovirhe). Jotta geometria säilyy Harjassa, päivitetään lakkautetuista tieosuuksista ainoastaan lakkautuspäivämäärä.

    ; Silta_id on vanhan Siltarekisterin tunniste, nykyinen Taitorakennerekisteri yksilöi trex-oid-tiedolla. Siltaid ei enää kuulu aineistoon.
    ; Siltanumero on molempien id:n kanssa relevantti => Otetaan aineistoon mukaan vain sillat, joissa siltanumero on annettu.
    ; Jos trex-oid puuttuu, ei viedä siltaa kantaan.

    (when-not (or (nil? siltanumero)
                (nil? trex-oid))
      (if-not (empty? urakkatiedot)
        (q-sillat/paivita-silta! db sql-parametrit)
        (when (= "kaytossa" tila)
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
             (and (seq (:vaylanpito %)) (str/includes? (str/lower-case (:vaylanpito %)) "tieverkko"))
             (let [kunnossapitaja (str/lower-case (:nykyinenku %))]
               (not (or (str/includes? kunnossapitaja "yksityinen")
                      (str/includes? kunnossapitaja "kaupunki")
                      (str/includes? kunnossapitaja "kunta")
                      (str/includes? kunnossapitaja "tieyhtiö")
                      (str/includes? kunnossapitaja "ruotsi")
                      (str/includes? kunnossapitaja "hkl")
                      (str/includes? kunnossapitaja "sairaanhoitopiiri"))))) sillat))

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
