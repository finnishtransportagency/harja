(ns harja.palvelin.palvelut.vesivaylat.materiaalit-test
  (:require [harja.palvelin.palvelut.vesivaylat.materiaalit :as sut]
            [clojure.test :as t :refer [deftest is]]
            [harja.testi :refer [jarjestelma kutsu-palvelua q-map] :as testi]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.vesivaylat.materiaalit :as vvm-q]
            [harja.palvelin.palvelut.vesivaylat.materiaalit :as vv-materiaalit]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [clojure.spec.alpha :as s]
            [harja.domain.vesivaylat.materiaali :as m]
            [clj-time.core :as time]
            [harja.pvm :as pvm]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.jms-test :refer [feikki-sonja]]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.palvelin.komponentit.fim-test :refer [+testi-fim+]]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.sonja.sahkoposti :as sahkoposti]
            [clojure.java.io :as io]
            [harja.palvelin.komponentit.sonja :as sonja])
  (:use org.httpkit.fake))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testi/testitietokanta)
                        :http-palvelin (testi/testi-http-palvelin)
                        :pois-kytketyt-ominaisuudet testi/testi-pois-kytketyt-ominaisuudet
                        :fim (component/using
                               (fim/->FIM +testi-fim+)
                               [:db :integraatioloki])
                        :integraatioloki (component/using
                                           (integraatioloki/->Integraatioloki nil)
                                           [:db])
                        :sonja (feikki-sonja)
                        :sonja-sahkoposti (component/using
                                            (sahkoposti/luo-sahkoposti "foo@example.com"
                                                                       {:sahkoposti-sisaan-jono "email-to-harja"
                                                                        :sahkoposti-ulos-jono "harja-to-email"
                                                                        :sahkoposti-ulos-kuittausjono "harja-to-email-ack"})
                                            [:sonja :db :integraatioloki])
                        :vv-materiaalit (component/using
                                          (vv-materiaalit/->Materiaalit)
                                          [:db :http-palvelin :fim :sonja-sahkoposti])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(t/use-fixtures :each testi/tietokanta-fixture jarjestelma-fixture)

(def pvm-gen
  (gen/fmap (fn [[vuosi kk pv]]
              (pvm/luo-pvm vuosi kk pv))
            (gen/tuple (gen/choose 2000 2020)
                       (gen/choose 1 12)
                       (gen/choose 1 28))))

(def testimateriaali-gen
  (gen/fmap
    (fn [[nimi maara pvm lisatieto yksikko]]
      {::m/urakka-id 1
       ::m/nimi nimi
       ::m/maara maara
       ::m/pvm pvm
       ::m/lisatieto lisatieto
       ::m/yksikko yksikko})
    (gen/tuple (gen/elements #{"poiju" "viitta" "akku"})
               (gen/choose -1000 1000)
               pvm-gen
               gen/string-alphanumeric
               gen/string-alphanumeric)))

(deftest materiaalen-kirjaus-ja-haku
  (let [urakka-id 1]
    (loop [testauskerrat 50
           generoidut-materiaalit []]

      ;; Assertoi, että palvelun kautta haettu ja generoiduista materiaaleista laskettu
      ;; summa on sama kaikille materiaaleille
      (let [haettu-listaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-vesivayla-materiaalilistaus
                                           testi/+kayttaja-jvh+
                                           {::m/urakka-id urakka-id})

            listauksen-maara (fmap (comp ::m/maara-nyt first)
                                   (group-by ::m/nimi haettu-listaus))

            generoidut-maarat (fmap #(reduce + 0 (map ::m/maara %))
                                    (group-by ::m/nimi generoidut-materiaalit))]

        (is (= listauksen-maara generoidut-maarat))

        (when (pos? testauskerrat)
          ;; Generoi ja kirjaa uusi materiaali
          (let [m (gen/generate testimateriaali-gen)]
            (kutsu-palvelua (:http-palvelin jarjestelma)
                            :kirjaa-vesivayla-materiaali
                            testi/+kayttaja-jvh+
                            m)
            (recur (dec testauskerrat) (conj generoidut-materiaalit m))))))))

(deftest materiaalen-haku-ilman-oikeutta
  (let [urakka-id (testi/hae-helsingin-vesivaylaurakan-id)]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-vesivayla-materiaalilistaus
                                           testi/+kayttaja-ulle+
                                           {::m/urakka-id urakka-id})))))

(deftest materiaalen-kirjaus-ilman-oikeutta
  (let [urakka-id (testi/hae-helsingin-vesivaylaurakan-id)]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :kirjaa-vesivayla-materiaali
                                           testi/+kayttaja-ulle+
                                           {::m/urakka-id urakka-id
                                            ::m/nimi "Ulle"
                                            ::m/maara 80000
                                            ::m/pvm (pvm/nyt)
                                            ::m/yksikko "kpl"})))))

(deftest materiaalien-poisto
  (let [urakka-id (testi/hae-helsingin-vesivaylaurakan-id)
        poistettava-materiaali-ennen (first (q-map "SELECT id, poistettu FROM vv_materiaali WHERE poistettu IS NOT TRUE LIMIT 1"))
        materiaalien-lkm-ennen (:maara (first (q-map "SELECT COUNT(*) as maara FROM vv_materiaali WHERE poistettu IS NOT TRUE")))
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :poista-materiaalikirjaus
                          testi/+kayttaja-jvh+
                          {::m/urakka-id urakka-id
                           ::m/id (:id poistettava-materiaali-ennen)})
        poistettava-materiaali-jalkeen (first (q-map "SELECT id, poistettu FROM vv_materiaali WHERE id = " (:id poistettava-materiaali-ennen)))
        materiaalien-lkm-jalkeen (:maara (first (q-map "SELECT COUNT(*) as maara FROM vv_materiaali WHERE poistettu IS NOT TRUE")))]

    ;; Ei-poistettu materiaali merkittiin poistetuksi
    (is (false? (:poistettu poistettava-materiaali-ennen)))
    (is (true? (:poistettu poistettava-materiaali-jalkeen)))

    ;; Muita matskuja ei poistettu
    (is (= materiaalien-lkm-ennen (+ materiaalien-lkm-jalkeen 1)))))

(deftest materiaalen-poisto-ilman-oikeutta
  (let [urakka-id (testi/hae-helsingin-vesivaylaurakan-id)]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :poista-materiaalikirjaus
                                           testi/+kayttaja-ulle+
                                           {::m/urakka-id urakka-id
                                            ::m/id 666})))))

(deftest materiaalen-poisto-eri-urakasta
  (let [urakka-id (testi/hae-muhoksen-paallystysurakan-id)
        poistettava-materiaali-id (:id (first (q-map "SELECT id FROM vv_materiaali WHERE poistettu IS NOT TRUE LIMIT 1")))]
    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :poista-materiaalikirjaus
                                                   testi/+kayttaja-jvh+
                                                   {::m/urakka-id urakka-id
                                                    ::m/id poistettava-materiaali-id})))))

(deftest materiaalien-maaran-ja-yksikon-muokkaus
  (let [urakka-id (testi/hae-helsingin-vesivaylaurakan-id)
        materiaali-nimi "Hiekkasäkki"
        uusi-alkuperainen-maara 48598
        materiaalilistaukset (testi/hae-helsingin-vesivaylaurakan-materiaalit)
        hiekkasakin-id (some #(when (= "Hiekkasäkki" (:nimi %))
                                (get-in % [:muutokset 0 :id]))
                             materiaalilistaukset)
        idt (some #(when (= "Hiekkasäkki" (:nimi %))
                     (map :id (:muutokset %)))
                  materiaalilistaukset)
        yksikko "kg"
        uusi-halytysraja 3000
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :muuta-materiaalien-alkuperaiset-tiedot
                          testi/+kayttaja-jvh+
                          {::m/urakka-id urakka-id
                           :uudet-alkuperaiset-tiedot [{::m/ensimmainen-kirjaus-id hiekkasakin-id
                                                        ::m/idt idt
                                                        ::m/alkuperainen-maara uusi-alkuperainen-maara
                                                        ::m/yksikko yksikko
                                                        ::m/halytysraja uusi-halytysraja}]})
        uudet-materiaalit (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :hae-vesivayla-materiaalilistaus
                                          testi/+kayttaja-jvh+
                                          {::m/urakka-id urakka-id})
        hiekkasakki (first (filter #(= (::m/nimi %) materiaali-nimi) uudet-materiaalit))]

    (is (= (::m/alkuperainen-maara hiekkasakki) uusi-alkuperainen-maara))
    (is (= (::m/yksikko hiekkasakki) yksikko))
    (is (= (::m/halytysraja hiekkasakki) uusi-halytysraja))))

(deftest materiaalien-alkuperaisen-maaran-muokkaus-ilman-oikeutta
  (let [urakka-id (testi/hae-helsingin-vesivaylaurakan-id)
        uusi-alkuperainen-maara 48598
        materiaalilistaukset (testi/hae-helsingin-vesivaylaurakan-materiaalit)
        hiekkasakin-id (some #(when (= "Hiekkasäkki" (:nimi %))
                                (get-in % [:muutokset 0 :id]))
                             materiaalilistaukset)]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :muuta-materiaalien-alkuperaiset-tiedot
                                           testi/+kayttaja-ulle+
                                           {::m/urakka-id urakka-id
                                            :uudet-alkuperaiset-tiedot [{::m/ensimmainen-kirjaus-id hiekkasakin-id
                                                                         ::m/idt [hiekkasakin-id]
                                                                         ::m/alkuperainen-maara uusi-alkuperainen-maara}]})))))

(deftest materiaalien-alkuperaisen-maaran-muokkaus-eri-urakkaan
  (let [urakka-id (testi/hae-muhoksen-paallystysurakan-id)
        materiaalilistaukset (testi/hae-helsingin-vesivaylaurakan-materiaalit)
        hiekkasakin-id (some #(when (= "Hiekkasäkki" (:nimi %))
                                (get-in % [:muutokset 0 :id]))
                             materiaalilistaukset)
        idt (some #(when (= "Hiekkasäkki" (:nimi %))
                     (map :id (:muutokset %)))
                  materiaalilistaukset)]
    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :muuta-materiaalien-alkuperaiset-tiedot
                                                   testi/+kayttaja-jvh+
                                                   {::m/urakka-id urakka-id
                                                    :uudet-alkuperaiset-tiedot [{::m/ensimmainen-kirjaus-id hiekkasakin-id
                                                                                 ::m/idt idt
                                                                                 ::m/alkuperainen-maara 666}]})))))


(deftest toimenpiteen-materiaalien-poisto
  (let [toimenpide-id (first (first (testi/q "select max(toimenpide) from vv_materiaali where poistettu = false")))
        poistettuja (vvm-q/poista-toimenpiteen-kaikki-materiaalikirjaukset (:db jarjestelma) testi/+kayttaja-jvh+ toimenpide-id)
        rivi-lkm (first (first (testi/q (str "select count(*) from vv_materiaali where poistettu = false and toimenpide = " toimenpide-id))))]
    (is (pos? toimenpide-id))
    (is (pos? poistettuja))
    (is (= 0 rivi-lkm))))

(deftest materiaalin-halytysrajan-alitus
  (let [urakka-id (testi/hae-helsingin-vesivaylaurakan-id)
        materiaali-halytysrajalla (first (q-map "SELECT nimi, maara, halytysraja FROM vv_materiaali WHERE \"urakka-id\"=" urakka-id " AND halytysraja IS NOT NULL ORDER BY nimi, \"urakka-id\""))
        {nimi :nimi
         aloitus-maara :maara
         halytysraja :halytysraja
         yksikko :yksikko} materiaali-halytysrajalla
        sahkoposti-valitetty (atom false)
        fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-helsingin-vesivaylaurakan-kayttajat.xml"))

        materiaalin-vahennys {::m/urakka-id urakka-id
                              ::m/nimi nimi
                              ::m/maara (- (- (+ aloitus-maara 1) halytysraja))
                              ::m/pvm (pvm/nyt)
                              ::m/yksikko yksikko}]
    (sonja/kuuntele! (:sonja jarjestelma) "harja-to-email" (fn [_] (reset! sahkoposti-valitetty true)))
    (with-fake-http
      [+testi-fim+ fim-vastaus]
      (testi/kutsu-http-palvelua :kirjaa-vesivayla-materiaali testi/+kayttaja-jvh+ materiaalin-vahennys))

    (testi/odota-ehdon-tayttymista #(true? @sahkoposti-valitetty) "Sähköposti lähetettiin" 10000)
    (is (true? @sahkoposti-valitetty) "Sähköposti lähetettiin")))
