(ns harja.palvelin.integraatiot.api.tyokalut.liitteet-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :as api-liitteet]))

(defn poista-liite [liite-id]
  (u (str "DELETE FROM liite WHERE id = " liite-id ";")))

(defn base64-enkoodaa-string [s]
  (let [data (.getBytes s "UTF-8")]
    (api-liitteet/enkoodaa-base64 data)))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :liitteiden-hallinta
          (component/using
            (harja.palvelin.komponentit.liitteet/->Liitteet nil nil nil)
            [:db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)


(deftest palauta-vain-uniikit-liitteet
  (testing "Kaikki uniikkeja"
    (let [db (:db jarjestelma)
          urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
          liitteet [{:liite {:nimi "ei-kannassa1.xml" :tyyppi "text/xml" :sisalto (base64-enkoodaa-string "data1")}}
                    {:liite {:nimi "ei-kannassa2.xml" :tyyppi "text/xml" :sisalto (base64-enkoodaa-string "data2")}}]]
      ;; Ei lisätä liitteitä kantaan, koska oletaan liitteet uniikeiksi
      (is (= liitteet (api-liitteet/palauta-vain-uniikit-liitteet db urakka-id liitteet)))))

  (testing "Osa duplikaatteje ja koko/nimi/urakka vaihtelee"
    (let [db (:db jarjestelma)
          urakka-id-1 (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
          urakka-id-2 (hae-iin-maanteiden-hoitourakan-2021-2026-id)
          liitteet-kantaan-1 [{:liite {:nimi "liite1.xml" :tyyppi "text/xml" :sisalto (base64-enkoodaa-string "data1")}}
                              {:liite {:nimi "liite2.xml" :tyyppi "text/xml" :sisalto (base64-enkoodaa-string "data2")}}]
          liitteet-kantaan-2 [{:liite {:nimi "liite3.xml" :tyyppi "text/xml" :sisalto (base64-enkoodaa-string "data3")}}]
          liite-db-idt (atom [])
          _ (api-liitteet/luo-liitteet db (:liitteiden-hallinta jarjestelma) urakka-id-1 nil liitteet-kantaan-1 #(swap! liite-db-idt conj %))
          _ (api-liitteet/luo-liitteet db (:liitteiden-hallinta jarjestelma) urakka-id-2 nil liitteet-kantaan-2 #(swap! liite-db-idt conj %))
          lisattavat-liitteet [
                               ;; Uniikki liite. Ei lainkaan tietokannassa
                               {:liite {:nimi "uniikki.xml" :tyyppi "text/xml" :sisalto (base64-enkoodaa-string "uniikki")}}
                               ;; Eri nimi, mutta sama data kuin liite1:ssä
                               {:liite {:nimi "uniikki-nimi.xml" :tyyppi "text/xml" :sisalto (base64-enkoodaa-string "data1")}}
                               ;; Sama nimi ja data kuin liite1:ssä
                               {:liite {:nimi "liite1.xml" :tyyppi "text/xml" :sisalto (base64-enkoodaa-string "data1")}}
                               ;; Sama nimi kuin liite2:ssä, mutta eri data
                               {:liite {:nimi "liite2.xml" :tyyppi "text/xml" :sisalto (base64-enkoodaa-string "uniikki-data")}}
                               ;; Sama nimi ja data kuin liite3:ssä
                               {:liite {:nimi "liite3.xml" :tyyppi "text/xml" :sisalto (base64-enkoodaa-string "data3")}}]
          uniikit-urakka-1 (api-liitteet/palauta-vain-uniikit-liitteet db urakka-id-1 lisattavat-liitteet)
          uniikit-urakka-2 (api-liitteet/palauta-vain-uniikit-liitteet db urakka-id-2 lisattavat-liitteet)]

      ;; Testataan urakan 1 uniikit liitteet
      ;; Nimi ja datasisältö (ja täten myös koko vaihtuu, joten näiden pitäisi sen myötä olla uniikkeja)
      (is (= [{:liite {:nimi "uniikki.xml" :tyyppi "text/xml" :sisalto (String. (base64-enkoodaa-string "uniikki"))}}
              {:liite {:nimi "uniikki-nimi.xml" :tyyppi "text/xml" :sisalto (String. (base64-enkoodaa-string "data1"))}}
              {:liite {:nimi "liite2.xml" :tyyppi "text/xml" :sisalto (String. (base64-enkoodaa-string "uniikki-data"))}}
              {:liite {:nimi "liite3.xml" :tyyppi "text/xml" :sisalto (String. (base64-enkoodaa-string "data3"))}}]
            (mapv (fn [l] (assoc-in l [:liite :sisalto] (String. (get-in l [:liite :sisalto])))) uniikit-urakka-1)))

      ;; Testataan urakan 2 uniikit liitteet
      (is (= [{:liite {:nimi "uniikki.xml" :tyyppi "text/xml" :sisalto (String. (base64-enkoodaa-string "uniikki"))}}
              {:liite {:nimi "uniikki-nimi.xml" :tyyppi "text/xml" :sisalto (String. (base64-enkoodaa-string "data1"))}}
              {:liite {:nimi "liite1.xml" :tyyppi "text/xml" :sisalto (String. (base64-enkoodaa-string "data1"))}}
              {:liite {:nimi "liite2.xml" :tyyppi "text/xml" :sisalto (String. (base64-enkoodaa-string "uniikki-data"))}}]
            (mapv (fn [l] (assoc-in l [:liite :sisalto] (String. (get-in l [:liite :sisalto])))) uniikit-urakka-2)))

      (doseq [liite-id @liite-db-idt]
        (poista-liite liite-id))))
  (testing "Kaikki duplikaatteja"
    (let [db (:db jarjestelma)
          urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
          liitteet [{:liite {:nimi "duplikaatti1.xml" :tyyppi "text/xml" :sisalto (base64-enkoodaa-string "duplikaatti1")}}
                    {:liite {:nimi "duplikaatti2.xml" :tyyppi "text/xml" :sisalto (base64-enkoodaa-string "duplikaatti2")}}]
          liite-db-idt (atom [])]

      (api-liitteet/luo-liitteet db (:liitteiden-hallinta jarjestelma) urakka-id nil liitteet #(swap! liite-db-idt conj %))

      (is (= [] (api-liitteet/palauta-vain-uniikit-liitteet db urakka-id liitteet)))

      (doseq [liite-id @liite-db-idt]
        (poista-liite liite-id))))

  (testing "Ei liitteitä"
    (let [db (:db jarjestelma)
          urakka-id 1
          liitteet []]
      (is (= [] (api-liitteet/palauta-vain-uniikit-liitteet db urakka-id liitteet))))))
