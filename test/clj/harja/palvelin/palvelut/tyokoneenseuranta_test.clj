(ns harja.palvelin.palvelut.tyokoneenseuranta-test
  (:require [harja.palvelin.palvelut.tyokoneenseuranta :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [clojure.test :refer :all]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                     (component/system-map
                      :db (apply tietokanta/luo-tietokanta testitietokanta)
                      :http-palvelin (testi-http-palvelin)
                      :tyokoneenseuranta (component/using
                                          (->TyokoneseurantaHaku)
                                          [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(defn- kutsu [xmin ymin xmax ymax]
  (kutsu-http-palvelua :hae-tyokoneseurantatiedot +kayttaja-jvh+
                       {:xmin xmin :ymin ymin :xmax xmax :ymax ymax}))

(deftest testaa-tyokoneseurantahaku []
  (let [tyokoneet (kutsu 0 0 9000000 9000000)
        yksi-kone (kutsu 7211200 427800 7211340 427961)
        ei-koneita (kutsu 0 0 50 50)]
    (testing "tyokoneita pitäisi löytyä envelopen sisältä"
      (is (= (count tyokoneet) 3)))
    (testing "yhden tyokoneen palautetut tiedot about oikein"
      (is (= (count yksi-kone) 1))
      (let [kone (first yksi-kone)]
        (is (= (:tyokoneid kone) 31338))
        (is (= (:tyokonetyyppi kone) "aura-auto"))
        (is (= (:jarjestelma "Urakoitsijan järjestelmä 1")))))
    (testing "tyokoneita ei pitäisi löytyä alueelta jossa niitä ei ole (surprise!)"
      (is (= (count ei-koneita) 0)))))
