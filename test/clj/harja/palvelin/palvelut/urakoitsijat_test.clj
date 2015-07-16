(ns harja.palvelin.palvelut.urakoitsijat-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.urakoitsijat :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :hae (component/using
                               (->Urakoitsijat)
                               [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once jarjestelma-fixture)

(deftest urakoitsijoiden-haku-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakoitsijat +kayttaja-jvh+ "Joku turha parametri?")]

    (is (not (nil? vastaus)))
    (is (>= (count vastaus) 10))))

(deftest urakkatyypin-urakoitsijoiden-haku-toimii
  (let [hoito (kutsu-palvelua (:http-palvelin jarjestelma)
                              :urakkatyypin-urakoitsijat +kayttaja-jvh+ :hoito)
        paallystys (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :urakkatyypin-urakoitsijat +kayttaja-jvh+ :paallystys)
        tiemerkinta (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :urakkatyypin-urakoitsijat +kayttaja-jvh+ :tiemerkinta)
        valaistus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :urakkatyypin-urakoitsijat +kayttaja-jvh+ :valaistus)]

    (is (not (nil? hoito)))
    (is (not (nil? paallystys)))
    (is (not (nil? tiemerkinta)))
    (is (not (nil? valaistus)))

    (is (>= (count hoito) 1))
    (is (>= (count paallystys) 1))
    (is (>= (count tiemerkinta) 1))
    (is (>= (count valaistus) 1))))

(deftest yllapidon-urakoitsijoiden-haku-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :yllapidon-urakoitsijat +kayttaja-jvh+)]

    (is (set? vastaus))
    (is (>= (count vastaus) 1))))