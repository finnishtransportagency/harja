(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.tieverkko
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as clj-str]
            [harja.kyselyt.tieverkko :as k]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]
            [harja.geo :as geo]
            [clojure.set :as clj-set])
  (:import (org.locationtech.jts.geom Coordinate LineString MultiLineString GeometryFactory)
           (org.locationtech.jts.geom.impl CoordinateArraySequence)
           (org.locationtech.jts.operation.linemerge LineSequencer)
           (java.lang Character)))

(defn- line-string-seq
  ([multilinestring]
   (line-string-seq 0 (.getNumGeometries multilinestring) multilinestring))
  ([i n multilinestring]
   (lazy-seq
     (cons (.getGeometryN multilinestring i)
           (when (< i (dec n))
             (line-string-seq (inc i) n multilinestring))))))

(defn- ensimmainen-piste [g]
  (let [arr (.getCoordinates g)]
    (aget arr 0)))

(defn- viimeinen-piste [g]
  (let [arr (.getCoordinates g)]
    (aget arr (dec (alength arr)))))

(defn luo-line-string [pisteet]
  (LineString. (CoordinateArraySequence.
                 (into-array Coordinate pisteet))
               (GeometryFactory.)))

(defn luo-multi-line-string [line-strings]
  (MultiLineString. (into-array LineString line-strings)
                    (GeometryFactory.)))

(defn luo-coordinate [[x y]]
  (Coordinate. x y))

(defn jatkuva-line-string
  "Yritä yhdistää tie yhtenäiseksi linestringiksi. Valtaosa teiden osien geometrioista jakaa alku-
  ja loppupisteet, joten ne voidaan muuntaa linestringiksi pudottamalla
  duplikoitu yhtenäinen piste.
  Joissain teissä on oikeasti reikiä geometriassa eikä niitä voi yhdistää
  yhdeksi linestringiksi. Tässä tapauksessa pitää palauttaa multilinestring."
  [lines]
  (let [line-strings
        (mapv luo-line-string
              (reduce
                (fn [viivat ls]
                  (let [viimeinen-viiva (last viivat)
                        koordinaatit (seq (.getCoordinates ls))]
                    (if-not viimeinen-viiva
                      ;; Ensimmäinen viiva
                      [(vec koordinaatit)]

                      ;; Yritä yhdistää edelliseen, jos se alkaa samalla kuin
                      ;; edellinen loppuu
                      (let [viimeinen-piste (last viimeinen-viiva)]
                        (if (= viimeinen-piste (first koordinaatit))
                          ;; Voidaan jatkaa samaa linestringiä
                          (conj (vec (butlast viivat))
                                (vec (concat viimeinen-viiva (drop 1 koordinaatit))))

                          ;; Tehdään uusi linestring
                          (conj viivat
                                (vec koordinaatit)))))))
                [] lines))]
    (if (= 1 (count line-strings))
      (first line-strings)
      (luo-multi-line-string line-strings))))

(defn- coord [c]
  (str "X: " (.x c) ", Y: " (.y c)))

(defn- piste [^Coordinate c]
  [(.x c) (.y c)])


(defn ota-ls-alkupisteella [alkupiste ls]
  (let [loytynyt-ls (some #(when (= alkupiste (ensimmainen-piste %))
                             %)
                          ls)]
    (if-not loytynyt-ls
      [nil ls]
      [loytynyt-ls (filter #(not= loytynyt-ls %) ls)])))

(defn- etaisyys-viivan-alkuun [coord line-string]
  (and coord line-string
       (geo/etaisyys (piste coord)
                     (piste (ensimmainen-piste line-string)))))

(defn- yhdista-viivat
  "Yhdistää kaksi multilinestringiä siten, että viiva alkaa ensimmäisen
  multilinestringin osalla. Jos yhdistys ei onnistu siten, että kaikki
  linestringit tulevat käytettyä, palautetaan nil."
  ([g0 g1 fallback]
   (yhdista-viivat g0 g1 fallback false))
  ([g0 g1 fallback ota-lahin?]
   (let [ls0 (line-string-seq g0)
         ls1 (line-string-seq g1)]
     (loop [result [(first ls0)]
            loppupiste (viimeinen-piste (first ls0))
            ls0 (rest ls0)
            ls1 ls1]
       (if (and (empty? ls0) (empty? ls1))
         ;; Molemmat empty, onnistui!
         ;; HUOM: fallbackin ei tarvitse olla tyhjä
         (jatkuva-line-string result)

         ;; Ei vielä loppu, ota jommasta kummasta seuraava pala, joka
         ;; jatkaa loppupisteestä
         (let [seuraava-ls0 (first ls0)
               seuraava-ls1 (first ls1)]
           (cond
             ;; ls0 jatkaa geometriaa
             (and seuraava-ls0 (= loppupiste (ensimmainen-piste seuraava-ls0)))
             (recur (conj result seuraava-ls0)
                    (viimeinen-piste seuraava-ls0)
                    (rest ls0)
                    ls1)

             ;; ls1 jatkaa geometriaa
             (and seuraava-ls1 (= loppupiste (ensimmainen-piste seuraava-ls1)))
             (recur (conj result seuraava-ls1)
                    (viimeinen-piste seuraava-ls1)
                    ls0
                    (rest ls1))

             ;; Last ditch effort: kutsutaan fallbackia etsimään
             ;; jatkopala joko seuraavaan ls0 tai ls1 pätkään.
             ;; fallback on funktio joka ottaa nykyisen loppupisteen
             :default
             (let [fallback-ls (and fallback
                                    (fallback (piste loppupiste)))]
               (cond
                 fallback-ls
                 (recur (conj result fallback-ls)
                        (viimeinen-piste fallback-ls)
                        ls0
                        ls1)

                 ;; Jos ota lähin on päällä, otetaan ls0/ls1 lähempi
                 ota-lahin?
                 (let [et0 (etaisyys-viivan-alkuun loppupiste seuraava-ls0)
                       et1 (etaisyys-viivan-alkuun loppupiste seuraava-ls1)
                       valitse (cond (or (and et0 et1 (< et0 et1))
                                         (and et0 (nil? et1)))
                                     0

                                     (or (and et0 et1 (< et1 et0))
                                         (and et1 (nil? et0)))
                                     1

                                     :default nil)]
                   (case valitse
                     ;; ls0 lähempänä
                     0
                     (recur (conj result seuraava-ls0)
                            (viimeinen-piste seuraava-ls0)
                            (rest ls0)
                            ls1)

                     ;; ls1 lähempänä
                     1
                     (recur (conj result seuraava-ls1)
                            (viimeinen-piste seuraava-ls1)
                            ls0
                            (rest ls1))

                     :default nil))

                 :default nil)))))))))

(defn- keraa-geometriat
  "Yhdistää 1-ajorataisen (ajr0) ja 2-ajorataisen halutun suunnan mukaisen osan
  viivat yhdeksi viivaksi. Osasta ei tiedetä kummalla ajoradalle se alkaa, mutta
  koko viiva on kulutettava, joten pitää yrittää molempia."
  [tie osa {g0 :the_geom :as ajr0} {g1 :the_geom :as ajr1} fallback ota-lahin?]
  (cond
    ;; Jos ei ole kumpaakaan ajorataa, palautetaan nil
    (and (nil? g0) (nil? g1))
    (do (log/error "Tie " tie ", osa " osa
                   " geometrian luonnissa virhe, molemmat ajoratageometriat nil")
        nil)

    ;; Jos toinen on nil, valitaan suoraan toinen
    (nil? g0) (jatkuva-line-string (line-string-seq g1))
    (nil? g1) (jatkuva-line-string (line-string-seq g0))

    ;; Muuten yhdistetään viivat molemmin päin
    :default
    (or (yhdista-viivat g0 g1 fallback ota-lahin?)
        (yhdista-viivat g1 g0 fallback ota-lahin?))))

(defn- alkaen-pisteesta
  "Palauttaa LineString, joka alkaa annetusta [x y] pisteestä. Jos input
  linestring ei sisällä pistettä, palauttaa nil."
  [ls alkupiste]
  (let [coords (drop-while #(not= alkupiste (piste %))
                           (seq (.getCoordinates ls)))]
    (when (> (count coords) 1)
      (luo-line-string coords))))

(defn luo-fallback [{g :the_geom}]
  (fn [alkupiste & _]
    (some #(alkaen-pisteesta % alkupiste)
          (line-string-seq g))))

(defn vie-tieosa [db tie osa osan-geometriat]
  ;; Yrittää ensin muodostaa "normaali"tapauksen, jossa tien geometria
  ;; on yhtenäinen linestring. Fallback tapauksessa joudutaan tekemään
  ;; multilinestring. Emme tiedä kummalla ajoradalla osa alkaa, joten yhdistämistä
  ;; yritetään molemmin päin joista jompi kumpi saa kaikki osat käytettyä.
  ;;
  ;; Normaalitapaus, jossa yhdistetään vain yhteinen ja haluttu ajorata
  ;; yhtenäiseksi onnistuu noin 98,4% tapauksista.
  ;;
  ;; Fallback tapaus on lainata toiselta ajoradalta pätkä, jos tällä ajoradalla
  ;; on aukko geometriassa. Esim 2-ajr geometria on puutteellinen, mutta lainaamalla
  ;; pala 1-ajr geometriasta täydentäää sen. Näitä tapauksia on noin 1,1%
  ;;
  ;; Loput geometriat ovat tapauksia, joissa linestring muodostaminen ei onnistu.
  ;; Näissä tapauksissa osan keskellä on oikeasti aukko. Tällöin käytetään vielä
  ;; fallbackia joka ottaa aina lähimmän palan jatkoksi ja muodostaa multilinestringin.
  ;; Tämä kattaa loput noin vajaa 0,5% tapauksista.
  ;;
  ;; Tieosan pituus otetaan ensimmäisen ajoradan pituudesta.
  (let [ajoradat (into {}
                       (map (juxt :ajorata identity))
                       osan-geometriat)
        oikea (or (keraa-geometriat tie osa (ajoradat 0) (ajoradat 1)
                                    (luo-fallback (ajoradat 2)) false)
                  (keraa-geometriat tie osa (ajoradat 0) (ajoradat 1)
                                    (luo-fallback (ajoradat 2)) true))
        vasen (or (keraa-geometriat tie osa (ajoradat 0) (ajoradat 2)
                                    (luo-fallback (ajoradat 1)) false)
                  (keraa-geometriat tie osa (ajoradat 0) (ajoradat 2)
                                    (luo-fallback (ajoradat 1)) true))
        tallenna-ajoradan-pituus (fn [ajorata]
                                   (when (ajoradat ajorata)
                                     (k/luo-ajoradan-pituus!
                                       db
                                       {:tie tie
                                        :osa osa
                                        :ajorata ajorata
                                        :pituus (:tr_pituus (ajoradat ajorata))})))]

    (k/vie-tien-osan-ajorata! db {:tie tie :osa osa :ajorata 1 :geom (some-> oikea str)})
    (k/vie-tien-osan-ajorata! db {:tie tie :osa osa :ajorata 2 :geom (some-> vasen str)})
    (tallenna-ajoradan-pituus 0)
    (tallenna-ajoradan-pituus 1)
    (tallenna-ajoradan-pituus 2)))

(defn vie-tieverkko-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan tieosoiteverkkoa kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]
        (k/tuhoa-tien-osien-ajoradat! db)
        (k/tuhoa-ajoratojen-pituudet! db)
        (shapefile/tuo-ryhmiteltyna
         shapefile :tie
         (fn [tien-geometriat]
           (let [tie (:tie (first tien-geometriat))]
             (doseq [[osa geometriat] (sort-by first (group-by :osa tien-geometriat))]
               (vie-tieosa db tie osa geometriat)))))

        (k/paivita-paloiteltu-tieverkko db))
      (log/debug "Tieosoiteverkon tuonti kantaan valmis."))
    (log/debug "Tieosoiteverkon tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))

(defn- lue-csv
  "Tämän funktion voi poistaa sitten, kun oikea integraatio on saatu"
  [tiedosto-polku]
  (let [raakasisalto (slurp tiedosto-polku)
        rivit (clj-str/split raakasisalto #"\r")
        muuta-rivi (fn [rivi f]
                     (map (fn [kentta]
                            (->> kentta
                                 clj-str/trim
                                 (remove #(Character/isIdentifierIgnorable %))
                                 (apply str)
                                 f))
                          (clj-str/split rivi #";")))
        otsikot (muuta-rivi (first rivit) keyword)
        datarivit (map (fn [rivi]
                         (muuta-rivi rivi identity))
                       (rest rivit))
        int-parse (fn [n]
                    (when n
                      (Integer. n)))]
    (sequence
      (comp
        (map (fn [datarivi]
               (zipmap otsikot datarivi)))
        (map (fn [m]
               (reduce-kv (fn [m k f]
                            (update m k f))
                          m {:tie int-parse
                             :ajorata int-parse
                             :kaista int-parse
                             :osa int-parse
                             :aet int-parse
                             :let int-parse
                             :tietyyppi int-parse}))))
      datarivit)))

;; TODO: Kato nämä tr-taulukot kuntoon. Käytetään väärää terminologiaa ja muutenkin dublikaatti dataa.

(defn vie-laajennettu-tieverkko-kantaan [db csv]
  (if csv
    (jdbc/with-db-transaction [db db]
      (let [tr-tiedot (map (fn [tr-tieto]
                             (-> tr-tieto
                                 (select-keys #{:tie :ajorata :kaista :osa :aet :let :tietyyppi})
                                 (clj-set/rename-keys {:tie :tr-numero
                                                       :osa :tr-osa
                                                       :aet :tr-alkuetaisyys
                                                       :let :tr-loppuetaisyys
                                                       :ajorata :tr-ajorata
                                                       :kaista :tr-kaista})))
                           (lue-csv csv)                    ;(shapefile/tuo shapefile)
                           )
            tr-tiedot-ryhmiteltyna (group-by (juxt :tr-numero :tr-osa :tr-ajorata :tr-kaista) tr-tiedot)
            ;; Data saattaa sisältää kohtia, joissa sama kaistan pätkä on pilkottu useampaan osaan.
            ;; Tämä johtuu historiallisista syistä, jolloinka tässä pilkkomiskohdassa on muuttunut
            ;; jokin joskus. Tämmöiset turhat pilkkoontumiset pitää korjata.
            ;;
            ;; esim. {:tie 1792 :tr-ajorata 0 :tr-kaista 1 :osa 6 :aet 0 :let 3126} ja
            ;;       {:tie 1792 :tr-ajorata 0 :tr-kaista 1 :osa 6 :aet 3126 :let 4647} tulee yhdistää arvoksi
            ;;       {:tie 1792 :tr-ajorata 0 :tr-kaista 1 :osa 6 :aet 0 :let 4647}
            ;; Lisäksi vähän oudompia:
            ;;       {:tie 3022, :tr-ajorata 0, :tr-kaista 1, :osa 4, :alkuetaisyys 0, :loppuetaisyys 724}
            ;;       {:tie 3022, :tr-ajorata 0, :tr-kaista 1, :osa 4, :alkuetaisyys 0, :loppuetaisyys 6910}
            ;;       {:tie 3022, :tr-ajorata 0, :tr-kaista 1, :osa 4, :alkuetaisyys 724, :loppuetaisyys 955}
            ;;       {:tie 3022, :tr-ajorata 0, :tr-kaista 1, :osa 4, :alkuetaisyys 955, :loppuetaisyys 6405}
            kaistat-paallekkain? (fn [{aet-1 :tr-alkuetaisyys let-1 :tr-loppuetaisyys}
                                      {aet-2 :tr-alkuetaisyys let-2 :tr-loppuetaisyys}]
                                   (or (and (>= aet-2 aet-1)
                                            (<= aet-2 let-1))
                                       (and (>= let-2 aet-1)
                                            (<= let-2 let-1))))
            yhdista-trt (fn this [kaistakohtaiset-trt]
                          (let [[kayttamattomat-trt kaytetyt-trt] (reduce (fn [trt tr]
                                                                            (if (contains? (meta tr) :kaytetty?)
                                                                              (update trt 1 conj tr)
                                                                              (update trt 0 conj tr)))
                                                                          [[] []] kaistakohtaiset-trt)
                                trt-kasattu (reduce (fn [v {:keys [tr-alkuetaisyys tr-loppuetaisyys tietyyppi] :as tr}]
                                                      (if (and (first v)
                                                               (kaistat-paallekkain? (first v) tr)
                                                               (= (-> v first :tietyyppi) tietyyppi))
                                                        (update v 0 (fn [vanha-tr]
                                                                      (-> vanha-tr
                                                                          (update :tr-alkuetaisyys min tr-alkuetaisyys)
                                                                          (update :tr-loppuetaisyys max tr-loppuetaisyys))))
                                                        (conj v tr)))
                                                    [] kayttamattomat-trt)
                                trt-kasattu (concat (rest trt-kasattu) (conj kaytetyt-trt
                                                                             (with-meta (first trt-kasattu)
                                                                                        {:kaytetty? true})))]
                            (if (every? #(contains? (meta %) :kaytetty?)
                                        trt-kasattu)
                              trt-kasattu
                              (this trt-kasattu))))
            tr-tiedot (mapcat #(-> % second yhdista-trt)
                              tr-tiedot-ryhmiteltyna)]
        (log/debug (str "Tuodaan laajennettua tieosoiteverkkoa kantaan tiedostosta " csv))
        (k/tuhoa-laajennettu-tien-osien-tiedot! db)
        (doseq [tr-tieto tr-tiedot]
          (k/vie-laajennettu-tien-osa-kantaan<! db tr-tieto))
        (k/paivita-tr-tiedot db)
        (log/debug "Laajennetun tieosoiteverkon tuonti kantaan valmis.")))
    (log/debug "Laajennetun tieosoiteverkon tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))

;; Tuonnin testaus REPListä:
;;(def db (:db harja.palvelin.main/harja-jarjestelma))
;;(vie-tieverkko-kantaan db "file:shp/Tieosoiteverkko/PTK_tieosoiteverkko.shp")


;; Hae tietyn tien pätkät tarkasteluun:
;; (def t (harja.shp/lue-shapefile "file:shp/Tieosoiteverkko/PTK_tieosoiteverkko.shp"))
;; (def tie110 (into [] (comp (map harja.shp/feature-propertyt) (filter #(= 110 (:tie %)))) (harja.shp/featuret t)))
