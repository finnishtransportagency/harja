(ns harja.kyselyt.konversio-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
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
