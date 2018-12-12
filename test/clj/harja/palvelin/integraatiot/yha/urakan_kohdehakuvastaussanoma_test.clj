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
    (is (= {:karttapaivamaara #inst "2015-12-31T22:00:00.000-00:00"
            :aosa 3
            :aet 3
            :losa 3
            :let 3
            :tienumero 3}
           kohteen-tierekisteriosoite))
    (is (= {:karttapaivamaara #inst "2015-12-31T22:00:00.000-00:00"
            :ajorata 0
            :kaista 11
            :aosa 3
            :aet 3
            :losa 3
            :let 3
            :tienumero 3}
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