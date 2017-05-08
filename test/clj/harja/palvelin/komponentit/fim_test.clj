(ns harja.palvelin.komponentit.fim-test
  (:require [harja.palvelin.komponentit.todennus :as todennus]
            [harja.domain.oikeudet :as oikeudet]
            [harja.testi :refer :all]
            [clojure.test :as t :refer [deftest is use-fixtures testing]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.fim :as fim]
            [clojure.java.io :as io]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki])
  (:use org.httpkit.fake))

(def +testi-fim+ "https://localhost:6666/FIMDEV/SimpleREST4FIM/1/Group.svc/getGroupUsersFromEntitity")

(defn jarjestelma-fixture [testit]
  (alter-var-root
    #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :integraatioloki (component/using (integraatioloki/->Integraatioloki nil) [:db])
          :fim (component/using
                 (fim/->FIM +testi-fim+)
                 [:db :integraatioloki])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(deftest kayttajaroolien-suodatus-toimii
  (let [kayttajat [{:roolit #{"ely urakanvalvoja" "urakan vastuuhenkilö"}}
                   {:roolit #{"urakan vastuuhenkilö"}}
                   {:roolit #{"ely urakanvalvoja"}}]
        pida-kaikki #{"ely urakanvalvoja" "urakan vastuuhenkilö"}
        pida-urakanvalvoja #{"ely urakanvalvoja"}
        pida-vastuuhenkilo #{"urakan vastuuhenkilö"}]
    ;; Pidetään kaikki
    (is (= (count (fim/suodata-kayttajaroolit kayttajat pida-kaikki)) 3))
    (is (every? #(% "ely urakanvalvoja")
                (map :roolit (fim/suodata-kayttajaroolit kayttajat pida-urakanvalvoja))))
    (is (every? #(% "urakan vastuuhenkilö")
                (map :roolit (fim/suodata-kayttajaroolit kayttajat pida-vastuuhenkilo))))

    ;; Pidetään urakanvalvojat
    (is (= (count (fim/suodata-kayttajaroolit kayttajat pida-urakanvalvoja)) 2))
    (is (every? #(% "ely urakanvalvoja")
                (map :roolit (fim/suodata-kayttajaroolit kayttajat pida-urakanvalvoja))))

    ;; Pidetään vastuuhenkilöt
    (is (= (count (fim/suodata-kayttajaroolit kayttajat pida-vastuuhenkilo)) 2))
    (is (every? #(% "urakan vastuuhenkilö")
                (map :roolit (fim/suodata-kayttajaroolit kayttajat pida-vastuuhenkilo))))))

(deftest kayttajien-haku-toimii
  (let [vastaus-xml (slurp (io/resource "xsd/fim/esimerkit/hae-oulun-hoidon-urakan-kayttajat.xml"))]
    (with-fake-http
      [+testi-fim+ vastaus-xml]
      (let [vastaus (fim/hae-urakan-kayttajat (:fim jarjestelma) "1242141-OULU2")]
        (is (= vastaus [{:etunimi "Erkki"
                         :kayttajatunnus "A000001"
                         :organisaatio "ELY"
                         :poistettu false
                         :puhelin ""
                         :roolinimet ["ELY_Urakanvalvoja"]
                         :roolit ["ELY urakanvalvoja"]
                         :sahkoposti "erkki.esimerkki@example.com"
                         :sukunimi "Esimerkki"
                         :tunniste nil}
                        {:etunimi "Eero"
                         :kayttajatunnus "A000002"
                         :organisaatio "ELY"
                         :poistettu false
                         :puhelin "0400123456789"
                         :roolinimet ["vastuuhenkilo"]
                         :roolit ["Urakan vastuuhenkilö"]
                         :sahkoposti "eero.esimerkki@example.com"
                         :sukunimi "Esimerkki"
                         :tunniste nil}
                        {:etunimi "Eetvartti"
                         :kayttajatunnus "A000003"
                         :organisaatio "ELY"
                         :poistettu false
                         :puhelin "0400123456788"
                         :roolinimet []
                         :roolit []
                         :sahkoposti "eetvartti.esimerkki@example.com"
                         :sukunimi "Esimerkki"
                         :tunniste nil}]))
        (is (every? #(false? (:poistettu %)) vastaus) "Yhtään poistettua henkilöä ei ole listassa")))))
