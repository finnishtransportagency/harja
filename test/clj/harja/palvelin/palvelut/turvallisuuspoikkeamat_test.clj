(ns harja.palvelin.palvelut.turvallisuuspoikkeamat-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.turvallisuuspoikkeamat :as tp]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
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
  (let []
    (is (oikeat-sarakkeet-palvelussa?
          [:id :urakka :tapahtunut :paattynyt :kasitelty :tyontekijanammatti :tyotehtava :kuvaus
           :vammat :sairauspoissaolopaivat :sairaalavuorokaudet :sijainti :tyyppi
           [:tr :numero] [:tr :alkuetaisyys] [:tr :loppuetaisyys] [:tr :alkuosa] [:tr :loppuosa]]

          :hae-turvallisuuspoikkeamat
          {:urakka-id @oulun-alueurakan-id
           :alku      (java.sql.Date. 105 9 1)
           :loppu     (java.sql.Date. 106 8 30)}))))

(defn poista-tp-taulusta
  [kuvaus]
  (let [id (ffirst (q (str "SELECT id FROM turvallisuuspoikkeama WHERE kuvaus='" kuvaus "'")))]
    (u (str "DELETE FROM korjaavatoimenpide WHERE turvallisuuspoikkeama="id))
    (u (str "DELETE FROM turvallisuuspoikkeama_kommentti WHERE turvallisuuspoikkeama="id))
    (u (str "DELETE FROM turvallisuuspoikkeama_liite WHERE turvallisuuspoikkeama="id))
    (u (str "DELETE FROM turvallisuuspoikkeama WHERE id="id))))

(deftest tallenna-turvallisuuspoikkeama-test
  (let [tp {:urakka    @oulun-alueurakan-id :tapahtunut (java.sql.Date. 105 9 1) :paattynyt (java.sql.Date. 105 9 1)
            :kasitelty (java.sql.Date. 105 9 1) :tyontekijanammatti "Testaaja" :tyotehtava "Testaus"
            :kuvaus    "e2e taas punaisena" :vammat "Lähinnä tympäsee" :sairauspoissaolopaivat 0 :sairaalavuorokaudet 0
            :sijainti  [0 0] :tr {:numero 6 :alkuetaisyys 6 :loppuetaisyys 6 :alkuosa 6 :loppuosa 6} :tyyppi [:turvallisuuspoikkeama]}
        korjaavattoimenpiteet [{:kuvaus "Ei ressata liikaa" :suoritettu nil :vastaavahenkilo "Kaikki yhdessä"}]
        uusi-kommentti {:tekija "Teemu" :kommentti "Näin on!" :liite nil}
        hoitokausi [(java.sql.Date. 105 9 1) (java.sql.Date. 106 8 30)]
        hae-tp-maara (fn[] (ffirst (q "SELECT count(*) FROM turvallisuuspoikkeama;")))
        vanha-maara (hae-tp-maara)]

    (is (oikeat-sarakkeet-palvelussa?
          [:id :urakka :tapahtunut :paattynyt :kasitelty :tyontekijanammatti :tyotehtava :kuvaus
           :vammat :sairauspoissaolopaivat :sairaalavuorokaudet :sijainti :tyyppi
           [:tr :numero] [:tr :alkuetaisyys] [:tr :loppuetaisyys] [:tr :alkuosa] [:tr :loppuosa]]

           :tallenna-turvallisuuspoikkeama
           {:tp                    tp
            :korjaavattoimenpiteet korjaavattoimenpiteet
            :uusi-kommentti        uusi-kommentti
            :hoitokausi            hoitokausi}))

    (is (= (hae-tp-maara) (+ 1 vanha-maara)))

    (poista-tp-taulusta "e2e taas punaisena")))


