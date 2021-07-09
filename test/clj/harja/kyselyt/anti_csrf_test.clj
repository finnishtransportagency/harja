(ns harja.kyselyt.anti-csrf-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.kyselyt.anti-csrf :as anti-csrf-q]
            [com.stuartsierra.component :as component]
            [clj-time.core :as t]))

(use-fixtures :once tietokantakomponentti-fixture)

(deftest sessio-testi
  (let [db (:db jarjestelma)
        nyt (t/now)
        kayttaja "jvh"
        nyt+vanhentuminen (t/plus nyt (t/seconds (inc anti-csrf-q/csrf-voimassa-s)))
        nyt+vanhentuminen-1 (t/plus nyt (t/seconds (dec anti-csrf-q/csrf-voimassa-s)))
        nyt+vanhentuminen*2 (t/plus nyt (t/seconds (inc (* anti-csrf-q/csrf-voimassa-s 2))))]

    (testing "Ensimmäisen session luonti"
      (anti-csrf-q/poista-ja-luo-csrf-sessio db kayttaja "token" nyt)
      (is (true? (anti-csrf-q/kayttajan-csrf-sessio-voimassa? db kayttaja "token" nyt))))

    (testing "Uuden session luonti"
      (anti-csrf-q/poista-ja-luo-csrf-sessio db kayttaja "token2" nyt)
      (is (true? (anti-csrf-q/kayttajan-csrf-sessio-voimassa? db kayttaja "token2" nyt)))
      (is (true? (anti-csrf-q/kayttajan-csrf-sessio-voimassa? db kayttaja "token" nyt))
          "Edellinen sessio on yhä voimissaan"))

    (testing "Session vanhentuminen"
      (is (false? (anti-csrf-q/kayttajan-csrf-sessio-voimassa?
                    db kayttaja "token" nyt+vanhentuminen)))
      (is (false? (anti-csrf-q/kayttajan-csrf-sessio-voimassa?
                    db kayttaja "token2" nyt+vanhentuminen))))

    (testing "Session virkistäminen"
      ;; Aluksi: jonkun muun session virkitys ei virkistä käyttäjän sessiota
      (anti-csrf-q/virkista-csrf-sessio-jos-voimassa db kayttaja "elite_haxor" nyt+vanhentuminen-1)
      (is (false? (anti-csrf-q/kayttajan-csrf-sessio-voimassa?
                   db kayttaja "token" nyt+vanhentuminen)))
      ;; Käyttäjän sessioiden virkitystys toimii
      (anti-csrf-q/virkista-csrf-sessio-jos-voimassa db kayttaja "token" nyt+vanhentuminen-1)
      (anti-csrf-q/virkista-csrf-sessio-jos-voimassa db kayttaja "token2" nyt+vanhentuminen-1)
      (is (true? (anti-csrf-q/kayttajan-csrf-sessio-voimassa?
                    db kayttaja "token" nyt+vanhentuminen)))
      (is (true? (anti-csrf-q/kayttajan-csrf-sessio-voimassa?
                    db kayttaja "token2" nyt+vanhentuminen))))

    (testing "Uuden session luonti poistaa vanhentuneet"
      (anti-csrf-q/poista-ja-luo-csrf-sessio db kayttaja "token3" nyt+vanhentuminen*2)
      ;; Vanhentuneet sessiot ei enää voimassa
      (is (false? (anti-csrf-q/kayttajan-csrf-sessio-voimassa?
                    db kayttaja "token" nyt+vanhentuminen)))
      (is (false? (anti-csrf-q/kayttajan-csrf-sessio-voimassa?
                    db kayttaja "token2" nyt+vanhentuminen)))
      ;; Uusi on voimassa
      (is (true? (anti-csrf-q/kayttajan-csrf-sessio-voimassa?
                    db kayttaja "token3" nyt+vanhentuminen))))))