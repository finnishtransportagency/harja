(ns harja.palvelin.palvelut.yllapitokohteet.muut-kohdeosat-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.yllapitokohteet.muut-kohdeosat :as muut-kohdeosat]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.sonja.sahkoposti :as sahkoposti]
            [harja.palvelin.palvelut.yllapitokohteet :as yllapitokohteet]
            [harja.palvelin.komponentit.fim-test :refer [+testi-fim+]]
            [harja.jms-test :refer [feikki-sonja]]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :muut-kohdeosat (component/using
                                          (muut-kohdeosat/->MuutKohdeosat)
                                          [:http-palvelin :db])
                        :integraatioloki (component/using
                                           (integraatioloki/->Integraatioloki nil)
                                           [:db])
                        :fim (component/using
                               (fim/->FIM +testi-fim+)
                               [:db :integraatioloki])
                        :sonja (feikki-sonja)
                        :sonja-sahkoposti (component/using
                                            (sahkoposti/luo-sahkoposti "foo@example.com"
                                                                       {:sahkoposti-sisaan-jono "email-to-harja"
                                                                        :sahkoposti-ulos-jono "harja-to-email"
                                                                        :sahkoposti-ulos-kuittausjono "harja-to-email-ack"})
                                            [:sonja :db :integraatioloki])
                        :http-palvelin (testi-http-palvelin)
                        :yllapitokohteet (component/using
                                           (yllapitokohteet/->Yllapitokohteet {})
                                           [:http-palvelin :db :fim :sonja-sahkoposti])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures jarjestelma-fixture tietokanta-fixture))

(def ok-data
  [{:id -1
    :tr-numero 20
    :tr-ajorata 1
    :tr-kaista 1
    :tr-alkuosa 10
    :tr-alkuetaisyys 10
    :tr-loppuosa 12
    :tr-loppuetaisyys 1}
   {:id -2
    :tr-numero 20
    :tr-ajorata 1
    :tr-kaista 1
    :tr-alkuosa 19
    :tr-alkuetaisyys 2
    :tr-loppuosa 19
    :tr-loppuetaisyys 5}
   {:id -3
    :tr-numero 20
    :tr-ajorata 1
    :tr-kaista 1
    :tr-alkuosa 21
    :tr-alkuetaisyys 90
    :tr-loppuosa 21
    :tr-loppuetaisyys 100}
   {:id -4
    :tr-numero 20
    :tr-ajorata 1
    :tr-kaista 1
    :tr-alkuosa 21
    :tr-alkuetaisyys 200
    :tr-loppuosa 21
    :tr-loppuetaisyys 300}
   {:id -5
    :tr-numero 1
    :tr-ajorata 1
    :tr-kaista 1
    :tr-alkuosa 1
    :tr-alkuetaisyys 2
    :tr-loppuosa 1
    :tr-loppuetaisyys 3}
   {:id -6
    :tr-numero 20
    :tr-ajorata 2
    :tr-kaista 1
    :tr-alkuosa 12
    :tr-alkuetaisyys 1
    :tr-loppuosa 12
    :tr-loppuetaisyys 3}
   {:id -7
    :tr-numero 20
    :tr-ajorata 1
    :tr-kaista 11
    :tr-alkuosa 12
    :tr-alkuetaisyys 1
    :tr-loppuosa 12
    :tr-loppuetaisyys 3}])

(def keskenaan-paallekkaiset
  (conj ok-data
        {:id -8
         :tr-numero 20
         :tr-ajorata 1
         :tr-kaista 1
         :tr-alkuosa 21
         :tr-alkuetaisyys 99
         :tr-loppuosa 21
         :tr-loppuetaisyys 201}
        {:id -9
         :tr-numero 20
         :tr-ajorata 1
         :tr-kaista 1
         :tr-alkuosa 21
         :tr-alkuetaisyys 220
         :tr-loppuosa 21
         :tr-loppuetaisyys 230}))

(deftest kohdeosa-paalekkain-muiden-kohdeosien-kanssa
  (let [paalekkainen-osa {:id -9
                          :tr-numero 20
                          :tr-ajorata 1
                          :tr-kaista 1
                          :tr-alkuosa 21
                          :tr-alkuetaisyys 220
                          :tr-loppuosa 21
                          :tr-loppuetaisyys 230}
        virhe-sisalto (muut-kohdeosat/kohdeosa-paalekkain-muiden-kohdeosien-kanssa paalekkainen-osa ok-data)]
    (is (= 1 (count virhe-sisalto)))
    (is (= (first virhe-sisalto) {:viesti "Kohdeosa on päälekkäin toisen kohdeosan kanssa"
                                  :validointivirhe :kohteet-paallekain
                                  :kohteet '({:id -4
                                              :tr-numero 20
                                              :tr-ajorata 1
                                              :tr-kaista 1
                                              :tr-alkuosa 21
                                              :tr-alkuetaisyys 200
                                              :tr-loppuosa 21
                                              :tr-loppuetaisyys 300}
                                              {:id -9
                                               :tr-numero 20
                                               :tr-ajorata 1
                                               :tr-kaista 1
                                               :tr-alkuosa 21
                                               :tr-alkuetaisyys 220
                                               :tr-loppuosa 21
                                               :tr-loppuetaisyys 230})}))))

(deftest kohdeosat-keskenaan-paalekkain
  (let [paallekkaiset-osat (muut-kohdeosat/kohdeosat-keskenaan-paallekkain keskenaan-paallekkaiset)
        paallekkaisten-parien-idt (into #{} (map (fn [virhe-sisalto]
                                                   (into #{} (map :id (:kohteet virhe-sisalto))))
                                                 paallekkaiset-osat))]
    (is (= paallekkaisten-parien-idt #{#{-3 -8} #{-4 -8} #{-4 -9}}))))

(deftest tallenna-muut-kohdeosat
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        yllapitokohde-id (ffirst (q (str "SELECT id FROM yllapitokohde WHERE nimi = 'Nakkilan ramppi'")))
        tallenna-palvelu #(kutsu-palvelua
                            (:http-palvelin jarjestelma)
                            :tallenna-muut-kohdeosat +kayttaja-jvh+
                            {:urakka-id urakka-id
                             :yllapitokohde-id yllapitokohde-id
                             :muut-kohdeosat (zipmap (range) %)
                             :vuosi 2017})
        tallennuksen-vastaus (tallenna-palvelu ok-data)
        tallennetut-kannassa (kutsu-palvelua
                               (:http-palvelin jarjestelma)
                               :hae-muut-kohdeosat +kayttaja-jvh+
                               {:urakka-id urakka-id
                                :yllapitokohde-id yllapitokohde-id
                                :vuosi 2017})
        kannassa-olevat-kaksi-kohdetta (sort-by :tr-alkuetaisyys (keep #(when (#{{:tr-numero 20 :tr-ajorata 1 :tr-kaista 1 :tr-alkuosa 21
                                                                                  :tr-alkuetaisyys 90 :tr-loppuosa 21 :tr-loppuetaisyys 100}
                                                                                 {:tr-numero 20 :tr-ajorata 1 :tr-kaista 1 :tr-alkuosa 21
                                                                                  :tr-alkuetaisyys 200 :tr-loppuosa 21 :tr-loppuetaisyys 300}}
                                                                                (select-keys % #{:tr-numero :tr-ajorata :tr-kaista :tr-alkuosa
                                                                                                 :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys}))
                                                                          (select-keys % #{:id :tr-numero :tr-ajorata :tr-kaista :tr-alkuosa
                                                                                           :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys}))
                                                                       tallennetut-kannassa))
        tallenna-paalekkainen-kohde (tallenna-palvelu '({:id -1
                                                         :tr-numero 20
                                                         :tr-ajorata 1
                                                         :tr-kaista 1
                                                         :tr-alkuosa 21
                                                         :tr-alkuetaisyys 220
                                                         :tr-loppuosa 21
                                                         :tr-loppuetaisyys 230}))]
    (is (= tallennuksen-vastaus {:onnistui? true}))
    (is (= (count tallennetut-kannassa) (count ok-data)))
    (is (= (count tallenna-paalekkainen-kohde) 1))
    (is (= (:validointivirhe (first tallenna-paalekkainen-kohde)) :kohteet-paallekain))
    ;; Testaillaan jo frontilla tehtävät validoinnit
    ;; tr-loppuetaisyys puuttuu
    (is (thrown? AssertionError (tallenna-palvelu '({:id -1
                                                     :tr-numero 20
                                                     :tr-ajorata 1
                                                     :tr-kaista 1
                                                     :tr-alkuosa 21
                                                     :tr-alkuetaisyys 220
                                                     :tr-loppuosa 21}))))
    ;; Muihin kohdeosiin ei tulisi tallentaa kohteen sisäisiä osiaM
    (is (thrown? AssertionError (tallenna-palvelu '({:id -1
                                                     :tr-numero 20
                                                     :tr-ajorata 1
                                                     :tr-kaista 1
                                                     :tr-alkuosa 12
                                                     :tr-alkuetaisyys 1
                                                     :tr-loppuosa 12
                                                     :tr-loppuetaisyys 1}))))
    ;; Annetut kohteenosat ovat päällekkäin
    (is (thrown? AssertionError (tallenna-palvelu '({:id -1
                                                     :tr-numero 20
                                                     :tr-ajorata 0
                                                     :tr-kaista 1
                                                     :tr-alkuosa 12
                                                     :tr-alkuetaisyys 1
                                                     :tr-loppuosa 12
                                                     :tr-loppuetaisyys 3}
                                                     {:id -2
                                                      :tr-numero 20
                                                      :tr-ajorata 0
                                                      :tr-kaista 1
                                                      :tr-alkuosa 12
                                                      :tr-alkuetaisyys 2
                                                      :tr-loppuosa 12
                                                      :tr-loppuetaisyys 3}))))
    ;; Tässä tarkastetaan, ettei jo kannassa oleva kohde aiheuta "Kohdeosa on päälekkäin toisen kohdeosan kanssa"
    ;; virhettä, kun sitäkin kohdetta muokataan. Tässä siis otetaan 'ok-datasta' id:llä -3 ja muokataan se
    ;; olamaan päälekkäin id -4 kanssa. Samalla id -4 muokataan siten, etteivät palat menekkään päälekkäin.
    (= {:onnistui? true} (tallenna-palvelu [(merge (first kannassa-olevat-kaksi-kohdetta)
                                                   {:tr-alkuetaisyys 200 :tr-loppuetaisyys 210})
                                            (merge (second kannassa-olevat-kaksi-kohdetta)
                                                   {:tr-alkuetaisyys 210})]))))

(deftest tallenna-muut-kohdeosat-hypyn-paalle
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        leppajarven-yllapitokohde-id (ffirst (q (str "SELECT id FROM yllapitokohde WHERE nimi = 'Leppäjärven ramppi'")))
        nakkilan-yllapitokohde-id (ffirst (q (str "SELECT id FROM yllapitokohde WHERE nimi = 'Nakkilan ramppi'")))
        nakkilan-rampin-kohdeosat (kutsu-palvelua
                                    (:http-palvelin jarjestelma)
                                    :yllapitokohteen-yllapitokohdeosat +kayttaja-jvh+
                                    {:urakka-id urakka-id
                                     ; :sopimus-id
                                     :yllapitokohde-id nakkilan-yllapitokohde-id})
        nakkilan-rampin-hyppy (assoc (some #(when (:hyppy? %)
                                              (select-keys % #{:tr-numero :tr-ajorata :tr-kaista :tr-alkuosa
                                                               :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys}))
                                           nakkilan-rampin-kohdeosat)
                                :id -1)]
    (is (= {:onnistui? true} (kutsu-palvelua
                               (:http-palvelin jarjestelma)
                               :tallenna-muut-kohdeosat +kayttaja-jvh+
                               {:urakka-id urakka-id
                                :yllapitokohde-id leppajarven-yllapitokohde-id
                                :muut-kohdeosat {1 nakkilan-rampin-hyppy}
                                :vuosi 2017})))))
