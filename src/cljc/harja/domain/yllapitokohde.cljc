(ns harja.domain.yllapitokohde
  "Ylläpitokohteiden yhteisiä apureita"
  (:require
    [harja.tyokalut.spec-apurit :as spec-apurit]
    [clojure.string :as str]
    [clojure.set :as clj-set]
    [harja.domain.tierekisteri :as tr-domain]
    [harja.domain.nil :as nil-ns]
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as gen]
    [harja.pvm :as pvm]
    #?@(:clj
        [
         [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]

         [harja.pvm :as pvm]
         [clj-time.core :as t]
         [taoensso.timbre :as log]
         [clj-time.coerce :as c]
         [harja.kyselyt
          [tieverkko :as q-tieverkko]
          [yllapitokohteet :as q-yllapitokohteet]
          [konversio :as konversio]]])))

(s/def ::id ::spec-apurit/postgres-serial)
(s/def ::kohdenumero (s/nilable string?))
(s/def ::nimi string?)
(s/def ::kokonaishinta (s/and number?))

(def ^{:doc "Sisältää vain nykyisin käytössä olevat luokat 1,2 ja 3 (eli numerot 8, 9 ja 10)."}
nykyiset-yllapitoluokat
  [{:lyhyt-nimi "1" :nimi "Luokka 1" :numero 8}
   {:lyhyt-nimi "2" :nimi "Luokka 2" :numero 9}
   {:lyhyt-nimi "3" :nimi "Luokka 3" :numero 10}
   {:lyhyt-nimi "-" :nimi "Ei ylläpitoluokkaa" :numero nil}])

(def vanhat-yllapitoluokat ^{:doc "Sisältää vanhat ylläpitoluokat, tarvitaan YHA:n kanssa taakseepäinyhteensopivuuden vuoksi."}
[{:lyhyt-nimi "1a" :nimi "Luokka 1a" :numero 1}
 {:lyhyt-nimi "1b" :nimi "Luokka 1b" :numero 2}
 {:lyhyt-nimi "1c" :nimi "Luokka 1c" :numero 3}
 {:lyhyt-nimi "2a" :nimi "Luokka 2a" :numero 4}
 {:lyhyt-nimi "2b" :nimi "Luokka 2b" :numero 5}
 {:lyhyt-nimi "3a" :nimi "Luokka 3a" :numero 6}
 {:lyhyt-nimi "3b" :nimi "Luokka 3b" :numero 7}])

(def kaikki-yllapitoluokat (concat nykyiset-yllapitoluokat vanhat-yllapitoluokat))

(s/def ::yllapitoluokka (s/int-in (apply min (keep :numero kaikki-yllapitoluokat))
                                  (inc (apply max (keep :numero kaikki-yllapitoluokat)))))

(def ^{:doc "Mahdolliset ylläpitoluokat. Nimi kertoo käyttöliittymässä käytetyn
nimen. Numero on YHA:n koodi luokalle joka talletetaan myös Harjan kantaan.
2017 alkaen pyritään käyttämään enää luokkia 1,2 ja 3 (eli numerot 8, 9 ja 10), mutta
taaksenpäinyhteensopivuuden nimissä pidetään vanhatkin luokat koodistossa."}
yllapitoluokat
  (into [] kaikki-yllapitoluokat))


(def ^{:doc "Mäppäys ylläpitoluokan numerosta sen lyhyeen nimeen."}
yllapitoluokkanumero->lyhyt-nimi
  (into {} (map (juxt :numero :lyhyt-nimi)) yllapitoluokat))

(def ^{:doc "Mäppäys ylläpitoluokan numerosta sen kokonimeen."}
yllapitoluokkanumero->nimi
  (into {} (map (juxt :numero :nimi)) yllapitoluokat))

(def ^{:doc "Mäppäys ylläpitoluokan nimestä sen numeroon."}
yllapitoluokkanimi->numero
  (into {} (map (juxt :nimi :numero)) yllapitoluokat))

(def yllapitoluokka-xf
  (map #(assoc % :yllapitoluokka {:nimi       (yllapitoluokkanumero->nimi (:yllapitoluokka %))
                                  :lyhyt-nimi (yllapitoluokkanumero->lyhyt-nimi (:yllapitoluokka %))
                                  :numero     (:yllapitoluokka %)})))

(def +kohteissa-viallisia-sijainteja+ "viallisia-sijainteja")
(def +viallinen-yllapitokohteen-sijainti+ "viallinen-kohteen-sijainti")
(def +viallinen-yllapitokohdeosan-sijainti+ "viallinen-alikohteen-sijainti")
(def +viallinen-alustatoimenpiteen-sijainti+ "viallinen-alustatoimenpiteen-sijainti")

(defn kohdenumero-str->kohdenumero-vec
  "Palauttaa \"301b\":n muodossa [301 \"b\"]"
  [kohdenumero]
  (when kohdenumero
    (let [numero (re-find #"\d+" kohdenumero)
          kirjain (re-find #"\D+" kohdenumero)]
      [(when numero
         (#?(:clj  Integer.
             :cljs js/parseInt) numero))
       kirjain])))

(defn yllapitokohteen-jarjestys
  [kohde]
  ((juxt #(kohdenumero-str->kohdenumero-vec (:kohdenumero %))
         :tie :tr-numero :tienumero
         :ajr :tr-ajorata :ajorata
         :kaista :tr-kaista
         :aosa :tr-alkuosa
         :aet :tr-alkuetaisyys) kohde))

(defn- jarjesta-yllapitokohteet*
  [kohteet]
  (sort-by yllapitokohteen-jarjestys kohteet))

(defn jarjesta-yllapitokohteet [yllapitokohteet]
  (let [kohteet-kohdenumerolla (filter #(not (str/blank? (:kohdenumero %))) yllapitokohteet)
        kohteet-ilman-kohdenumeroa (filter #(str/blank? (:kohdenumero %)) yllapitokohteet)]
    (vec (concat (jarjesta-yllapitokohteet* kohteet-kohdenumerolla)
                 (tr-domain/jarjesta-tiet kohteet-ilman-kohdenumeroa)))))

(defn losa>aosa? [{:keys [tr-alkuosa tr-loppuosa]}]
  (and tr-alkuosa tr-loppuosa (> tr-loppuosa tr-alkuosa)))

(defn let>aet? [{:keys [tr-alkuetaisyys tr-loppuetaisyys]}]
  (and tr-alkuetaisyys tr-loppuetaisyys
       (> tr-loppuetaisyys tr-alkuetaisyys)))

(defn losa=aosa? [{:keys [tr-alkuosa tr-loppuosa]}]
  (and tr-alkuosa tr-loppuosa
       (= tr-alkuosa tr-loppuosa)))

(defn let=aet? [{:keys [tr-alkuetaisyys tr-loppuetaisyys]}]
  (and tr-alkuetaisyys tr-loppuetaisyys
       (= tr-alkuetaisyys tr-loppuetaisyys)))

(defn tr-paalupiste-tr-paaluvalin-sisalla?
  "Tarkastaa onko tr-piste tr-väin sisällä. Olettaa, että annetut tr-piste ja tr-vali
   ovat oikeasti olemassa. Toisin sanoen, tälle funktiolle voi antaa vaikkapa tr-pisteeksi
   {:tr-alkuosa 10000000 :tr-alkuetaisyys -3.23} ja se antaa jonkun tuloksen ulos vaikka
   tuollaista tr-pistettä ei ole olemassa."
  ([tr-piste tr-vali] (tr-paalupiste-tr-paaluvalin-sisalla? tr-piste tr-vali true))
  ([tr-piste tr-vali sisaltaen-rajat?]
   (let [{aosa-piste :tr-alkuosa aet-piste :tr-alkuetaisyys} tr-piste
         {aosa-vali :tr-alkuosa aet-vali :tr-alkuetaisyys
          losa-vali :tr-loppuosa let-vali :tr-loppuetaisyys} tr-vali
         ;; Ajatuksena on luoda 1-D jana, jolta voi simppelisit katsoa onko piste kyseisellä janalla.
         ;; Eli kerrotan osa jollain isolla luvulla, joka on vähintään isompi kuin suurin etäisyys millään osalla.
         ;; Tähän lisätään etäisyys, jollonika meillä on jana ja piste.
         kerroin 10000000
         jana-alku (+ (* kerroin aosa-vali) aet-vali)
         jana-loppu (+ (* kerroin losa-vali) let-vali)
         piste (+ (* kerroin aosa-piste) aet-piste)]
     (if sisaltaen-rajat?
       (<= jana-alku piste jana-loppu)
       (< jana-alku piste jana-loppu)))))

(defn tr-paaluvali-tr-paaluvalin-sisalla?
  "Tarkastaa onko tr-vali-2 tr-vali-1:sen sisalla. Palauttaa true myös jos tr-vali-1 on kokonaan tr-vali2:sen sisällä
   sekä argumentti kokonaan-sisalla? on false"
  ([tr-vali-1 tr-vali-2] (tr-paaluvali-tr-paaluvalin-sisalla? tr-vali-1 tr-vali-2 true))
  ([tr-vali-1 tr-vali-2 kokonaan-sisalla?]
   (let [tr-valin-alkupiste (select-keys tr-vali-2 #{:tr-alkuosa :tr-alkuetaisyys})
         tr-valin-loppupiste (-> tr-vali-2
                                 (select-keys #{:tr-loppuosa :tr-loppuetaisyys})
                                 (clj-set/rename-keys {:tr-loppuosa      :tr-alkuosa
                                                       :tr-loppuetaisyys :tr-alkuetaisyys}))
         alkupiste-sisalla? (tr-paalupiste-tr-paaluvalin-sisalla? tr-valin-alkupiste tr-vali-1 kokonaan-sisalla?)
         loppupiste-sisalla? (tr-paalupiste-tr-paaluvalin-sisalla? tr-valin-loppupiste tr-vali-1 kokonaan-sisalla?)]
     (if kokonaan-sisalla?
       (and alkupiste-sisalla? loppupiste-sisalla?)
       (or alkupiste-sisalla? loppupiste-sisalla?
           (tr-paaluvali-tr-paaluvalin-sisalla? tr-vali-2 tr-vali-1 true))))))

(defn get-in-kohteen-tieto
  [kohteen-tieto haku]
  (let [[tr-ajorata tr-kaista tr-alkuetaisyys tr-loppuetaisyys] haku]
    (->> kohteen-tieto
         :pituudet
         :ajoradat
         (some (fn [ajoradan-tiedot]
                 (when (= (:tr-ajorata ajoradan-tiedot) tr-ajorata)
                   ajoradan-tiedot)))
         :osiot
         (filter (fn [osion-tiedot]
                   (when (and (or (nil? tr-alkuetaisyys) (>= tr-alkuetaisyys (:tr-alkuetaisyys osion-tiedot)))
                              (or (nil? tr-loppuetaisyys) (<= tr-loppuetaisyys (+ (:tr-alkuetaisyys osion-tiedot)
                                                                                  (:pituus osion-tiedot)))))
                     osion-tiedot)))
         (mapcat :kaistat)
         (keep (fn [kaistan-tiedot]
                 (when (and (= tr-kaista (:tr-kaista kaistan-tiedot))
                            (or (nil? tr-alkuetaisyys) (>= tr-alkuetaisyys (:tr-alkuetaisyys kaistan-tiedot)))
                            (or (nil? tr-loppuetaisyys) (<= tr-loppuetaisyys (+ (:tr-alkuetaisyys kaistan-tiedot)
                                                                                (:pituus kaistan-tiedot)))))
                   kaistan-tiedot))))))

(defn tr-paalupiste-tr-tiedon-mukainen?
  [paalupiste kohteen-tieto]
  (if kohteen-tieto
    (let [{tr-numero-kohde  :tr-numero
           tr-alkuosa-kohde :tr-alkuosa} paalupiste
          {tr-numero-kohteen-tieto                 :tr-numero
           tr-osa-kohteen-tieto                    :tr-osa
           {tr-alkuetaisyys-kohteen-tieto :tr-alkuetaisyys
            tr-osa-pituus                 :pituus} :pituudet} kohteen-tieto
          tr-loppuetaisyys-kohteen-tieto (+ tr-alkuetaisyys-kohteen-tieto tr-osa-pituus)
          kohteen-tieto-vali {:tr-alkuosa       tr-osa-kohteen-tieto
                              :tr-loppuosa      tr-osa-kohteen-tieto
                              :tr-alkuetaisyys  tr-alkuetaisyys-kohteen-tieto
                              :tr-loppuetaisyys tr-loppuetaisyys-kohteen-tieto}]
      (and (= tr-numero-kohde tr-numero-kohteen-tieto)
           (= tr-alkuosa-kohde tr-osa-kohteen-tieto)
           (tr-paalupiste-tr-paaluvalin-sisalla? paalupiste kohteen-tieto-vali)))
    false))

(defn tr-piste-tr-tiedon-mukainen?
  [piste kohteen-tieto]
  (let [{tr-numero-alikohde       :tr-numero
         tr-ajorata-alikohde      :tr-ajorata
         tr-kaista-alikohde       :tr-kaista
         tr-alkuosa-alikohde      :tr-alkuosa
         tr-alkuetaisyys-alikohde :tr-alkuetaisyys} piste
        kaista-kohteen-tiedot (and (= tr-numero-alikohde (:tr-numero kohteen-tieto))
                                   (= tr-alkuosa-alikohde (:tr-osa kohteen-tieto))
                                   (get-in-kohteen-tieto kohteen-tieto [tr-ajorata-alikohde tr-kaista-alikohde tr-alkuetaisyys-alikohde tr-alkuetaisyys-alikohde]))]
    (boolean kaista-kohteen-tiedot)))

(defn tr-pisteesta-paatyyn-yhtenainen?
  [piste kohteen-tieto alku?]
  (let [{tr-numero-alikohde       :tr-numero
         tr-ajorata-alikohde      :tr-ajorata
         tr-kaista-alikohde       :tr-kaista
         tr-alkuosa-alikohde      :tr-alkuosa
         tr-alkuetaisyys-alikohde :tr-alkuetaisyys} piste
        kaista-kohteen-tieto (first (get-in-kohteen-tieto kohteen-tieto [tr-ajorata-alikohde tr-kaista-alikohde tr-alkuetaisyys-alikohde tr-alkuetaisyys-alikohde]))
        osan-paatypituus (if alku?
                           (+ (-> kohteen-tieto :pituudet :tr-alkuetaisyys)
                              (-> kohteen-tieto :pituudet :pituus))
                           (-> kohteen-tieto :pituudet :tr-alkuetaisyys))]
    (and (not (nil? kaista-kohteen-tieto))
         (= tr-numero-alikohde (:tr-numero kohteen-tieto))
         (= tr-alkuosa-alikohde (:tr-osa kohteen-tieto))
         (if alku?
           (= osan-paatypituus
              (+ (:tr-alkuetaisyys kaista-kohteen-tieto)
                 (:pituus kaista-kohteen-tieto)))
           (= osan-paatypituus
              (:tr-alkuetaisyys kaista-kohteen-tieto))))))

(defn tr-pisteesta-pisteeseen-yhtenainen?
  [{tr-numero-alkupiste       :tr-numero
    tr-ajorata-alkupiste      :tr-ajorata
    tr-kaista-alkupiste       :tr-kaista
    tr-alkuosa-alkupiste      :tr-alkuosa
    tr-alkuetaisyys-alkupiste :tr-alkuetaisyys}
   {tr-numero-loppupiste       :tr-numero
    tr-ajorata-loppupiste      :tr-ajorata
    tr-kaista-loppupiste       :tr-kaista
    tr-alkuosa-loppupiste      :tr-alkuosa
    tr-alkuetaisyys-loppupiste :tr-alkuetaisyys}
   kohteen-tieto-alku kohteen-tieto-loppu]
  (let [kaista-alkukohteen-tieto (first (get-in-kohteen-tieto kohteen-tieto-alku [tr-ajorata-alkupiste tr-kaista-alkupiste tr-alkuetaisyys-alkupiste tr-alkuetaisyys-alkupiste]))
        kaista-loppukohteen-tieto (first (get-in-kohteen-tieto kohteen-tieto-loppu [tr-ajorata-loppupiste tr-kaista-loppupiste tr-alkuetaisyys-loppupiste tr-alkuetaisyys-loppupiste]))]
    (and (not (nil? kaista-alkukohteen-tieto))
         (= tr-numero-alkupiste (:tr-numero kohteen-tieto-alku))
         (= tr-alkuosa-alkupiste (:tr-osa kohteen-tieto-alku))
         (= tr-numero-loppupiste (:tr-numero kohteen-tieto-loppu))
         (= tr-alkuosa-loppupiste (:tr-osa kohteen-tieto-loppu))
         (= kaista-alkukohteen-tieto kaista-loppukohteen-tieto))))

(s/def ::positive-int? (s/and integer? #(>= % 0)))

(s/def ::tr-numero ::positive-int?)
(s/def ::tr-ajorata #{0 1 2})
(s/def ::tr-kaista #{1 11 12 13 14 15 21 22 23 24 25 31})
(s/def ::tr-alkuosa ::positive-int?)
(s/def ::tr-alkuetaisyys ::positive-int?)
(s/def ::tr-loppuosa ::positive-int?)
(s/def ::tr-loppuetaisyys ::positive-int?)

(s/def ::tr-paalupiste (s/and map?
                              (s/keys :req-un [::tr-numero
                                               ::tr-alkuosa
                                               ::tr-alkuetaisyys]
                                      :opt-un [::nil-ns/tr-loppuosa
                                               ::nil-ns/tr-loppuetaisyys])))

(s/def ::pelkka-tr-paalupiste (s/and ::tr-paalupiste
                                     (s/keys :opt-un [::nil-ns/tr-ajorata
                                                      ::nil-ns/tr-kaista])))

(s/def ::tr-piste (s/or :paalupiste ::pelkka-tr-paalupiste
                        :piste (s/and ::tr-paalupiste
                                      (s/keys :req-un [::tr-ajorata
                                                       ::tr-kaista]))))

(s/def ::tr-paaluvali (s/with-gen (s/and map?
                                         (s/keys :req-un [::tr-numero
                                                          ::tr-alkuosa
                                                          ::tr-alkuetaisyys
                                                          ::tr-loppuosa
                                                          ::tr-loppuetaisyys])
                                         (fn tr-osat-vaarin? [tr]
                                           (or (losa>aosa? tr)
                                               (losa=aosa? tr)))
                                         (fn tr-etaisyydet-vaarin? [tr]
                                           (if (losa=aosa? tr)
                                             (or (let>aet? tr)
                                                 (let=aet? tr))
                                             true)))
                                  #(gen/fmap (fn [[tr-numero tr-alkuosa tr-alkuetaisyys]]
                                               (let [semi-random-int (fn [n]
                                                                       ;; Eli ideana tässä on se, että 50% todennäköisyydellä
                                                                       ;; saadaan 0 (tämä siksi, että saadaan samalla osalla olevia
                                                                       ;; tr-osotteita todennäköisesti)
                                                                       (if (= 0 (rand-int 2))
                                                                         0
                                                                         (rand-int n)))
                                                     tr-loppuosa (+ tr-alkuosa (semi-random-int 50))
                                                     tr-loppuetaisyys (+ tr-alkuetaisyys (semi-random-int 1000))]
                                                 {:tr-numero   tr-numero :tr-alkuosa tr-alkuosa :tr-alkuetaisyys tr-alkuetaisyys
                                                  :tr-loppuosa tr-loppuosa :tr-loppuetaisyys tr-loppuetaisyys}))
                                             (gen/tuple (s/gen (s/and ::tr-numero
                                                                      (fn [n]
                                                                        (> 10000 n))))
                                                        (s/gen (s/and ::tr-alkuosa
                                                                      (fn [n]
                                                                        (> 100 n))))
                                                        (s/gen (s/and ::tr-alkuetaisyys
                                                                      (fn [n]
                                                                        (> 10000 n))))))))

(s/def ::pelkka-tr-paaluvali (s/and ::tr-paaluvali
                                    (s/keys :opt-un [::nil-ns/tr-ajorata
                                                     ::nil-ns/tr-kaista])))

(s/def ::tr-vali (s/or :paaluvali ::pelkka-tr-paaluvali
                       :vali (s/and ::tr-paaluvali
                                    (s/keys :req-un [::tr-ajorata
                                                     ::tr-kaista]))))

(s/def ::tr (s/or :vali ::tr-vali
                  :piste ::tr-piste))

(s/def ::kohteen-tieto map?)

(s/def ::kohteen-tiedot (s/coll-of ::kohteen-tieto :min-count 1))

(s/def ::kohde ::tr)

(defn kohde-tiedon-mukainen
  ([kohde kohteen-tiedot] (kohde-tiedon-mukainen kohde kohteen-tiedot true))
  ([kohde kohteen-tiedot paakohde?]
   (let [kohteen-tiedot (sort-by (juxt :tr-numero :tr-osa) kohteen-tiedot)
         tr-alkupiste (dissoc kohde :tr-loppuosa :tr-loppuetaisyys)
         tr-loppupiste (-> kohde
                           (dissoc :tr-alkuosa :tr-alkuetaisyys)
                           (clj-set/rename-keys {:tr-loppuosa      :tr-alkuosa
                                                 :tr-loppuetaisyys :tr-alkuetaisyys}))
         kohteen-tieto (fn [tr-piste]
                         (first (filter (fn [kohteen-tieto]
                                          (and (= (:tr-numero tr-piste) (:tr-numero kohteen-tieto))
                                               (= (:tr-alkuosa tr-piste) (:tr-osa kohteen-tieto))))
                                        kohteen-tiedot)))
         kohteen-tieto-alku (kohteen-tieto tr-alkupiste)
         kohteen-tieto-loppu (kohteen-tieto tr-loppupiste)
         kohteen-tiedot-vali (when-not paakohde?
                               (filter (fn [kohteen-tieto]
                                         (and (= (:tr-numero kohde) (:tr-numero kohteen-tieto))
                                              (> (:tr-osa kohteen-tieto) (:tr-alkuosa kohde))
                                              (< (:tr-osa kohteen-tieto) (:tr-loppuosa kohde))))
                                       kohteen-tiedot))
         pisteen-testaus-fn (if paakohde?
                              tr-paalupiste-tr-tiedon-mukainen?
                              tr-piste-tr-tiedon-mukainen?)
         pisteesta-paatyyn-testaus-fn (if paakohde?
                                        (constantly true)
                                        tr-pisteesta-paatyyn-yhtenainen?)
         pisteesta-pisteeseen-testaus-fn (if paakohde?
                                           (constantly true)
                                           tr-pisteesta-pisteeseen-yhtenainen?)
         validoidut-paatepisteet (keep identity
                                       (map (fn [piste tieto]
                                              (cond
                                                ;; Tietoa ei ole osalle
                                                (and (nil? tieto)
                                                     (:tr-numero piste)
                                                     (:tr-alkuosa piste)) (with-meta {:tr-numero (:tr-numero piste)
                                                                                      :tr-osa    (:tr-alkuosa piste)}
                                                                                     {:ei-osaa true})
                                                ;; Piste ei ole tiedon mukainen
                                                (not (pisteen-testaus-fn piste tieto)) tieto
                                                :else nil))
                                            [tr-alkupiste tr-loppupiste]
                                            [kohteen-tieto-alku kohteen-tieto-loppu]))
         validoitu-paatepistevali (if (= (:tr-alkuosa tr-alkupiste) (:tr-alkuosa tr-loppupiste))
                                    (when (not (pisteesta-pisteeseen-testaus-fn tr-alkupiste tr-loppupiste kohteen-tieto-alku kohteen-tieto-loppu))
                                      [tr-alkupiste tr-loppupiste])
                                    (keep identity
                                          (map (fn [piste tieto alku?]
                                                 ;; TR pisteestä päätyyn saakka ei samanlainen
                                                 (when (not (pisteesta-paatyyn-testaus-fn piste tieto alku?))
                                                   tieto))
                                               [tr-alkupiste tr-loppupiste]
                                               [kohteen-tieto-alku kohteen-tieto-loppu]
                                               [true false])))
         validoitu-vali (when-not paakohde?
                          (keep (fn [kohteen-tieto]
                                  ;; onko välillä olevat kohteet koko pituudeltaan sitä ajorataa ja kaistaa
                                  ;; mitkä on annettu
                                  (when-not (= (get-in kohteen-tieto [:pituudet :pituus])
                                               (apply max (conj (map :pituus (get-in-kohteen-tieto kohteen-tieto [(:tr-ajorata kohde) (:tr-kaista kohde) nil nil]))
                                                                -1)))
                                    kohteen-tieto))
                                kohteen-tiedot-vali))
         ongelmalliset-kohteen-tiedot (concat validoidut-paatepisteet validoitu-vali validoitu-paatepistevali)]
     (when (not (empty? ongelmalliset-kohteen-tiedot))
       {:kohde kohde :kohteen-tiedot kohteen-tiedot}))))
(defn oikean-muotoinen-tr
  ([tr] (oikean-muotoinen-tr tr ::tr))
  ([tr tr-spec]
   (s/explain-data tr-spec tr)))

(defn tr-valit-paallekkain?
  "Tetaa onko tr-2 tr-1:n kanssa päällekkäin. Jos kolmas argumentti on true, testaa onko tr-2 kokonaan tr-1 sisällä"
  ([tr-1 tr-2] (tr-valit-paallekkain? tr-1 tr-2 false))
  ([tr-1 tr-2 kokonaan-sisalla?]
   (let [tr-osoitteet [tr-1 tr-2]
         tr-spekista #(mapv (fn [spectulos]
                              (loop [[savain stulos] spectulos]
                                (if (or (nil? savain) (map? stulos))
                                  stulos
                                  (recur stulos))))
                            %)]
     (s/valid?
       (s/and
         ;; Ovathan molemmat valideja tr-osotteita
         (if kokonaan-sisalla?
           (s/tuple ::tr-vali ::tr)
           (s/tuple ::tr ::tr))
         ;; Sama tienumero, ajorata ja kaista?
         #(let [[tr-1 tr-2] (tr-spekista %)]
            (and (= (:tr-numero tr-1) (:tr-numero tr-2))
                 ;; Ideana tässä se, että tr-1 voi olla pääkohde, jolloinka sillä ei ole ajorataa ja
                 ;; tr-2 voi olla alikohde, jolloinka sillä on ajorata. Nämä voi kumminkin olla päällekkäin.
                 (or (nil? (:tr-ajorata tr-1))
                     (= (:tr-ajorata tr-1) (:tr-ajorata tr-2)))
                 (or (nil? (:tr-kaista tr-1))
                     (= (:tr-kaista tr-1) (:tr-kaista tr-2)))))
         ;; Ovatko tierekisterit päällekkäin?
         #(let [[tr-1 tr-2 :as trt] (map (fn [tr]
                                           (with-meta tr
                                                      {:tyyppi (if (s/valid? ::tr-vali tr)
                                                                 :tr-vali
                                                                 :tr-piste)}))
                                         (tr-spekista %))]
            (case (count (filter (fn [tr]
                                   (-> tr meta :tyyppi (= :tr-piste)))
                                 trt))
              ;; molemmat tr-välejä
              0 (tr-paaluvali-tr-paaluvalin-sisalla? tr-1 tr-2 kokonaan-sisalla?)
              ;; toinen tr-piste
              1 (let [[tr-piste tr-vali] (sort-by ::tr-vali trt)]
                  (tr-paalupiste-tr-paaluvalin-sisalla? tr-piste tr-vali))
              ;; molemmat tr-pisteitä
              2 (= tr-1 tr-2))))
       tr-osoitteet))))

(defn validoi-paikka
  ([kohde kohteen-tiedot] (validoi-paikka kohde kohteen-tiedot true))
  ([kohde kohteen-tiedot paakohde?]
   (when (and (not (nil? kohde))
              (not (empty? kohteen-tiedot)))
     (if paakohde?
       (kohde-tiedon-mukainen kohde kohteen-tiedot)
       (kohde-tiedon-mukainen kohde kohteen-tiedot false)))))

(defn validoi-kohde
  "Tarkistaa, että annettu pääkohde on validi."
  ([kohde kohteen-tiedot] (validoi-kohde kohde kohteen-tiedot {}))
  ([kohde kohteen-tiedot
    {:keys [vuosi] :or {vuosi (pvm/vuosi (pvm/nyt))}}]
   (let [tr-vali-spec (cond
                        (>= vuosi 2019) (s/and ::tr-paaluvali
                                               (s/keys :opt-un [::nil-ns/tr-ajorata
                                                                ::nil-ns/tr-kaista]))
                        (= vuosi 2018) ::tr-vali
                        (<= vuosi 2017) (s/and ::tr-paaluvali
                                               (s/keys :req-un [::tr-ajorata
                                                                ::tr-kaista])))
         validoitu-muoto (oikean-muotoinen-tr kohde tr-vali-spec)
         validoitu-paikka (when (empty? validoitu-muoto)
                            (validoi-paikka kohde kohteen-tiedot))]
     (cond-> nil
             (not (empty? validoitu-muoto)) (assoc :muoto validoitu-muoto)
             (not (nil? validoitu-paikka)) (assoc :validoitu-paikka validoitu-paikka)))))

(defn validoi-alikohde
  "Tarkistaa, että annettu alikohde on validi. toiset-alikohteet parametri sisältää kohteen muut alikohteet.
   Näiden kohteiden lisäksi se voi sisältää myös 'muukohde' tyylisiä kohteita."
  ([paakohde alikohde toiset-alikohteet osien-tiedot] (validoi-alikohde paakohde alikohde toiset-alikohteet osien-tiedot (pvm/vuosi (pvm/nyt))))
  ([paakohde alikohde toiset-alikohteet osien-tiedot vuosi] (validoi-alikohde paakohde alikohde toiset-alikohteet osien-tiedot vuosi nil))
  ([paakohde alikohde toiset-alikohteet osien-tiedot vuosi urakan-toiset-kohdeosat] (validoi-alikohde paakohde alikohde toiset-alikohteet osien-tiedot vuosi urakan-toiset-kohdeosat nil))
  ([paakohde alikohde toiset-alikohteet osien-tiedot vuosi urakan-toiset-kohdeosat eri-urakoiden-alikohteet]
   (let [tr-vali-spec (cond ;; s/and short circuittaa, jonka johdosta tässä täytyy luetella kaikki tr-valin avaimet pelkän ::tr-ajorata ja ::tr-kaista sijasta ja ::tr-paaluvali tulee tulla toisena.
                        ;; Eli jos tätä ei tekisi näin, niin ::tr-ajorata ja ::tr-kaista jäisi ilman virheviestiä, kun tarkastetaan tr-osoitteen muoto vaikka ne olisikin virheellisiä.
                        (>= vuosi 2019) (s/and (s/keys :req-un [::tr-numero
                                                                ::tr-ajorata
                                                                ::tr-kaista
                                                                ::tr-alkuosa
                                                                ::tr-alkuetaisyys
                                                                ::tr-loppuosa
                                                                ::tr-loppuetaisyys])
                                               ::tr-paaluvali)
                        (= vuosi 2018) ::tr-vali
                        (<= vuosi 2017) ::tr-paaluvali)
         validoitu-muoto (oikean-muotoinen-tr alikohde tr-vali-spec)
         validoitu-alikohteidenpaallekkyys (when (empty? validoitu-muoto)
                                             (filter #(tr-valit-paallekkain? alikohde %)
                                                     (concat toiset-alikohteet urakan-toiset-kohdeosat eri-urakoiden-alikohteet)))
         ;; Alikohteen tulee olla pääkohteen sisällä
         validoitu-paakohteenpaallekkyys (when (empty? validoitu-muoto)
                                           (tr-valit-paallekkain? paakohde alikohde true))
         validoitu-paikka (when (empty? validoitu-muoto)
                            (validoi-paikka alikohde osien-tiedot false))]
     (cond-> nil
             (not (empty? validoitu-alikohteidenpaallekkyys)) (assoc :alikohde-paallekkyys validoitu-alikohteidenpaallekkyys)
             (false? validoitu-paakohteenpaallekkyys) (assoc :alikohde-paakohteen-ulkopuolella? true)
             (not (empty? validoitu-muoto)) (assoc :muoto validoitu-muoto)
             (not (nil? validoitu-paikka)) (assoc :validoitu-paikka validoitu-paikka)))))

(defn validoi-muukohde
  "Tarkistaa, että annettu muukohde on validi. toiset-kohteet sisältää kaikki mahdolliset ali- ja muukohteet joita Harjassa on."
  ([paakohde muukohde toiset-kohteet osien-tiedot] (validoi-muukohde paakohde muukohde toiset-kohteet osien-tiedot (pvm/vuosi (pvm/nyt))))
  ([paakohde muukohde toiset-kohteet osien-tiedot vuosi] (validoi-muukohde paakohde muukohde toiset-kohteet osien-tiedot vuosi nil))
  ([paakohde muukohde toiset-kohteet osien-tiedot vuosi urakan-toiset-kohdeosat]
   (let [validoitu-alikohteena (-> paakohde
                                   (validoi-alikohde muukohde toiset-kohteet osien-tiedot vuosi urakan-toiset-kohdeosat)
                                   (dissoc :alikohde-paakohteen-ulkopuolella?)
                                   (clj-set/rename-keys {:alikohde-paallekkyys :muukohde-paallekkyys}))
         validoitu-alikohteena (when-not (empty? validoitu-alikohteena)
                                 validoitu-alikohteena)
         ;; Muukohde ei saa olla pääkohteen sisällä tai sen kanssa päällekkäin
         validoitu-paakohteenpaallekkyys (when-not (contains? validoitu-alikohteena :muoto)
                                           (tr-valit-paallekkain? paakohde muukohde))]
     (if validoitu-paakohteenpaallekkyys
       (assoc validoitu-alikohteena :muukohde-paakohteen-ulkopuolella? false)
       validoitu-alikohteena))))

(defn validoi-alustatoimenpide
  "Olettaa, että annetut alikohteet ovat oikein. Alikohde voi olla myös muukohde."
  ([alikohteet alustatoimenpide toiset-alustatoimenpiteet osien-tiedot] (validoi-alustatoimenpide alikohteet alustatoimenpide toiset-alustatoimenpiteet osien-tiedot (pvm/vuosi (pvm/nyt))))
  ([alikohteet alustatoimenpide toiset-alustatoimenpiteet osien-tiedot vuosi]
   (let [tr-vali-spec (cond
                        (>= vuosi 2019) (s/and ::tr-paaluvali
                                               (s/keys :req-un [::tr-ajorata
                                                                ::tr-kaista]))
                        (= vuosi 2018) ::tr-vali
                        (<= vuosi 2017) ::tr-paaluvali)
         validoitu-muoto (oikean-muotoinen-tr alustatoimenpide tr-vali-spec)
         validoitu-alustatoimenpiteiden-paallekkyys (when (empty? validoitu-muoto)
                                                      (filter #(and (tr-valit-paallekkain? alustatoimenpide %)
                                                                    (= (:kasittelymenetelma alustatoimenpide) (:kasittelymenetelma %)))
                                                              toiset-alustatoimenpiteet))
         ;; Alustatoimenpiteen pitäisi olla jonku alikohteen sisällä
         validoitu-alikohdepaallekkyys (when (empty? validoitu-muoto)
                                         (keep (fn [alikohde]
                                                 (when (tr-valit-paallekkain? alikohde alustatoimenpide true)
                                                   alikohde))
                                               alikohteet))
         validoitu-paikka (when (empty? validoitu-muoto)
                            (validoi-paikka alustatoimenpide osien-tiedot false))]
     (cond-> nil
             (not (empty? validoitu-alustatoimenpiteiden-paallekkyys)) (assoc :alustatoimenpide-paallekkyys validoitu-alustatoimenpiteiden-paallekkyys)
             (not (= 1 (count validoitu-alikohdepaallekkyys))) (assoc :paallekkaiset-alikohteet validoitu-alikohdepaallekkyys)
             (not (empty? validoitu-muoto)) (assoc :muoto validoitu-muoto)
             (not (nil? validoitu-paikka)) (assoc :validoitu-paikka validoitu-paikka)))))

;;;;;;;; Virhetekstien pohjia ;;;;;;;;;;
;; "tr-osa" on tierekisteriosa, kun taas "osa" on ajoradan osa.
(def paikka-virhetekstit
  {:tr-numero
   {:tr-osa
                {:tr-ajorata
                                    {:osa
                                              {:tr-kaista
                                                              {:ei-paaluvalia
                                                               {:sama-tr-alku-ja-loppuosa
                                                                             {:yksi-paaluvali    (fn [tr-numero-tieto tr-osa-tieto tr-ajorata tr-kaista tr-paaluvali]
                                                                                                   (str "Tie: " tr-numero-tieto " osa: " tr-osa-tieto " ajorata: " tr-ajorata " kaista: " tr-kaista " tr-paaluvali on " tr-paaluvali))
                                                                              :useampi-paaluvali (fn [tr-numero-tieto tr-osa-tieto tr-ajorata tr-kaista tr-paaluvalit]
                                                                                                   (apply str "Tie: " tr-numero-tieto " osa: " tr-osa-tieto " ajorata: " tr-ajorata " kaista: " tr-kaista " tr-paaluvalit ovat " (interpose " " tr-paaluvalit)))}
                                                                :tr-alkuosa  (fn [tr-ajorata-annettu tr-kaista-annettu tr-alkuosa-tieto]
                                                                               (str "Ajorata " tr-ajorata-annettu " ja kaista " tr-kaista-annettu " ei päätä osaa " tr-alkuosa-tieto))
                                                                :tr-loppuosa (fn [tr-ajorata-annettu tr-kaista-annettu tr-loppuosa-tieto]
                                                                               (str "Ajorata " tr-ajorata-annettu " ja kaista " tr-kaista-annettu " ei päätä osaa " tr-loppuosa-tieto))
                                                                :tr-valiosa  (fn [tr-ajorata-annettu tr-kaista-annettu tr-osa-tieto tr_alkuetaisyys tr_loppuetaisyys]
                                                                               (str "Kaista " tr-kaista-annettu " ajoradalla " tr-ajorata-annettu " ei kata koko osaa " tr-osa-tieto ". Tarkista kaista ja paaluväli (aet: " tr_alkuetaisyys ", let: " tr_loppuetaisyys ")."))}}
                                               :ei-tr-kaistaa {:yksi-kaista    (fn [tr-numero-tieto tr-osa-tieto tr-kaista-tieto]
                                                                                 (str "Tien " tr-numero-tieto " osalla " tr-osa-tieto " on ainoastaan kaista " tr-kaista-tieto))
                                                               :useampi-kaista (fn [tr-numero-tieto tr-osa-tieto tr-kaistat-tieto]
                                                                                 (apply str "Tien " tr-numero-tieto " osalla " tr-osa-tieto " on kaistat " (interpose " " tr-kaistat-tieto)))}}
                                     :ei-osaa {:sama-tr-alku-ja-loppuosa {:yksi-paaluvali    (fn [tr-ajorata-tieto tr-paaluvali]
                                                                                               (str "Ajoradan " tr-ajorata-tieto " paaluvali on " tr-paaluvali))
                                                                          :useampi-paaluvali (fn [tr-ajorata-tieto tr-paaluvalit]
                                                                                               (apply str "Ajoradan " tr-ajorata-tieto " paaluvalit ovat " (interpose " " tr-paaluvalit)))}
                                               :tr-alkuosa               (fn [tr-ajorata-annettu tr-alkuosa-tieto]
                                                                           (str "Ajorata " tr-ajorata-annettu " ei päätä osaa " tr-alkuosa-tieto))
                                               :tr-loppuosa              (fn [tr-ajorata-annettu tr-loppuosa-tieto]
                                                                           (str "Ajorata " tr-ajorata-annettu " ei aloita osaa " tr-loppuosa-tieto))
                                               :tr-valiosa               (fn [tr-kaista-annettu tr-ajorata-annettu tr-osa-tieto tr_alkuetaisyys tr_loppuetaisyys]
                                                                           (str "Kaista " tr-kaista-annettu " ajoradalla " tr-ajorata-annettu " ei kata koko osaa " tr-osa-tieto ". Tarkista kaista ja paaluväli (aet: " tr_alkuetaisyys ", let: " tr_loppuetaisyys ")."))}}
                 :ei-tr-ajorataa    (fn [tr-numero-tieto tr-osa-tieto ajorata-annettu]
                                      (str "Tien " tr-numero-tieto " osalla " tr-osa-tieto " ei ole ajorataa " ajorata-annettu))
                 :tr-osan-paaluvali (fn [tr-numero-tieto tr-osa-tieto paaluvali]
                                      (str "Tien " tr-numero-tieto " osan " tr-osa-tieto " paaluvali on " paaluvali))}
    :ei-tr-osaa (fn [tr-numero-tieto tr-osa-annettu]
                  (str "Tiellä " tr-numero-tieto " ei ole osaa " tr-osa-annettu))}})

(def muoto-virhetekstit
  {:tr-numero        {:ei-arvoa "Anna tienumero"}
   :tr-ajorata       {:ei-arvoa          "Anna ajorata"
                      :ei-saa-olla-arvoa "Ei saa olla ajorataa"}
   :tr-kaista        {:ei-arvoa          "Anna kaista"
                      :ei-saa-olla-arvoa "Ei saa olla kaistaa"}
   :tr-alkuosa       {:ei-arvoa    #?(:clj  "Anna alkuosa"
                                      :cljs "An\u00ADna al\u00ADku\u00ADo\u00ADsa")
                      :vaarin-pain #?(:clj  "Alkuosa ei voi olla loppuosan jälkeen"
                                      :cljs "Al\u00ADku\u00ADo\u00ADsa ei voi olla lop\u00ADpu\u00ADo\u00ADsan jäl\u00ADkeen")}
   :tr-alkuetaisyys  {:ei-arvoa    #?(:clj  "Anna alkuetaisyys"
                                      :cljs "An\u00ADna al\u00ADku\u00ADe\u00ADtäi\u00ADsyys")
                      :vaarin-pain #?(:clj  "Alkuetaisyys ei voi olla loppuetäisyyden jälkeen"
                                      :cljs "Alku\u00ADe\u00ADtäi\u00ADsyys ei voi olla lop\u00ADpu\u00ADe\u00ADtäi\u00ADsyy\u00ADden jäl\u00ADkeen")}
   :tr-loppuosa      {:ei-arvoa          #?(:clj  "Anna loppuosa"
                                            :cljs "An\u00ADna lop\u00ADpu\u00ADo\u00ADsa")
                      :ei-saa-olla-arvoa "Ei saa olla loppuosaa"
                      :vaarin-pain       #?(:clj  "Loppuosa ei voi olla alkuosaa ennen"
                                            :cljs "Lop\u00ADpu\u00ADosa ei voi olla al\u00ADku\u00ADo\u00ADsaa ennen")}
   :tr-loppuetaisyys {:ei-arvoa          #?(:clj  "Anna loppuetaisyys"
                                            :cljs "An\u00ADna lop\u00ADpu\u00ADe\u00ADtäi\u00ADsyys")
                      :ei-saa-olla-arvoa "Ei saa olla loppuetaisyytta"
                      :vaarin-pain       #?(:clj  "Loppuetäisyys ei voi olla ennen alkuetäisyyttä"
                                            :cljs "Lop\u00ADpu\u00ADe\u00ADtäi\u00ADsyys ei voi olla enn\u00ADen al\u00ADku\u00ADe\u00ADtäi\u00ADsyyt\u00ADtä")}})

(def paallekkaisyys-virhetekstit
  {:alikohde         {:paakohteen-ulkopuolella                      "Alikohde ei voi olla pääkohteen ulkopuolella"
                      :alikohteet-paallekkain                       (fn [nimi]
                                                                      (str "Kohteenosa on päällekkäin "
                                                                           (if (empty? nimi) "toisen osan" (str "osan \"" nimi "\""))
                                                                           " kanssa"))
                      :toisen-kohteen-alikohteen-kanssa-paallekkain (fn [validoitu-alikohde paallekkainen-kohde]
                                                                      (let [paallekkaisen-kohteen-nimi (:paakohteen-nimi paallekkainen-kohde)
                                                                            paallekkaisen-alikohteen-nimi (:yllapitokohteen-nimi paallekkainen-kohde)]
                                                                        (str "Kohteenosa (" (apply str (interpose ", "
                                                                                                                  ((juxt :tr-numero :tr-ajorata :tr-kaista :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys)
                                                                                                                    validoitu-alikohde)))
                                                                             ") on päällekkäin "
                                                                             (if (empty? paallekkaisen-kohteen-nimi)
                                                                               "toisen kohteen "
                                                                               (str "kohteen \"" paallekkaisen-kohteen-nimi "\" "))
                                                                             "kohdeosan "
                                                                             (if (empty? paallekkaisen-alikohteen-nimi)
                                                                               "kanssa"
                                                                               (str "\"" paallekkaisen-alikohteen-nimi "\" kanssa")))))
                      :toisen-urakan-alikohteen-kanssa-paallekkain  (fn [validoitu-alikohde paallekkainen-kohde]
                                                                      (str "Kohteenosa (" (apply str (interpose ", "
                                                                                                                ((juxt :tr-numero :tr-ajorata :tr-kaista :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys)
                                                                                                                  validoitu-alikohde)))
                                                                           ") on päällekkäin toisen urakan kohdeosan kanssa"))}
   :paakohde         {:paakohteet-paallekkain (fn [nimi]
                                                (str "Kohde on päällekkäin "
                                                     (if (empty? nimi) "toisen kohteen" (str "kohteen \"" nimi "\""))
                                                     " kanssa"))}
   :muukohde         {:paakohteen-sisapuolella "Muukohde ei voi olla pääkohteen kanssa samalla tiellä"}
   :alustatoimenpide {:ei-alikohteen-sisalla          "Alustatoimenpide ei ole minkään alikohteen sisällä"
                      :usean-alikohteen-sisalla       "Alustatoimenpide on päällekkäin usean alikohteen kanssa"
                      :alustatoimenpiteet-paallekkain (fn [nimi]
                                                        (str "Alustatoimenpide on päällekkäin "
                                                             (if (empty? nimi) "toisen osan" (str "osan \"" nimi "\""))
                                                             " kanssa"))}})

(defn validoidun-paikan-teksti [validoitu-paikka paakohde?]
  ;; TODO Olisi hyvä, jos validointifunktio tallentaisi kohteen tietoihin havaitsemansa ongelman nimen ja tässä vain mäpättäisiin ongelman nimi ja kohteen tiedot virheilmoitukseen.
  (mapv (fn [kohteen-tieto]
          ;; (:kohde validoitu-paikka) on käyttäjän syöttämä
          (let [{kohteen-ajorata       :tr-ajorata
                 kohteen-kaista        :tr-kaista
                 kohteen-alkuosa       :tr-alkuosa
                 kohteen-alkuetaisyys  :tr-alkuetaisyys
                 kohteen-loppuosa      :tr-loppuosa
                 kohteen-loppuetaisyys :tr-loppuetaisyys} (:kohde validoitu-paikka)
                alkupaa? (= kohteen-alkuosa (:tr-alkuosa kohteen-tieto)) ;; Käsittääkö käsiteltävä kohteen tieto kohteen alkupään
                loppupaa? (= kohteen-loppuosa (:tr-alkuosa kohteen-tieto))] ;; ...vai loppupään?
            (if (-> kohteen-tieto meta (contains? :ei-osaa))
              ;; Jos ei ole koko tr-osaa?
              ((-> paikka-virhetekstit :tr-numero :ei-tr-osaa)
                (:tr-numero kohteen-tieto) (:tr-alkuosa kohteen-tieto))
              (if paakohde?
                ((-> paikka-virhetekstit :tr-numero :tr-osa :tr-osan-paaluvali)
                  (:tr-numero kohteen-tieto) (:tr-alkuosa kohteen-tieto) (str "(" (:tr-alkuosa kohteen-tieto) ", "
                                                                              (-> kohteen-tieto :pituudet :tr-alkuetaisyys) ", "
                                                                              (:tr-osa kohteen-tieto) ", "
                                                                              (+ (-> kohteen-tieto :pituudet :tr-alkuetaisyys)
                                                                                 (-> kohteen-tieto :pituudet :pituus))
                                                                              ")"))
                (if-let [ajorata-tiedot (first (filter #(= kohteen-ajorata
                                                           (:tr-ajorata %))
                                                       (:ajoradat (:pituudet kohteen-tieto))))]
                  ;; Jos ajorata löytyy
                    (if-let [osan-tiedot (cond
                                         ;; onko sama osa?
                                           (and alkupaa? loppupaa?) (first (filter (fn [osion-tiedot]
                                                                                     (and (>= kohteen-alkuetaisyys (:tr-alkuetaisyys osion-tiedot))
                                                                                          (<= kohteen-loppuetaisyys (+ (:tr-alkuetaisyys osion-tiedot)
                                                                                                                       (:pituus osion-tiedot)))))
                                                                                   (:osiot ajorata-tiedot)))
                                           ;; onko alkupää?
                                           alkupaa? (first (filter (fn [osion-tiedot]
                                                                     (and (>= kohteen-alkuetaisyys (:tr-alkuetaisyys osion-tiedot))
                                                                          ;; Onko osa koko tr-osan pituinen?
                                                                          (= (+ (:tr-alkuetaisyys osion-tiedot)
                                                                                (:pituus osion-tiedot))
                                                                             (+ (get-in kohteen-tieto [:pituudet :tr-alkuetaisyys])
                                                                                (get-in kohteen-tieto [:pituudet :pituus])))))
                                                                   (:osiot ajorata-tiedot)))
                                         ;; onko loppupää?
                                           loppupaa? (first (filter (fn [osion-tiedot]
                                                                      (and (<= kohteen-loppuetaisyys (+ (:tr-alkuetaisyys osion-tiedot)
                                                                                                        (:pituus osion-tiedot)))
                                                                           (= (:tr-alkuetaisyys osion-tiedot) (get-in kohteen-tieto [:pituudet :tr-alkuetaisyys]))))
                                                                    (:osiot ajorata-tiedot)))
                                         ;; Muuten väliltä. Jos näin, niin pitää olla yksi yhteinäinen osio
                                           :else (when (= 1 (count (:osiot ajorata-tiedot)))
                                                   (first (:osiot ajorata-tiedot))))]
                      ;; Jos osa löytyy (eri kuin tr-osa)
                      (let [kaistojen-tiedot (filter #(= kohteen-kaista (:tr-kaista %))
                                                     (:kaistat osan-tiedot))
                            paaluvalit (map (fn [kaista-tiedot]
                                              (str "(" kohteen-alkuosa ", " (:tr-alkuetaisyys kaista-tiedot) ", "
                                                   kohteen-loppuosa ", " (+ (:tr-alkuetaisyys kaista-tiedot) (:pituus kaista-tiedot))
                                                   ")"))
                                            kaistojen-tiedot)
                            osa-virhetekstit (-> paikka-virhetekstit :tr-numero :tr-osa :tr-ajorata :osa)
                            osan-kaistat (distinct (map :tr-kaista (:kaistat osan-tiedot)))]
                        (if (empty? kaistojen-tiedot)
                          ;; Jos kaistaa ei löydy
                          (if (= 1 (count osan-kaistat))
                            ;; Jos tiellä on vain yksi kaista tällä ajoradalla
                            ((-> osa-virhetekstit :ei-tr-kaistaa :yksi-kaista)
                              (:tr-numero kohteen-tieto) (:tr-osa kohteen-tieto) (first osan-kaistat))
                            ((-> osa-virhetekstit :ei-tr-kaistaa :useampi-kaista)
                              (:tr-numero kohteen-tieto) (:tr-osa kohteen-tieto) osan-kaistat))
                          ;; Jos kaista löytyy
                          (cond
                            (and alkupaa? loppupaa?) (if (= (count kaistojen-tiedot) 1)
                                                       ((-> osa-virhetekstit :tr-kaista :ei-paaluvalia :sama-tr-alku-ja-loppuosa :yksi-paaluvali)
                                                         (:tr-numero kohteen-tieto) (:tr-osa kohteen-tieto) kohteen-ajorata kohteen-kaista (first paaluvalit))
                                                       ((-> osa-virhetekstit :tr-kaista :ei-paaluvalia :sama-tr-alku-ja-loppuosa :useampi-paaluvali)
                                                         (:tr-numero kohteen-tieto) (:tr-osa kohteen-tieto) kohteen-ajorata kohteen-kaista paaluvalit))
                            alkupaa? ((-> osa-virhetekstit :tr-kaista :ei-paaluvalia :tr-alkuosa)
                                       kohteen-ajorata kohteen-kaista (:tr-osa kohteen-tieto))
                            loppupaa? ((-> osa-virhetekstit :tr-kaista :ei-paaluvalia :tr-loppuosa)
                                        kohteen-ajorata kohteen-kaista (:tr-osa kohteen-tieto))
                            :else ((-> osa-virhetekstit :tr-kaista :ei-paaluvalia :tr-valiosa)
                                    kohteen-ajorata kohteen-kaista (:tr-osa kohteen-tieto) kohteen-alkuetaisyys kohteen-loppuetaisyys))))
                      ;; Jos osaa ei löydy
                      (let [ei-osaa-virheteksti (-> paikka-virhetekstit :tr-numero :tr-osa :tr-ajorata :ei-osaa)
                            ajoradan-paaluvalit (map (fn [osio]
                                                       (str "(" (:tr-osa kohteen-tieto) ", "
                                                            (:tr-alkuetaisyys osio) ", "
                                                            (:tr-osa kohteen-tieto) ", "
                                                            (+ (:tr-alkuetaisyys osio) (:pituus osio)) ")"))
                                                     (:osiot ajorata-tiedot))]
                        (cond
                          (and alkupaa? loppupaa?) (if (= 1 (count ajoradan-paaluvalit))
                                                     ((-> ei-osaa-virheteksti :sama-tr-alku-ja-loppuosa :yksi-paaluvali)
                                                       (:tr-ajorata ajorata-tiedot) (first ajoradan-paaluvalit))
                                                     ((-> ei-osaa-virheteksti :sama-tr-alku-ja-loppuosa :useampi-paaluvali)
                                                       (:tr-ajorata ajorata-tiedot) ajoradan-paaluvalit))
                          alkupaa? ((-> ei-osaa-virheteksti :tr-alkuosa)
                                     kohteen-ajorata (:tr-osa kohteen-tieto))
                          loppupaa? ((-> ei-osaa-virheteksti :tr-loppuosa)
                                      kohteen-ajorata (:tr-osa kohteen-tieto))
                          :else ((-> ei-osaa-virheteksti :tr-valiosa)
                                  kohteen-kaista kohteen-ajorata (:tr-osa kohteen-tieto) kohteen-alkuetaisyys kohteen-loppuetaisyys))))
                  ;; Jos ajorataa ei löydy
                  ((-> paikka-virhetekstit :tr-numero :tr-osa :ei-tr-ajorataa)
                   (:tr-numero kohteen-tieto) (:tr-osa kohteen-tieto) kohteen-ajorata))))))
        (:kohteen-tiedot validoitu-paikka)))

(defn ongelma? [virheet f]
  (->> virheet
       ::s/problems
       (filter (fn [virhe-map]
                 (f virhe-map)))
       first
       map?))

(defn validoidun-muodon-teksti [validoitu-muoto tr-avain]
  ;; s/explain-data ei valitettavasti palauta epäonnistuneen funktion nimeä vaan se nimi pitää kaivaa
  ;; oudolla tavalla tuon :pred avaimen alta, niinkuin muoto-vaarin funktiossa tehdään.
  (let [tyhja-fn (fn [avain]
                   (ongelma? validoitu-muoto (fn [virhe-map]
                                               (= #?(:clj  (or (try (-> virhe-map :pred last last)
                                                                    ;; last funktion kutsuminen symbolille aiheuttaa virheen
                                                                    (catch Exception e
                                                                      nil))
                                                               (-> virhe-map :path first))
                                                     :cljs (and (symbol? (:pred virhe-map))
                                                                (-> virhe-map :path first)))
                                                  avain))))
        pitaisi-olla-tyhja-fn (fn [avain]
                                (ongelma? validoitu-muoto (fn [virhe-map]
                                                            (and (= (first (:path virhe-map)) avain)
                                                                 (symbol? (:pred virhe-map))
                                                                 (= (:pred virhe-map) `nil?)))))
        spec-fn-nimi (cond
                       (#{:tr-alkuosa :tr-loppuosa} tr-avain) "tr-osat-vaarin?"
                       (#{:tr-alkuetaisyys :tr-loppuetaisyys} tr-avain) "tr-etaisyydet-vaarin?"
                       :else nil)
        muoto-vaarin (fn [spec-fn-nimi]
                       (ongelma? validoitu-muoto (fn [virhe-map]
                                                   (let [pred (:pred virhe-map)]
                                                     (and (sequential? pred)
                                                          (= spec-fn-nimi (str (second pred))))))))
        tyhja? (tyhja-fn tr-avain)
        pitaisi-olla-tyhja? (pitaisi-olla-tyhja-fn tr-avain)
        muoto-vaarin? (when spec-fn-nimi (muoto-vaarin spec-fn-nimi))]
    (cond-> []
            tyhja? (conj (-> muoto-virhetekstit tr-avain :ei-arvoa))
            pitaisi-olla-tyhja? (conj (-> muoto-virhetekstit tr-avain :ei-saa-olla-arvoa))
            muoto-vaarin? (conj (-> muoto-virhetekstit tr-avain :vaarin-pain)))))

(defn validoitu-kohde-tekstit [validoitu-kohde paakohde?]
  (let [{:keys [muoto alikohde-paakohteen-ulkopuolella? alikohde-paallekkyys muukohde-paallekkyys
                muukohde-paakohteen-ulkopuolella? validoitu-paikka paallekkyys]} validoitu-kohde
        paikka-vaarin (when validoitu-paikka
                        (validoidun-paikan-teksti validoitu-paikka paakohde?))
        tr-numero-vaarin (when muoto
                           (validoidun-muodon-teksti muoto :tr-numero))
        tr-ajorata-vaarin (when muoto
                            (validoidun-muodon-teksti muoto :tr-ajorata))
        tr-kaista-vaarin (when muoto
                           (validoidun-muodon-teksti muoto :tr-kaista))
        tr-alkuosa-vaarin (when muoto
                            (validoidun-muodon-teksti muoto :tr-alkuosa))
        tr-alkuetaisyys-vaarin (when muoto
                                 (validoidun-muodon-teksti muoto :tr-alkuetaisyys))
        tr-loppuosa-vaarin (when muoto
                             (validoidun-muodon-teksti muoto :tr-loppuosa))
        tr-loppuetaisyys-vaarin (when muoto
                                  (validoidun-muodon-teksti muoto :tr-loppuetaisyys))
        alikohde-ulkopuolella (when alikohde-paakohteen-ulkopuolella?
                                [(get-in paallekkaisyys-virhetekstit [:alikohde :paakohteen-ulkopuolella])])
        muukohde-sisapuolella (when (false? muukohde-paakohteen-ulkopuolella?)
                                [(get-in paallekkaisyys-virhetekstit [:muukohde :paakohteen-sisapuolella])])
        alikohteet-paallekkain (when alikohde-paallekkyys
                                 (mapv #(cond
                                          (:urakka %) ((get-in paallekkaisyys-virhetekstit [:alikohde :toisen-urakan-alikohteen-kanssa-paallekkain])
                                                        (:alikohde (meta validoitu-kohde))
                                                        %)
                                          (:paakohteen-nimi %) ((get-in paallekkaisyys-virhetekstit [:alikohde :toisen-kohteen-alikohteen-kanssa-paallekkain])
                                                                 (:alikohde (meta validoitu-kohde))
                                                                 %)
                                          :else ((get-in paallekkaisyys-virhetekstit [:alikohde :alikohteet-paallekkain]) (:nimi %)))
                                       alikohde-paallekkyys))
        paakohde-paallekkain (when paallekkyys
                               (mapv #((get-in paallekkaisyys-virhetekstit [:paakohde :paakohteet-paallekkain]) (:nimi %))
                                     paallekkyys))
        muutkohteet-paallekkain (when muukohde-paallekkyys
                                  ;; Otetaan alikohteesta, koska sama teksti
                                  (mapv #((get-in paallekkaisyys-virhetekstit [:alikohde :alikohteet-paallekkain]) (:nimi %))
                                        muukohde-paallekkyys))]
    (into {}
          (keep (fn [[k v]]
                  (let [v (keep identity v)]
                    (when-not (empty? v)
                      [k v]))))
          {:tr-numero        tr-numero-vaarin
           :tr-ajorata       (concat tr-ajorata-vaarin
                                     paikka-vaarin
                                     alikohteet-paallekkain
                                     muutkohteet-paallekkain
                                     paakohde-paallekkain)
           :tr-kaista        (concat tr-kaista-vaarin
                                     paikka-vaarin
                                     alikohteet-paallekkain
                                     muutkohteet-paallekkain
                                     paakohde-paallekkain)
           :tr-alkuosa       (concat tr-alkuosa-vaarin
                                     alikohde-ulkopuolella
                                     muukohde-sisapuolella
                                     paikka-vaarin
                                     alikohteet-paallekkain
                                     muutkohteet-paallekkain
                                     paakohde-paallekkain)
           :tr-alkuetaisyys  (concat tr-alkuetaisyys-vaarin
                                     alikohde-ulkopuolella
                                     muukohde-sisapuolella
                                     paikka-vaarin
                                     alikohteet-paallekkain
                                     muutkohteet-paallekkain
                                     paakohde-paallekkain)
           :tr-loppuosa      (concat tr-loppuosa-vaarin
                                     alikohde-ulkopuolella
                                     muukohde-sisapuolella
                                     paikka-vaarin
                                     alikohteet-paallekkain
                                     muutkohteet-paallekkain
                                     paakohde-paallekkain)
           :tr-loppuetaisyys (concat tr-loppuetaisyys-vaarin
                                     alikohde-ulkopuolella
                                     muukohde-sisapuolella
                                     paikka-vaarin
                                     alikohteet-paallekkain
                                     muutkohteet-paallekkain
                                     paakohde-paallekkain)})))

(defn validoi-alustatoimenpide-teksti [validoitu-alustatoimenpide]
  (let [kohdetekstit (validoitu-kohde-tekstit validoitu-alustatoimenpide false)
        {:keys [paallekkaiset-alikohteet alustatoimenpide-paallekkyys]} validoitu-alustatoimenpide
        paallekkaisyysteksti-alikohde (when-not (nil? paallekkaiset-alikohteet)
                                        (if (empty? paallekkaiset-alikohteet)
                                          (get-in paallekkaisyys-virhetekstit [:alustatoimenpide :ei-alikohteen-sisalla])
                                          (get-in paallekkaisyys-virhetekstit [:alustatoimenpide :usean-alikohteen-sisalla])))
        alustatoimenpiteet-paallekkain (when alustatoimenpide-paallekkyys
                                         (mapv #((get-in paallekkaisyys-virhetekstit [:alustatoimenpide :alustatoimenpiteet-paallekkain]) (:nimi %))
                                               alustatoimenpide-paallekkyys))
        lisaa-paallekkaisyysteksti (fn [kohdetekstit avain]
                                     (update kohdetekstit avain #(concat % [paallekkaisyysteksti-alikohde]
                                                                         alustatoimenpiteet-paallekkain)))]
    (if paallekkaisyysteksti-alikohde
      (-> kohdetekstit
          (lisaa-paallekkaisyysteksti :tr-ajorata)
          (lisaa-paallekkaisyysteksti :tr-kaista)
          (lisaa-paallekkaisyysteksti :tr-alkuosa)
          (lisaa-paallekkaisyysteksti :tr-alkuetaisyys)
          (lisaa-paallekkaisyysteksti :tr-loppuosa)
          (lisaa-paallekkaisyysteksti :tr-loppuetaisyys))
      kohdetekstit)))

(defn validoi-kaikki
  "Parametri kohteen-tiedot sisältää pääkohdetta vastaavat tiedot validoinnissa käytetystä csv-tiedostosta.
  Parametri muiden-kohteiden-tiedot sisältää muita kohteita vastaava tiedot validoinnissa käytetystä csv-tiedostosta."
  [tr-osoite kohteen-tiedot muiden-kohteiden-tiedot muiden-kohteiden-verrattavat-kohteet
   vuosi alikohteet muutkohteet alustatoimet urakan-toiset-kohdeosat eri-urakoiden-alikohteet]
  (let [kohde-validoitu (validoi-kohde
                          tr-osoite kohteen-tiedot {:vuosi vuosi})
        alikohteet-validoitu (keep identity
                                   (for [alikohde alikohteet
                                         :let [toiset-alikohteet (remove #(= alikohde %) alikohteet)
                                               validoitu-alikohde (validoi-alikohde tr-osoite alikohde toiset-alikohteet kohteen-tiedot vuosi urakan-toiset-kohdeosat eri-urakoiden-alikohteet)]]
                                     (when validoitu-alikohde
                                       (with-meta validoitu-alikohde
                                                  {:alikohde (select-keys alikohde tr-domain/vali-avaimet)}))))
        muutkohteet-validoitu (keep identity
                                    (for [muukohde muutkohteet
                                          :let [toiset-muutkohteet (remove #(= muukohde %)
                                                                           (first (filter #(-> % first :tr-numero (= (:tr-numero muukohde)))
                                                                                          muiden-kohteiden-verrattavat-kohteet)))
                                                kohteen-tiedot (some #(when (and (= (:tr-numero (first %)) (:tr-numero muukohde))
                                                                                 (= (:tr-osa (first %)) (:tr-alkuosa muukohde)))
                                                                        %)
                                                                     muiden-kohteiden-tiedot)]]
                                      (validoi-muukohde tr-osoite muukohde toiset-muutkohteet kohteen-tiedot vuosi urakan-toiset-kohdeosat)))
        alustatoimet-validoitu (keep identity
                                     (for [alustatoimi alustatoimet
                                           :let [toiset-alustatoimenpiteet (remove #(= alustatoimi %) alustatoimet)]]
                                       (validoi-alustatoimenpide alikohteet alustatoimi toiset-alustatoimenpiteet kohteen-tiedot vuosi)))]
    (cond-> {}
            (not (empty? kohde-validoitu)) (assoc :paakohde [(validoitu-kohde-tekstit kohde-validoitu true)])
            (not (empty? alikohteet-validoitu)) (assoc :alikohde (map #(validoitu-kohde-tekstit % false) alikohteet-validoitu))
            (not (empty? muutkohteet-validoitu)) (assoc :muukohde (map #(validoitu-kohde-tekstit % false) muutkohteet-validoitu))
            (not (empty? alustatoimet-validoitu)) (assoc :alustatoimenpide (map #(validoi-alustatoimenpide-teksti %) alustatoimet-validoitu)))))

#?(:clj
   (defn validoi-kaikki-backilla
     ([db kohde-id urakka-id vuosi tr-osoite ali-ja-muut-kohteet alustatoimet] (validoi-kaikki-backilla db kohde-id urakka-id vuosi tr-osoite ali-ja-muut-kohteet alustatoimet nil))
     ([db kohde-id urakka-id vuosi tr-osoite ali-ja-muut-kohteet alustatoimet urakan-toiset-kohdeosat]
      (let [;; Otetaan tarkastukseen ne kohdeosat, jotka ovat jonkun sellaisen kohteen
            ;; alikohteita, mikä kuuluu tähän urakkaan
            urakan-toiset-kohdeosat (or urakan-toiset-kohdeosat
                                        (remove (fn [kohdeosa]
                                                  (some #(= (:id kohdeosa)
                                                            (:id %))
                                                        ali-ja-muut-kohteet))
                                                (q-yllapitokohteet/hae-yhden-vuoden-kohdeosat-urakalle db {:vuosi vuosi
                                                                                                           :kohde-id kohde-id
                                                                                                           :urakka-id urakka-id})))
            verrattavat-kohteet (fn [yhden-vuoden-kohteet kohde-id urakka-id]
                                  (sequence
                                    (comp (remove
                                            (fn [verrattava-kohde]
                                              (= (:id verrattava-kohde)
                                                 kohde-id)))
                                          ;; Tässä on tarkoituksena, ettei näytetä muiden urakoiden kohteiden tietoja virheviesteissä
                                          (map #(update % :urakka (fn [nimi]
                                                                    (when (= (:urakka-id %) urakka-id)
                                                                      nimi)))))
                                    yhden-vuoden-kohteet))
            ;; Otetaan mukaan tarkastukseen ne kohdeosat, jotka ovat jonkun sellaisen kohteen
            ;; alikohteita, mikä on tämän kohteen kanssa päällekkäin
            eri-urakoiden-alikohteet (let [kohteet (keep (fn [kohde]
                                                           (when (and (not= (:id kohde) kohde-id)
                                                                      (tr-valit-paallekkain? kohde tr-osoite))
                                                             kohde))
                                                         (q-yllapitokohteet/hae-yhden-vuoden-yha-kohteet db {:vuosi vuosi}))]
                                       (when-not (empty? kohteet)
                                         (map (fn [yllapitokohdeosa]
                                                ;; Tämä mergeäminen tehdään virheviestin muodostamista varten
                                                (merge yllapitokohdeosa
                                                       (some #(when (= (:yllapitokohde-id yllapitokohdeosa)
                                                                       (:id %))
                                                                (select-keys % #{:urakka :urakka-id}))
                                                             kohteet)))
                                              (q-yllapitokohteet/hae-urakan-yllapitokohteiden-yllapitokohdeosat db {:idt (map :id kohteet)}))))
            kohteen-tiedot (map #(update % :pituudet konversio/jsonb->clojuremap)
                                (q-tieverkko/hae-trpisteiden-valinen-tieto db
                                                                           (select-keys tr-osoite #{:tr-numero :tr-alkuosa :tr-loppuosa})))
            alikohteet (filter #(= (:tr-numero %) (:tr-numero tr-osoite))
                               ali-ja-muut-kohteet)
            muutkohteet (filter #(not= (:tr-numero %) (:tr-numero tr-osoite))
                                ali-ja-muut-kohteet)
            yhden-vuoden-muut-kohteet (map #(q-yllapitokohteet/hae-yhden-vuoden-muut-kohdeosat db {:vuosi vuosi :tr-numero (:tr-numero %)})
                                           muutkohteet)
            muiden-kohteiden-tiedot (for [muukohde muutkohteet]
                                      (map #(update % :pituudet konversio/jsonb->clojuremap)
                                           (q-tieverkko/hae-trpisteiden-valinen-tieto db
                                                                                      (select-keys muukohde #{:tr-numero :tr-alkuosa :tr-loppuosa}))))
            muiden-kohteiden-verrattavat-kohteet (map (fn [muukohde toiset-kohteet]
                                                        (verrattavat-kohteet toiset-kohteet (:id muukohde) urakka-id))
                                                      muutkohteet yhden-vuoden-muut-kohteet)]
        (validoi-kaikki tr-osoite kohteen-tiedot
                        muiden-kohteiden-tiedot muiden-kohteiden-verrattavat-kohteet
                        vuosi alikohteet muutkohteet alustatoimet
                        urakan-toiset-kohdeosat eri-urakoiden-alikohteet)))))


#?(:clj
   (defn yllapitokohteen-tarkka-tila [yllapitokohde]
     (cond
       (and (:kohde-valmispvm yllapitokohde)
            (pvm/sama-tai-ennen? (pvm/suomen-aikavyohykkeeseen (c/from-sql-date (:kohde-valmispvm yllapitokohde)))
                                 (pvm/nyt-suomessa)))
       :kohde-valmis

       (and (:tiemerkinta-loppupvm yllapitokohde)
            (pvm/sama-tai-ennen? (pvm/suomen-aikavyohykkeeseen (c/from-sql-date (:tiemerkinta-loppupvm yllapitokohde)))
                                 (pvm/nyt-suomessa)))
       :tiemerkinta-valmis

       (and (:tiemerkinta-alkupvm yllapitokohde)
            (pvm/sama-tai-ennen? (pvm/suomen-aikavyohykkeeseen (c/from-sql-date (:tiemerkinta-alkupvm yllapitokohde)))
                                 (pvm/nyt-suomessa)))
       :tiemerkinta-aloitettu

       (and (:paallystys-loppupvm yllapitokohde)
            (pvm/sama-tai-ennen? (pvm/suomen-aikavyohykkeeseen (c/from-sql-date (:paallystys-loppupvm yllapitokohde)))
                                 (pvm/nyt-suomessa)))
       :paallystys-valmis

       (and (:paallystys-alkupvm yllapitokohde)
            (pvm/sama-tai-ennen? (pvm/suomen-aikavyohykkeeseen (c/from-sql-date (:paallystys-alkupvm yllapitokohde)))
                                 (pvm/nyt-suomessa)))
       :paallystys-aloitettu

       (and (:paikkaus-loppupvm yllapitokohde)
            (pvm/sama-tai-ennen? (pvm/suomen-aikavyohykkeeseen (c/from-sql-date (:paikkaus-loppupvm yllapitokohde)))
                                 (pvm/nyt-suomessa)))
       :paikkaus-valmis

       (and (:paikkaus-alkupvm yllapitokohde)
            (pvm/sama-tai-ennen? (pvm/suomen-aikavyohykkeeseen (c/from-sql-date (:paikkaus-alkupvm yllapitokohde)))
                                 (pvm/nyt-suomessa)))
       :paikkaus-aloitettu

       (and (:kohde-alkupvm yllapitokohde)
            (pvm/sama-tai-ennen? (pvm/suomen-aikavyohykkeeseen (c/from-sql-date (:kohde-alkupvm yllapitokohde)))
                                 (pvm/nyt-suomessa)))
       :kohde-aloitettu

       :default
       :ei-aloitettu)))

(defn kuvaile-kohteen-tila [tila]
  (case tila
    :ei-aloitettu "Ei aloitettu"
    :kohde-aloitettu "Kohde aloitettu"
    :paallystys-aloitettu "Päällystys aloitettu"
    :paallystys-valmis "Päällystys valmis"
    :paikkaus-aloitettu "Paikkaus aloitettu"
    :paikkaus-valmis "Paikkaus valmis"
    :tiemerkinta-aloitettu "Tiemerkintä aloitettu"
    :tiemerkinta-valmis "Tiemerkintä valmis"
    :kohde-valmis "Kohde valmis"
    "Ei tiedossa"))

(defn yllapitokohteen-tila-kartalla [tarkka-tila]
  (case tarkka-tila
    :ei-aloitettu :ei-aloitettu
    :kohde-aloitettu :kesken
    :paallystys-aloitettu :kesken
    :paallystys-valmis :kesken
    :paikkaus-aloitettu :kesken
    :paikkaus-valmis :kesken
    :tiemerkinta-aloitettu :kesken
    :tiemerkinta-valmis :valmis
    :kohde-valmis :valmis))

(defn kuvaile-kohteen-tila-kartalla [tila]
  (case tila
    :valmis "Valmis"
    :kesken "Kesken"
    :ei-aloitettu "Ei aloitettu"
    "Ei tiedossa"))

(def yllapitokohde-kartalle-xf
  ;; Ylläpitokohde näytetään kartalla 'kohdeosina'.
  ;; Tämä transducer olettaa saavansa vectorin ylläpitokohteita ja palauttaa
  ;; ylläpitokohteiden kohdeosat valmiina näytettäväksi kartalle.
  ;; Palautuneilla kohdeosilla on pääkohteen tiedot :yllapitokohde avaimen takana.
  (comp
    (mapcat (fn [kohde]
              (keep (fn [kohdeosa]
                      (assoc kohdeosa :yllapitokohde (dissoc kohde :kohdeosat)
                                      :tyyppi-kartalla (:yllapitokohdetyotyyppi kohde)
                                      :tila (:tila kohde)
                                      :yllapitokohde-id (:id kohde)))
                    (:kohdeosat kohde))))
    (keep #(and (:sijainti %) %))))

(defn yllapitokohteen-kokonaishinta [{:keys [sopimuksen-mukaiset-tyot maaramuutokset toteutunut-hinta
                                             bitumi-indeksi arvonvahennykset kaasuindeksi sakot-ja-bonukset]}]
  (reduce + 0 (remove nil? [sopimuksen-mukaiset-tyot        ;; Sama kuin kohteen tarjoushinta
                            maaramuutokset                  ;; Kohteen määrämuutokset summattuna valmiiksi yhteen
                            arvonvahennykset                ;; Sama kuin arvonmuutokset
                            sakot-ja-bonukset               ;; Sakot ja bonukset summattuna valmiiksi yhteen.
                            ;; HUOM. sillä oletuksella, että sakot ovat miinusta ja bonukset plussaa.
                            bitumi-indeksi
                            kaasuindeksi
                            toteutunut-hinta                ;; Kohteen toteutunut hinta (vain paikkauskohteilla)
                            ])))

(defn yllapitokohde-tekstina
  "Näyttää ylläpitokohteen kohdenumeron ja nimen.

  Optiot on map, jossa voi olla arvot:
  osoite              Kohteen tierekisteriosoite.
                      Näytetään sulkeissa kohteen tietojen perässä sulkeissa, jos löytyy."
  ([kohde] (yllapitokohde-tekstina kohde {}))
  ([kohde optiot]
   (let [kohdenumero (or (:kohdenumero kohde) (:numero kohde) (:yllapitokohdenumero kohde))
         nimi (or (:nimi kohde) (:yllapitokohdenimi kohde))
         osoite (when-let [osoite (:osoite optiot)]
                  (let [tr-osoite (tr-domain/tierekisteriosoite-tekstina osoite {:teksti-ei-tr-osoitetta? false
                                                                                 :teksti-tie?             false})]
                    (when-not (empty? tr-osoite)
                      (str " (" tr-osoite ")"))))]
     (str kohdenumero " " nimi osoite))))

(defn lihavoi-vasta-muokatut [rivit]
  (let [viikko-sitten (pvm/paivaa-sitten 7)]
    (map (fn [{:keys [muokattu aikataulu-muokattu] :as rivi}]
           (assoc rivi :lihavoi
                       (or (and muokattu (pvm/ennen? viikko-sitten muokattu))
                           (and aikataulu-muokattu (pvm/ennen? viikko-sitten aikataulu-muokattu)))))
         rivit)))

(def tarkan-aikataulun-toimenpiteet [:murskeenlisays :ojankaivuu :rp_tyot :rumpujen_vaihto :sekoitusjyrsinta :muu])
(def tarkan-aikataulun-toimenpide-fmt
  {:ojankaivuu       "Ojankaivuu"
   :rp_tyot          "RP-työt"
   :rumpujen_vaihto  "Rumpujen vaihto"
   :sekoitusjyrsinta "Sekoitusjyrsintä"
   :murskeenlisays   "Murskeenlisäys"
   :muu              "Muu"})
