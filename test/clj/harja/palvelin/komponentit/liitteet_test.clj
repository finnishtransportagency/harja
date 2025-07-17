(ns harja.palvelin.komponentit.liitteet-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.java.io :as io]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.liitteet :as liitteet])
  (:import (java.util.concurrent ThreadPoolExecutor)
           (org.apache.commons.io IOUtils)))

(defn poista-liite [liite-id]
  (u (str "DELETE FROM liite WHERE id = " liite-id ";")))

(def thread-pool-size 5)

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :liitteiden-hallinta
          (component/using
            (harja.palvelin.komponentit.liitteet/->Liitteet nil thread-pool-size nil)
            [:db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(deftest tallenna-xml-liite
  (let [liitteiden-hallinta (:liitteiden-hallinta jarjestelma)
        tiedosto "test/resurssit/sampo/kustannussuunnitelma_ack.xml"
        tiedoston-sisalto-tekstina (slurp tiedosto)
        tiedoston-sisalto (IOUtils/toByteArray (io/input-stream tiedosto))
        luotu-liite (liitteet/luo-liite liitteiden-hallinta nil 1 "kustannussuunnitelma_ack.xml" "text/xml" 581 tiedoston-sisalto nil "harja-ui")
        liite-id (:id luotu-liite)
        luettu-liite (liitteet/lataa-liite liitteiden-hallinta liite-id {})
        liitteen-sisalto-tekstina (slurp (:data luettu-liite))]

    ;; (println luotu-liite)

    (is (= tiedoston-sisalto-tekstina liitteen-sisalto-tekstina) "Luetun liitteen sisältö on sama kuin mitä lähdetiedoston.")
    (is (not (:pikkukuva luettu-liite)) "XML-tiedostolla ei saa olla pikkukuvaa.")

    (poista-liite liite-id)))

(deftest tallenna-kuvaliite
  (let [liitteiden-hallinta (:liitteiden-hallinta jarjestelma)
        tiedosto "dev-resources/images/harja-brand-text.png"
        tiedoston-sisalto (IOUtils/toByteArray (io/input-stream tiedosto))
        luotu-liite (liitteet/luo-liite liitteiden-hallinta nil 1 "harja-brand-text.png" "image/png" 3 tiedoston-sisalto nil "harja-ui")
        liite-id (:id luotu-liite)
        luettu-pikkukuva (liitteet/lataa-pikkukuva liitteiden-hallinta liite-id)]

    (is luettu-pikkukuva "Kuvatiedostolla pitää olla pikkukuva.")

    (poista-liite liite-id)))


;; -- Virustarkastuksen odotustestit --

(def s3-url "test-url")

(defn- mock-lue-s3-tiedosto
  "Mock-versio lue-s3-tiedosto-funktiosta, joka simuloi virustarkistuksen valmistumista.
   Parametri :simuloi-onnistuuko määrittää palautetaanko tiedosto onnistuneesti.
   Parametri :virustarkistus-valmis-kierroksella kertoo monennellako kutsulla tiedosto on valmis."
  [url s3hash db {:keys [simuloi-onnistuuko virustarkistus-valmis-kierroksella kutsulaskuri-atom]
                  :or {simuloi-onnistuuko true
                       virustarkistus-valmis-kierroksella 3}
                  :as asetukset}]
  (swap! kutsulaskuri-atom inc)

  (when (and simuloi-onnistuuko
          (>= @kutsulaskuri-atom virustarkistus-valmis-kierroksella))
    {:data (byte-array 10)}))

(deftest tarkista-virustarkistus-onnistuu
  (let [liitteiden-hallinta (:liitteiden-hallinta jarjestelma)
        s3hash "test-hash"
        kutsulaskuri-atom (atom 0)]
    (testing "Virustarkistus onnistuu normaalisti kolmannella yrityksellä"
      (with-redefs [liitteet/lue-s3-tiedosto
                    (fn [url hash db]
                      (mock-lue-s3-tiedosto url hash db {:virustarkistus-valmis-kierroksella 2 :kutsulaskuri-atom kutsulaskuri-atom}))]
        ;; Valmistelu: Lisätään liite tietokantaan
        (let [liite-id (first (first
                                (q (str "INSERT INTO liite (s3hash, \"virustarkastettu?\", tyyppi, koko, lahde, nimi)
                              VALUES ('" s3hash "', false, 'text/plain', 100, 'harja-api', 'testitiedosto.txt') RETURNING id;"))))]

          ;; Varmistetaan, että liite ei ole virustarkastettu
          (let [virustarkastettu? (ffirst (q "SELECT \"virustarkastettu?\" FROM liite WHERE id = '" liite-id "';"))]
            (is (= false virustarkastettu?)))

          ;; Suoritetaan virustarkistuksen odotus
          (let [thread1 (liitteet/odota-s3-virustarkistus-saije
                          (:db jarjestelma) s3-url s3hash
                          (:virustarkistus-thread-pool liitteiden-hallinta)
                          {:max-yritykset 3
                           :odotusaika 0
                           :odotusaika-rnd 0
                           :odotusaika-lisays 50})]

            @thread1

            ;; Tarkistetaan että kutsulaskuri osoittaa funktiota kutsutun 3 kertaa
            (is (= 2 @kutsulaskuri-atom) "Virustarkistusta pitäisi yrittää kunnes se onnistuu")

            ;; Tarkistetaan että liitteen virustarkistus-tila on päivitetty
            (let [virustarkastettu? (ffirst (q "SELECT \"virustarkastettu?\" FROM liite WHERE id = " liite-id ";"))]
              (is (= true virustarkastettu?) "Liitteen virustarkistus-tilan pitäisi päivittyä virustarkistuksen jälkeen")))

          ;; Siivoa
          (poista-liite liite-id)))))

  (let [liitteiden-hallinta (:liitteiden-hallinta jarjestelma)
        s3hash "test-hash-fail"
        kutsulaskuri-atom (atom 0)]
    (testing "Virustarkistus aikakatkaistaan maksimi yritysten jälkeen"
      (with-redefs [liitteet/lue-s3-tiedosto
                    (fn [url hash db]
                      (mock-lue-s3-tiedosto url hash db {:simuloi-onnistuuko false
                                                         :kutsulaskuri-atom kutsulaskuri-atom}))]
        ;; Valmistelu: Lisätään liite tietokantaan
        (let [liite-id (first (first
                                (q (str "INSERT INTO liite (s3hash, \"virustarkastettu?\", tyyppi, koko, lahde, nimi)
                              VALUES ('" s3hash "', false, 'text/plain', 100, 'harja-api', 'testitiedosto-fail.txt') RETURNING id;"))))]

          ;; Varmistetaan, että liite ei ole virustarkastettu
          (let [virustarkastettu? (ffirst (q "SELECT \"virustarkastettu?\" FROM liite WHERE id = " liite-id ";"))]
            (is (= false virustarkastettu?)))

          ;; Suoritetaan virustarkistuksen odotus
          (let [thread1 (liitteet/odota-s3-virustarkistus-saije
                          (:db jarjestelma) s3-url s3hash
                          (:virustarkistus-thread-pool liitteiden-hallinta)
                          {:max-yritykset 2
                           :odotusaika 0
                           :odotusaika-rnd 0
                           :odotusaika-lisays 50})]

            @thread1

            (is (= 2 @kutsulaskuri-atom) "Virustarkistusta pitäisi yrittää maksimi yritysten verran")

            ;; Tarkastetaan, että liitteen virustarkistuksen tila on edelleen sama
            (let [virustarkastettu? (ffirst (q "SELECT \"virustarkastettu?\" FROM liite WHERE id = " liite-id ";"))]
              (is (= false virustarkastettu?) "Liitteen virustarkastus-tilan ei pitäisi päivittyä epäonnistuneen virustarkistuksen jälkeen")))

          ;; Siivoa
          (poista-liite liite-id)))))

  (testing "Rinnakkainen virustarkistus toimii usealle tiedostolle samanaikaisesti"
    (let [liitteiden-hallinta (:liitteiden-hallinta jarjestelma)
          s3hash1 "test-hash-1"
          s3hash2 "test-hash-2"
          s3hash3 "test-hash-3"
          kutsulaskuri-atom1 (atom 0)
          kutsulaskuri-atom2 (atom 0)
          kutsulaskuri-atom3 (atom 0)]
      (with-redefs [liitteet/lue-s3-tiedosto
                    (fn [url hash db]
                      (case hash
                        "test-hash-1" (mock-lue-s3-tiedosto url hash db {:virustarkistus-valmis-kierroksella 1 :kutsulaskuri-atom kutsulaskuri-atom1})
                        "test-hash-2" (mock-lue-s3-tiedosto url hash db {:virustarkistus-valmis-kierroksella 2 :kutsulaskuri-atom kutsulaskuri-atom2})
                        "test-hash-3" (mock-lue-s3-tiedosto url hash db {:virustarkistus-valmis-kierroksella 3 :kutsulaskuri-atom kutsulaskuri-atom3})
                        nil))]
        ;; Lisätään liitteet tietokantaan
        (let [liite-id-1 (first (first
                                  (q (str "INSERT INTO liite (s3hash, \"virustarkastettu?\", tyyppi, koko, lahde, nimi)
                                  VALUES ('" s3hash1 "', false, 'text/plain', 100, 'harja-api', 'testitiedosto-1.txt') RETURNING id;"))))
              liite-id-2 (first (first
                                  (q (str "INSERT INTO liite (s3hash, \"virustarkastettu?\", tyyppi, koko, lahde, nimi)
                                  VALUES ('" s3hash2 "', false, 'text/plain', 100, 'harja-api', 'testitiedosto-2.txt') RETURNING id;"))))
              liite-id-3 (first (first
                                  (q (str "INSERT INTO liite (s3hash, \"virustarkastettu?\", tyyppi, koko, lahde, nimi)
                                  VALUES ('" s3hash3 "', false, 'text/plain', 100, 'harja-api', 'testitiedosto-3.txt') RETURNING id;"))))]

          ;; Suoritetaan virustarkistuksen odotus rinnakkain kaikille tiedostoille
          (let [thread1 (liitteet/odota-s3-virustarkistus-saije
                          (:db jarjestelma) s3-url s3hash1
                          (:virustarkistus-thread-pool liitteiden-hallinta)
                          {:max-yritykset 3
                           :odotusaika 0
                           :odotusaika-rnd 0
                           :odotusaika-lisays 50})
                thread2 (liitteet/odota-s3-virustarkistus-saije
                          (:db jarjestelma) s3-url s3hash2
                          (:virustarkistus-thread-pool liitteiden-hallinta)
                          {:max-yritykset 3
                           :odotusaika 0
                           :odotusaika-rnd 0
                           :odotusaika-lisays 50})
                thread3 (liitteet/odota-s3-virustarkistus-saije
                          (:db jarjestelma) s3-url s3hash3
                          (:virustarkistus-thread-pool liitteiden-hallinta)
                          {:max-yritykset 3
                           :odotusaika 0
                           :odotusaika-rnd 0
                           :odotusaika-lisays 50})]

            ;; Odota kaikkien threadien valmistumista
            @thread1
            @thread2
            @thread3

            (is (= 1 @kutsulaskuri-atom1) "Virustarkistusta pitäisi yrittää maksimi yritysten verran")
            (is (= 2 @kutsulaskuri-atom2) "Virustarkistusta pitäisi yrittää maksimi yritysten verran")
            (is (= 3 @kutsulaskuri-atom3) "Virustarkistusta pitäisi yrittää maksimi yritysten verran")

            ;; Tarkastetaan, että liitteiden virustarkastuksen tilat ovat päivittyneet
            (let [liitteet-jalkeen (q (str "SELECT \"virustarkastettu?\" FROM liite
                                      WHERE id IN (" liite-id-1 ", " liite-id-2 ", " liite-id-3 ") ORDER BY id;"))]
              (is (= true (first (nth liitteet-jalkeen 0))))
              (is (= true (first (nth liitteet-jalkeen 1))))
              (is (= true (first (nth liitteet-jalkeen 2))))))

          ;; Siivoa
          (poista-liite liite-id-1)
          (poista-liite liite-id-2)
          (poista-liite liite-id-3))))))

(deftest tarkasta-virustarkistus-virhetilanteissa
  (let [liitteiden-hallinta (:liitteiden-hallinta jarjestelma)
        s3hash "test-hash"
        kutsulaskuri-atom (atom 0)]
    (testing "Virustarkistus käsittelee poikkeukset oikein"
      (with-redefs [liitteet/lue-s3-tiedosto
                    (fn [url hash db]
                      (if (= @kutsulaskuri-atom 2)
                        (throw (Exception. "Simuloitu virhe S3-tiedoston lukemisessa"))
                        (do
                          (swap! kutsulaskuri-atom inc)
                          nil)))]
        ;; Valmistelu: Lisätään liite tietokantaan
        (let [liite-id (first (first
                                (q (str "INSERT INTO liite (s3hash, \"virustarkastettu?\", tyyppi, koko, lahde, nimi)
                              VALUES ('" s3hash "test-hash-error', false, 'text/plain', 100, 'harja-api', 'testitiedosto-error.txt') RETURNING id;"))))]

          ;; Suoritetaan virustarkistuksen odotus, pitäisi selviytyä virheestä
          (let [thread1 (liitteet/odota-s3-virustarkistus-saije
                          (:db jarjestelma) s3-url s3hash
                          (:virustarkistus-thread-pool liitteiden-hallinta)
                          {:max-yritykset 3
                           :odotusaika 0
                           :odotusaika-rnd 0
                           :odotusaika-lisays 50})]

            @thread1

            ;; Tarkastetaan, että liitteen virustarkituksen tila on edelleen sama
            (let [virustarkastettu? (ffirst (q (str "SELECT \"virustarkastettu?\" FROM liite WHERE id = " liite-id ";")))]
              (is (= false virustarkastettu?) "Liitteen virustarkistus-tilan ei pitäisi päivittyä virhetilanteessa")))

          ;; Siivoa
          (poista-liite liite-id))))))

(deftest tarkista-thread-poolin-toimivuus
  (testing "Thread-pool käsittelee rinnakkaisia tehtäviä oikein"
    (let [liitteiden-hallinta (:liitteiden-hallinta jarjestelma)
          thread-pool ^ThreadPoolExecutor (:virustarkistus-thread-pool liitteiden-hallinta)
          kutsulaskuri-atom (atom 0)]
      (with-redefs [liitteet/lue-s3-tiedosto
                    (fn [url hash db]
                      (swap! kutsulaskuri-atom inc)
                      {:data (byte-array 10)})]

        ;; Suoritetaan useita threadeja rinnakkain poolin kautta
        (let [threads (doall
                        (for [i (range 20)]
                          (liitteet/odota-s3-virustarkistus-saije
                            (:db jarjestelma) s3-url (str "test-hash-" i)
                            thread-pool
                            {:max-yritykset 1
                             :odotusaika 200
                             :odotusaika-rnd 0
                             :odotusaika-lisays 50})))]

          ;; Odota hetki, jotta thread-pool saa tehtävät käsiteltyä
          (Thread/sleep 50)

          ;; Tarkistetaan, että thread-pool on käynnissä ja aktiivisia threadejä on odotettu määrä
          (is (= thread-pool-size (.getPoolSize thread-pool)))
          (is (= thread-pool-size (.getActiveCount thread-pool)))

          ;; Tarkistetaan, että thread-poolin jonossa on odotettu määrä tehtäviä
          (is (= (- 20 thread-pool-size) (.size (.getQueue thread-pool))))

          ;; Odota kaikkien threadien valmistumista
          (doseq [t threads] @t))

        ;; Tarkistetaan, että kutsuja on kutsuttu odotettu määrä
          (is (= 20 @kutsulaskuri-atom) "Thread-poolin pitäisi käsitellä kaikki rinnakkaiset tehtävät")

          ;; Tarkista lopputilanne
          (is (= 0 (.size (.getQueue thread-pool))))
          (is (= 0 (.getActiveCount thread-pool)))))))
