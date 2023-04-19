(ns harja.kyselyt.konversio-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [clj-time.coerce :as coerce]
            [clj-time.core :as t]
            [clojure.data :refer [diff]]
            [harja.kyselyt.konversio :as konversio]
            [harja.pvm :as pvm]))

(deftest sarakkeet-vektoriin-test
  (let [mankeloitava [{:id 1 :juttu {:id 1}}
                      {:id 1 :juttu {:id 2} :homma {:id 1}}
                      {:id 1 :juttu nil :homma {:id 2}}

                      {:id 2 :juttu {:id 3} :homma {:id 3}}
                      {:id 2 :juttu {:id 4} :homma {:id 3}}

                      {:id 3}

                      {:id 4 :juttu {:id nil} :homma {:id nil}}]
        haluttu [{:id 1, :jutut [{:id 2} {:id 1}], :hommat [{:id 2} {:id 1}]}
                 {:id 2, :jutut [{:id 4} {:id 3}], :hommat [{:id 3}]}
                 {:id 3, :jutut [], :hommat []}
                 {:id 4, :jutut [], :hommat []}]

        [only-in-a only-in-b in-both] (diff
                                        (harja.kyselyt.konversio/sarakkeet-vektoriin mankeloitava
                                                                                     {:juttu :jutut :homma :hommat})
                                        haluttu)]
    (is (= (konversio/sarakkeet-vektoriin mankeloitava
                                          {:juttu :jutut :homma :hommat})
           haluttu))
    (is (nil? only-in-a))
    (is (nil? only-in-b))
    (is (= (count in-both) (count haluttu)))))

(deftest tarkista-sekvenssin-muuttaminen-jdbc-arrayksi
  (is (= "{1,2,3}" (konversio/seq->array ["1" "2" "3"])) "Merkkijonosekvenssi muunnettin oikein")
  (is (= "{1,2,3}" (konversio/seq->array [:1 :2 :3])) "Keyword-sekvenssi muunnettin oikein")
  (is (= "{1,2,3}" (konversio/seq->array [1 2 3])) "Kokonaislukusekvenssi muunnettin oikein")
  (is (= "{1.1,2.2,3.3}" (konversio/seq->array [1.1 2.2 3.3])) "Desimaalilukusekvenssi muunnettin oikein")  )

(deftest pgobject-luku
  (testing "Moniarvoinen pgobject, jossa merkkijono sisältää pilkun"
    (let [m (konversio/pgobject->map
             "(6893,178.304790486446,-,\"Liikennemerkkien, opasteiden ja liikenteenohjauslaitteiden hoito sekä reunapaalujen kp\",666)"
             :toimenpidekoodi :long
             :maara :double
             :yksikko :string
             :toimenpide :string
             :pedonluku :long)]
      (is (= (:toimenpidekoodi m) 6893))
      (is (=marginaalissa? (:maara m) 178.3))
      (is (= (:yksikko m) "-"))
      (is (= (:toimenpide m) "Liikennemerkkien, opasteiden ja liikenteenohjauslaitteiden hoito sekä reunapaalujen kp"))
      (is (= (:pedonluku m) 666))))

  (testing "Reunatapaukset, tyhjä tai yksi arvo"
    (is (= {:eka ""} (konversio/pgobject->map "()" :eka :string)))
    (is (= {:meaning-of-life 42} (konversio/pgobject->map "(42)" :meaning-of-life :long)))
    (is (= {:meta "foo, bar and baz"}
           (konversio/pgobject->map "(\"foo, bar and baz\")" :meta :string))))


  (testing "Tyhjät arvot lasketaan oikein"
    (is (= {:id 2
            :kuvaus "kuvataan!"
            :suoritettu ""}
           (konversio/pgobject->map "(2,kuvataan!,)"
                                    :id :long
                                    :kuvaus :string
                                    :suoritettu :string))
        "Lopussa oleva tyhjä arvo toimii")
    (is (= {:id 666
            :eka ""
            :toka "loppu"}
           (konversio/pgobject->map "(666,,loppu)"
                                    :id :long
                                    :eka :string
                                    :toka :string))))

  (testing "Päivämäärän parsinta toimii"
    (is (= (pvm/luo-pvm 3000 0 1)
           (:p (konversio/pgobject->map "(3000-01-01 00:00:00)" :p :date))))))

(deftest sql-date-suomalaiseen-formaattiin-test
  (testing "HappyCase - palautetaan asiallinen vastaus"
    (let [joda-datetime (t/to-time-zone (t/date-time 2022 10 14 12 12 12 111) (t/time-zone-for-id "Europe/Helsinki"))
          sqldate (coerce/to-sql-date joda-datetime)
          j-date (konversio/java-date sqldate)
          p-date (pvm/pvm-aika j-date)]
      (is (not (nil? j-date)))
      (is (= "14.10.2022 15:12" p-date))))
  (testing "ClassCastException - palautetaan virhe"
    (let [nyt (t/now)]
      (is (thrown? ClassCastException (konversio/java-date nyt))))))


(deftest xml-prettyprint-data-xml-toimii
  (let [data-xml (slurp "test/resurssit/konversio/prettyprint-xml.xml" )
        prettyprint-xml (konversio/prettyprint-xml data-xml)]
    (is (not (nil? prettyprint-xml)))))

(deftest xml-prettyprint-xxe-palauttaa-konvertoimattoman-xmln
  (let [data-xml (slurp "test/resurssit/konversio/xxe-xml.xml" )
        vastaus (konversio/prettyprint-xml data-xml)]
    (is (= data-xml vastaus))))

(deftest parsi-utc-str-aika->sql-timestamp-test-toimii
  (is (= (konversio/parsi-utc-str-aika->sql-timestamp "2023-04-14T09:07:20.162457Z") #inst "2023-04-14T09:07:20.000-00:00"))
  (is (= (konversio/parsi-utc-str-aika->sql-timestamp "2023-04-14T09:07:20.12345Z") #inst "2023-04-14T09:07:20.000-00:00"))
  (is (= (konversio/parsi-utc-str-aika->sql-timestamp "2023-04-14T09:07:20.1234Z") #inst "2023-04-14T09:07:20.000-00:00"))
  (is (= (konversio/parsi-utc-str-aika->sql-timestamp "2023-04-14T09:07:20.123Z") #inst "2023-04-14T09:07:20.000-00:00"))
  (is (= (konversio/parsi-utc-str-aika->sql-timestamp "2023-04-14T09:07:20.12Z") #inst "2023-04-14T09:07:20.000-00:00"))
  (is (= (konversio/parsi-utc-str-aika->sql-timestamp "2023-04-14T09:07:20.1Z") #inst "2023-04-14T09:07:20.000-00:00")))

(deftest parsi-utc-str-aika->sql-timestamp-test-epaonnistuu
  (is (= (konversio/parsi-utc-str-aika->sql-timestamp "2023-04-14T09:07:2") nil)))
