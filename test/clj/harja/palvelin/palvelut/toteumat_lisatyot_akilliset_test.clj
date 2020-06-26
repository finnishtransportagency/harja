(ns harja.palvelin.palvelut.toteumat-lisatyot-akilliset-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toteumat :refer :all]
            [harja.tyokalut.functor :refer [fmap]]
            [taoensso.timbre :as log]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.palvelin.palvelut.toteumat :as toteumat]
            [harja.palvelin.palvelut.tehtavamaarat :as tehtavamaarat]
            [harja.palvelin.palvelut.karttakuvat :as karttakuvat]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]))

(def +testi-tierekisteri-url+ "harja.testi.tierekisteri")

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (let [tietokanta (tietokanta/luo-tietokanta testitietokanta)]
                      (component/start
                        (component/system-map
                          :db tietokanta
                          :db-replica tietokanta
                          :http-palvelin (testi-http-palvelin)
                          :karttakuvat (component/using
                                         (karttakuvat/luo-karttakuvat)
                                         [:http-palvelin :db])
                          :integraatioloki (component/using
                                             (integraatioloki/->Integraatioloki nil)
                                             [:db])
                          :tierekisteri (component/using
                                          (tierekisteri/->Tierekisteri +testi-tierekisteri-url+ nil)
                                          [:db :integraatioloki])
                          :toteumat (component/using
                                      (toteumat/->Toteumat)
                                      [:http-palvelin :db :db-replica :karttakuvat :tierekisteri])
                          :tehtavamaarat (component/using
                                           (tehtavamaarat/->Tehtavamaarat)
                                           [:http-palvelin :db]))))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

;; MH-urakoille määrien toteumat, äkilliset hoitotyöt ja lisätyöt
(def default-toteuma-maara {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                            :tehtavaryhma "6 MUUTA"
                            :maara 1
                            :tehtava {:id 3050 :otsikko "Pysäkkikatoksen uusiminen" :yksikko "kpl"}
                            :loppupvm "24.06.2020"
                            :lisatieto nil
                            :tyyppi "kokonaishintainen"})

(def default-akillinen-hoitotyo {:urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
                                 :tehtavaryhma "4 LIIKENTEEN VARMISTAMINEN ERIKOISTILANTEESSA"
                                 :maara nil
                                 :tehtava {:id 3070 :otsikko "Äkillinen hoitotyö (talvihoito)" :yksikko nil}
                                 :loppupvm "25.06.2020"
                                 :lisatieto nil
                                 :tyyppi "akillinen-hoitotyo"})

(defn- muokkaa-toteuman-arvot-palvelua-varten
  "Muokataan toteumat tiedot palvelulle lähetettävään muotoon ja samalla hieman muutetaan arvoja, jotta
  voidaan varmistua siitä, että toteuman muokkaus toimii."
  [t urakka-id]
  {:toteuma-id (:toteuma_id t)
   :toteuma-tehtava-id (:toteuma_tehtava_id t)
   :lisatieto (str (:lisatieto t) "-muokattu")
   :tehtava {:id (:tehtava_id t)
             :otsikko (:tehtava t)
             :yksikko (:yksikko t)}
   :maara (when (:toteutunut t)
            (inc (:toteutunut t)))
   :tyyppi (:tyyppi t)
   :urakka-id urakka-id
   :tehtavaryhma (:toimenpide_otsikko t)
   :loppupvm (pvm/pvm (:toteuma_aika t))})

(defn- lisaa-toteuma [toteuma]
  (kutsu-palvelua (:http-palvelin jarjestelma)
                  :tallenna-toteuma +kayttaja-jvh+
                  toteuma))

;;
(deftest lisaa-maarien-toteuma-test
  (let [_ (lisaa-toteuma default-toteuma-maara)
        alkupvm "2019-10-01"
        loppupvm "2020-09-30"
        ;; :urakan-maarien-toteumat ottaa hakuparametrina: urakka-id tehtavaryhma alkupvm loppupvm
        toteumat-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :urakan-maarien-toteumat +kayttaja-jvh+
                                         {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                          :tehtavaryhma "Kaikki"
                                          :alkupvm alkupvm
                                          :loppupvm loppupvm})

        tallennettu-toteuma (some #(when (= "Pysäkkikatoksen uusiminen" (:tehtava %))
                                     %)
                                  toteumat-vastaus)

        ;; Siivotaan toteuma pois
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :poista-toteuma +kayttaja-jvh+
                          {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                           :toteuma-id (:toteuma_id tallennettu-toteuma)})]
    (is (= 1 (count toteumat-vastaus)) "Yksi lisätty toteuma pitäisi löytyä")))

(deftest lisaa-virheellinen-toteuma-test
  (let [virheellinen-toteuma (assoc default-toteuma-maara :maara -1)]
    (is (thrown? Exception (lisaa-toteuma virheellinen-toteuma)))))

(deftest muokkaa-maarien-toteuma-test
  (let [tallennettu-toteuma (lisaa-toteuma default-toteuma-maara)

        ;; :hae-maarien-toteuma ottaa hakuparametrina: id (toteuma-id)
        haettu-toteuma (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :hae-maarien-toteuma +kayttaja-jvh+
                                       {:id tallennettu-toteuma})

        ;; Muokataan tietoja
        muokattava (muokkaa-toteuman-arvot-palvelua-varten haettu-toteuma (hae-oulun-maanteiden-hoitourakan-2019-2024-id))

        muokattu (lisaa-toteuma muokattava)
        haettu-muokattu-toteuma (kutsu-palvelua (:http-palvelin jarjestelma)
                                                :hae-maarien-toteuma +kayttaja-jvh+
                                                {:id muokattu})
        _ (log/debug "haettu-muokattu-toteuma" (pr-str haettu-muokattu-toteuma))

        ;; Siivotaan toteuma pois
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :poista-toteuma +kayttaja-jvh+
                          {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                           :toteuma-id (:toteuma_id haettu-toteuma)})]

    (is (= (:lisatieto haettu-toteuma) (:lisatieto default-toteuma-maara)) "Toteuman lisätieto täsmää tallennuksen jälkeen")
    (is (= (:tehtava haettu-toteuma) (get-in default-toteuma-maara [:tehtava :otsikko])) "Toteuman tehtava täsmää tallennuksen jälkeen")
    (is (= (:toimenpide_otsikko haettu-toteuma) (:tehtavaryhma default-toteuma-maara)) "Toteuman tehtäväryhmä/toimenpide täsmää tallennuksen jälkeen")
    (is (= (:toteutunut haettu-toteuma) (bigdec (:maara default-toteuma-maara))) "Toteuman määrä täsmää tallennuksen jälkeen")
    (is (= (:yksikko haettu-toteuma) (get-in default-toteuma-maara [:tehtava :yksikko])) "Toteuman yksikkö täsmää tallennuksen jälkeen")
    (is (= (:tyyppi haettu-toteuma) (:tyyppi default-toteuma-maara)) "Toteuman tyyppi täsmää tallennuksen jälkeen")

    ;; Muokattu toteuma
    (is (not (nil? haettu-muokattu-toteuma)))
    (is (= "-muokattu" (:lisatieto haettu-muokattu-toteuma)))
    (is (= (inc (:toteutunut haettu-toteuma)) (:toteutunut haettu-muokattu-toteuma)))))

(deftest lisaa-akillinen-hoitotyo-test
  (let [_ (lisaa-toteuma default-akillinen-hoitotyo)
        alkupvm "2019-10-01"
        loppupvm "2020-09-30"
        ;; :urakan-maarien-toteumat ottaa hakuparametrina: urakka-id tehtavaryhma alkupvm loppupvm
        akillinen-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :urakan-maarien-toteumat +kayttaja-jvh+
                                          {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                           :tehtavaryhma "Kaikki"
                                           :alkupvm alkupvm
                                           :loppupvm loppupvm})

        tallennettu-hoitotyo (some #(when (= "Äkillinen hoitotyö (talvihoito)" (:tehtava %))
                                      %)
                                   akillinen-vastaus)

        ;; Siivotaan toteuma pois
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :poista-toteuma +kayttaja-jvh+
                          {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                           :toteuma-id (:toteuma_id tallennettu-hoitotyo)})]
    (is (= 1 (count akillinen-vastaus)) "Yksi lisätty toteuma pitäisi löytyä")))

(deftest muokkaa-akillinen-hoitotyo-test
  (let [tallennettu-hoitotyo (lisaa-toteuma default-akillinen-hoitotyo)

        ;; :hae-maarien-toteuma ottaa hakuparametrina: id (toteuma-id)
        haettu-hoitotyo (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :hae-akillinen-toteuma +kayttaja-jvh+
                                       {:id tallennettu-hoitotyo})
        muokattava (muokkaa-toteuman-arvot-palvelua-varten haettu-hoitotyo (hae-oulun-maanteiden-hoitourakan-2019-2024-id))
        muokattu (lisaa-toteuma muokattava)
        haettu-muokattu-hoitotyo (kutsu-palvelua (:http-palvelin jarjestelma)
                                                :hae-akillinen-toteuma +kayttaja-jvh+
                                                {:id muokattu})
        ;; Siivotaan toteuma pois
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :poista-toteuma +kayttaja-jvh+
                          {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                           :toteuma-id (:toteuma_id haettu-hoitotyo)})]

    (is (= (:lisatieto haettu-hoitotyo) (:lisatieto default-akillinen-hoitotyo)) "Toteuman lisätieto täsmää tallennuksen jälkeen")
    (is (= (:tehtava haettu-hoitotyo) (get-in default-akillinen-hoitotyo [:tehtava :otsikko])) "Toteuman tehtava täsmää tallennuksen jälkeen")
    (is (= (:toimenpide_otsikko haettu-hoitotyo) (:tehtavaryhma default-akillinen-hoitotyo)) "Toteuman tehtäväryhmä/toimenpide täsmää tallennuksen jälkeen")
    (is (= (:toteutunut haettu-hoitotyo) (:maara default-akillinen-hoitotyo)) "Toteuman määrä täsmää tallennuksen jälkeen")
    (is (= (:yksikko haettu-hoitotyo) (get-in default-akillinen-hoitotyo [:tehtava :yksikko])) "Toteuman yksikkö täsmää tallennuksen jälkeen")
    (is (= (:tyyppi haettu-hoitotyo) (:tyyppi default-akillinen-hoitotyo)) "Toteuman tyyppi täsmää tallennuksen jälkeen")

    ;; Muokattu toteuma
    (is (not (nil? haettu-muokattu-hoitotyo)))
    (is (= "-muokattu" (:lisatieto haettu-muokattu-hoitotyo)))
    (is (nil? (:toteutunut haettu-muokattu-hoitotyo)))))


#_(deftest hae-toteumalistaus-test
    (let [hoitokauden-alkuvuosi 2019
          _ (lisaa-toteuma (hae-oulun-maanteiden-hoitourakan-2019-2024-id) "6 MUUTA" 1
                           {:id 3050 :otsikko "Pysäkkikatoksen uusiminen" :yksikko "kpl"}
                           "24.06.2020" nil)
          alkupvm "2019-10-01"
          loppupvm "2020-09-30"
          ;; :urakan-maarien-toteumat ottaa hakuparametrina: urakka-id tehtavaryhma alkupvm loppupvm
          toteumat-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :urakan-maarien-toteumat +kayttaja-jvh+
                                           {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                            :tehtavaryhma "Kaikki"
                                            :alkupvm alkupvm
                                            :loppupvm loppupvm})
          _ (log/debug "toteumat-vastaus " (pr-str toteumat-vastaus))
          ;; Haetaan suunniteltuja tehtäviä
          ;; tehtavamaara-hierarkia ottaa hakuparametreina: urakka-id hoitokauden-alkuvuosi
          suunnitelmat-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                               :tehtavamaarat-hierarkiassa +kayttaja-jvh+
                                               {:urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
                                                :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
          _ (log/debug "suunnitelmat-vastaus" (pr-str suunnitelmat-vastaus))
          ;; Tarkistetaan, että kaikki 2019 alkavan hoitokauden suunnitellut tehtävät löytyy vastauksesta
          ]
      (is (= 1 (count toteumat-vastaus)) "Yksi lisätty toteuma pitäisi löytyä")))