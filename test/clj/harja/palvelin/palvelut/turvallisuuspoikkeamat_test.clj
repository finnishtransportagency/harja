(ns harja.palvelin.palvelut.turvallisuuspoikkeamat-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.turvallisuuspoikkeamat :as tp]
            [harja.testi :refer :all]
            [clojure.core.match :refer [match]]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :hae-turvallisuuspoikkeamat (component/using
                                                      (tp/->Turvallisuuspoikkeamat)
                                                      [:http-palvelin :db])
                        :tallenna-turvallisuuspoikkeama (component/using
                                                          (tp/->Turvallisuuspoikkeamat)
                                                          [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hae-turvallisuuspoikkeamat-test
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-turvallisuuspoikkeamat +kayttaja-jvh+
                                {:urakka-id @oulun-alueurakan-2005-2010-id
                                 :alku (pvm/luo-pvm (+ 1900 105) 9 1)
                                 :loppu (pvm/luo-pvm (+ 1900 106) 8 30)})]
    (is (match vastaus [{:id _
                         :ilmoituksetlahetetty nil
                         :kasitelty (_ :guard #(and (= (t/year (c/from-sql-date %)) 2005)
                                                       (= (t/month (c/from-sql-date %)) 10)
                                                       (= (t/day (c/from-sql-date %)) 5)))
                         :kommentti {:tyyppi nil}
                         :korjaavattoimenpiteet []
                         :kuvaus "Sepolla oli kiire lastata laatikot, ja torni kaatui päälle. Ehti onneksi pois alta niin ei henki lähtenyt."
                         :lahetetty nil
                         :lahetysonnistunut nil
                         :luotu _
                         :sairaalavuorokaudet 1
                         :sairauspoissaolopaivat 7
                         :sijainti {:coordinates [435847.0
                                                  7216217.0]
                                    :type :point}
                         :tapahtunut (_ :guard #(and (= (t/year (c/from-sql-date %)) 2005)
                                                     (= (t/month (c/from-sql-date %)) 9)
                                                     (= (t/day (c/from-sql-date %)) 30)))
                         :tr {:alkuetaisyys 6
                              :alkuosa 6
                              :loppuetaisyys 6
                              :loppuosa 6
                              :numero 6}
                         :tyontekijanammatti :porari
                         :tyontekijanammattimuu nil
                         :tyyppi #{:tyotapaturma}
                         :urakka 1
                         :vaaralliset-aineet #{}
                         :vahingoittuneetruumiinosat #{}
                         :vahinkoluokittelu #{}
                         :vammat #{}}]
               true))))

(defn poista-tp-taulusta
  [kuvaus]
  (let [id (ffirst (q (str "SELECT id FROM turvallisuuspoikkeama WHERE kuvaus='" kuvaus "'")))]
    (u (str "DELETE FROM korjaavatoimenpide WHERE turvallisuuspoikkeama=" id))
    (u (str "DELETE FROM turvallisuuspoikkeama_kommentti WHERE turvallisuuspoikkeama=" id))
    (u (str "DELETE FROM turvallisuuspoikkeama_liite WHERE turvallisuuspoikkeama=" id))
    (u (str "DELETE FROM turvallisuuspoikkeama WHERE id=" id))))

(deftest tallenna-turvallisuuspoikkeama-test
  (let [tp {:urakka @oulun-alueurakan-2005-2010-id
            :tapahtunut (pvm/luo-pvm (+ 1900 105) 9 1)
            :paattynyt (pvm/luo-pvm (+ 1900 105) 9 1)
            :kasitelty (pvm/luo-pvm (+ 1900 105) 9 1)
            :tyontekijanammatti :kuorma-autonkuljettaja
            :tyotehtava "Testaus"
            :kuvaus "e2e taas punaisena"
            :vammat #{:luunmurtumat}
            :sairauspoissaolopaivat 0
            :sairaalavuorokaudet 0
            :vakavuusaste :lieva
            :vaylamuoto :tie
            :tyyppi #{:tyotapaturma}
            :vahinkoluokittelu #{:ymparistovahinko}
            :sijainti {:type :point :coordinates [0 0]}
            :tr {:numero 6 :alkuetaisyys 6 :loppuetaisyys 6 :alkuosa 6 :loppuosa 6}}
        korjaavattoimenpiteet [{:kuvaus "Ei ressata liikaa" :suoritettu nil :vastaavahenkilo "Kaikki yhdessä"}]
        uusi-kommentti {:tekija "Teemu" :kommentti "Näin on!" :liite nil}
        hoitokausi [(pvm/luo-pvm (+ 1900 105) 9 1) (pvm/luo-pvm (+ 1900 106) 8 30)]
        hae-tp-maara (fn [] (ffirst (q "SELECT count(*) FROM turvallisuuspoikkeama;")))
        vanha-maara (hae-tp-maara)]

    (is (oikeat-sarakkeet-palvelussa?
          [:id :urakka :tapahtunut :kasitelty :tyontekijanammatti :kuvaus
           :vammat :sairauspoissaolopaivat :sairaalavuorokaudet :sijainti :tyyppi
           [:tr :numero] [:tr :alkuetaisyys] [:tr :loppuetaisyys] [:tr :alkuosa] [:tr :loppuosa]]

          :tallenna-turvallisuuspoikkeama
          {:tp tp
           :korjaavattoimenpiteet korjaavattoimenpiteet
           :uusi-kommentti uusi-kommentti
           :hoitokausi hoitokausi}))

    (is (= (hae-tp-maara) (+ 1 vanha-maara)))

    (poista-tp-taulusta "e2e taas punaisena")))
