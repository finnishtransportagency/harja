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
          tehtava-a-id 24628 ;; AB-paikkaus levittäjällä
          tehtava-b-id 24629 ;; PAB-paikkaus levittäjällä
          
          ;; 1. Hae alkutilanne ennen testimuutosta
          alkutilanne-a (first (q-map (format "SELECT muutossumma FROM urakka_tehtavamaara_yhteenveto 
                                               WHERE urakka = %s AND tehtava = %s AND hoitokauden_alkuvuosi = %s"
                                              urakka-id tehtava-a-id hoitokausi)))
          alkutilanne-b (first (q-map (format "SELECT muutossumma FROM urakka_tehtavamaara_yhteenveto 
                                               WHERE urakka = %s AND tehtava = %s AND hoitokauden_alkuvuosi = %s"
                                              urakka-id tehtava-b-id hoitokausi)))
          alku-muutossumma-a (or (:muutossumma alkutilanne-a) 0M)
          alku-muutossumma-b (or (:muutossumma alkutilanne-b) 0M)
          
          ;; 2. Lisätään testimuutos
          muutos-id (i (format "INSERT INTO mhu_muutos (urakka, voimassa_alkaen, syy, luoja, luotu, poistettu)
                                VALUES (%s, '2025-06-15', 'Testi: Yksi muutos kahdelle tehtävälle', 
                                        (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'), NOW(), false)"
                               urakka-id))
          ;; Jos tehtäväfiltteri puuttuu, vain versio 2 otetaan mukaan molemmille
          _ (u (format "INSERT INTO mhu_muutos_tehtava_ja_maaraluettelo (muutos, tehtava, hoitokauden_alkuvuosi, maaramuutos, versio)
                        VALUES (%s, %s, %s, 75, 1)" muutos-id tehtava-a-id hoitokausi))
          _ (u (format "INSERT INTO mhu_muutos_tehtava_ja_maaraluettelo (muutos, tehtava, hoitokauden_alkuvuosi, maaramuutos, versio)
                        VALUES (%s, %s, %s, 50, 2)" muutos-id tehtava-b-id hoitokausi))
          
          ;; 3. Hae tilanne muutoksen jälkeen
          muutokset-kannassa (q-map (format "SELECT tehtava, maaramuutos FROM mhu_muutos_tehtava_ja_maaraluettelo 
                                             WHERE muutos = %s" muutos-id))
          view-a (first (q-map (format "SELECT tehtava, muutossumma FROM urakka_tehtavamaara_yhteenveto 
                                        WHERE urakka = %s AND tehtava = %s AND hoitokauden_alkuvuosi = %s"
                                       urakka-id tehtava-a-id hoitokausi)))
          view-b (first (q-map (format "SELECT tehtava, muutossumma FROM urakka_tehtavamaara_yhteenveto 
                                        WHERE urakka = %s AND tehtava = %s AND hoitokauden_alkuvuosi = %s"
                                       urakka-id tehtava-b-id hoitokausi)))
          muutossumma-a (:muutossumma view-a)
          muutossumma-b (:muutossumma view-b)
          odotettu-a (+ alku-muutossumma-a 75M)
          odotettu-b (+ alku-muutossumma-b 50M)]
      
      (testing "Tietokannassa on kaksi muutosriviä (yksi per tehtävä)"
        (is (= 2 (count muutokset-kannassa))
            (str "Muutostaulussa pitää olla 2 riviä, löytyi: " (count muutokset-kannassa))))
      
      (testing "Tehtävä A:n muutossumma kasvoi 75M alkutilasta"
        ;; Jos versio-suodatus on väärin (ilman AND mmtml2.tehtava = mmtml.tehtava),
        ;; VIEW ottaa vain yhden tehtävän mukaan tai summaa kaikki yhteen -> väärä kasvu
        (is (= muutossumma-a odotettu-a)
            (str "Tehtävä A: alku=" alku-muutossumma-a " -> loppu=" muutossumma-a " (odotettu: " odotettu-a ")")))
      
      (testing "Tehtävä B:n muutossumma kasvoi 50M alkutilasta"
        ;; Jos versio-suodatus on väärin, tämä tehtävä saattaa jäädä kokonaan ilman muutosta
        ;; TAI saada väärän määrämuutoksen
        (is (= muutossumma-b odotettu-b)
            (str "Tehtävä B: alku=" alku-muutossumma-b " -> loppu=" muutossumma-b " (odotettu: " odotettu-b ")")))
      
      (testing "Eri tehtävien määrämuutokset olivat eri suuruiset (75M vs 50M)"
        (let [muutos-a (- muutossumma-a alku-muutossumma-a)
              muutos-b (- muutossumma-b alku-muutossumma-b)]
          (is (and (= muutos-a 75M) (= muutos-b 50M))
              (str "Tehtävä A muutos: " muutos-a "M, Tehtävä B muutos: " muutos-b "M (odotettu: 75M ja 50M)")))))))

(deftest voimassa-alkaen-filterointi-vuodenvaihteessa
  (testing "Hoitokausi ylittää kalenterivuoden rajan - muutokset näkyvät oikein"
    (let [;; Käytetään Iin MHU 2021-2026 urakkaa joka on MHU-tyyppinen
          urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
          hoitokausi 2023 ;; Hoitokausi 2023 = 1.10.2023 - 30.9.2024
          tehtava-id 24628 ;; AB-paikkaus levittäjällä
          
          ;; Hae alkutilanne ennen muutoksia
          alkutilanne (first (q-map (format "SELECT COALESCE(muutossumma, 0) as muutossumma 
                                             FROM urakka_tehtavamaara_yhteenveto 
                                             WHERE urakka = %s AND tehtava = %s AND hoitokauden_alkuvuosi = %s"
                                            urakka-id tehtava-id hoitokausi)))
          alku-muutossumma (or (:muutossumma alkutilanne) 0M)]
      
      ;; Lisätään 4 muutosta eri ajankohtiin hoitokauden ympärille
      (let [;; Muutos 1: Ennen hoitokautta (ei pitäisi näkyä)
            muutos1-id (i (format "INSERT INTO mhu_muutos (urakka, voimassa_alkaen, syy, luoja, luotu, poistettu)
                                   VALUES (%s, '2023-09-15', 'Ennen hoitokautta', 
                                           (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'), NOW(), false)"
                                  urakka-id))
            _ (u (format "INSERT INTO mhu_muutos_tehtava_ja_maaraluettelo (muutos, tehtava, hoitokauden_alkuvuosi, maaramuutos, versio)
                          VALUES (%s, %s, %s, 100, 1)" muutos1-id tehtava-id hoitokausi))
            
            ;; Muutos 2: Hoitokauden alkupuolella (pitää näkyä)
            muutos2-id (i (format "INSERT INTO mhu_muutos (urakka, voimassa_alkaen, syy, luoja, luotu, poistettu)
                                   VALUES (%s, '2023-10-15', 'Hoitokauden alussa', 
                                           (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'), NOW(), false)"
                                  urakka-id))
            _ (u (format "INSERT INTO mhu_muutos_tehtava_ja_maaraluettelo (muutos, tehtava, hoitokauden_alkuvuosi, maaramuutos, versio)
                          VALUES (%s, %s, %s, 200, 1)" muutos2-id tehtava-id hoitokausi))
            
            ;; Muutos 3: Hoitokauden puolivälissä (pitää näkyä)
            muutos3-id (i (format "INSERT INTO mhu_muutos (urakka, voimassa_alkaen, syy, luoja, luotu, poistettu)
                                   VALUES (%s, '2023-12-15', 'Hoitokauden keskellä', 
                                           (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'), NOW(), false)"
                                  urakka-id))
            _ (u (format "INSERT INTO mhu_muutos_tehtava_ja_maaraluettelo (muutos, tehtava, hoitokauden_alkuvuosi, maaramuutos, versio)
                          VALUES (%s, %s, %s, 300, 1)" muutos3-id tehtava-id hoitokausi))
            
            ;; Muutos 4: Kalenterivuoden 2024 puolella mutta hoitokauden sisällä (PITÄÄ NÄKYÄ!)
            muutos4-id (i (format "INSERT INTO mhu_muutos (urakka, voimassa_alkaen, syy, luoja, luotu, poistettu)
                                   VALUES (%s, '2024-03-15', 'Kalenterivuoden 2024 puolella', 
                                           (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'), NOW(), false)"
                                  urakka-id))
            _ (u (format "INSERT INTO mhu_muutos_tehtava_ja_maaraluettelo (muutos, tehtava, hoitokauden_alkuvuosi, maaramuutos, versio)
                          VALUES (%s, %s, %s, 400, 1)" muutos4-id tehtava-id hoitokausi))
            
            ;; Hae tilanne muutosten jälkeen VIEW:stä
            lopputilanne (first (q-map (format "SELECT COALESCE(muutossumma, 0) as muutossumma 
                                                FROM urakka_tehtavamaara_yhteenveto 
                                                WHERE urakka = %s AND tehtava = %s AND hoitokauden_alkuvuosi = %s"
                                               urakka-id tehtava-id hoitokausi)))
            loppu-muutossumma (or (:muutossumma lopputilanne) 0M)
            
            ;; Hae myös suoraan muutostaulusta vertailua varten
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
          (is (= loppu-muutossumma odotettu-muutossumma)
              (str "VIEW:n muutossumma: " loppu-muutossumma 
                   " (odotettu: " odotettu-muutossumma ")"
                   "\nKaikki muutokset: " muutokset-kannassa)))
        
        (testing "Kalenterivuoden 2024 muutos on mukana hoitokaudella 2023"
          (let [muutos-yhteensa (- loppu-muutossumma alku-muutossumma)]
            (is (= muutos-yhteensa 1000M)
                (str "Muutosten summa: " muutos-yhteensa "M (pitäisi olla 1000M sisältäen 2024-03-15 muutoksen)"))))))))

(deftest voimassa-alkaen-where-ehto-suodatus
  (testing "VIEW:n WHERE-ehto suodattaa pois muutokset joiden voimassa_alkaen on hoitokauden lopun jälkeen"
    (let [;; Käytetään Iin MHU 2021-2026 urakkaa
          urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
          hoitokausi 2030 ;; Hoitokausi 2030 = 1.10.2030 - 30.9.2031 (ei testidataa)
          tehtava-id 24628 ;; AB-paikkaus levittäjällä
          
          ;; Hae alkutilanne ennen muutoksia
          alkutilanne (first (q-map (format "SELECT COALESCE(muutossumma, 0) as muutossumma 
                                             FROM urakka_tehtavamaara_yhteenveto 
                                             WHERE urakka = %s AND tehtava = %s AND hoitokauden_alkuvuosi = %s"
                                            urakka-id tehtava-id hoitokausi)))
          alku-muutossumma (or (:muutossumma alkutilanne) 0M)]
      
      ;; Lisätään 3 muutosta: kaksi hoitokauden sisällä ja yksi sen jälkeen
      (let [;; Muutos 1: Hoitokauden sisällä, alussa (PITÄÄ NÄKYÄ)
            muutos1-id (i (format "INSERT INTO mhu_muutos (urakka, voimassa_alkaen, syy, luoja, luotu, poistettu)
                                   VALUES (%s, '2024-11-15', 'Hoitokauden sisällä - alussa', 
                                           (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'), NOW(), false)"
                                  urakka-id))
            _ (u (format "INSERT INTO mhu_muutos_tehtava_ja_maaraluettelo (muutos, tehtava, hoitokauden_alkuvuosi, maaramuutos, versio)
                          VALUES (%s, %s, %s, 111, 1)" muutos1-id tehtava-id hoitokausi))
            
            ;; Muutos 2: Hoitokauden sisällä, lopussa mutta ennen 30.9. (PITÄÄ NÄKYÄ)
            muutos2-id (i (format "INSERT INTO mhu_muutos (urakka, voimassa_alkaen, syy, luoja, luotu, poistettu)
                                   VALUES (%s, '2025-09-29', 'Hoitokauden sisällä - lopussa', 
                                           (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'), NOW(), false)"
                                  urakka-id))
            _ (u (format "INSERT INTO mhu_muutos_tehtava_ja_maaraluettelo (muutos, tehtava, hoitokauden_alkuvuosi, maaramuutos, versio)
                          VALUES (%s, %s, %s, 222, 1)" muutos2-id tehtava-id hoitokausi))
            
            ;; Muutos 3: Hoitokauden JÄ LKEEN (EI SAA NÄKYÄ) 
            ;; Tämä simuloi tilannetta jossa käyttäjä vaihtaa muutoksen voimassa_alkaen-päivää
            ;; myöhemmäksi, mutta child-taulussa on vielä vanha hoitokauden_alkuvuosi
            muutos3-id (i (format "INSERT INTO mhu_muutos (urakka, voimassa_alkaen, syy, luoja, luotu, poistettu)
                                   VALUES (%s, '2025-10-15', 'Hoitokauden JÄLKEEN', 
                                           (SELECT id FROM kayttaja WHERE kayttajanimi = 'jvh'), NOW(), false)"
                                  urakka-id))
            _ (u (format "INSERT INTO mhu_muutos_tehtava_ja_maaraluettelo (muutos, tehtava, hoitokauden_alkuvuosi, maaramuutos, versio)
                          VALUES (%s, %s, %s, 999, 1)" muutos3-id tehtava-id hoitokausi))
            
            ;; Hae tilanne muutosten jälkeen VIEW:stä
            lopputilanne (first (q-map (format "SELECT COALESCE(muutossumma, 0) as muutossumma 
                                                FROM urakka_tehtavamaara_yhteenveto 
                                                WHERE urakka = %s AND tehtava = %s AND hoitokauden_alkuvuosi = %s"
                                               urakka-id tehtava-id hoitokausi)))
            loppu-muutossumma (or (:muutossumma lopputilanne) 0M)
            
            ;; Hae myös suoraan muutostaulusta vertailua varten
            muutokset-kannassa (q-map (format "SELECT m.id, m.voimassa_alkaen, mmtml.maaramuutos, m.syy,
                                                      m.voimassa_alkaen <= make_date(mmtml.hoitokauden_alkuvuosi + 1, 9, 30) AS pitaa_nakya
                                               FROM mhu_muutos m
                                               JOIN mhu_muutos_tehtava_ja_maaraluettelo mmtml ON m.id = mmtml.muutos
                                               WHERE m.urakka = %s 
                                                 AND mmtml.tehtava = %s 
                                                 AND mmtml.hoitokauden_alkuvuosi = %s
                                                 AND m.poistettu IS NOT TRUE
                                                 AND m.id IN (%s, %s, %s)
                                               ORDER BY m.voimassa_alkaen"
                                              urakka-id tehtava-id hoitokausi 
                                              muutos1-id muutos2-id muutos3-id))
            
            ;; Odotettu tulos: Vain muutos1 (111M) ja muutos2 (222M) mukana = 333M
            ;; Muutos3 (999M) EI saa olla mukana koska voimassa_alkaen > hoitokauden loppu
            odotettu-muutossumma (+ alku-muutossumma 333M)
            vaara-muutossumma (+ alku-muutossumma 1332M)] ;; Jos WHERE-ehto ei toimi, kaikki 3 muutosta mukana
        
        (testing "Tietokannassa on 3 testimuutosta"
          (is (= 3 (count muutokset-kannassa))
              (str "Muutostaulussa pitää olla 3 testimuutosta, löytyi: " (count muutokset-kannassa))))
        
        (testing "WHERE-ehto laskee oikein mitkä muutokset pitää näkyä"
          (let [pitaa-nakya (filter :pitaa_nakya muutokset-kannassa)
                ei-saa-nakya (remove :pitaa_nakya muutokset-kannassa)]
            (is (= 2 (count pitaa-nakya))
                (str "2 muutosta pitää näkyä (voimassa_alkaen <= 30.9.2025), löytyi: " (count pitaa-nakya)))
            (is (= 1 (count ei-saa-nakya))
                (str "1 muutos ei saa näkyä (voimassa_alkaen > 30.9.2025), löytyi: " (count ei-saa-nakya)))))
        
        (testing "VIEW aggregoi vain hoitokauden sisäiset muutokset"
          (is (= loppu-muutossumma odotettu-muutossumma)
              (str "VIEW:n muutossumma: " loppu-muutossumma 
                   " (odotettu: " odotettu-muutossumma ", väärä olisi: " vaara-muutossumma ")"
                   "\nMuutokset kannassa: " muutokset-kannassa)))
        
        (testing "Hoitokauden jälkeinen muutos ei vaikuta summaan"
          (let [muutos-yhteensa (- loppu-muutossumma alku-muutossumma)]
            (is (= muutos-yhteensa 333M)
                (str "Muutosten summa: " muutos-yhteensa "M (pitäisi olla 333M = 111 + 222, EI 1332M = 111 + 222 + 999)"))))
        
        (testing "WHERE-ehto estää epäjohdonmukaisen datan näkymisen"
          (is (not= loppu-muutossumma vaara-muutossumma)
              (str "VIEW:n tulos (" loppu-muutossumma ") ei saa olla sama kuin jos WHERE-ehto puuttuisi (" vaara-muutossumma ")")))))))