(ns harja.domain.tilannekuva-test
  (:require [clojure.test :refer [deftest is]]
            [harja.testi :refer :all]
            [harja.domain.tilannekuva :as tk]))

(defn tee-suodatin [id]
  (tk/->Suodatin id "foo" "Foo"))

(def suodattimet {:eka {:toka {{:id 1 :nimi :foo} {:neljas {(tee-suodatin 1) true
                                                            (tee-suodatin 2) true
                                                            (tee-suodatin 3) false}}}}
                  :foo {:bar {:baz {(tee-suodatin 4) false
                                    (tee-suodatin 5) true
                                    (tee-suodatin 6) false}}}})

(deftest valitut-suodattimet
  (let [alku suodattimet
        haluttu {:eka {:toka {{:id 1 :nimi :foo} {:neljas #{1 2}}}}
                 :foo {:bar {:baz #{5}}}}
        tulos (tk/valitut-suodattimet alku)]
    (is (= haluttu tulos))
    (is (not= {} tulos))))

(deftest valittu?
  (is (true? (tk/valittu? #{1 2 3} {:id 1})))
  (is (false? (tk/valittu? #{1 2 3} {:id 4}))))

(deftest valitut-kentat
  (let [valitut (into #{} (map tee-suodatin [1 2 5]))
        tulos (tk/valitut-kentat suodattimet)]
    (is (= 3 (count tulos)))
    (is (= valitut (into #{} tulos)))))

(deftest suodatin-muutettuna
  (let [alkutilanne suodattimet
        haluttu {:eka {:toka {{:id 1 :nimi :foo} {:neljas {:foo false
                                                           (tee-suodatin 2) true
                                                           (tee-suodatin 3) false}}}}
                 :foo {:bar {:baz {(tee-suodatin 4) false
                                   (tee-suodatin 5) true
                                   (tee-suodatin 6) false}}}}]
    (is (= haluttu (tk/suodatin-muutettuna alkutilanne #(do [:foo (not %2)]) #{1})))

    (is (not (= {} (tk/suodatin-muutettuna alkutilanne #(do [:foo (not %2)]) #{1}))))))

(deftest valittujen-suodattimien-idt
  (let [valitut (into #{} (map :id (map tee-suodatin [1 2 5])))
        tulos (tk/valittujen-suodattimien-idt suodattimet)]
    (is (= 3 (count tulos)))
    (is (= valitut (into #{} tulos)))))
