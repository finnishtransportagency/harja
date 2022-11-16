(ns harja.palvelin.integraatiot.yha.urakan-kohdehakuvastaussanoma-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.yha.sanomat.urakan-kohdehakuvastaussanoma :as vastaussanoma]
            [harja.palvelin.integraatiot.yha.tyokalut :refer :all])
  (:use [slingshot.slingshot :only [try+]]))

(deftest tarkista-invalidi-xml
  (is (thrown? RuntimeException (vastaussanoma/lue-sanoma +invalidi-urakan-kohdehakuvastaus+))))

(deftest tarkista-tyhja-vastaus
  (let [vastaus (vastaussanoma/lue-sanoma +tyhja-urakan-kohteidenhakuvastaus+)]
    (is (contains? vastaus :kohteet))
    (is (= 0 (count (:kohteet vastaus))))))

(deftest tarkista-tierekisteriosoitteen-parsinta
  (let [vastaus (vastaussanoma/lue-sanoma +onnistunut-urakan-kohdehakuvastaus+)
        kohteen-tierekisteriosoite (:tierekisteriosoitevali (first (:kohteet vastaus)))
        alikohteen-tierekisteriosoite (:tierekisteriosoitevali (first (:alikohteet (first (:kohteet vastaus)))))]
    (is (= {:aet 3
            :aosa 101
            :karttapaivamaara #inst "2015-12-31T22:00:00.000-00:00"
            :let 300
            :losa 101
            :tienumero 4}
           kohteen-tierekisteriosoite))
    (is (= {:aet 3
            :ajorata 1
            :aosa 101
            :kaista 11
            :karttapaivamaara #inst "2015-12-31T22:00:00.000-00:00"
            :let 30
            :losa 101
            :tienumero 4}
           alikohteen-tierekisteriosoite))))

(deftest tarkista-usean-urakan-hakuvastaus
  (let [vastaus (vastaussanoma/lue-sanoma +onnistunut-urakan-kohdehakuvastaus+)]
    (is (= 1 (count (:kohteet vastaus))))
    (is (= 3 (:yha-id (first (:kohteet vastaus)))))
    (is (= 2 (count (:alikohteet (first (:kohteet vastaus))))))
    (is (= "A" (:tunnus (first (:kohteet vastaus)))))))

(deftest tarkista-kohdetyypin-maaritys
  (let [vastaus (vastaussanoma/lue-sanoma (.replace
                                            +onnistunut-urakan-kohdehakuvastaus+
                                            "<kohdetyyppi>1</kohdetyyppi>"
                                            "<kohdetyyppi>2</kohdetyyppi>"))]
    (is (= "kevytliikenne" (:yllapitokohdetyyppi (first (:kohteet vastaus)))))))