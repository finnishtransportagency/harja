(ns harja.palvelin.raportointi.tuotekohtainen-raportti-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.komponentit.http-palvelin :as palvelin]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.palvelin.raportointi :refer [suorita-raportti]]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :pdf-vienti (component/using
                                      (pdf-vienti/luo-pdf-vienti)
                                      [:http-palvelin])
                        :raportointi (component/using
                                       (raportointi/luo-raportointi)
                                       [:db :pdf-vienti])
                        :raportit (component/using
                                    (raportit/->Raportit)
                                    [:http-palvelin :db :raportointi :pdf-vienti])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest tuotekohtainen-laskutusyhteenveto-raportti-toimii
  (palvelin/julkaise-palvelu (:http-palvelin jarjestelma) :suorita-raportti
                             (fn [user raportti]
                               (suorita-raportti (:raportointi jarjestelma) user raportti))
                             {:trace false})
  
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :suorita-raportti
                                +kayttaja-jvh+
                                {:nimi       :laskutusyhteenveto-tuotekohtainen
                                 :konteksti  "urakka"
                                 :urakka-id  (hae-urakan-id-nimella "Oulun MHU 2019-2024")
                                 :parametrit {:urakkatyyppi :teiden-hoito
                                              :alkupvm      (c/to-date (t/local-date 2019 10 1))
                                              :loppupvm     (c/to-date (t/local-date 2020 9 30))
                                              :aikarajaus   :hoitokausi}})

        raportin-nimi (-> vastaus second :nimi)
        perusluku-teksti (nth vastaus 3)
        indeksikerroin-teksti (nth vastaus 4)
        raportit (nth vastaus 5)
        laskutusyhteenveto (take 16 raportit)
        talvihoito-yhteensa (-> (first laskutusyhteenveto) (nth 3) (nth 5) second second :arvo)
        liikenneymp-akilliset-hoitotyot (-> (second laskutusyhteenveto) (nth 3) (nth 3) second second :arvo)
        liikenneymp-vahinkojen-korjaukset (-> (second laskutusyhteenveto) (nth 3) (nth 4) second second :arvo)
        liikenneymp-yhteensa (-> (second laskutusyhteenveto) (nth 3) (nth 5) second second :arvo)
        soratiet-yhteensa (-> (nth laskutusyhteenveto 2) (nth 3) (nth 5) second second :arvo)
        paallyste-yhteensa (-> (nth laskutusyhteenveto 3) (nth 3) (nth 3) second second :arvo)
        mhu-yllapito-yhteensa (-> (nth laskutusyhteenveto 4) (nth 3) (nth 4) second second :arvo)
        mhu-korvaus-yhteensa (-> (nth laskutusyhteenveto 5) (nth 3) (nth 3) second second :arvo)
        mhu-bonukset (-> (nth laskutusyhteenveto 6) (nth 3) (nth 3) second second :arvo)]

    (is (= raportin-nimi "Laskutusyhteenveto (01.10.2019 - 30.09.2020)"))
    (is (= perusluku-teksti [:teksti "Indeksilaskennan perusluku: 110,8"]) "perusluku")
    (is (= indeksikerroin-teksti [:teksti "Hoitokauden 2019-20 indeksikerroin: 1,081"]) "indeksikerroin")
    (is (= talvihoito-yhteensa 5411.791430M))
    (is (= liikenneymp-akilliset-hoitotyot 4444.44M))
    (is (= liikenneymp-vahinkojen-korjaukset 0.0M))
    (is (= liikenneymp-yhteensa 6251.487630M))
    (is (= soratiet-yhteensa 8801.94M))
    (is (= paallyste-yhteensa 11001.94M))
    (is (= mhu-yllapito-yhteensa 15401.94M))
    (is (= mhu-korvaus-yhteensa 13201.94M))
    (is (= mhu-bonukset 5634.500M))))
