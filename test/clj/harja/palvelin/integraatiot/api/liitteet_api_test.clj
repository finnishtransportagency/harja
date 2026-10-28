(ns harja.palvelin.integraatiot.api.liitteet-api-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [cheshire.core :as cheshire]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.liitteet :as liitteet]
            [harja.palvelin.integraatiot.api.liitteet :as api-liitteet]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.testi :refer :all]))

(def kayttaja "yit-rakennus")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :liitteiden-hallinta (component/using (liitteet/->Liitteet nil nil nil) [:db])
    :api-liitteet (component/using
                    (api-liitteet/->Liitteet)
                    [:http-palvelin :db :integraatioloki :liitteiden-hallinta])))

(use-fixtures :once jarjestelma-fixture)

(defn- base64 [sisalto]
  (.encodeToString (java.util.Base64/getEncoder) (.getBytes sisalto "UTF-8")))

(defn- liite-pyynto [kohdeluokka kohde-id liite-tunniste nimi]
  (cheshire/encode
    {:otsikko {:lahettaja {:jarjestelma "liite-api-testi"
                           :organisaatio {:nimi "Harja"
                                          :ytunnus "1234567-8"}}
               :viestintunniste {:id liite-tunniste}
               :lahetysaika "2026-01-01T12:00:00Z"}
     :liite-lahetys {:kohdeluokka kohdeluokka
                     :kohdetunniste {:id kohde-id}
                     :liite {:tunniste {:id liite-tunniste}
                             :nimi nimi
                             :tyyppi "text/plain"
                             :sisalto (base64 "liitteen testisisalto")}}}))

(defn- poisto-pyynto [liite-tunniste]
  (cheshire/encode
    {:otsikko {:lahettaja {:jarjestelma "liite-api-testi"
                           :organisaatio {:nimi "Harja"
                                          :ytunnus "1234567-8"}}
               :viestintunniste {:id liite-tunniste}
               :lahetysaika "2026-01-01T12:00:00Z"}
     :liite-poisto {:tunniste {:id liite-tunniste}}}))

(defn- hae-toinen-kayttaja []
  (ffirst (q (format "SELECT kayttajanimi FROM kayttaja WHERE kayttajanimi <> '%s' AND kayttajanimi <> 'Integraatio' LIMIT 1"
                    kayttaja))))

(deftest liite-tarkastukselle-tallentuu
  (let [urakka-id (hae-urakan-id-nimella "Oulun alueurakka 2014-2019")
        kohde-id 910001
        liite-tunniste 920001
        nimi "liite-api-tarkastus.txt"
        kayttaja-id (ffirst (q (format "SELECT id FROM kayttaja WHERE kayttajanimi = '%s'" kayttaja)))]
    (anna-kirjoitusoikeus kayttaja)
    (u (format "INSERT INTO tarkastus (urakka, aika, tyyppi, ulkoinen_id, poistettu, luoja, lahde) VALUES (%s, CURRENT_TIMESTAMP, 'soratie', %s, FALSE, %s, 'harja-api')"
               urakka-id kohde-id kayttaja-id))
    (try
      (let [pyynto (liite-pyynto "tarkastus" kohde-id liite-tunniste nimi)
            ennen (ffirst (q (format "SELECT count(*) FROM liite WHERE urakka = %s" urakka-id)))
            vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/liite"] kayttaja portti pyynto)
            toinen-vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/liite"] kayttaja portti pyynto)]
        (is (= 200 (:status vastaus)))
        (is (= "Liite vastaanotettu onnistuneesti"
               (get (cheshire/decode (:body vastaus) true) :ilmoitukset)))
        (is (= 200 (:status toinen-vastaus)))
        (is (= (inc ennen)
               (ffirst (q (format "SELECT count(*) FROM liite WHERE urakka = %s AND ulkoinen_id = '%s'"
                                  urakka-id liite-tunniste)))))
        (is (= 1
               (ffirst (q (format "SELECT count(*) FROM tarkastus_liite hl JOIN liite l ON l.id = hl.liite WHERE hl.tarkastus = (SELECT id FROM tarkastus WHERE urakka = %s AND ulkoinen_id = %s LIMIT 1) AND l.ulkoinen_id = '%s'"
                                  urakka-id kohde-id liite-tunniste))))))
      (finally
        (u (format "DELETE FROM tarkastus_liite WHERE tarkastus = (SELECT id FROM tarkastus WHERE urakka = %s AND ulkoinen_id = %s)"
                   urakka-id kohde-id))
        (u (format "DELETE FROM liite WHERE urakka = %s AND ulkoinen_id = '%s'"
                   urakka-id liite-tunniste))
        (u (format "DELETE FROM tarkastus WHERE urakka = %s AND ulkoinen_id = %s"
                   urakka-id kohde-id))))))

(deftest liite-poistetaan
  (let [urakka-id (hae-urakan-id-nimella "Oulun alueurakka 2014-2019")
        kohde-id 910002
        liite-tunniste 920002
        kayttaja-id (ffirst (q (format "SELECT id FROM kayttaja WHERE kayttajanimi = '%s'" kayttaja)))]
    (anna-kirjoitusoikeus kayttaja)
    (u (format "INSERT INTO tarkastus (urakka, aika, tyyppi, ulkoinen_id, poistettu, luoja, lahde) VALUES (%s, CURRENT_TIMESTAMP, 'soratie', %s, FALSE, %s, 'harja-api')"
               urakka-id kohde-id kayttaja-id))
    (try
      (let [pyynto (liite-pyynto "tarkastus" kohde-id liite-tunniste "poistettava-liite.txt")
            vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/liite"] kayttaja portti pyynto)
            poisto-vastaus (api-tyokalut/delete-kutsu ["/api/urakat/" urakka-id "/liite"] kayttaja portti
                                                       (poisto-pyynto liite-tunniste))]
        (is (= 200 (:status vastaus)))
        (is (= 200 (:status poisto-vastaus)))
        (is (= "Liite poistettu onnistuneesti"
               (get (cheshire/decode (:body poisto-vastaus) true) :ilmoitukset)))
        (is (= true
               (ffirst (q (format "SELECT poistettu FROM liite WHERE urakka = %s AND ulkoinen_id = %s"
                                  urakka-id liite-tunniste)))))
        (is (= 1
               (ffirst (q (format "SELECT count(*) FROM tarkastus_liite hl JOIN liite l ON l.id = hl.liite WHERE hl.tarkastus = (SELECT id FROM tarkastus WHERE urakka = %s AND ulkoinen_id = %s LIMIT 1) AND l.ulkoinen_id = %s"
                                  urakka-id kohde-id liite-tunniste))))))
      (finally
        (u (format "DELETE FROM tarkastus_liite WHERE tarkastus = (SELECT id FROM tarkastus WHERE urakka = %s AND ulkoinen_id = %s)"
                   urakka-id kohde-id))
        (u (format "DELETE FROM liite WHERE urakka = %s AND ulkoinen_id = '%s'"
                   urakka-id liite-tunniste))
        (u (format "DELETE FROM tarkastus WHERE urakka = %s AND ulkoinen_id = %s"
                   urakka-id kohde-id))))))
(deftest liitteen-poisto-tuntemattomalla-tunnisteella-epaeonnistuu
  (let [urakka-id (hae-urakan-id-nimella "Oulun alueurakka 2014-2019")
        poisto-vastaus (api-tyokalut/delete-kutsu ["/api/urakat/" urakka-id "/liite"] kayttaja portti
                                                   (poisto-pyynto 920003))]
    (is (= 400 (:status poisto-vastaus)))))

(deftest liitteen-lahetyksen-skeema-validoidaan
  (let [urakka-id (hae-urakan-id-nimella "Oulun alueurakka 2014-2019")
        kohde-id 910003
        liite-tunniste 920004
        pyynto (cheshire/decode (liite-pyynto "tarkastus" kohde-id liite-tunniste "validointi.txt") true)
        puuttuva-liite (update-in pyynto [:liite-lahetys] dissoc :liite)
        vaeaerae-tunniste (assoc-in pyynto [:liite-lahetys :liite :tunniste :id] "merkkijono")
        tuntematon-kohdeluokka (assoc-in pyynto [:liite-lahetys :kohdeluokka] "tuntematon")]
    (doseq [virheellinen-pyynto [puuttuva-liite vaeaerae-tunniste tuntematon-kohdeluokka]]
      (let [vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/liite"] kayttaja portti
                                              (cheshire/encode virheellinen-pyynto))]
        (is (= 400 (:status vastaus)))))))

(deftest liitteen-poiston-skeema-validoidaan
  (let [urakka-id (hae-urakan-id-nimella "Oulun alueurakka 2014-2019")
        pyynto (cheshire/decode (poisto-pyynto 920005) true)
        puuttuva-liite-poisto (dissoc pyynto :liite-poisto)
        puuttuva-tunniste (update pyynto :liite-poisto dissoc :tunniste)
        vaeaerae-tunniste (assoc-in pyynto [:liite-poisto :tunniste :id] "merkkijono")]
    (doseq [virheellinen-pyynto [puuttuva-liite-poisto puuttuva-tunniste vaeaerae-tunniste]]
      (let [vastaus (api-tyokalut/delete-kutsu ["/api/urakat/" urakka-id "/liite"] kayttaja portti
                                                (cheshire/encode virheellinen-pyynto))]
        (is (= 400 (:status vastaus)))))))

(deftest liitteen-virheellinen-sisalto-palauttaa-400
  (let [urakka-id (hae-urakan-id-nimella "Oulun alueurakka 2014-2019")
        kohde-id 910005
        liite-tunniste 920006
        kayttaja-id (ffirst (q (format "SELECT id FROM kayttaja WHERE kayttajanimi = '%s'" kayttaja)))]
    (anna-kirjoitusoikeus kayttaja)
    (u (format "INSERT INTO tarkastus (urakka, aika, tyyppi, ulkoinen_id, poistettu, luoja, lahde) VALUES (%s, CURRENT_TIMESTAMP, 'soratie', %s, FALSE, %s, 'harja-api')"
               urakka-id kohde-id kayttaja-id))
    (try
      (let [pyynto (assoc-in (cheshire/decode (liite-pyynto "tarkastus" kohde-id liite-tunniste "virheellinen.txt") true)
                             [:liite-lahetys :liite :sisalto]
                             "tama-ei-ole-validi-sisalto")
            vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/liite"] kayttaja portti
                                              (cheshire/encode pyynto))]
        (is (= 400 (:status vastaus)))
        (is (.contains (:body vastaus) "Liitteen sisältö on virheellinen")))
      (finally
        (u (format "DELETE FROM tarkastus WHERE urakka = %s AND ulkoinen_id = %s"
                   urakka-id kohde-id))))))

(deftest liitteen-toinen-kayttaja-ei-voi-poistaa
  (let [poistaja (hae-toinen-kayttaja)
        urakka-id (hae-urakan-id-nimella "Oulun alueurakka 2014-2019")
        kohde-id 910006
        liite-tunniste 920007
        kayttaja-id (ffirst (q (format "SELECT id FROM kayttaja WHERE kayttajanimi = '%s'" kayttaja)))]
    (anna-kirjoitusoikeus kayttaja)
    (anna-kirjoitusoikeus poistaja)
    (u (format "INSERT INTO kayttajan_lisaoikeudet_urakkaan (kayttaja, urakka, luoja, luotu, poistettu) VALUES ((SELECT id FROM kayttaja WHERE kayttajanimi = '%s'), %s, (SELECT id FROM kayttaja WHERE kayttajanimi = '%s'), CURRENT_TIMESTAMP, FALSE)"
               poistaja urakka-id kayttaja))
    (u (format "INSERT INTO tarkastus (urakka, aika, tyyppi, ulkoinen_id, poistettu, luoja, lahde) VALUES (%s, CURRENT_TIMESTAMP, 'soratie', %s, FALSE, %s, 'harja-api')"
               urakka-id kohde-id kayttaja-id))
    (try
      (let [pyynto (liite-pyynto "tarkastus" kohde-id liite-tunniste "toisen-kayttajan-liite.txt")
            vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/liite"] kayttaja portti pyynto)
            poisto-vastaus (api-tyokalut/delete-kutsu ["/api/urakat/" urakka-id "/liite"] poistaja portti
                                                       (poisto-pyynto liite-tunniste))]
        (is (= 200 (:status vastaus)))
        (is (= 400 (:status poisto-vastaus)))
        (is (= false
               (ffirst (q (format "SELECT poistettu FROM liite WHERE urakka = %s AND ulkoinen_id = %s"
                                  urakka-id liite-tunniste))))))
      (finally
        (u (format "DELETE FROM kayttajan_lisaoikeudet_urakkaan WHERE kayttaja = (SELECT id FROM kayttaja WHERE kayttajanimi = '%s') AND urakka = %s"
                   poistaja urakka-id))
        (u (format "DELETE FROM tarkastus_liite WHERE tarkastus = (SELECT id FROM tarkastus WHERE urakka = %s AND ulkoinen_id = %s)"
                   urakka-id kohde-id))
        (u (format "DELETE FROM liite WHERE urakka = %s AND ulkoinen_id = '%s'"
                   urakka-id liite-tunniste))
        (u (format "DELETE FROM tarkastus WHERE urakka = %s AND ulkoinen_id = %s"
                   urakka-id kohde-id))))))

(deftest liitteen-uudelleenpoisto-epaonnistuu
  (let [urakka-id (hae-urakan-id-nimella "Oulun alueurakka 2014-2019")
        kohde-id 910007
        liite-tunniste 920008
        kayttaja-id (ffirst (q (format "SELECT id FROM kayttaja WHERE kayttajanimi = '%s'" kayttaja)))]
    (anna-kirjoitusoikeus kayttaja)
    (u (format "INSERT INTO tarkastus (urakka, aika, tyyppi, ulkoinen_id, poistettu, luoja, lahde) VALUES (%s, CURRENT_TIMESTAMP, 'soratie', %s, FALSE, %s, 'harja-api')"
               urakka-id kohde-id kayttaja-id))
    (try
      (let [pyynto (liite-pyynto "tarkastus" kohde-id liite-tunniste "uudelleenpoisto.txt")
            vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/liite"] kayttaja portti pyynto)
            poisto-pyynto (poisto-pyynto liite-tunniste)
            ensimmainen-poisto (api-tyokalut/delete-kutsu ["/api/urakat/" urakka-id "/liite"] kayttaja portti poisto-pyynto)
            toinen-poisto (api-tyokalut/delete-kutsu ["/api/urakat/" urakka-id "/liite"] kayttaja portti poisto-pyynto)]
        (is (= 200 (:status vastaus)))
        (is (= 200 (:status ensimmainen-poisto)))
        (is (= 400 (:status toinen-poisto)))
        (is (= true
               (ffirst (q (format "SELECT poistettu FROM liite WHERE urakka = %s AND ulkoinen_id = %s"
                                  urakka-id liite-tunniste))))))
      (finally
        (u (format "DELETE FROM tarkastus_liite WHERE tarkastus = (SELECT id FROM tarkastus WHERE urakka = %s AND ulkoinen_id = %s)"
                   urakka-id kohde-id))
        (u (format "DELETE FROM liite WHERE urakka = %s AND ulkoinen_id = %s"
                   urakka-id liite-tunniste))
        (u (format "DELETE FROM tarkastus WHERE urakka = %s AND ulkoinen_id = %s"
                   urakka-id kohde-id))))))

(deftest liitteen-lahetys-olemattomaan-tarkastukseen-epaonnistuu
  (let [urakka-id (hae-urakan-id-nimella "Oulun alueurakka 2014-2019")
        kohde-id 910008
        liite-tunniste 920009
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/liite"] kayttaja portti
                                          (liite-pyynto "tarkastus" kohde-id liite-tunniste "olemattomaan-tarkastukseen.txt"))]
    (is (= 400 (:status vastaus)))))
