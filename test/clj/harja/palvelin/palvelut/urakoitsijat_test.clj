(ns harja.palvelin.palvelut.urakoitsijat-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.urakoitsijat :refer :all]
            [harja.domain.organisaatio :as o]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.test.check.generators :as gen]
            [clojure.spec.alpha :as s]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :hae (component/using
                               (->Urakoitsijat)
                               [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once jarjestelma-fixture tietokanta-fixture)

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

(deftest vesivaylaurakoitsijoiden-haku-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :vesivaylaurakoitsijat +kayttaja-jvh+ {})]
    (is (>= (count vastaus) 2))
    (is (s/valid? ::o/vesivaylaurakoitsijat-vastaus vastaus))))

(deftest vesivaylaurakoitsijan-tallennus-ja-paivitys-toimii
  (let [testiurakoitsijat (map-indexed (fn [index urakoitsija]
                                         (-> urakoitsija
                                             (dissoc ::o/id)
                                             (assoc ::o/ytunnus (str "FirmaOY" index)
                                                    ::o/postinumero "86300"
                                                    ::o/postitoimipaikka "Oulainen")))
                                       (gen/sample (s/gen ::o/tallenna-urakoitsija-kysely)))]

    (doseq [urakoitsija testiurakoitsijat]
      ;; Luo uusi urakoitsija
      (let [urakoitsija-kannassa (kutsu-palvelua
                                   (:http-palvelin jarjestelma)
                                   :tallenna-urakoitsija +kayttaja-jvh+
                                   urakoitsija)]
        ;; Uusi urakoitsija löytyy vastauksesesta
        (is (= (::o/nimi urakoitsija-kannassa (::o/nimi urakoitsija))))

        ;; Päivitetään urakoitsija
        (let [paivitetty-urakoitsija (assoc urakoitsija ::o/nimi (str (::o/nimi urakoitsija) " päivitetty")
                                                        ::o/id (::o/id urakoitsija-kannassa))
              paivitetty-urakoitsija-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                                              :tallenna-urakoitsija +kayttaja-jvh+
                                                              paivitetty-urakoitsija)]

          ;; Urakoitsija päivittyi
          (is (= (::o/nimi paivitetty-urakoitsija-kannassa)
                 (::o/nimi paivitetty-urakoitsija))))))))
