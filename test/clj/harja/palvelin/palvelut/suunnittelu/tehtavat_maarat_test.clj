(ns harja.palvelin.palvelut.suunnittelu.tehtavat-maarat-test
  (:require [clojure.test :refer [deftest testing use-fixtures compose-fixtures is]]
            [clojure.java.jdbc :as jdbc :refer [with-db-connection query]]
            [harja.palvelin.palvelut.suunnittelu.tehtavat-maarat-palvelu :as tm-palvelu]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.tyokalut.yleiset :refer :all]
            [harja.kyselyt.tehtavat-maarat-kyselyt :as tm-kyselyt]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (luo-testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :tehtavat-maarat (component/using
                             (tm-palvelu/->TehtavatJaMaarat)
                             [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

;; === Hoitokausi utility-funktiot ===

(defn hoitokauden-alku-pvm
  "Palauttaa hoitokauden alkupäivämäärän (1.10.vuosi) java.sql.Date-muodossa."
  [alkuvuosi]
  (java.sql.Date/valueOf (format "%d-10-01" alkuvuosi)))

(defn hoitokauden-loppu-pvm
  "Palauttaa hoitokauden loppupäivämäärän (30.9.vuosi+1) java.sql.Date-muodossa."
  [alkuvuosi]
  (java.sql.Date/valueOf (format "%d-09-30" (inc alkuvuosi))))

(defn paiva-hoitokaudella?
  "Testaa onko päivä hoitokauden sisällä."
  [pvm alkuvuosi]
  (let [alku (hoitokauden-alku-pvm alkuvuosi)
        loppu (hoitokauden-loppu-pvm alkuvuosi)]
    (and (>= (.compareTo pvm alku) 0)
         (<= (.compareTo pvm loppu) 0))))

(defn varmista-tarjousrivi!
  "Varmistaa että annetulle urakalle, hoitokaudelle ja tehtävälle on tarjousrivi.
   Palauttaa tarjousrivin id:n."
  [urakka-id hoitokauden-alkuvuosi tehtava-id]
  (u (format "INSERT INTO urakka_tehtavamaara (urakka, \"hoitokauden-alkuvuosi\", tehtava, maara, luoja, luotu, poistettu)
              VALUES (%s, %s, %s, 0, (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'), NOW(), false)
              ON CONFLICT (urakka, \"hoitokauden-alkuvuosi\", tehtava) DO NOTHING"
             urakka-id hoitokauden-alkuvuosi tehtava-id)))

(defn luo-muutos!
  "Luo uuden MHU-muutoksen ja siihen liittyvän tehtävä-määräluettelon.
   Parametrit:
   - urakka-id: Urakan id
   - voimassa-alkaen: Päivämäärä (String tai Date)
   - tehtava-id: Tehtävän id
   - hoitokauden-alkuvuosi: Hoitokauden alkuvuosi
   - maaramuutos: Määrän muutos
   - syy: Syy muutokselle (String)
   
   Palauttaa muutoksen id:n."
  [urakka-id voimassa-alkaen tehtava-id hoitokauden-alkuvuosi maaramuutos syy]
  (let [muutos-id (i (format "INSERT INTO mhu_muutos (urakka, voimassa_alkaen, syy, luoja, luotu, poistettu)
                              VALUES (%s, '%s', '%s', 
                                      (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'), NOW(), false)"
                             urakka-id voimassa-alkaen syy))]
    (u (format "INSERT INTO mhu_muutos_tehtava_ja_maaraluettelo (muutos, tehtava, hoitokauden_alkuvuosi, maaramuutos, versio)
                VALUES (%s, %s, %s, %s, 1)"
               muutos-id tehtava-id hoitokauden-alkuvuosi maaramuutos))
    muutos-id))

(defn luo-muutos-usealle-tehtavalle!
  "Luo uuden MHU-muutoksen usealle tehtävälle eri määrämuutoksilla ja versioilla.
   Käytetään erityisesti versio-suodatustesteihin.
   
   Parametrit:
   - urakka-id: Urakan id
   - voimassa-alkaen: Päivämäärä (String tai Date)
   - hoitokauden-alkuvuosi: Hoitokauden alkuvuosi
   - syy: Syy muutokselle (String)
   - tehtavat: Vector of maps [{:tehtava-id X :maaramuutos Y :versio Z}]
   
   Palauttaa muutoksen id:n."
  [urakka-id voimassa-alkaen hoitokauden-alkuvuosi syy tehtavat]
  (let [muutos-id (i (format "INSERT INTO mhu_muutos (urakka, voimassa_alkaen, syy, luoja, luotu, poistettu)
                              VALUES (%s, '%s', '%s', 
                                      (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'), NOW(), false)"
                             urakka-id voimassa-alkaen syy))]
    (doseq [{:keys [tehtava-id maaramuutos versio]} tehtavat]
      (u (format "INSERT INTO mhu_muutos_tehtava_ja_maaraluettelo (muutos, tehtava, hoitokauden_alkuvuosi, maaramuutos, versio)
                  VALUES (%s, %s, %s, %s, %s)"
                 muutos-id tehtava-id hoitokauden-alkuvuosi maaramuutos versio)))
    muutos-id))

;; === VIEW-kyselyt ===

(defn hae-view-muutossumma
  "Hakee yhteenveto-VIEW:stä muutossumman annetulle urakalle, tehtävälle ja hoitokaudelle.
   Palauttaa BigDecimal-arvon, 0M jos riviä ei löydy."
  [urakka-id tehtava-id hoitokauden-alkuvuosi]
  (or (:muutossumma
        (first (q-map (format "SELECT COALESCE(muutossumma, 0) as muutossumma 
                               FROM urakka_tehtavamaara_yhteenveto 
                               WHERE urakka = %s AND tehtava = %s AND hoitokauden_alkuvuosi = %s"
                              urakka-id tehtava-id hoitokauden-alkuvuosi))))
      0M))

(defn hae-muutokset-kannasta
  "Hakee muutokset suoraan kannasta testaustarkoituksiin.
   Palauttaa muutosten tiedot mukaan lukien WHERE-ehdon tuloksen."
  [urakka-id tehtava-id hoitokauden-alkuvuosi & muutos-ids]
  (q-map (format "SELECT m.id, m.voimassa_alkaen, mmtml.maaramuutos, m.syy,
                         m.voimassa_alkaen <= make_date(mmtml.hoitokauden_alkuvuosi + 1, 9, 30) AS pitaa_nakya
                  FROM mhu_muutos m
                  JOIN mhu_muutos_tehtava_ja_maaraluettelo mmtml ON m.id = mmtml.muutos
                  WHERE m.urakka = %s 
                    AND mmtml.tehtava = %s 
                    AND mmtml.hoitokauden_alkuvuosi = %s
                    AND m.poistettu IS NOT TRUE
                    AND m.id IN (%s)
                  ORDER BY m.voimassa_alkaen"
                 urakka-id tehtava-id hoitokauden-alkuvuosi
                 (clojure.string/join ", " muutos-ids))))

;; === Assertion-helperit ===

(defn assert-muutossumma=
  "Assertoi että VIEW:n muutossumma vastaa odotettua. Tulostaa hyödyllisen virheviestin."
  [urakka-id tehtava-id hoitokauden-alkuvuosi odotettu & [muutokset]]
  (let [tulos (hae-view-muutossumma urakka-id tehtava-id hoitokauden-alkuvuosi)]
    (is (= tulos odotettu)
        (str "VIEW:n muutossumma: " tulos 
             " (odotettu: " odotettu ")"
             (when muutokset 
               (str "\nMuutokset kannassa: " muutokset))))))

;; === Tehtävä-helperit ===

(defn hae-tehtava-id-nimella
  "Hakee tehtävän id:n nimen perusteella tietokannasta.
   Palauttaa nil jos tehtävää ei löydy."
  [nimi]
  (:id (first (q-map (format "SELECT id FROM tehtava WHERE nimi = '%s' LIMIT 1" nimi)))))

(defn hae-tehtava-nimella
  "Hakee tehtävän nimen perusteella tehtävälistasta"
  [tehtavat nimi]
  (first (filter #(and (:tehtava_id %)
                    (= nimi (:nimi %)))
           tehtavat)))

(defn hae-tehtavat-urakan-hoitovuodelle
  "Hakee tehtävät ja määrät annetulle urakalle ja hoitovuodelle"
  [db urakka-nimi hoitovuoden-alkuvuosi]
  (let [urakka-id (hae-urakan-id-nimella urakka-nimi)]
    (:tehtavat (tm-kyselyt/hae-tehtavat-ja-maarat db urakka-id hoitovuoden-alkuvuosi))))

(defn tarkista-muutoksen-rakenne
  "Tarkistaa että muutoksessa on kaikki vaaditut kentät"
  [muutos]
  (and (contains? muutos :id)
    (contains? muutos :edellinen_maara)
    (contains? muutos :maaramuutos)
    (contains? muutos :uusi_maara)
    (contains? muutos :voimassa_alkaen)
    (contains? muutos :syy)))

(defn tarkista-muutoksen-laskenta
  "Tarkistaa että uusi_maara = edellinen_maara + maaramuutos"
  [muutos]
  (when (and (:edellinen_maara muutos)
          (:maaramuutos muutos)
          (:uusi_maara muutos))
    (= (:uusi_maara muutos)
       (+ (:edellinen_maara muutos)
          (:maaramuutos muutos)))))

(defn tarkista-muutosten-kumuloituminen
  "Tarkistaa että peräkkäiset muutokset kumuloituvat oikein"
  [muutos1 muutos2]
  (= (:uusi_maara muutos1)
     (:edellinen_maara muutos2)))

(deftest hae-tehtavat-ja-maarat-tietokannasta-onnistuneesti
  (testing "Hoitovuosi 2021"
    (let [db (:db jarjestelma)
          urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
          hoitovuoden-alkuvuosi 2021
          tehtavat (:tehtavat (tm-kyselyt/hae-tehtavat-ja-maarat db urakka-id hoitovuoden-alkuvuosi))
          tehtavaryhmat (filter #(not (nil? (:valiotsikko %))) tehtavat)]

      (is (= (:nimi (first tehtavat)) "1.0 TALVIHOITO") "Ensimmäinen rivi on talvihoidon tehtäväryhmä")
      (is (= (count tehtavaryhmat) 13) "Tehtäväryhmiä on 13")))

  (testing "Hoitovuosi 2022"
    (let [db (:db jarjestelma)
          urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
          hoitovuoden-alkuvuosi 2022
          tehtavat (:tehtavat (tm-kyselyt/hae-tehtavat-ja-maarat db urakka-id hoitovuoden-alkuvuosi))
          tehtavaryhmat (filter #(not (nil? (:valiotsikko %))) tehtavat)]

      (is (= (:nimi (first tehtavat)) "1.0 TALVIHOITO") "Ensimmäinen rivi on talvihoidon tehtäväryhmä")
      (is (= (count tehtavaryhmat) 13) "Tehtäväryhmiä on 13"))))

(deftest tallenna-tarjouksen-tehtavat-ja-maarat-onnistuneesti
  (let [db (:db jarjestelma)]
    (testing "Talletetaan tehtävät ja määrät hoitovuodelle 2024"
      (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
            kayttaja-id (:id +kayttaja-jvh+)
            hoitokauden-alkuvuosi 2024
            tehtavat (tm-kyselyt/hae-tehtavat-ja-maarat db urakka-id hoitokauden-alkuvuosi)
            ; Ise 2-ajorat, Ise 1-ajorat, Ise rampit
            tehtava-idt (map :tehtava_id
                          (take 3 (filter (fn [tehtava]
                                            (not (nil? (:tehtava_id tehtava)))) (:tehtavat tehtavat))))

            ;; Muokataan muutamia määriä
            tehtavat (mapv (fn [t]
                             (cond
                               (= (:tehtava_id t) (nth tehtava-idt 0)) (assoc t :tarjous_maara 10) ; Ise 2-ajorat.
                               (= (:tehtava_id t) (nth tehtava-idt 1)) (assoc t :tarjous_maara 20) ; Ise 1-ajorat.
                               (= (:tehtava_id t) (nth tehtava-idt 2)) (assoc t :tarjous_maara 30) ; Ise rampit
                               :else t))
                       (:tehtavat tehtavat))]
        (tm-kyselyt/tallenna-tarjouksen-tehtavat-ja-maarat db urakka-id kayttaja-id hoitokauden-alkuvuosi tehtavat)

        ;; Haetaan talletetut tehtävät ja määrät
        (let [talteenotetut-tehtavat (:tehtavat (tm-kyselyt/hae-tehtavat-ja-maarat db urakka-id hoitokauden-alkuvuosi))
              talteenotetut-tehtavat (filter #(contains? (into #{} tehtava-idt) (:tehtava_id %)) talteenotetut-tehtavat)
              tehtava1 (first (filter #(= (nth tehtava-idt 0) (:tehtava_id %)) talteenotetut-tehtavat))
              tehtava2 (first (filter #(= (:tehtava_id %) (nth tehtava-idt 1)) talteenotetut-tehtavat))
              tehtava3 (first (filter #(= (:tehtava_id %) (nth tehtava-idt 2)) talteenotetut-tehtavat))]

          (is (= (:tarjous_maara tehtava1) 10M) "Ise 2-ajorat (eli tehtävä1) määrä on tallennettu oikein")
          (is (= (:tarjous_maara tehtava2) 20M) "Ise 1-ajorat (eli tehtävä2) määrä on tallennettu oikein")
          (is (= (:tarjous_maara tehtava3) 30M) "Ise rampit (eli tehtävä3) määrä on tallennettu oikein"))))))

(deftest ei-tallenna-nulleja-sopimuksen-maariin
  (testing "Tallennus ei saa kirjoittaa NULL-maaria niille tehtäville joihin ei ole koskettu"
    (let [db (:db jarjestelma)
          urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
          kayttaja-id (:id +kayttaja-jvh+)
          hoitokauden-alkuvuosi 2024
          haettu (tm-kyselyt/hae-tehtavat-ja-maarat db urakka-id hoitokauden-alkuvuosi)
          kaikki-rivit (:tehtavat haettu)
          tehtavarivit (filter (fn [rivi]
                                (some? (:tehtava_id rivi)))
                       kaikki-rivit)
          muokattava-tehtava-id (:tehtava_id (first tehtavarivit))
          nil-tehtava-id (:tehtava_id (second tehtavarivit))
          tehtavat (mapv (fn [t]
                           (cond
                             (= (:tehtava_id t) muokattava-tehtava-id) (assoc t :tarjous_maara 100M)
                             (= (:tehtava_id t) nil-tehtava-id) (assoc t :tarjous_maara nil)
                             :else t))
                    kaikki-rivit)]

      (is (some? muokattava-tehtava-id) "Testidata: löytyi muokattava tehtävä")
      (is (some? nil-tehtava-id) "Testidata: löytyi toinen tehtävä, jolle asetetaan nil")

      (tm-kyselyt/tallenna-tarjouksen-tehtavat-ja-maarat db urakka-id kayttaja-id hoitokauden-alkuvuosi tehtavat)

      (let [nulleja (or (:lkm (first (q-map (format "SELECT COUNT(*) AS lkm\n                                             FROM urakka_tehtavamaara\n                                            WHERE urakka = %s\n                                              AND \"hoitokauden-alkuvuosi\" = %s\n                                              AND maara IS NULL"
                                            urakka-id hoitokauden-alkuvuosi))))
                    0)]
        (is (= 0 nulleja)
            (str "Kantaan ei saa tallentua NULL-maaria. NULL-riveja: " nulleja))))))

(deftest muutokset-sisaltavat-oikeat-kentat
  (testing "Muutokset-kentässä on edellinen_maara, maaramuutos ja uusi_maara oikein"
    (let [db (:db jarjestelma)
          urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
          hoitovuoden-alkuvuosi 2025
          tehtavat (:tehtavat (tm-kyselyt/hae-tehtavat-ja-maarat db urakka-id hoitovuoden-alkuvuosi))
          ;; Haetaan tehtävä jolla on muutoksia (AB-paikkaus levittäjällä)
          tehtava-muutoksilla (first (filter #(and (:tehtava_id %)
                                                    (= "AB-paikkaus levittäjällä" (:nimi %))
                                                    (seq (:muutokset %))) tehtavat))
          muutokset (:muutokset tehtava-muutoksilla)
          ensimmainen-muutos (first muutokset)] 
      
      (is (some? tehtava-muutoksilla) "Löytyy tehtävä jolla on muutoksia")
      (is (seq muutokset) "Tehtävällä on muutoksia")
      
      (when ensimmainen-muutos
        (testing "Ensimmäinen muutos sisältää kaikki vaaditut kentät"
          (is (contains? ensimmainen-muutos :id) "Muutoksella on :id")
          (is (contains? ensimmainen-muutos :edellinen_maara) "Muutoksella on :edellinen_maara")
          (is (contains? ensimmainen-muutos :maaramuutos) "Muutoksella on :maaramuutos")
          (is (contains? ensimmainen-muutos :uusi_maara) "Muutoksella on :uusi_maara")
          (is (contains? ensimmainen-muutos :voimassa_alkaen) "Muutoksella on :voimassa_alkaen")
          (is (contains? ensimmainen-muutos :syy) "Muutoksella on :syy"))
        
        (testing "Kentät sisältävät oikeita arvoja"
          (is (number? (:edellinen_maara ensimmainen-muutos)) "edellinen_maara on numero")
          (is (number? (:maaramuutos ensimmainen-muutos)) "maaramuutos on numero")
          (is (number? (:uusi_maara ensimmainen-muutos)) "uusi_maara on numero")
          (is (some? (:voimassa_alkaen ensimmainen-muutos)) "voimassa_alkaen on asetettu"))
        
        (testing "Laskennat ovat oikein"
          (when (and (:edellinen_maara ensimmainen-muutos)
                     (:maaramuutos ensimmainen-muutos)
                     (:uusi_maara ensimmainen-muutos))
            (is (= (:uusi_maara ensimmainen-muutos)
                   (+ (:edellinen_maara ensimmainen-muutos) 
                      (:maaramuutos ensimmainen-muutos)))
                "uusi_maara = edellinen_maara + maaramuutos")))))))


(deftest muutosten-kumuloituminen-kaksi-positiivista
  (testing "Skenaario 1: Kaksi positiivista muutosta kumuloituvat oikein"
    (let [db (:db jarjestelma)
          tehtavat (hae-tehtavat-urakan-hoitovuodelle db "Iin MHU 2021-2026" 2025)
          tehtava (hae-tehtava-nimella tehtavat "AB-paikkaus levittäjällä")
          muutokset (:muutokset tehtava)]
      
      (is (some? tehtava) "Tehtävä löytyy")
      (is (>= (count muutokset) 2) "Tehtävällä on vähintään 2 muutosta")
      
      (when (>= (count muutokset) 2)
        (let [muutos1 (first muutokset)
              muutos2 (second muutokset)]
          
          (testing "Ensimmäinen muutos"
            (is (tarkista-muutoksen-rakenne muutos1) "Muutos 1 sisältää kaikki kentät")
            (is (tarkista-muutoksen-laskenta muutos1) "Muutos 1 laskenta on oikein")
            (is (== (:edellinen_maara muutos1) (:tarjous_maara tehtava))
                "Muutos 1 alkaa tarjousmäärästä"))
          
          (testing "Toinen muutos"
            (is (tarkista-muutoksen-rakenne muutos2) "Muutos 2 sisältää kaikki kentät")
            (is (tarkista-muutoksen-laskenta muutos2) "Muutos 2 laskenta on oikein"))
          
          (testing "Kumuloituminen"
            (is (tarkista-muutosten-kumuloituminen muutos1 muutos2)
                "Muutos 2 alkaa siitä mihin muutos 1 päättyi")))))))

(deftest muutosten-kumuloituminen-positiivinen-ja-negatiivinen
  (testing "Skenaario 2: Positiivinen ja negatiivinen muutos"
    (let [db (:db jarjestelma)
          tehtavat (hae-tehtavat-urakan-hoitovuodelle db "Iin MHU 2021-2026" 2025)
          ;; Etsitään tehtävä jolla on sekä positiivisia että negatiivisia muutoksia
          tehtava-neg (first (filter #(and (:muutokset %)
                                           (seq (:muutokset %))
                                           (some (fn [m] (< (:maaramuutos m) 0)) (:muutokset %)))
                                     tehtavat))]
      
      (if tehtava-neg
        (let [muutokset (:muutokset tehtava-neg)] 
          (is (some? tehtava-neg) "Tehtävä negatiivisella muutoksella löytyy")
          
          ;; Tarkista jokainen muutos
          (doseq [[idx muutos] (map-indexed vector muutokset)]
            (testing (str "Muutos " (inc idx))
              (is (tarkista-muutoksen-rakenne muutos) 
                  (str "Muutos " (inc idx) " sisältää kaikki kentät"))
              (is (tarkista-muutoksen-laskenta muutos) 
                  (str "Muutos " (inc idx) " laskenta on oikein"))))
          
          ;; Tarkista kumuloituminen peräkkäisten muutosten välillä
          (when (> (count muutokset) 1)
            (doseq [i (range (dec (count muutokset)))]
              (let [muutos1 (nth muutokset i)
                    muutos2 (nth muutokset (inc i))]
                (testing (str "Kumuloituminen muutoksesta " (inc i) " muutokseen " (+ i 2))
                  (is (tarkista-muutosten-kumuloituminen muutos1 muutos2)
                      (str "Muutos " (+ i 2) " alkaa siitä mihin muutos " (inc i) " päättyi")))))))))))

(deftest muutosten-eristys-eri-tehtavien-valilla
  (testing "Skenaario 3: Eri tehtävien muutokset eivät vaikuta toisiinsa"
    (let [db (:db jarjestelma)
          tehtavat (hae-tehtavat-urakan-hoitovuodelle db "Iin MHU 2021-2026" 2025)
          ;; Hae kaksi eri tehtävää joilla molemmilla on muutoksia
          tehtavat-muutoksilla (filter #(and (:tehtava_id %)
                                             (seq (:muutokset %)))
                                       tehtavat)
          tehtava-a (first tehtavat-muutoksilla)
          tehtava-b (second tehtavat-muutoksilla)]
      
      (is (some? tehtava-a) "Ensimmäinen tehtävä löytyy")
      
      (when-not tehtava-b
        (println "VAROITUS: Vain yksi tehtävä muutoksilla, ohitetaan testi")
        (is true "Testi ohitettu - vain yksi tehtävä"))
      
      (when tehtava-b
        (is (not= (:tehtava_id tehtava-a) (:tehtava_id tehtava-b)) 
            "Tehtävät ovat eri tehtäviä"))
      
      (when (and tehtava-a tehtava-b)
        (testing "Tehtävä A:n muutokset alkavat sen omasta tarjouksesta"
          (when-let [muutos1-a (first (:muutokset tehtava-a))]
            (is (= (:edellinen_maara muutos1-a) (:tarjous_maara tehtava-a))
                "Tehtävä A:n ensimmäinen muutos alkaa A:n tarjouksesta")))
        
        (testing "Tehtävä B:n muutokset alkavat sen omasta tarjouksesta"
          (when-let [muutos1-b (first (:muutokset tehtava-b))]
            (is (= (:edellinen_maara muutos1-b) (:tarjous_maara tehtava-b))
                "Tehtävä B:n ensimmäinen muutos alkaa B:n tarjouksesta")))
        
        (testing "Tehtävien muutokset eivät sekoitu"
          (let [muutokset-a (:muutokset tehtava-a)
                muutokset-b (:muutokset tehtava-b)
                tehtavaid-a (:tehtava_id tehtava-a)
                tehtavaid-b (:tehtava_id tehtava-b)]
            
            ;; Tarkista että tehtävä A:n muutoksissa on oikea tehtavaid
            (doseq [muutos muutokset-a]
              (is (= (:tehtavaid muutos) tehtavaid-a)
                  (str "Tehtävä A:n muutos " (:id muutos) " kuuluu tehtävälle A")))
            
            ;; Tarkista että tehtävä B:n muutoksissa on oikea tehtavaid
            (doseq [muutos muutokset-b]
              (is (= (:tehtavaid muutos) tehtavaid-b)
                  (str "Tehtävä B:n muutos " (:id muutos) " kuuluu tehtävälle B")))))))))

(deftest muutosten-jarjestys-aikaleimalla
  (testing "Skenaario 4: Muutokset ovat aikajärjestyksessä"
    (let [db (:db jarjestelma)
          tehtavat (hae-tehtavat-urakan-hoitovuodelle db "Iin MHU 2021-2026" 2025)
          tehtava (hae-tehtava-nimella tehtavat "AB-paikkaus levittäjällä")
          muutokset (:muutokset tehtava)] 
      
      (is (some? tehtava) "Tehtävä löytyy")
      (is (>= (count muutokset) 2) "Tehtävällä on vähintään 2 muutosta")
      
      (when (>= (count muutokset) 2)
        (testing "Muutokset ovat aikajärjestyksessä"
          (doseq [i (range (dec (count muutokset)))]
            (let [muutos1 (nth muutokset i)
                  muutos2 (nth muutokset (inc i))
                  aika1 (:voimassa_alkaen muutos1)
                  aika2 (:voimassa_alkaen muutos2)]
              (is (or (nil? aika1) (nil? aika2) 
                      (.before aika1 aika2)
                      (.equals aika1 aika2))
                  (str "Muutos " (inc i) " on ennen tai samaan aikaan kuin muutos " (+ i 2))))))))))

(deftest yhteensa-lasketaan-oikein
  (testing "Skenaario 5: Yhteensä = Tarjous + Muutossumma"
    (let [db (:db jarjestelma)
          tehtavat (hae-tehtavat-urakan-hoitovuodelle db "Iin MHU 2021-2026" 2025)
          tehtava (hae-tehtava-nimella tehtavat "AB-paikkaus levittäjällä")
          muutokset (:muutokset tehtava)
          tarjous (:tarjous_maara tehtava)
          muutossumma (:muutos_maaramuutos tehtava)
          yhteensa (:yhteensa tehtava)]
            
      ;; Laske muutossumma manuaalisesti muutoksista
      (when (seq muutokset)
        (let [laskettu-muutossumma (reduce + 0M (map :maaramuutos muutokset))
              odotettu-yhteensa (+ (or tarjous 0M) laskettu-muutossumma)
              viimeinen-muutos (last muutokset)
              viimeinen-uusi-maara (:uusi_maara viimeinen-muutos)]
          
          (testing "Muutossumma vastaa muutosten summaa"
            (is (== muutossumma laskettu-muutossumma)
                (str "Muutossumma-kenttä (" muutossumma 
                     ") = summa muutoksista (" laskettu-muutossumma ")")))
          
          (testing "Yhteensä = Tarjous + Muutossumma"
            (is (== yhteensa odotettu-yhteensa)
                (str "Yhteensä (" yhteensa 
                     ") = Tarjous (" tarjous 
                     ") + Muutossumma (" muutossumma ")")))
          
          (testing "Yhteensä = Viimeisen muutoksen uusi_maara"
            (is (== yhteensa viimeinen-uusi-maara)
                (str "Yhteensä (" yhteensa 
                     ") = Viimeisen muutoksen uusi_maara (" viimeinen-uusi-maara ")")))
          
          (testing "Viimeisen muutoksen uusi_maara = Tarjous + Kaikki muutokset"
            (is (== viimeinen-uusi-maara odotettu-yhteensa)
                (str "Viimeisen muutoksen uusi_maara (" viimeinen-uusi-maara 
                     ") = Tarjous + Muutokset (" odotettu-yhteensa ")")))))
      
      ;; Perustestit
      (is (some? tehtava) "Tehtävä löytyy")
      (is (number? tarjous) "Tarjous on numero")
      (is (number? muutossumma) "Muutossumma on numero")
      (is (number? yhteensa) "Yhteensä on numero"))))

(deftest versio-suodatus-toimii
  (testing "Skenaario 6: Vain uusimmat versiot muutoksista lasketaan mukaan"
    (let [db (:db jarjestelma)
          ;; Hae AB-paikkaus tehtävän ID
          ab-paikkaus-id (with-db-connection [c db]
                          (:id (first (query c "SELECT id FROM tehtava WHERE nimi = 'AB-paikkaus levittäjällä' LIMIT 1"))))
          
          ;; Hae AB-paikkaus-tehtävän muutokset suoraan tietokannasta ilman versio-suodatusta
          kaikki-muutokset (with-db-connection [c db]
                            (query c ["SELECT mm.id, mmtm.versio, mmtm.maaramuutos, mmtm.tehtava 
                                      FROM mhu_muutos mm 
                                      JOIN mhu_muutos_tehtava_ja_maaraluettelo mmtm ON mm.id = mmtm.muutos 
                                      WHERE mm.urakka = (SELECT id FROM urakka WHERE nimi = 'Iin MHU 2021-2026') 
                                      AND mmtm.hoitokauden_alkuvuosi = 2025 
                                      AND mmtm.tehtava = ?
                                      AND mm.poistettu IS NOT TRUE"
                                      ab-paikkaus-id]))
          
          ;; Hae tehtävät kyselyn kautta (VIEW käyttää versio-suodatusta)
          tehtavat (hae-tehtavat-urakan-hoitovuodelle db "Iin MHU 2021-2026" 2025)
          tehtava (hae-tehtava-nimella tehtavat "AB-paikkaus levittäjällä")
          muutokset-kyselysta (:muutokset tehtava)]
      
      ;; Ryhmittele tietokannan muutokset (muutos,tehtava) parin mukaan ja ota maksimiversiot
      (let [muutokset-by-muutos-tehtava (group-by (juxt :id :tehtava) kaikki-muutokset)
            uusimmat-versiot (map (fn [[_ versions]]
                                   (apply max-key :versio versions))
                              muutokset-by-muutos-tehtava)
            uusimpien-maara (count uusimmat-versiot)
            kyselyn-maara (count muutokset-kyselysta)]
        
        (testing "VIEW palauttaa vain uusimmat versiot"
          (is (= kyselyn-maara uusimpien-maara)
              (str "VIEW:n muutosten määrä (" kyselyn-maara 
                   ") = Uusimpien versioiden määrä (" uusimpien-maara ")")))
        
        (testing "Jos versioita on useampia, VIEW ei saa palauttaa vanhoja"
          (let [useita-versioita? (some #(> (count %) 1) (vals muutokset-by-muutos-tehtava))]
            (when useita-versioita?
              (is (< kyselyn-maara (count kaikki-muutokset))
                  "VIEW palauttaa vähemmän muutoksia kuin tietokannassa on yhteensä (= versio-suodatus toimii)"))))))))

(deftest versio-suodatus-tehtavakohtainen
  (testing "Versio-suodatus on tehtäväkohtainen: Kun yksi muutos sisältää useita tehtäviä eri versioilla, jokainen tehtävä saa oikean versionsa"
    (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
          hoitokausi 2025
          
          ;; Valitaan kaksi eri tehtävää testiin
          tehtava-a-id (hae-tehtava-id-nimella "AB-paikkaus levittäjällä")
          tehtava-b-id (hae-tehtava-id-nimella "PAB-paikkaus levittäjällä")
          
          ;; Hae alkutilanne ennen testimuutosta
          alku-muutossumma-a (hae-view-muutossumma urakka-id tehtava-a-id hoitokausi)
          alku-muutossumma-b (hae-view-muutossumma urakka-id tehtava-b-id hoitokausi)
          
          ;; Lisää testimuutos kahdelle tehtävälle eri versioilla
          ;; Tämä testaa että VIEW:n versio-suodatus on tehtäväkohtainen
          muutos-id (luo-muutos-usealle-tehtavalle!
                      urakka-id "2025-06-15" hoitokausi
                      "Testi: Yksi muutos kahdelle tehtävälle"
                      [{:tehtava-id tehtava-a-id :maaramuutos 75 :versio 1}
                       {:tehtava-id tehtava-b-id :maaramuutos 50 :versio 2}])
          
          ;; 3. Hae tilanne muutoksen jälkeen
          muutokset-kannassa (q-map (format "SELECT tehtava, maaramuutos FROM mhu_muutos_tehtava_ja_maaraluettelo 
                                             WHERE muutos = %s" muutos-id))
          muutossumma-a (hae-view-muutossumma urakka-id tehtava-a-id hoitokausi)
          muutossumma-b (hae-view-muutossumma urakka-id tehtava-b-id hoitokausi)
          odotettu-a (+ alku-muutossumma-a 75M)
          odotettu-b (+ alku-muutossumma-b 50M)]
      
      (testing "Tietokannassa on kaksi muutosriviä (yksi per tehtävä)"
        (is (= 2 (count muutokset-kannassa))
            (str "Muutostaulussa pitää olla 2 riviä, löytyi: " (count muutokset-kannassa))))
      
      (testing "Tehtävä A:n muutossumma kasvoi 75M alkutilasta"
        ;; Jos versio-suodatus on väärin (ilman AND mmtml2.tehtava = mmtml.tehtava),
        ;; VIEW ottaa vain yhden tehtävän mukaan tai summaa kaikki yhteen -> väärä kasvu
        (assert-muutossumma= urakka-id tehtava-a-id hoitokausi odotettu-a))
      
      (testing "Tehtävä B:n muutossumma kasvoi 50M alkutilasta"
        ;; Jos versio-suodatus on väärin, tämä tehtävä saattaa jäädä kokonaan ilman muutosta
        ;; TAI saada väärän määrämuutoksen
        (assert-muutossumma= urakka-id tehtava-b-id hoitokausi odotettu-b))
      
      (testing "Eri tehtävien määrämuutokset olivat eri suuruiset (75M vs 50M)"
        (let [muutos-a (- muutossumma-a alku-muutossumma-a)
              muutos-b (- muutossumma-b alku-muutossumma-b)]
          (is (and (= muutos-a 75M) (= muutos-b 50M))
              (str "Tehtävä A muutos: " muutos-a "M, Tehtävä B muutos: " muutos-b "M (odotettu: 75M ja 50M)")))))))

(deftest voimassa-alkaen-filterointi-vuodenvaihteessa
  (testing "Hoitokausi ylittää kalenterivuoden rajan - muutokset näkyvät oikein"
    (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
          hoitokausi 2023 ;; Hoitokausi 2023 = 1.10.2023 - 30.9.2024
          tehtava-id (hae-tehtava-id-nimella "AB-paikkaus levittäjällä")
          
          ;; Hae alkutilanne ennen muutoksia
          alku-muutossumma (hae-view-muutossumma urakka-id tehtava-id hoitokausi)]
      
      ;; Luo 4 muutosta eri ajankohtiin hoitokauden ympärille
      (let [;; Muutos 1: Ennen hoitokautta (ei pitäisi näkyä)
            muutos1-id (luo-muutos! urakka-id "2023-09-15" tehtava-id hoitokausi 100 "Ennen hoitokautta")
            
            ;; Muutos 2: Hoitokauden alkupuolella (pitää näkyä)
            muutos2-id (luo-muutos! urakka-id "2023-10-15" tehtava-id hoitokausi 200 "Hoitokauden alussa")
            
            ;; Muutos 3: Hoitokauden puolivälissä (pitää näkyä)
            muutos3-id (luo-muutos! urakka-id "2023-12-15" tehtava-id hoitokausi 300 "Hoitokauden keskellä")
            
            ;; Muutos 4: Kalenterivuoden 2024 puolella mutta hoitokauden sisällä (PITÄÄ NÄKYÄ!)
            muutos4-id (luo-muutos! urakka-id "2024-03-15" tehtava-id hoitokausi 400 "Kalenterivuoden 2024 puolella")
            
            ;; Hae tulos VIEW:stä
            loppu-muutossumma (hae-view-muutossumma urakka-id tehtava-id hoitokausi)
            
            ;; Hae muutokset kannasta vertailua varten
            muutokset-kannassa (q-map (format "SELECT m.voimassa_alkaen, mmtml.maaramuutos, m.syy
                                               FROM mhu_muutos m
                                               JOIN mhu_muutos_tehtava_ja_maaraluettelo mmtml ON m.id = mmtml.muutos
                                               WHERE m.urakka = %s 
                                                 AND mmtml.tehtava = %s 
                                                 AND mmtml.hoitokauden_alkuvuosi = %s
                                                 AND m.poistettu IS NOT TRUE
                                               ORDER BY m.voimassa_alkaen"
                                              urakka-id tehtava-id hoitokausi))
            
            ;; Odotettu tulos: Kaikki 4 muutosta mukana (100 + 200 + 300 + 400 = 1000)
            odotettu-muutossumma (+ alku-muutossumma 1000M)]
        
        (testing "Tietokannassa on 4 muutosta tehtävälle"
          (is (= 4 (count muutokset-kannassa))
              (str "Muutostaulussa pitää olla 4 riviä, löytyi: " (count muutokset-kannassa))))
        
        (testing "VIEW aggregoi kaikki hoitokauden muutokset, myös kalenterivuoden 2024 puolelta"
          ;; Hoitokausi 2023 kattaa 1.10.2023-30.9.2024
          ;; joten maaliskuun 2024 muutos kuuluu hoitokauteen 2023
          (assert-muutossumma= urakka-id tehtava-id hoitokausi odotettu-muutossumma muutokset-kannassa))
        
        (testing "Kalenterivuoden 2024 muutos on mukana hoitokaudella 2023"
          (let [muutos-yhteensa (- loppu-muutossumma alku-muutossumma)]
            (is (= muutos-yhteensa 1000M)
                (str "Muutosten summa: " muutos-yhteensa "M (pitäisi olla 1000M sisältäen 2024-03-15 muutoksen)"))))))))

(deftest voimassa-alkaen-where-ehto-suodatus
  (testing "VIEW:n WHERE-ehto suodattaa pois muutokset joiden voimassa_alkaen on hoitokauden lopun jälkeen"
    (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
          hoitokausi 2030 ;; Hoitokausi 2030 = 1.10.2030 - 30.9.2031
          tehtava-id (hae-tehtava-id-nimella "AB-paikkaus levittäjällä")
          
          ;; Varmista että hoitokaudelle on tarjousrivi
          _ (varmista-tarjousrivi! urakka-id hoitokausi tehtava-id)
          
          ;; Hae alkutilanne ennen muutoksia
          alku-muutossumma (hae-view-muutossumma urakka-id tehtava-id hoitokausi)]
      
      ;; Luo 3 muutosta: kaksi hoitokauden sisällä ja yksi sen jälkeen
      (let [;; Muutos 1: Hoitokauden sisällä, alussa (PITÄÄ NÄKYÄ)
            muutos1-id (luo-muutos! urakka-id "2030-11-15" tehtava-id hoitokausi 111 "Hoitokauden sisällä - alussa")
            
            ;; Muutos 2: Hoitokauden sisällä, lopussa mutta ennen 30.9. (PITÄÄ NÄKYÄ)
            muutos2-id (luo-muutos! urakka-id "2031-09-29" tehtava-id hoitokausi 222 "Hoitokauden sisällä - lopussa")
            
            ;; Muutos 3: Hoitokauden JÄLKEEN (EI SAA NÄKYÄ)
            ;; Simuloi tilannetta jossa käyttäjä vaihtaa voimassa_alkaen-päivää myöhemmäksi,
            ;; mutta child-taulussa on vielä vanha hoitokauden_alkuvuosi
            muutos3-id (luo-muutos! urakka-id "2031-10-15" tehtava-id hoitokausi 999 "Hoitokauden JÄLKEEN")
            
            ;; Hae muutokset ja tulos VIEW:stä
            loppu-muutossumma (hae-view-muutossumma urakka-id tehtava-id hoitokausi)
            muutokset-kannassa (hae-muutokset-kannasta urakka-id tehtava-id hoitokausi muutos1-id muutos2-id muutos3-id)
            
            ;; Odotettu tulos: Vain muutos1 (111M) ja muutos2 (222M) mukana = 333M
            ;; Muutos3 (999M) EI saa olla mukana koska voimassa_alkaen > hoitokauden loppu
            odotettu-muutossumma (+ alku-muutossumma 333M)
            vaara-muutossumma (+ alku-muutossumma 1332M)] ;; Jos WHERE-ehto ei toimi
        
        (testing "Tietokannassa on 3 testimuutosta"
          (is (= 3 (count muutokset-kannassa))
              (str "Muutostaulussa pitää olla 3 testimuutosta, löytyi: " (count muutokset-kannassa))))
        
        (testing "WHERE-ehto laskee oikein mitkä muutokset pitää näkyä"
          (let [pitaa-nakya (filter :pitaa_nakya muutokset-kannassa)
                ei-saa-nakya (remove :pitaa_nakya muutokset-kannassa)]
            (is (= 2 (count pitaa-nakya))
                (str "2 muutosta pitää näkyä (voimassa_alkaen <= 30.9.2031), löytyi: " (count pitaa-nakya)))
            (is (= 1 (count ei-saa-nakya))
                (str "1 muutos ei saa näkyä (voimassa_alkaen > 30.9.2031), löytyi: " (count ei-saa-nakya)))))
        
        (testing "VIEW aggregoi vain hoitokauden sisäiset muutokset"
          (assert-muutossumma= urakka-id tehtava-id hoitokausi odotettu-muutossumma muutokset-kannassa)
          (is (not= loppu-muutossumma vaara-muutossumma)
              (str "VIEW:n tulos ei saa olla " vaara-muutossumma " (jos WHERE-ehto puuttuisi)")))
        
        (testing "Hoitokauden jälkeinen muutos ei vaikuta summaan"
          (let [muutos-yhteensa (- loppu-muutossumma alku-muutossumma)]
            (is (= muutos-yhteensa 333M)
                (str "Muutosten summa: " muutos-yhteensa "M (pitäisi olla 333M = 111 + 222, EI 1332M = 111 + 222 + 999)"))))
        
        (testing "WHERE-ehto estää epäjohdonmukaisen datan näkymisen"
          (is (not= loppu-muutossumma vaara-muutossumma)
              (str "VIEW:n tulos (" loppu-muutossumma ") ei saa olla sama kuin jos WHERE-ehto puuttuisi (" vaara-muutossumma ")")))))))